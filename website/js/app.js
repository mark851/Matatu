import {
  AVATAR_PRESETS,
  BOT_UID,
  CUT_THRESHOLD_POINTS,
  STAKE_TIERS,
  SUITS,
  calculatePoints,
  canTriggerSevenCut,
  effectiveSuit,
  formatUgx,
  isCardPlayable,
  parseCard,
  sortHandCodes,
  suitByName,
} from "./engine.js";
import { SoundEffectsManager } from "./audio.js";
import { fsOps, getFirebaseServices } from "./firebase-config.js";
import { createAmatatuRepository, timestampToSeconds } from "./repository.js";
import {
  PRACTICE_PLAYER_UID,
  createPracticeSession,
  drawPracticeCard,
  playPracticeCard,
  stepPracticeBotTurn,
} from "./practice-mode.js";

// ============================================================================
// DOM & Safe Rendering Utilities
// ============================================================================

const $ = (id) => document.getElementById(id);

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function avatarGlyph(key) {
  return AVATAR_PRESETS.find((a) => a.key === key)?.glyph || "♛";
}

function formatDateSeconds(sec) {
  if (!sec) return "Just now";
  const d = new Date(sec * 1000);
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function showToast(message, isError = false) {
  const stack = $("toast-stack");
  if (!stack) return;
  const el = document.createElement("div");
  el.className = `toast${isError ? " error" : ""}`;
  el.textContent = String(message || "Done");
  stack.appendChild(el);
  setTimeout(() => {
    el.remove();
  }, 4200);
}

function showModal(html) {
  const backdrop = $("modal-backdrop");
  const dialog = $("modal-dialog");
  if (!backdrop || !dialog) return;
  dialog.innerHTML = html;
  backdrop.classList.remove("hidden");
}

function closeModal() {
  const backdrop = $("modal-backdrop");
  const dialog = $("modal-dialog");
  if (backdrop) backdrop.classList.add("hidden");
  if (dialog) dialog.innerHTML = "";
}

// ============================================================================
// Application State
// ============================================================================

const sound = new SoundEffectsManager();
const services = getFirebaseServices();
const repo = createAmatatuRepository(services.db, services.auth, fsOps);

const urlParams = new URLSearchParams(window.location.search);
const initialInviteCode = (urlParams.get("invite") || "").trim().toUpperCase();

const state = {
  authUser: null,
  authReady: false,
  authError: "",
  user: null,
  userLoadError: null,
  isAdmin: false,
  activeNav: "lobby", // lobby | rules | wallet | history | profile | admin
  adminSubTab: "momo", // momo | players | tables | cheats
  selectedStakeUgx: STAKE_TIERS[2].stakeUgx, // UGX 2,500 default
  inviteInput: initialInviteCode,
  sortHand: true,
  isBusy: false,
  mobileMenuOpen: false,

  // Cloud lists
  waitingTables: [],
  myTables: [],
  leaderboard: [],
  transactions: [],
  gameHistory: [],

  // Admin datasets
  adminUsers: [],
  adminTransactions: [],
  adminTables: [],
  adminCheatLogs: [],

  // Active Cloud Table
  activeTableId: null,
  activeTable: null,
  myHand: null,
  botHand: null,
  moves: [],
  settledTableIds: new Set(),
  revealedTableIds: new Set(),
  lastBotMoveKey: null,

  // Offline Practice Session (no account required)
  practiceSession: null,

  // Form drafts (preserved across re-renders)
  walletMode: "DEPOSIT", // DEPOSIT | WITHDRAWAL
  walletProvider: "MTN_MOMO",
  walletAmount: "10000",
  walletPhone: "",
  profileDraft: null,
};

let shellUnsubs = [];
let adminUnsubs = [];
let tableUnsubs = [];
let botHandUnsub = null;

function clearSubs(list) {
  for (const fn of list) {
    try {
      fn?.();
    } catch {
      // ignore unsubscribe error
    }
  }
  list.length = 0;
}

// ============================================================================
// Card HTML Builder (Ivory Card Face + Cut-7 Halo)
// ============================================================================

function renderCardHtml(cardCode, opts = {}) {
  const card = parseCard(cardCode);
  if (!card) return "";
  const {
    playable = false,
    dimmed = false,
    isCutSeven = false,
    size = "",
    interactive = false,
  } = opts;

  const classes = [
    "playing-card",
    card.suit.isRed ? "red" : "",
    size,
    playable ? "playable" : "",
    dimmed ? "dimmed" : "",
    isCutSeven ? "cut-seven" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const tag = interactive ? "button" : "div";
  const attrs = interactive
    ? `type="button" data-action="card-click" data-card="${escapeHtml(card.code)}" aria-label="Play ${escapeHtml(card.displayLabel)} (${card.points} points)"`
    : `aria-label="${escapeHtml(card.displayLabel)}"`;

  return `
    <${tag} class="${classes}" ${attrs}>
      <div class="card-corner-top">
        <div class="card-rank-suit">
          <span>${escapeHtml(card.rank.symbol)}</span>
          <span>${escapeHtml(card.suit.symbol)}</span>
        </div>
        <span class="card-pts-tag">${card.points}p</span>
      </div>
      <div class="card-center-pip">${escapeHtml(card.suit.symbol)}</div>
      <div class="card-corner-bottom">
        <span>${escapeHtml(card.rank.symbol)}</span>
        <span>${escapeHtml(card.suit.symbol)}</span>
      </div>
      ${isCutSeven ? '<span class="cut-chop-ribbon">CHOP</span>' : ""}
    </${tag}>
  `;
}

// ============================================================================
// Header & Root Webpage Rendering
// ============================================================================

function syncHeaderControls() {
  if (state.user) {
    sound.isEnabled = state.user.soundEnabled !== false;
  }
  const soundBtn = $("header-sound-btn");
  if (soundBtn) soundBtn.textContent = sound.isEnabled ? "🔊" : "🔇";

  const signedIn = Boolean(state.authUser);
  const showWallet = signedIn && state.user;

  $("nav-wallet-desktop")?.classList.toggle("hidden", !signedIn);
  $("nav-history-desktop")?.classList.toggle("hidden", !signedIn);
  $("nav-profile-desktop")?.classList.toggle("hidden", !signedIn);
  $("nav-admin-desktop")?.classList.toggle("hidden", !(signedIn && state.isAdmin));

  $("nav-wallet-mobile")?.classList.toggle("hidden", !signedIn);
  $("nav-history-mobile")?.classList.toggle("hidden", !signedIn);
  $("nav-profile-mobile")?.classList.toggle("hidden", !signedIn);
  $("nav-admin-mobile")?.classList.toggle("hidden", !(signedIn && state.isAdmin));
  $("nav-signout-mobile")?.classList.toggle("hidden", !signedIn);

  const walletChip = $("header-wallet-chip");
  if (walletChip) {
    walletChip.classList.toggle("hidden", !showWallet);
    if (showWallet) walletChip.textContent = formatUgx(state.user.walletBalanceUgx);
  }

  const signBtn = $("header-signin-btn");
  if (signBtn) {
    signBtn.textContent = signedIn ? "Sign Out" : "Sign In";
    signBtn.className = signedIn ? "btn btn-outline btn-sm" : "btn btn-primary btn-sm";
  }

  document.querySelectorAll("[data-nav]").forEach((btn) => {
    const isActive = !state.activeTableId && !state.practiceSession && btn.dataset.nav === state.activeNav;
    btn.classList.toggle("active", isActive);
  });

  const drawer = $("mobile-web-drawer");
  const toggleBtn = $("mobile-menu-toggle");
  if (drawer && toggleBtn) {
    drawer.classList.toggle("hidden", !state.mobileMenuOpen);
    toggleBtn.setAttribute("aria-expanded", String(state.mobileMenuOpen));
  }
}

function renderRoot() {
  syncHeaderControls();
  const root = $("page-root");
  if (!root) return;

  // Preserve active input cursor when Firestore background snapshot fires
  const activeEl = document.activeElement;
  const activeId = activeEl?.id || "";
  const selStart = activeEl && "selectionStart" in activeEl ? activeEl.selectionStart : null;
  const selEnd = activeEl && "selectionEnd" in activeEl ? activeEl.selectionEnd : null;

  // Embedded Live Game Table (Offline Practice or Online Cloud Match)
  if (state.practiceSession || state.activeTableId) {
    renderEmbeddedTableView(root);
    return;
  }

  // Dedicated How To Play Webpage Section (accessible logged-out or logged-in)
  if (state.activeNav === "rules") {
    root.innerHTML = renderRulesPageHtml();
    return;
  }

  // Public Website Landing Page (when not signed in)
  if (!state.authUser) {
    root.innerHTML = renderPublicHomeWebsiteHtml();
    return;
  }

  // Authenticated User Loading / Error
  if (!state.user) {
    if (state.userLoadError) {
      root.innerHTML = `
        <div class="surface-card">
          <h2 class="danger-text">Unable to load your Kampala wallet</h2>
          <p class="muted" style="margin:8px 0 14px;">${escapeHtml(state.userLoadError)}</p>
          <button class="btn btn-primary" type="button" data-action="signout">Sign Out &amp; Retry</button>
        </div>
      `;
    } else {
      root.innerHTML = `
        <div class="surface-card">
          <h2>Loading your Kampala wallet &amp; live tables...</h2>
          <p class="muted" style="margin-top:6px;">Syncing your starter UGX chips and Firestore listeners across Android &amp; Web.</p>
        </div>
      `;
    }
    return;
  }

  // Authenticated Website Pages
  if (state.activeNav === "wallet") renderWalletTab(root, state.user);
  else if (state.activeNav === "history") renderHistoryTab(root, state.user);
  else if (state.activeNav === "profile") renderProfileTab(root, state.user);
  else if (state.activeNav === "admin" && state.isAdmin) renderAdminTab(root);
  else {
    state.activeNav = "lobby";
    renderLobbyTab(root, state.user);
  }

  if (activeId && $(activeId)) {
    const restored = $(activeId);
    restored.focus();
    if (selStart !== null && selEnd !== null && "setSelectionRange" in restored) {
      try {
        restored.setSelectionRange(selStart, selEnd);
      } catch {
        // ignore selection errors
      }
    }
  }
}

// ============================================================================
// Public Website Homepage (Visitors before Google Sign-In)
// ============================================================================

function renderPublicHomeWebsiteHtml() {
  return `
    ${
      initialInviteCode
        ? `<div class="invite-banner">
            <div>
              <span class="pill pill-gold">PRIVATE ROOM INVITE</span>
              <strong style="margin-left:8px;">Code <span class="mono gold">${escapeHtml(initialInviteCode)}</span> is ready!</strong>
              <span class="muted" style="font-size:0.88rem;margin-left:6px;">Sign in with Google to join your challenger on Android or Desktop.</span>
            </div>
            <button class="btn btn-primary btn-sm" type="button" data-action="google-signin">Sign In &amp; Join</button>
          </div>`
        : ""
    }

    ${state.authError ? `<div class="error-banner">${escapeHtml(state.authError)}</div>` : ""}

    <section class="web-hero-grid">
      <div class="web-hero-copy">
        <span class="pill pill-gold">KAMPALA'S UGANDAN CARD PARLOUR • WEB EDITION</span>
        <h1>Play Official Ugandan Amatatu Online</h1>
        <p class="muted" style="margin-top:14px;font-size:1.02rem;max-width:560px;">
          Play directly from your laptop, tablet, or phone browser—synced live with the Android club.
          Join UGX escrow tables, stack Pick-Two attacks, shield with <strong class="gold">A♠</strong>, and win 7-Cut Showdowns.
        </p>

        <div style="display:flex;flex-wrap:wrap;gap:12px;margin-top:22px;">
          <button class="btn btn-primary" type="button" data-action="launch-practice">
            Play Offline Practice vs Bot
          </button>
          <button class="btn btn-emerald" type="button" data-action="google-signin">
            Sign In with Google (UGX 25,000 Starter Chips)
          </button>
          <button class="btn btn-outline" type="button" data-nav="rules">
            Read Official Rules
          </button>
        </div>

        <p class="muted" style="font-size:0.82rem;margin-top:14px;">
          Instant browser practice requires no account. Google Sign-In unlocks multiplayer UGX tables, private <span class="mono">AMT</span> room links, and the Sandbox Mobile Money cashier ledger.
        </p>
      </div>

      <div class="web-hero-visual">
        <img src="./assets/img_amatatu_hero.jpg" alt="Amatatu Ugandan card game parlour" />
        <div class="web-hero-visual-scrim"></div>
        <div class="web-hero-cards">
          ${renderCardHtml("AS", { playable: true })}
          ${renderCardHtml("7H", { isCutSeven: true, playable: true })}
          ${renderCardHtml("2D", { playable: true })}
        </div>
        <div style="position:relative;z-index:2;text-align:center;">
          <span class="pill pill-success">CROSS-PLATFORM ANDROID + WEB SYNC</span>
        </div>
      </div>
    </section>

    <section class="features-three-col">
      <article class="surface-card">
        <h3 class="gold">Real-Time Online Tables</h3>
        <p class="muted" style="margin-top:6px;font-size:0.9rem;">
          Match instantly in public lobbies, host private 2–4 player rooms with shareable <span class="mono">AMT</span> invite links, or warm up against Kampala Master Bot.
        </p>
      </article>
      <article class="surface-card">
        <h3 class="gold">95% Winner Escrow Pots</h3>
        <p class="muted" style="margin-top:6px;font-size:0.9rem;">
          Table stakes lock safely into player escrow on entry and settle automatically to the winner's UGX wallet across both Web and Android.
        </p>
      </article>
      <article class="surface-card">
        <h3 class="gold">SHA-256 Anti-Cheat Audit</h3>
        <p class="muted" style="margin-top:6px;font-size:0.9rem;">
          Each match commits a SHA-256 deck hash, isolates secret hands per UID, and logs every card play and draw digest transparently.
        </p>
      </article>
    </section>

    <section class="surface-card gold-border">
      <div class="section-header">
        <div>
          <h2>Kampala Table Stakes &amp; Payouts</h2>
          <p class="section-subtitle">Choose stakes from free practice up to Royal Pot tables (winner receives 95% of pool)</p>
        </div>
        <button class="btn btn-primary btn-sm" type="button" data-action="google-signin">Sign In to Join Tables</button>
      </div>
      <div class="stake-grid">
        ${STAKE_TIERS.map(
          (t) => `
            <div class="stake-card">
              <span class="pill" style="font-size:0.68rem;align-self:flex-start;">${escapeHtml(t.tag)}</span>
              <strong style="margin-top:4px;">${escapeHtml(t.label)}</strong>
              <span class="mono gold" style="font-size:0.95rem;">${t.stakeUgx === 0 ? "FREE" : formatUgx(t.stakeUgx)}</span>
              <span class="muted" style="font-size:0.78rem;">${escapeHtml(t.description)}</span>
            </div>
          `
        ).join("")}
      </div>
    </section>

    ${renderRulesGuideHtml()}
  `;
}

function renderRulesPageHtml() {
  return `
    <div style="display:flex;flex-wrap:wrap;justify-content:space-between;align-items:center;gap:12px;">
      <div>
        <h1 style="font-size:1.7rem;">Official Ugandan Amatatu Rules</h1>
        <p class="muted">Complete card guide, 7-Cut Showdown rules, point scoring, and fair-play protections</p>
      </div>
      <div style="display:flex;gap:10px;flex-wrap:wrap;">
        <button class="btn btn-primary btn-sm" type="button" data-action="launch-practice">Try Offline Practice</button>
        <button class="btn btn-outline btn-sm" type="button" data-nav="lobby">← Back to Tables</button>
      </div>
    </div>
    ${renderRulesGuideHtml()}
  `;
}

// ============================================================================
// Authenticated Lobby Tab
// ============================================================================

function renderLobbyTab(container, user) {
  const tier = STAKE_TIERS.find((t) => t.stakeUgx === state.selectedStakeUgx) || STAKE_TIERS[2];
  const openTables = state.waitingTables.filter((t) => !(t.playerIds || []).includes(user.userId));

  container.innerHTML = `
    <div class="hero-banner">
      <img src="./assets/img_amatatu_hero.jpg" alt="" />
      <div class="hero-banner-scrim"></div>
      <div class="hero-banner-body">
        <div>
          <span class="pill pill-gold">KAMPALA PARLOUR • LIVE CLOUD TABLES</span>
          <h1 style="margin-top:8px;font-size:1.65rem;">Welcome back, ${escapeHtml(user.displayName)}</h1>
          <p class="muted" style="font-size:0.92rem;margin-top:4px;">
            Available: <strong class="gold mono">${formatUgx(user.walletBalanceUgx)}</strong>
            ${user.escrowUgx > 0 ? ` • Locked in Escrow: <strong class="mono">${formatUgx(user.escrowUgx)}</strong>` : ""}
          </p>
        </div>
        <div style="display:flex;gap:10px;flex-wrap:wrap;">
          <button class="btn btn-outline btn-sm" type="button" data-action="launch-practice">Offline Practice Mode</button>
        </div>
      </div>
    </div>

    ${user.isBanned ? `<div class="error-banner">Your account is currently suspended from joining or creating UGX tables. Contact the club Admin.</div>` : ""}

    <div class="lobby-grid">
      <div style="display:flex;flex-direction:column;gap:18px;">
        <section class="surface-card gold-border">
          <div class="section-header">
            <div>
              <h2>1. Select Table Stake</h2>
              <p class="section-subtitle">${escapeHtml(tier.description)}</p>
            </div>
            <span class="pill pill-gold mono">${formatUgx(tier.stakeUgx)}</span>
          </div>

          <div class="stake-grid">
            ${STAKE_TIERS.map((t) => {
              const selected = t.stakeUgx === state.selectedStakeUgx;
              return `
                <button type="button" class="stake-card ${selected ? "selected" : ""}" data-action="select-stake" data-stake="${t.stakeUgx}">
                  <div style="display:flex;justify-content:space-between;align-items:center;gap:6px;">
                    <span class="pill ${selected ? "pill-gold" : ""}" style="font-size:0.68rem;">${escapeHtml(t.tag)}</span>
                  </div>
                  <div style="font-weight:700;margin-top:4px;">${escapeHtml(t.label)}</div>
                  <div class="mono gold" style="font-size:0.9rem;">${t.stakeUgx === 0 ? "FREE" : formatUgx(t.stakeUgx)}</div>
                </button>
              `;
            }).join("")}
          </div>

          <div class="action-row" style="margin-top:16px;">
            <button class="btn btn-primary" type="button" data-action="quick-match" ${state.isBusy ? "disabled" : ""}>
              ⚡ Quick Match Online
            </button>
            <button class="btn btn-emerald" type="button" data-action="play-bot" ${state.isBusy ? "disabled" : ""}>
              🤖 Play vs Kampala Bot
            </button>
            <button class="btn btn-outline" type="button" data-action="open-private-modal" ${state.isBusy ? "disabled" : ""}>
              🔑 Create Private Room
            </button>
          </div>
        </section>

        <section class="surface-card">
          <div class="section-header">
            <div>
              <h3>Join Private Invite Room</h3>
              <p class="section-subtitle">Enter an <span class="mono">AMT</span> code shared from Android or Web</p>
            </div>
          </div>
          <form id="form-join-invite" style="display:flex;gap:10px;flex-wrap:wrap;">
            <input
              id="input-invite-code"
              class="input mono"
              style="flex:1;min-width:180px;text-transform:uppercase;"
              placeholder="e.g. AMT7K2P"
              maxlength="12"
              value="${escapeHtml(state.inviteInput)}"
            />
            <button class="btn btn-primary" type="submit" ${state.isBusy ? "disabled" : ""}>Join Room</button>
          </form>
        </section>

        <section class="surface-card">
          <div class="section-header">
            <div>
              <h3>My Tables</h3>
              <p class="section-subtitle">Resume active matches or manage hosted rooms</p>
            </div>
            <span class="pill">${state.myTables.length}</span>
          </div>
          ${
            state.myTables.length === 0
              ? `<p class="muted" style="font-size:0.9rem;">No joined tables yet. Launch a match above to begin.</p>`
              : `<div class="stack-list">
                  ${state.myTables.slice(0, 8).map((tbl) => renderLobbyTableRow(tbl, user.userId, true)).join("")}
                </div>`
          }
        </section>

        <section class="surface-card">
          <div class="section-header">
            <div>
              <h3>Open Public Tables</h3>
              <p class="section-subtitle">Challengers waiting across Android &amp; Desktop</p>
            </div>
            <span class="pill pill-success">${openTables.length} Open</span>
          </div>
          ${
            openTables.length === 0
              ? `<p class="muted" style="font-size:0.9rem;">No open public challengers waiting right now. Click Quick Match to open one!</p>`
              : `<div class="stack-list">
                  ${openTables.map((tbl) => renderLobbyTableRow(tbl, user.userId, false)).join("")}
                </div>`
          }
        </section>
      </div>

      <div style="display:flex;flex-direction:column;gap:18px;">
        <section class="surface-card">
          <div class="section-header">
            <div>
              <h3>Kampala Hall of Fame</h3>
              <p class="section-subtitle">Top players across Android &amp; Web</p>
            </div>
            <span class="pill pill-gold">LIVE</span>
          </div>
          ${
            state.leaderboard.length === 0
              ? `<p class="muted" style="font-size:0.88rem;">Be the first to claim a Kampala victory!</p>`
              : `<div class="stack-list">
                  ${state.leaderboard
                    .slice(0, 10)
                    .map((p, idx) => `
                      <div class="list-item-card" style="padding:10px 12px;">
                        <div style="display:flex;align-items:center;gap:10px;min-width:0;">
                          <span class="mono gold" style="font-weight:800;width:22px;">#${idx + 1}</span>
                          <span class="avatar-badge" style="width:32px;height:32px;font-size:0.85rem;">${escapeHtml(avatarGlyph(p.avatarKey))}</span>
                          <div style="min-width:0;">
                            <div style="font-weight:700;font-size:0.9rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(p.displayName)}</div>
                            <div class="muted" style="font-size:0.75rem;">${p.gamesWon} wins • ${p.gamesPlayed} played</div>
                          </div>
                        </div>
                        <div class="mono gold" style="font-size:0.8rem;font-weight:700;">${formatUgx(p.totalEarningsUgx)}</div>
                      </div>
                    `)
                    .join("")}
                </div>`
          }
        </section>

        <section class="surface-card">
          <div class="section-header">
            <h3>7-Cut Quick Reference</h3>
          </div>
          <p class="muted" style="font-size:0.88rem;">
            Keep hand points low (<strong class="gold">≤ 25 pts</strong> remaining), drop the <strong class="gold">7 of the Cut Suit</strong>, and force an instant Showdown.
            Stack <strong class="gold">2s</strong> (+2, +4, +6...) or shield with <strong class="gold">A♠</strong>!
          </p>
          <button class="btn btn-outline btn-sm btn-block" style="margin-top:12px;" type="button" data-nav="rules">
            Full Rules &amp; Point Values
          </button>
        </section>
      </div>
    </div>
  `;
}

function renderLobbyTableRow(tbl, uid, isMember) {
  const count = (tbl.playerIds || []).length;
  const max = Number(tbl.maxPlayers || 2);
  const statusPillClass =
    tbl.status === "ACTIVE" || tbl.status === "SHOWDOWN"
      ? "pill-success"
      : tbl.status === "WAITING"
      ? "pill-warning"
      : "";
  const stakeLabel = Number(tbl.stakeUgx || 0) === 0 ? "FREE" : formatUgx(tbl.stakeUgx);

  return `
    <div class="list-item-card">
      <div style="min-width:0;">
        <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
          <strong>${escapeHtml(tbl.title)}</strong>
          <span class="pill ${statusPillClass}">${escapeHtml(tbl.status)}</span>
          ${tbl.inviteCode ? `<span class="pill pill-gold mono">${escapeHtml(tbl.inviteCode)}</span>` : ""}
        </div>
        <div class="muted" style="font-size:0.82rem;margin-top:4px;">
          Stake <span class="mono gold">${stakeLabel}</span> • Seats ${count}/${max} • ${escapeHtml(tbl.lastActionSummary || "")}
        </div>
      </div>
      <div>
        ${
          isMember
            ? `<button class="btn btn-emerald btn-sm" type="button" data-action="resume-table" data-table-id="${escapeHtml(tbl.id)}">Open Table</button>`
            : `<button class="btn btn-primary btn-sm" type="button" data-action="join-public-table" data-table-id="${escapeHtml(tbl.id)}" ${state.isBusy ? "disabled" : ""}>Join (${stakeLabel})</button>`
        }
      </div>
    </div>
  `;
}

// ============================================================================
// Wallet & Mobile Money Cashier Tab
// ============================================================================

function renderWalletTab(container, user) {
  const quickAmounts = [5000, 10000, 25000, 50000];

  container.innerHTML = `
    <div class="hero-banner" style="min-height:145px;">
      <img src="./assets/img_momo_cashier.jpg" alt="" />
      <div class="hero-banner-scrim"></div>
      <div class="hero-banner-body">
        <div>
          <span class="pill pill-warning">SANDBOX CASHIER VERIFICATION LEDGER</span>
          <h1 style="margin-top:8px;font-size:1.55rem;">Mobile Money &amp; UGX Wallet</h1>
          <p class="muted" style="font-size:0.88rem;margin-top:4px;">
            Deposits &amp; withdrawals record to the shared Firestore ledger for Admin cashier verification (no live carrier billing).
          </p>
        </div>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi-box">
        <span class="muted" style="font-size:0.8rem;">Available Wallet</span>
        <span class="kpi-value">${formatUgx(user.walletBalanceUgx)}</span>
      </div>
      <div class="kpi-box">
        <span class="muted" style="font-size:0.8rem;">Locked in Table Escrow</span>
        <span class="kpi-value" style="color:var(--warm-white);">${formatUgx(user.escrowUgx)}</span>
      </div>
      <div class="kpi-box">
        <span class="muted" style="font-size:0.8rem;">Career Table Winnings</span>
        <span class="kpi-value success-text">${formatUgx(user.totalEarningsUgx)}</span>
      </div>
    </div>

    <div class="split-two">
      <section class="surface-card gold-border">
        <div class="segmented-bar" style="margin-bottom:14px;">
          <button type="button" class="seg-btn ${state.walletMode === "DEPOSIT" ? "active" : ""}" data-action="set-wallet-mode" data-mode="DEPOSIT">Deposit UGX</button>
          <button type="button" class="seg-btn ${state.walletMode === "WITHDRAWAL" ? "active" : ""}" data-action="set-wallet-mode" data-mode="WITHDRAWAL">Withdraw Cashout</button>
        </div>

        <form id="form-momo-cashier" style="display:grid;gap:12px;">
          <div>
            <span class="field-label">Mobile Money Network</span>
            <div style="display:flex;gap:8px;">
              <button type="button" class="seg-btn ${state.walletProvider === "MTN_MOMO" ? "active" : ""}" style="flex:1;" data-action="set-wallet-provider" data-provider="MTN_MOMO">MTN MoMo</button>
              <button type="button" class="seg-btn ${state.walletProvider === "AIRTEL_MONEY" ? "active" : ""}" style="flex:1;" data-action="set-wallet-provider" data-provider="AIRTEL_MONEY">Airtel Money</button>
            </div>
          </div>

          <div>
            <label class="field-label" for="input-momo-amount">Amount (UGX)</label>
            <input id="input-momo-amount" class="input mono" type="number" min="1000" max="5000000" step="500" value="${escapeHtml(state.walletAmount)}" />
            <div class="momo-quick-amounts" style="margin-top:8px;">
              ${quickAmounts.map((amt) => `<button type="button" class="btn btn-outline btn-sm mono" data-action="set-wallet-amount" data-amount="${amt}">${formatUgx(amt)}</button>`).join("")}
            </div>
          </div>

          <div>
            <label class="field-label" for="input-momo-phone">Ugandan MoMo Phone Number</label>
            <input id="input-momo-phone" class="input mono" type="tel" placeholder="+256 772 123 456" maxlength="20" value="${escapeHtml(state.walletPhone || user.phoneNumber || "")}" />
          </div>

          <button class="btn btn-primary btn-block" type="submit" ${state.isBusy ? "disabled" : ""}>
            ${state.walletMode === "DEPOSIT" ? "Submit Deposit Request" : "Request Cashout Withdrawal"}
          </button>
        </form>
      </section>

      <section class="surface-card">
        <div class="section-header">
          <div>
            <h3>Wallet Ledger &amp; MoMo History</h3>
            <p class="section-subtitle">Escrow locks, table payouts, deposits &amp; cashouts</p>
          </div>
          <span class="pill">${state.transactions.length}</span>
        </div>

        ${
          state.transactions.length === 0
            ? `<p class="muted">No transactions recorded yet.</p>`
            : `<div class="stack-list" style="max-height:420px;overflow-y:auto;">
                ${state.transactions
                  .map((tx) => {
                    const statusClass =
                      tx.status === "COMPLETED"
                        ? "pill-success"
                        : tx.status === "PENDING"
                        ? "pill-warning"
                        : "pill-danger";
                    const netSign = ["DEPOSIT", "WIN_PAYOUT", "REFUND", "WELCOME_BONUS"].includes(tx.type) ? "+" : "-";
                    return `
                      <div class="list-item-card" style="padding:11px 13px;">
                        <div style="min-width:0;">
                          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
                            <strong>${escapeHtml(tx.type.replaceAll("_", " "))}</strong>
                            <span class="pill ${statusClass}">${escapeHtml(tx.status)}</span>
                          </div>
                          <div class="muted" style="font-size:0.8rem;margin-top:3px;">
                            Ref <span class="mono">${escapeHtml(tx.referenceCode)}</span> • ${escapeHtml(tx.provider)}
                            ${tx.phoneNumber ? ` (${escapeHtml(tx.phoneNumber)})` : ""}
                          </div>
                          <div class="muted" style="font-size:0.76rem;margin-top:2px;">${escapeHtml(tx.note)}</div>
                        </div>
                        <div style="text-align:right;flex-shrink:0;">
                          <div class="mono ${netSign === "+" ? "success-text" : "gold"}" style="font-weight:700;">
                            ${netSign}${formatUgx(tx.netAmountUgx ?? tx.amountUgx)}
                          </div>
                          <div class="muted" style="font-size:0.72rem;">${formatDateSeconds(timestampToSeconds(tx.createdAt))}</div>
                        </div>
                      </div>
                    `;
                  })
                  .join("")}
              </div>`
        }
      </section>
    </div>
  `;
}

// ============================================================================
// Match History & Rules Guide Components
// ============================================================================

function renderHistoryTab(container, user) {
  const winRate = user.gamesPlayed > 0 ? Math.round((user.gamesWon / user.gamesPlayed) * 100) : 0;

  container.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;">
      <div>
        <h1 style="font-size:1.6rem;">Match History &amp; Audit Records</h1>
        <p class="muted">Completed games, 7-Cut Showdown scores, and SHA-256 verification</p>
      </div>
      <button class="btn btn-outline btn-sm" type="button" data-nav="rules">Open Amatatu Rules</button>
    </div>

    <div class="kpi-grid">
      <div class="kpi-box"><span class="muted" style="font-size:0.8rem;">Matches Played</span><span class="kpi-value">${user.gamesPlayed}</span></div>
      <div class="kpi-box"><span class="muted" style="font-size:0.8rem;">Wins</span><span class="kpi-value success-text">${user.gamesWon}</span></div>
      <div class="kpi-box"><span class="muted" style="font-size:0.8rem;">Win Rate</span><span class="kpi-value">${winRate}%</span></div>
    </div>

    <section class="surface-card">
      ${
        state.gameHistory.length === 0
          ? `<p class="muted">No completed matches yet. Completed bot &amp; online tables appear here with verified deck hashes and UGX settlement.</p>`
          : `<div class="stack-list">
              ${state.gameHistory
                .map((rec) => {
                  const iWon = rec.winnerId === user.userId;
                  const scores = Object.entries(rec.finalScores || {})
                    .map(([pid, pts]) => `${escapeHtml((rec.playerNames && rec.playerNames[pid]) || "Player")}: ${pts}p`)
                    .join(" • ");
                  return `
                    <div class="list-item-card" style="align-items:flex-start;">
                      <div style="display:grid;gap:4px;">
                        <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
                          <strong>${escapeHtml(rec.tableTitle)}</strong>
                          <span class="pill ${iWon ? "pill-success" : ""}">${iWon ? "WON" : "LOST"}</span>
                          <span class="pill ${rec.antiCheatVerified ? "pill-gold" : "pill-warning"}">
                            ${rec.antiCheatVerified ? "Deck Hash Verified" : "Flagged"}
                          </span>
                        </div>
                        <div class="gold" style="font-size:0.88rem;">
                          Winner: ${escapeHtml(rec.winnerName)} • ${escapeHtml(String(rec.winReason || "").replaceAll("_", " "))}
                        </div>
                        <div class="muted" style="font-size:0.82rem;">
                          ${Number(rec.stakeUgx || 0) === 0 ? "Free Practice" : `Stake ${formatUgx(rec.stakeUgx)} • Winner Payout ${formatUgx(rec.payoutUgx)}`} • ${rec.moveCount || 0} moves
                          ${scores ? `<br/>Scores: ${scores}` : ""}
                        </div>
                        <div class="mono muted" style="font-size:0.74rem;">
                          Commit: ${escapeHtml((rec.deckCommitHash || "").slice(0, 16))}...
                        </div>
                      </div>
                    </div>
                  `;
                })
                .join("")}
            </div>`
      }
    </section>
  `;
}

function renderRulesGuideHtml() {
  return `
    <div style="display:grid;gap:14px;">
      <section class="surface-card gold-border">
        <h3 class="gold">1. The Deal &amp; Cut Suit</h3>
        <p class="muted" style="margin-top:6px;">
          Played with a 52-card deck for 2 to 4 players. Everyone receives 7 cards. One starter card opens the Discard pile, and one exposed card establishes the table's <strong style="color:var(--warm-white);">Cut Suit (Chopper Suit)</strong>.
        </p>
      </section>

      <section class="surface-card">
        <h3 class="gold" style="margin-bottom:12px;">2. Special Power Cards</h3>
        <div class="stack-list">
          <div class="list-item-card" style="justify-content:flex-start;">
            ${renderCardHtml("2H", { size: "sm" })}
            <div>
              <strong>2 — Pick Two (Stackable Attack)</strong>
              <p class="muted" style="font-size:0.86rem;">Next player draws 2 cards — or defends by stacking another 2 (+4, +6, +8...) or blocking with the Ace of Spades.</p>
            </div>
          </div>
          <div class="list-item-card" style="justify-content:flex-start;">
            ${renderCardHtml("AS", { size: "sm" })}
            <div>
              <strong>A♠ — Wild Suit &amp; Attack Shield</strong>
              <p class="muted" style="font-size:0.86rem;">Any Ace can be played anytime to demand a new suit. Specifically <strong>A♠</strong> also neutralizes an active Pick-Two attack stack!</p>
            </div>
          </div>
          <div class="list-item-card" style="justify-content:flex-start;">
            ${renderCardHtml("JC", { size: "sm" })}
            <div>
              <strong>J or 8 — Jump / Skip Turn</strong>
              <p class="muted" style="font-size:0.86rem;">Skips the next challenger. On 2-player tables, jumping gives you an immediate extra turn.</p>
            </div>
          </div>
          <div class="list-item-card" style="justify-content:flex-start;">
            ${renderCardHtml("KD", { size: "sm" })}
            <div>
              <strong>K — Reverse Direction</strong>
              <p class="muted" style="font-size:0.86rem;">Reverses clockwise / counter-clockwise turn direction on 3–4 player tables.</p>
            </div>
          </div>
          <div class="list-item-card" style="justify-content:flex-start;">
            ${renderCardHtml("7H", { size: "sm", isCutSeven: true })}
            <div>
              <strong>7 of Cut Suit — The Chopper (7-Cut Showdown)</strong>
              <p class="muted" style="font-size:0.86rem;">If your REMAINING cards total <strong>${CUT_THRESHOLD_POINTS} points or less</strong>, dropping the 7 of the Cut Suit triggers a Showdown! Lowest hand points wins the pot.</p>
            </div>
          </div>
        </div>
      </section>

      <div class="split-two">
        <section class="surface-card">
          <h3 class="gold">3. Card Point Values</h3>
          <p class="muted" style="margin-top:6px;font-size:0.9rem;">
            • <strong class="mono">2</strong> = 20 pts<br/>
            • <strong class="mono">Ace (A)</strong> = 15 pts<br/>
            • <strong class="mono">K / Q / J</strong> = 13 / 12 / 11 pts<br/>
            • <strong class="mono">3 to 10</strong> = Face value (3–10 pts)
          </p>
        </section>
        <section class="surface-card">
          <h3 class="gold">4. UGX Escrow &amp; Anti-Cheat</h3>
          <p class="muted" style="margin-top:6px;font-size:0.9rem;">
            Entry stakes lock safely into Escrow on join. Winners automatically receive 95% of the total pot (5% parlour fee). Every deck commits a SHA-256 hash and isolates hands by UID.
          </p>
        </section>
      </div>
    </div>
  `;
}

// ============================================================================
// Profile Tab
// ============================================================================

function renderProfileTab(container, user) {
  if (!state.profileDraft || state.profileDraft.userId !== user.userId) {
    state.profileDraft = {
      userId: user.userId,
      displayName: user.displayName || "Kampala Player",
      phoneNumber: user.phoneNumber || "",
      preferredProvider: user.preferredProvider || "MTN_MOMO",
      avatarKey: user.avatarKey || "crown_gold",
      soundEnabled: user.soundEnabled !== false,
    };
  }
  const draft = state.profileDraft;

  container.innerHTML = `
    <div class="split-two">
      <section class="surface-card gold-border">
        <div class="section-header">
          <div>
            <h2>Player Identity &amp; Preferences</h2>
            <p class="section-subtitle">Synced immediately with Android &amp; Desktop sessions</p>
          </div>
          ${state.isAdmin ? `<span class="pill pill-gold">ADMIN VERIFIED</span>` : ""}
        </div>

        <form id="form-profile" style="display:grid;gap:14px;">
          <div>
            <label class="field-label" for="input-profile-name">Display Name</label>
            <input id="input-profile-name" class="input" maxlength="36" required value="${escapeHtml(draft.displayName)}" />
          </div>

          <div>
            <label class="field-label" for="input-profile-phone">Default MoMo Phone Number</label>
            <input id="input-profile-phone" class="input mono" maxlength="20" placeholder="+256 772 000 000" value="${escapeHtml(draft.phoneNumber)}" />
          </div>

          <div>
            <span class="field-label">Preferred Mobile Money Provider</span>
            <div style="display:flex;gap:8px;">
              <button type="button" class="seg-btn ${draft.preferredProvider === "MTN_MOMO" ? "active" : ""}" style="flex:1;" data-action="draft-provider" data-provider="MTN_MOMO">MTN MoMo</button>
              <button type="button" class="seg-btn ${draft.preferredProvider === "AIRTEL_MONEY" ? "active" : ""}" style="flex:1;" data-action="draft-provider" data-provider="AIRTEL_MONEY">Airtel Money</button>
            </div>
          </div>

          <div>
            <span class="field-label">Parlour Crest Avatar</span>
            <div class="avatar-grid">
              ${AVATAR_PRESETS.map((av) => `
                <button type="button" class="avatar-option ${draft.avatarKey === av.key ? "selected" : ""}" data-action="draft-avatar" data-avatar="${av.key}">
                  <span class="avatar-badge">${escapeHtml(av.glyph)}</span>
                  <span style="font-size:0.78rem;font-weight:600;">${escapeHtml(av.label)}</span>
                </button>
              `).join("")}
            </div>
          </div>

          <label style="display:flex;align-items:center;gap:10px;cursor:pointer;">
            <input id="input-profile-sound" type="checkbox" ${draft.soundEnabled ? "checked" : ""} style="width:18px;height:18px;" />
            <span>Enable Web Audio table sound effects</span>
          </label>

          <button class="btn btn-primary btn-block" type="submit" ${state.isBusy ? "disabled" : ""}>Save Profile Settings</button>
        </form>
      </section>

      <section class="surface-card" style="display:flex;flex-direction:column;justify-content:space-between;gap:16px;">
        <div>
          <h3>Account Overview</h3>
          <div class="stack-list" style="margin-top:12px;">
            <div class="list-item-card"><span class="muted">Google Account</span><span class="mono">${escapeHtml(state.authUser?.email || user.userId)}</span></div>
            <div class="list-item-card"><span class="muted">Player UID</span><span class="mono" style="font-size:0.78rem;">${escapeHtml(user.userId)}</span></div>
            <div class="list-item-card"><span class="muted">Lifetime Points Won</span><span class="mono gold">${user.totalPointsWon || 0}</span></div>
            <div class="list-item-card"><span class="muted">Status</span><span class="pill ${user.isBanned ? "pill-danger" : "pill-success"}">${user.isBanned ? "SUSPENDED" : "ACTIVE"}</span></div>
          </div>
        </div>
        <button class="btn btn-danger btn-block" type="button" data-action="signout">Sign Out of Amatatu Web</button>
      </section>
    </div>
  `;
}

// ============================================================================
// Admin Dashboard Tab
// ============================================================================

function renderAdminTab(container) {
  const pendingTxs = state.adminTransactions.filter((tx) => tx.status === "PENDING");
  const liveTables = state.adminTables.filter((t) => ["WAITING", "ACTIVE", "SHOWDOWN"].includes(t.status));
  const openCheats = state.adminCheatLogs.filter((c) => !c.resolved);

  let sectionHtml = "";
  if (state.adminSubTab === "momo") {
    sectionHtml =
      state.adminTransactions.length === 0
        ? `<p class="muted">No transactions recorded.</p>`
        : `<div class="stack-list">
            ${state.adminTransactions
              .map((tx) => `
                <div class="list-item-card" style="flex-wrap:wrap;">
                  <div>
                    <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
                      <strong>${escapeHtml(tx.userName)} • ${escapeHtml(tx.type)}</strong>
                      <span class="pill ${tx.status === "COMPLETED" ? "pill-success" : tx.status === "PENDING" ? "pill-warning" : "pill-danger"}">${escapeHtml(tx.status)}</span>
                    </div>
                    <div class="mono gold" style="font-size:0.84rem;margin-top:4px;">
                      ${formatUgx(tx.amountUgx)} via ${escapeHtml(tx.provider)} (${escapeHtml(tx.phoneNumber || "Wallet")}) • Ref ${escapeHtml(tx.referenceCode)}
                    </div>
                  </div>
                  ${
                    tx.status === "PENDING"
                      ? `<div style="display:flex;gap:8px;">
                          <button class="btn btn-success btn-sm" type="button" data-action="admin-approve-tx" data-tx-id="${escapeHtml(tx.id)}" ${state.isBusy ? "disabled" : ""}>Approve</button>
                          <button class="btn btn-danger btn-sm" type="button" data-action="admin-reject-tx" data-tx-id="${escapeHtml(tx.id)}" ${state.isBusy ? "disabled" : ""}>Reject</button>
                        </div>`
                      : ""
                  }
                </div>
              `)
              .join("")}
          </div>`;
  } else if (state.adminSubTab === "players") {
    sectionHtml =
      state.adminUsers.length === 0
        ? `<p class="muted">No registered players yet.</p>`
        : `<div class="stack-list">
            ${state.adminUsers
              .map((p) => `
                <div class="list-item-card">
                  <div>
                    <div style="display:flex;align-items:center;gap:8px;">
                      <strong>${escapeHtml(p.displayName)}</strong>
                      ${p.isBanned ? `<span class="pill pill-danger">BANNED</span>` : ""}
                    </div>
                    <div class="mono gold" style="font-size:0.82rem;margin-top:3px;">
                      Bal: ${formatUgx(p.walletBalanceUgx)} • Escrow: ${formatUgx(p.escrowUgx)}
                    </div>
                    <div class="muted" style="font-size:0.78rem;">
                      ${p.gamesWon}W / ${p.gamesPlayed}P • ${escapeHtml(p.phoneNumber || "No MoMo phone")}
                    </div>
                  </div>
                  <button class="btn ${p.isBanned ? "btn-success" : "btn-danger"} btn-sm" type="button" data-action="admin-toggle-ban" data-uid="${escapeHtml(p.userId)}" data-ban="${p.isBanned ? "0" : "1"}" ${state.isBusy ? "disabled" : ""}>
                    ${p.isBanned ? "Unban" : "Ban"}
                  </button>
                </div>
              `)
              .join("")}
          </div>`;
  } else if (state.adminSubTab === "tables") {
    sectionHtml =
      state.adminTables.length === 0
        ? `<p class="muted">No tables recorded yet.</p>`
        : `<div class="stack-list">
            ${state.adminTables
              .map((tbl) => `
                <div class="list-item-card">
                  <div>
                    <div style="display:flex;align-items:center;gap:8px;">
                      <strong>${escapeHtml(tbl.title)}</strong>
                      <span class="pill">${escapeHtml(tbl.status)}</span>
                    </div>
                    <div class="mono gold" style="font-size:0.82rem;margin-top:3px;">
                      Stake ${formatUgx(tbl.stakeUgx)} • Pot ${formatUgx(tbl.potUgx)} • Moves #${tbl.moveSequence || 0}
                    </div>
                  </div>
                  ${
                    ["WAITING", "ACTIVE", "SHOWDOWN"].includes(tbl.status)
                      ? `<button class="btn btn-danger btn-sm" type="button" data-action="admin-close-table" data-table-id="${escapeHtml(tbl.id)}" ${state.isBusy ? "disabled" : ""}>Close</button>`
                      : ""
                  }
                </div>
              `)
              .join("")}
          </div>`;
  } else {
    sectionHtml =
      state.adminCheatLogs.length === 0
        ? `<p class="muted">Zero anti-cheat incidents reported.</p>`
        : `<div class="stack-list">
            ${state.adminCheatLogs
              .map((log) => `
                <div class="list-item-card" style="flex-wrap:wrap;">
                  <div>
                    <div style="display:flex;align-items:center;gap:8px;">
                      <strong class="gold">${escapeHtml(log.eventType)}</strong>
                      <span class="pill ${log.resolved ? "pill-success" : "pill-danger"}">${log.resolved ? "RESOLVED" : escapeHtml(log.severity)}</span>
                    </div>
                    <p style="font-size:0.86rem;margin-top:4px;">${escapeHtml(log.details)}</p>
                    <div class="mono muted" style="font-size:0.75rem;margin-top:2px;">
                      Table: ${escapeHtml(log.tableId)} • Reported UID: ${escapeHtml(log.reportedUid)}
                    </div>
                  </div>
                  ${
                    !log.resolved
                      ? `<button class="btn btn-primary btn-sm" type="button" data-action="admin-resolve-cheat" data-log-id="${escapeHtml(log.id)}" ${state.isBusy ? "disabled" : ""}>Resolve</button>`
                      : ""
                  }
                </div>
              `)
              .join("")}
          </div>`;
  }

  container.innerHTML = `
    <div class="kpi-grid">
      <div class="kpi-box"><span class="muted" style="font-size:0.8rem;">Pending MoMo</span><span class="kpi-value" style="color:var(--amber-warning);">${pendingTxs.length}</span></div>
      <div class="kpi-box"><span class="muted" style="font-size:0.8rem;">Players</span><span class="kpi-value">${state.adminUsers.length}</span></div>
      <div class="kpi-box"><span class="muted" style="font-size:0.8rem;">Live Tables</span><span class="kpi-value success-text">${liveTables.length}</span></div>
      <div class="kpi-box"><span class="muted" style="font-size:0.8rem;">Cheat Alerts</span><span class="kpi-value danger-text">${openCheats.length}</span></div>
    </div>

    <div class="segmented-bar">
      <button type="button" class="seg-btn ${state.adminSubTab === "momo" ? "active" : ""}" data-action="set-admin-subtab" data-sub="momo">MoMo Queue (${pendingTxs.length})</button>
      <button type="button" class="seg-btn ${state.adminSubTab === "players" ? "active" : ""}" data-action="set-admin-subtab" data-sub="players">Players (${state.adminUsers.length})</button>
      <button type="button" class="seg-btn ${state.adminSubTab === "tables" ? "active" : ""}" data-action="set-admin-subtab" data-sub="tables">Tables (${state.adminTables.length})</button>
      <button type="button" class="seg-btn ${state.adminSubTab === "cheats" ? "active" : ""}" data-action="set-admin-subtab" data-sub="cheats">Anti-Cheat (${openCheats.length})</button>
    </div>

    <section class="surface-card">${sectionHtml}</section>
  `;
}

// ============================================================================
// Embedded Web Card Table Renderer (Inside Website Container)
// ============================================================================

function getActiveSessionData() {
  if (state.practiceSession) {
    return {
      isPractice: true,
      uid: PRACTICE_PLAYER_UID,
      table: state.practiceSession.table,
      myHand: state.practiceSession.myHand,
      moves: state.practiceSession.moves,
    };
  }
  return {
    isPractice: false,
    uid: state.authUser?.uid || "",
    table: state.activeTable,
    myHand: state.myHand,
    moves: state.moves,
  };
}

function renderEmbeddedTableView(container) {
  const { isPractice, uid, table, myHand, moves } = getActiveSessionData();

  if (!table) {
    container.innerHTML = `
      <div class="table-breadcrumb-bar">
        <button class="btn btn-outline btn-sm" type="button" data-action="back-to-lobby">← Back to Tables</button>
        <strong>Connecting to Table...</strong>
      </div>
      <div class="surface-card"><p class="muted">Syncing deck commit hash and seats.</p></div>
    `;
    return;
  }

  const potSubtitle = isPractice
    ? "OFFLINE PRACTICE MODE • ZERO REAL CHIPS"
    : Number(table.stakeUgx || 0) === 0
    ? "Free Practice Felt"
    : `Stake ${formatUgx(table.stakeUgx)} • Pot ${formatUgx(table.potUgx)} • Winner Payout ${formatUgx(table.payoutUgx)}`;

  const breadcrumbHtml = `
    <div class="table-breadcrumb-bar">
      <div style="display:flex;align-items:center;gap:12px;min-width:0;">
        <button id="table-back-btn" class="btn btn-outline btn-sm" type="button">← Back to Website Lobby</button>
        <div style="min-width:0;">
          <h2 style="font-size:1.1rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(table.title || "Amatatu Table")}</h2>
          <div class="mono gold" style="font-size:0.78rem;">${escapeHtml(potSubtitle)}</div>
        </div>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;">
        <button class="btn btn-outline btn-sm" type="button" data-action="open-rules-modal">Rules</button>
        <button class="btn btn-outline btn-sm" type="button" data-action="open-audit-modal">Fair Play</button>
      </div>
    </div>
  `;

  const auditPanelHtml = `
    <aside class="audit-sidebar">
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <h3>Table Fair-Play</h3>
        <span class="pill pill-gold mono">Moves: ${table.moveSequence || 0}</span>
      </div>
      <div class="surface-elevated" style="padding:10px;font-size:0.78rem;">
        <div class="muted">Deck SHA-256 Commit</div>
        <div class="mono gold" style="word-break:break-all;margin-top:4px;">${escapeHtml(table.deckCommitHash || "Pending deal...")}</div>
      </div>
      ${
        !isPractice && table.status === "ACTIVE"
          ? `<button class="btn btn-danger btn-sm btn-block" type="button" data-action="open-report-modal">Flag Suspicious Activity</button>`
          : ""
      }
      <div style="font-weight:700;font-size:0.86rem;">Move Audit Log</div>
      <div class="stack-list">
        ${
          moves.length === 0
            ? `<p class="muted" style="font-size:0.82rem;">No moves played yet.</p>`
            : moves
                .slice(0, 18)
                .map((m) => `
                  <div class="move-row">
                    <div style="display:flex;justify-content:space-between;">
                      <strong>#${m.moveSequence} ${escapeHtml(m.playerName)}</strong>
                      <span class="mono gold">${escapeHtml(m.actionType)} ${escapeHtml(m.cardPlayed || (m.drawCount ? `+${m.drawCount}` : ""))}</span>
                    </div>
                    <div class="muted mono" style="font-size:0.72rem;">
                      Cards left: ${m.handSizeAfter} • Digest: ${escapeHtml((m.antiCheatDigest || "").slice(0, 12))}
                    </div>
                  </div>
                `)
                .join("")
        }
      </div>
    </aside>
  `;

  if (table.status === "WAITING") {
    const seats = table.playerIds || [];
    const amHost = table.hostId === uid;
    const shareUrl = table.inviteCode
      ? `${window.location.origin}${window.location.pathname}?invite=${encodeURIComponent(table.inviteCode)}`
      : "";

    container.innerHTML = `
      ${breadcrumbHtml}
      <div class="felt-workspace">
        <div class="felt-arena-shell" style="align-items:center;justify-content:center;min-height:420px;">
          <div class="surface-card gold-border" style="width:100%;max-width:560px;display:grid;gap:16px;">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <h2>Waiting Room (${seats.length} / ${table.maxPlayers})</h2>
              <span class="pill pill-warning">WAITING FOR PLAYERS</span>
            </div>

            ${
              table.inviteCode
                ? `
                  <div class="surface-elevated" style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;">
                    <div>
                      <div class="muted" style="font-size:0.78rem;">Private Invite Code</div>
                      <div class="mono gold" style="font-size:1.45rem;font-weight:800;letter-spacing:0.12em;">${escapeHtml(table.inviteCode)}</div>
                    </div>
                    <div style="display:flex;gap:8px;">
                      <button class="btn btn-emerald btn-sm" type="button" data-action="copy-invite" data-code="${escapeHtml(table.inviteCode)}">Copy Code</button>
                      <button class="btn btn-outline btn-sm" type="button" data-action="copy-invite" data-code="${escapeHtml(shareUrl)}">Copy Web Link</button>
                    </div>
                  </div>
                `
                : `<p class="muted" style="font-size:0.9rem;">Public table open in the lobby for Android &amp; Desktop challengers.</p>`
            }

            <div class="stack-list">
              ${seats
                .map((pid, idx) => `
                  <div class="list-item-card">
                    <div style="display:flex;align-items:center;gap:10px;">
                      <span class="avatar-badge">${escapeHtml(avatarGlyph(table.playerAvatars?.[pid]))}</span>
                      <div>
                        <strong>${escapeHtml(table.playerNames?.[pid] || "Player")}</strong>
                        <div class="muted" style="font-size:0.76rem;">Seat ${idx + 1}${pid === table.hostId ? " • Host" : ""}</div>
                      </div>
                    </div>
                    <span class="pill pill-success">SEATED</span>
                  </div>
                `)
                .join("")}
            </div>

            <div style="display:grid;gap:10px;">
              <button class="btn btn-primary btn-block" type="button" data-action="start-table" ${seats.length < 2 || state.isBusy ? "disabled" : ""}>
                ${seats.length < 2 ? "Waiting for Challenger to Join..." : "Deal Cards & Start Match"}
              </button>
              ${
                amHost && seats.length === 1
                  ? `<button class="btn btn-danger btn-block" type="button" data-action="cancel-waiting-table" ${state.isBusy ? "disabled" : ""}>Cancel Table &amp; Refund Stake</button>`
                  : ""
              }
            </div>
          </div>
        </div>
        ${auditPanelHtml}
      </div>
    `;
    return;
  }

  if (table.status === "FINISHED" || table.status === "CANCELLED") {
    const iWon = table.winnerId === uid;
    const revealedEntries = Object.entries(table.revealedPoints || {});

    container.innerHTML = `
      ${breadcrumbHtml}
      <div class="felt-workspace">
        <div class="felt-arena-shell" style="align-items:center;justify-content:center;min-height:420px;">
          <div class="surface-card gold-border" style="width:100%;max-width:580px;display:grid;gap:16px;text-align:center;">
            <div>
              <span class="pill ${iWon ? "pill-success" : "pill-gold"}">${escapeHtml(String(table.winReason || table.status).replaceAll("_", " "))}</span>
              <h1 class="${iWon ? "gold" : ""}" style="margin-top:10px;font-size:2rem;">
                ${table.status === "CANCELLED" ? "Table Closed" : iWon ? "🏆 Victory!" : "Match Complete"}
              </h1>
              <p class="muted" style="margin-top:6px;">${escapeHtml(table.lastActionSummary || "")}</p>
            </div>

            ${
              !isPractice && Number(table.payoutUgx || 0) > 0 && table.status === "FINISHED"
                ? `<div class="surface-elevated">
                    <div class="muted" style="font-size:0.8rem;">Winner Pot Payout (95% after 5% Parlour Fee)</div>
                    <div class="mono gold" style="font-size:1.5rem;font-weight:800;margin-top:4px;">${formatUgx(table.payoutUgx)}</div>
                    <div class="muted" style="font-size:0.8rem;margin-top:4px;">Winner: <strong>${escapeHtml(table.winnerName)}</strong></div>
                  </div>`
                : ""
            }

            ${
              revealedEntries.length > 0
                ? `<div class="stack-list" style="text-align:left;">
                    ${revealedEntries
                      .map(([pid, pts]) => {
                        const cards = table.revealedHands?.[pid] || [];
                        return `
                          <div class="list-item-card" style="flex-direction:column;align-items:flex-start;">
                            <div style="width:100%;display:flex;justify-content:space-between;">
                              <strong>${escapeHtml(table.playerNames?.[pid] || "Player")}</strong>
                              <span class="mono gold">${pts} pts</span>
                            </div>
                            ${
                              cards.length > 0
                                ? `<div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:8px;">
                                    ${cards.map((c) => renderCardHtml(c, { size: "sm" })).join("")}
                                  </div>`
                                : `<span class="muted" style="font-size:0.8rem;">Empty hand (0 cards)</span>`
                            }
                          </div>
                        `;
                      })
                      .join("")}
                  </div>`
                : ""
            }

            <div style="display:flex;gap:10px;flex-wrap:wrap;">
              ${isPractice ? `<button class="btn btn-emerald" style="flex:1;" type="button" data-action="launch-practice">Play Another Practice Match</button>` : ""}
              <button class="btn btn-primary" style="flex:1;" type="button" data-action="back-to-lobby">Return to Tables</button>
            </div>
          </div>
        </div>
        ${auditPanelHtml}
      </div>
    `;
    return;
  }

  // ACTIVE or SHOWDOWN table arena
  const opponents = (table.playerIds || []).filter((pid) => pid !== uid);
  const myTurn = table.status === "ACTIVE" && table.currentTurnUid === uid;
  const handCardsRaw = myHand?.cards || [];
  const handCards = state.sortHand ? sortHandCodes(handCardsRaw) : handCardsRaw;
  const handPoints = calculatePoints(handCardsRaw);
  const activeSuit = effectiveSuit(table.topCard, table.demandedSuit);
  const cutSuitObj = suitByName(table.cutSuit);
  const penalty = Number(table.pendingDrawPenalty || 0);

  container.innerHTML = `
    ${breadcrumbHtml}
    <div class="felt-workspace">
      <div class="felt-arena-shell">
        <div class="opponents-rail">
          ${opponents
            .map((pid) => {
              const count = Number(table.cardCounts?.[pid] ?? 7);
              const isTheirTurn = table.currentTurnUid === pid;
              const visibleMini = Math.min(7, Math.max(0, count));
              return `
                <div class="opponent-seat ${isTheirTurn ? "active-turn" : ""}">
                  <div style="display:flex;align-items:center;gap:10px;">
                    <span class="avatar-badge">${escapeHtml(avatarGlyph(table.playerAvatars?.[pid]))}</span>
                    <div>
                      <div style="font-weight:700;font-size:0.9rem;">${escapeHtml(table.playerNames?.[pid] || "Challenger")}</div>
                      <div class="mono ${isTheirTurn ? "gold" : "muted"}" style="font-size:0.76rem;">
                        ${count} card${count === 1 ? "" : "s"}${isTheirTurn ? " • THINKING" : ""}
                      </div>
                    </div>
                  </div>
                  <div class="mini-cards-fan" aria-hidden="true">
                    ${Array.from({ length: visibleMini }, () => `<span class="mini-back"></span>`).join("")}
                  </div>
                </div>
              `;
            })
            .join("")}
        </div>

        <div class="table-stage">
          <div class="status-banner ${myTurn ? "my-turn" : ""}">
            <div style="display:flex;justify-content:center;align-items:center;gap:8px;flex-wrap:wrap;">
              <span class="pill ${myTurn ? "pill-gold" : ""}">${table.status === "SHOWDOWN" ? "7-CUT SHOWDOWN" : myTurn ? "YOUR TURN" : "OPPONENT TURN"}</span>
              ${penalty > 0 ? `<span class="pill pill-danger">PICK ${penalty} ACTIVE! Stack 2 or A♠</span>` : ""}
              ${table.demandedSuit ? `<span class="pill pill-gold">Demanded Suit: ${escapeHtml(activeSuit.symbol)} ${escapeHtml(activeSuit.label)}</span>` : ""}
            </div>
            <div style="margin-top:6px;font-weight:600;font-size:0.92rem;">${escapeHtml(table.lastActionSummary || "")}</div>
          </div>

          <div class="piles-row">
            ${
              table.cutCard
                ? `<div class="pile-slot">
                    <span class="pile-label">Cut Suit (${escapeHtml(cutSuitObj ? `${cutSuitObj.symbol} ${cutSuitObj.label}` : table.cutSuit)})</span>
                    ${renderCardHtml(table.cutCard, { size: "sm" })}
                  </div>`
                : ""
            }

            <div class="pile-slot">
              <span class="pile-label">Draw Deck (${(table.drawPile || []).length})</span>
              <button type="button" class="card-back-deck ${myTurn ? "clickable-draw" : ""}" data-action="draw-card" ${!myTurn || state.isBusy ? "disabled" : ""}>
                <span style="font-size:1.35rem;">♠</span>
                <span style="font-size:0.78rem;">${penalty > 0 ? `PICK ${penalty}` : "DRAW"}</span>
              </button>
            </div>

            <div class="pile-slot">
              <span class="pile-label">Discard Pile • Active ${escapeHtml(activeSuit.symbol)}</span>
              ${renderCardHtml(table.topCard, { size: "lg" })}
            </div>
          </div>
        </div>

        <div class="player-hand-dock">
          <div class="hand-controls-bar">
            <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
              <strong>Your Hand (${handCardsRaw.length})</strong>
              <span class="pill ${handPoints <= CUT_THRESHOLD_POINTS ? "pill-gold" : ""}">
                Points: <span class="mono">${handPoints}</span>${handPoints <= CUT_THRESHOLD_POINTS ? " • Cut Threshold ≤25" : ""}
              </span>
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap;">
              <button class="btn btn-outline btn-sm" type="button" data-action="toggle-sort">
                Sort: ${state.sortHand ? "Suit/Rank" : "Dealt"}
              </button>
              <button class="btn btn-primary btn-sm" type="button" data-action="draw-card" ${!myTurn || state.isBusy ? "disabled" : ""}>
                ${penalty > 0 ? `Draw ${penalty} Penalty` : "Draw 1 Card"}
              </button>
            </div>
          </div>

          <div class="hand-scroll-fan">
            ${
              handCards.length === 0
                ? `<p class="muted">No cards in hand.</p>`
                : handCards
                    .map((code) => {
                      const card = parseCard(code);
                      const playable =
                        myTurn &&
                        isCardPlayable(card, table.topCard, table.demandedSuit, penalty, table.cutSuit, handCardsRaw);
                      const canCutNow =
                        myTurn && canTriggerSevenCut(card, handCardsRaw, table.cutSuit, penalty);
                      return renderCardHtml(code, {
                        playable,
                        dimmed: myTurn && !playable,
                        isCutSeven: canCutNow,
                        interactive: true,
                      });
                    })
                    .join("")
            }
          </div>
        </div>
      </div>
      ${auditPanelHtml}
    </div>
  `;
}

// ============================================================================
// Auth, Modals & Table Lifecycle Actions
// ============================================================================

async function handleGoogleSignIn() {
  state.authError = "";
  try {
    await services.signInWithPopup();
  } catch (err) {
    const code = err?.code || "";
    if (code === "auth/popup-blocked") {
      try {
        await services.signInWithRedirect();
        return;
      } catch (rErr) {
        state.authError = rErr?.message || "Redirect sign-in failed.";
        renderRoot();
        return;
      }
    }
    let msg = err?.message || "Google Sign-In failed.";
    if (code === "auth/unauthorized-domain") {
      msg = `Domain "${window.location.hostname}" is not yet in Firebase Console → Authentication → Settings → Authorized domains. Add it there or use Offline Practice Mode below.`;
    }
    state.authError = msg;
    sound.playErrorTone();
    renderRoot();
  }
}

async function handleLaunchOfflinePractice() {
  state.mobileMenuOpen = false;
  state.practiceSession = await createPracticeSession(state.user?.displayName || "Kampala Guest");
  sound.playCardTap();
  renderRoot();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function navigateToNav(navKey) {
  const { table } = getActiveSessionData();
  if (table && (table.status === "ACTIVE" || table.status === "SHOWDOWN")) {
    openForfeitConfirmModal();
    return;
  }
  state.practiceSession = null;
  closeCloudTableListeners();
  state.activeNav = navKey;
  state.mobileMenuOpen = false;
  renderRoot();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function openCreatePrivateRoomModal() {
  showModal(`
    <h3>Create Private Room</h3>
    <p class="muted" style="font-size:0.88rem;">Generate a shareable <span class="mono">AMT</span> room code synced with Android &amp; Desktop players.</p>
    <form id="form-create-private" style="display:grid;gap:12px;">
      <div>
        <label class="field-label" for="private-room-title">Room Name</label>
        <input id="private-room-title" class="input" maxlength="44" value="${escapeHtml(`${state.user?.displayName || "Kampala"}'s Lounge`)}" required />
      </div>
      <div>
        <label class="field-label" for="private-room-stake">Stake Tier</label>
        <select id="private-room-stake" class="select">
          ${STAKE_TIERS.map((t) => `<option value="${t.stakeUgx}" ${t.stakeUgx === state.selectedStakeUgx ? "selected" : ""}>${escapeHtml(t.label)} (${t.stakeUgx === 0 ? "FREE" : formatUgx(t.stakeUgx)})</option>`).join("")}
        </select>
      </div>
      <div>
        <label class="field-label" for="private-room-seats">Max Players (2–4)</label>
        <select id="private-room-seats" class="select">
          <option value="2">2 Players</option>
          <option value="3">3 Players</option>
          <option value="4">4 Players</option>
        </select>
      </div>
      <div style="display:flex;gap:10px;justify-content:flex-end;">
        <button class="btn btn-outline" type="button" data-action="close-modal">Cancel</button>
        <button class="btn btn-primary" type="submit">Create Room</button>
      </div>
    </form>
  `);
}

function openSuitPickerModal(cardCode) {
  showModal(`
    <h3>Choose New Suit (${escapeHtml(parseCard(cardCode)?.displayLabel || "Ace")})</h3>
    <p class="muted" style="font-size:0.88rem;">Select the suit the next player must follow:</p>
    <div class="suit-picker-grid">
      ${SUITS.map(
        (s) => `
          <button type="button" class="suit-choice-btn ${s.isRed ? "red-suit" : ""}" data-action="confirm-ace-suit" data-card="${escapeHtml(cardCode)}" data-suit="${s.name}">
            <span style="font-size:1.5rem;">${escapeHtml(s.symbol)}</span>
            <span>${escapeHtml(s.label)}</span>
          </button>
        `
      ).join("")}
    </div>
    <button class="btn btn-outline btn-block" type="button" data-action="close-modal">Cancel</button>
  `);
}

function openSevenCutDecisionModal(cardCode, remainingPts) {
  showModal(`
    <h3>7-Cut Chopper Available!</h3>
    <p class="muted" style="font-size:0.9rem;">
      Your remaining hand is <strong class="gold mono">${remainingPts} pts</strong> (≤ ${CUT_THRESHOLD_POINTS} pts limit).
      Would you like to drop <strong>${escapeHtml(parseCard(cardCode)?.displayLabel)}</strong> as a normal discard or freeze the round for a <strong>7-Cut Showdown</strong>?
    </p>
    <div style="display:grid;gap:10px;">
      <button type="button" class="btn btn-primary btn-block" data-action="confirm-seven-cut" data-card="${escapeHtml(cardCode)}" data-cut="1">
        🔥 Trigger 7-Cut Showdown!
      </button>
      <button type="button" class="btn btn-outline btn-block" data-action="confirm-seven-cut" data-card="${escapeHtml(cardCode)}" data-cut="0">
        Play Normally (Keep Going)
      </button>
      <button type="button" class="btn btn-outline btn-block" data-action="close-modal">Cancel</button>
    </div>
  `);
}

function openRulesModal() {
  showModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;">
      <h3>Official Ugandan Amatatu Rules</h3>
      <button class="btn btn-outline btn-sm" type="button" data-action="close-modal">Close</button>
    </div>
    ${renderRulesGuideHtml()}
  `);
}

function openAuditModal() {
  const { table } = getActiveSessionData();
  if (!table) return;
  showModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;">
      <h3>Fair-Play Verification</h3>
      <button class="btn btn-outline btn-sm" type="button" data-action="close-modal">Close</button>
    </div>
    <p class="muted" style="font-size:0.86rem;">
      Every Amatatu deal commits a cryptographic SHA-256 hash before hands are isolated per player UID.
    </p>
    <div class="surface-elevated" style="font-size:0.8rem;">
      <div class="muted">Table ID</div>
      <div class="mono">${escapeHtml(table.id)}</div>
      <div class="muted" style="margin-top:8px;">Deck SHA-256 Commit Hash</div>
      <div class="mono gold" style="word-break:break-all;">${escapeHtml(table.deckCommitHash || "Pending deal...")}</div>
    </div>
  `);
}

function openForfeitConfirmModal() {
  showModal(`
    <h3>Leave Active Match?</h3>
    <p class="muted" style="font-size:0.9rem;">Leaving a live game counts as a forfeit and awards the pot to your challenger.</p>
    <div style="display:flex;gap:10px;justify-content:flex-end;">
      <button class="btn btn-outline" type="button" data-action="close-modal">Keep Playing</button>
      <button class="btn btn-danger" type="button" data-action="confirm-forfeit">Forfeit &amp; Exit</button>
    </div>
  `);
}

function openReportAnomalyModal(table) {
  const others = (table.playerIds || []).filter((id) => id !== state.authUser?.uid);
  const defaultTarget = others[0] || table.hostId;
  showModal(`
    <h3>Report Suspicious Table Activity</h3>
    <form id="form-report-cheat" style="display:grid;gap:12px;">
      <div>
        <label class="field-label" for="report-details">Describe issue for Admin review</label>
        <textarea id="report-details" class="textarea" rows="3" maxlength="220" required placeholder="e.g., Unusual card delay or turn anomaly"></textarea>
      </div>
      <input type="hidden" id="report-table-id" value="${escapeHtml(table.id)}" />
      <input type="hidden" id="report-target-uid" value="${escapeHtml(defaultTarget)}" />
      <div style="display:flex;gap:10px;justify-content:flex-end;">
        <button class="btn btn-outline" type="button" data-action="close-modal">Cancel</button>
        <button class="btn btn-danger" type="submit">Submit Report</button>
      </div>
    </form>
  `);
}

function openRejectTxModal(tx) {
  showModal(`
    <h3>Reject ${escapeHtml(tx.referenceCode)}?</h3>
    <p class="muted" style="font-size:0.88rem;">
      ${tx.type === "WITHDRAWAL" ? `Rejecting refunds ${formatUgx(tx.amountUgx)} back to ${escapeHtml(tx.userName)}'s wallet.` : "Deposit will be marked REJECTED without crediting funds."}
    </p>
    <form id="form-reject-tx" style="display:grid;gap:12px;">
      <input type="hidden" id="reject-tx-id" value="${escapeHtml(tx.id)}" />
      <div>
        <label class="field-label" for="reject-reason">Reason</label>
        <input id="reject-reason" class="input" maxlength="160" value="MoMo reference not confirmed" required />
      </div>
      <div style="display:flex;gap:10px;justify-content:flex-end;">
        <button class="btn btn-outline" type="button" data-action="close-modal">Cancel</button>
        <button class="btn btn-danger" type="submit">Confirm Reject</button>
      </div>
    </form>
  `);
}

// ============================================================================
// Card Play & Practice / Cloud Turn Execution
// ============================================================================

async function executePlayMove(cardCode, chosenSuitName = null, triggerCut = false) {
  const { isPractice, table, myHand } = getActiveSessionData();
  if (!table || !myHand) return;

  try {
    let summary = "";
    if (isPractice) {
      summary = await playPracticeCard(state.practiceSession, cardCode, chosenSuitName, triggerCut);
      playSoundForSummary(summary, triggerCut);
      renderRoot();
      if (state.practiceSession.table.status === "ACTIVE" && state.practiceSession.table.currentTurnUid === BOT_UID) {
        setTimeout(runPracticeBotStep, 750);
      } else if (state.practiceSession.table.status === "FINISHED" && state.practiceSession.table.winnerId === PRACTICE_PLAYER_UID) {
        sound.playWinFanfare();
      }
    } else {
      summary = await repo.playCard({
        table,
        hand: myHand,
        cardCode,
        chosenSuitForAce: chosenSuitName,
        triggerCut,
        botHandForInstantCut: state.botHand?.cards || null,
      });
      playSoundForSummary(summary, triggerCut);
    }
  } catch (err) {
    sound.playErrorTone();
    showToast(err?.message || "Invalid move", true);
  }
}

async function handleDrawAction() {
  const { isPractice, table, myHand } = getActiveSessionData();
  if (!table || !myHand) return;
  try {
    if (isPractice) {
      await drawPracticeCard(state.practiceSession);
      sound.playDrawCard();
      renderRoot();
      if (state.practiceSession.table.status === "ACTIVE" && state.practiceSession.table.currentTurnUid === BOT_UID) {
        setTimeout(runPracticeBotStep, 750);
      }
    } else {
      await repo.drawCard(table, myHand);
      sound.playDrawCard();
    }
  } catch (err) {
    sound.playErrorTone();
    showToast(err?.message || "Unable to draw card", true);
  }
}

async function runPracticeBotStep() {
  const session = state.practiceSession;
  if (!session || session.table.status !== "ACTIVE" || session.table.currentTurnUid !== BOT_UID) return;
  const summary = await stepPracticeBotTurn(session);
  playSoundForSummary(summary, summary.includes("Cut 7"));
  renderRoot();
  if (session.table.status === "ACTIVE" && session.table.currentTurnUid === BOT_UID) {
    setTimeout(runPracticeBotStep, 750);
  }
}

function playSoundForSummary(summary, triggerCut = false) {
  const s = String(summary || "");
  if (triggerCut || s.includes("Cut")) sound.playSevenCut();
  else if (s.includes("stacked")) sound.playAttackTwo();
  else if (s.includes("jumped") || s.includes("skipped")) sound.playJumpSkip();
  else if (s.includes("drew")) sound.playDrawCard();
  else sound.playCardTap();
}

function onHandCardClicked(cardCode) {
  const { table, myHand } = getActiveSessionData();
  if (!table || !myHand || table.status !== "ACTIVE") return;
  const uid = state.practiceSession ? PRACTICE_PLAYER_UID : state.authUser?.uid;
  if (table.currentTurnUid !== uid) {
    showToast("Wait for your turn!", true);
    return;
  }

  const card = parseCard(cardCode);
  if (!card) return;
  const cards = myHand.cards || [];
  if (!isCardPlayable(card, table.topCard, table.demandedSuit, table.pendingDrawPenalty, table.cutSuit, cards)) {
    sound.playErrorTone();
    showToast(`${card.displayLabel} cannot be played on ${parseCard(table.topCard)?.displayLabel || table.topCard}`, true);
    return;
  }

  if (canTriggerSevenCut(card, cards, table.cutSuit, table.pendingDrawPenalty)) {
    const idx = cards.indexOf(card.code);
    const remaining = cards.filter((_, i) => i !== idx);
    openSevenCutDecisionModal(card.code, calculatePoints(remaining));
    return;
  }

  if (card.rank.code === "A" && cards.length > 1) {
    openSuitPickerModal(card.code);
    return;
  }

  executePlayMove(card.code, null, false);
}

// ============================================================================
// Cloud Table Subscription & Reactive Automation
// ============================================================================

function openCloudTable(tableId) {
  closeCloudTableListeners();
  const uid = state.authUser?.uid;
  if (!uid) return;

  state.activeTableId = tableId;
  state.activeTable = null;
  state.myHand = null;
  state.botHand = null;
  state.moves = [];
  renderRoot();
  window.scrollTo({ top: 0, behavior: "smooth" });

  tableUnsubs.push(
    repo.observeTable(
      tableId,
      (tbl) => {
        state.activeTable = tbl;
        if (tbl?.isBotMatch && !botHandUnsub) {
          botHandUnsub = repo.observePlayerHand(
            tableId,
            BOT_UID,
            (bh) => {
              state.botHand = bh;
              runReactiveTableAutomation();
              renderRoot();
            },
            (err) => console.warn("Bot hand error", err)
          );
        }
        runReactiveTableAutomation();
        renderRoot();
      },
      (err) => showToast(err?.message || "Table sync error", true)
    )
  );

  tableUnsubs.push(
    repo.observePlayerHand(
      tableId,
      uid,
      (hand) => {
        state.myHand = hand;
        runReactiveTableAutomation();
        renderRoot();
      },
      (err) => console.warn("Hand error", err)
    )
  );

  tableUnsubs.push(
    repo.observeTableMoves(
      tableId,
      uid,
      (moves) => {
        state.moves = moves;
        renderRoot();
      },
      (err) => console.warn("Moves error", err)
    )
  );
}

function closeCloudTableListeners() {
  clearSubs(tableUnsubs);
  if (botHandUnsub) {
    try {
      botHandUnsub();
    } catch {
      // ignore
    }
    botHandUnsub = null;
  }
  state.activeTableId = null;
  state.activeTable = null;
  state.myHand = null;
  state.botHand = null;
  state.moves = [];
}

async function runReactiveTableAutomation() {
  const uid = state.authUser?.uid;
  const table = state.activeTable;
  if (!uid || !table) return;

  if (
    table.status === "SHOWDOWN" &&
    state.myHand &&
    (!table.revealedPoints || table.revealedPoints[uid] === undefined) &&
    !state.revealedTableIds.has(table.id)
  ) {
    state.revealedTableIds.add(table.id);
    repo.submitShowdownReveal(table, state.myHand).catch(() => {
      state.revealedTableIds.delete(table.id);
    });
  }

  if (
    table.status === "FINISHED" &&
    !(table.settledUids || []).includes(uid) &&
    !state.settledTableIds.has(table.id)
  ) {
    state.settledTableIds.add(table.id);
    try {
      const credited = await repo.settleFinishedMatchForCurrentUser(table);
      if (table.winnerId === uid) {
        sound.playWinFanfare();
        if (credited && Number(table.payoutUgx || 0) > 0) {
          setTimeout(() => {
            sound.playCoinChime();
            showToast(`Pot settled: +${formatUgx(table.payoutUgx)} credited to your wallet!`);
          }, 300);
        }
      }
    } catch {
      state.settledTableIds.delete(table.id);
    }
  }

  if (
    table.isBotMatch &&
    table.status === "ACTIVE" &&
    table.currentTurnUid === BOT_UID &&
    state.botHand
  ) {
    const seqKey = `${table.id}:${table.moveSequence || 0}`;
    if (state.lastBotMoveKey !== seqKey) {
      state.lastBotMoveKey = seqKey;
      setTimeout(async () => {
        if (
          !state.activeTable ||
          state.activeTable.id !== table.id ||
          state.activeTable.status !== "ACTIVE" ||
          state.activeTable.currentTurnUid !== BOT_UID ||
          !state.botHand
        ) {
          return;
        }
        try {
          const summary = await repo.executeBotTurn(state.activeTable, state.botHand);
          playSoundForSummary(summary, summary.includes("Cut 7"));
        } catch (err) {
          console.warn("Bot move skipped", err);
        }
      }, 850);
    }
  }
}

// ============================================================================
// Cloud Session Initialization (onAuthStateChanged)
// ============================================================================

async function startCloudSession(firebaseUser) {
  clearSubs(shellUnsubs);
  clearSubs(adminUnsubs);
  closeCloudTableListeners();

  state.authUser = firebaseUser;
  state.authError = "";
  state.user = null;
  state.userLoadError = null;
  state.isAdmin = false;
  state.practiceSession = null;
  renderRoot();

  if (!firebaseUser) return;

  try {
    await repo.ensureUserAccountInitialized(
      firebaseUser.displayName || "Kampala Player",
      firebaseUser.photoURL || null
    );
  } catch (err) {
    state.userLoadError = err?.message || "Profile initialization failed.";
    showToast(state.userLoadError, true);
  }

  const uid = firebaseUser.uid;
  shellUnsubs.push(
    repo.observeCurrentUser(
      uid,
      (u) => {
        state.user = u;
        renderRoot();
      },
      (err) => {
        state.userLoadError = err?.message || "Failed loading user.";
        renderRoot();
      }
    )
  );
  shellUnsubs.push(
    repo.observePublicWaitingTables((list) => {
      state.waitingTables = list;
      renderRoot();
    })
  );
  shellUnsubs.push(
    repo.observeUserTables(uid, (list) => {
      state.myTables = list;
      renderRoot();
    })
  );
  shellUnsubs.push(
    repo.observeLeaderboard((list) => {
      state.leaderboard = list;
      renderRoot();
    })
  );
  shellUnsubs.push(
    repo.observeUserTransactions(uid, (list) => {
      state.transactions = list;
      renderRoot();
    })
  );
  shellUnsubs.push(
    repo.observeUserGameHistory(uid, (list) => {
      state.gameHistory = list;
      renderRoot();
    })
  );

  const isAdmin = await repo.checkIsAdmin();
  if (state.authUser?.uid === uid && isAdmin) {
    state.isAdmin = true;
    adminUnsubs.push(
      repo.observeAllUsersForAdmin((list) => {
        state.adminUsers = list;
        if (state.activeNav === "admin") renderRoot();
      })
    );
    adminUnsubs.push(
      repo.observeAllTransactionsForAdmin((list) => {
        state.adminTransactions = list;
        if (state.activeNav === "admin") renderRoot();
      })
    );
    adminUnsubs.push(
      repo.observeAllTablesForAdmin((list) => {
        state.adminTables = list;
        if (state.activeNav === "admin") renderRoot();
      })
    );
    adminUnsubs.push(
      repo.observeAntiCheatLogsForAdmin((list) => {
        state.adminCheatLogs = list;
        if (state.activeNav === "admin") renderRoot();
      })
    );
    renderRoot();
  }
}

// ============================================================================
// Delegated Event Wiring
// ============================================================================

async function runBusy(fn) {
  state.isBusy = true;
  renderRoot();
  try {
    await fn();
  } finally {
    state.isBusy = false;
    renderRoot();
  }
}

document.addEventListener("click", async (ev) => {
  if (ev.target.id === "table-back-btn") {
    const { table } = getActiveSessionData();
    if (table && (table.status === "ACTIVE" || table.status === "SHOWDOWN")) {
      openForfeitConfirmModal();
    } else {
      state.practiceSession = null;
      closeCloudTableListeners();
      renderRoot();
    }
    return;
  }

  const target = ev.target.closest("[data-action], [data-nav]");
  if (!target) {
    if (ev.target.id === "modal-backdrop") closeModal();
    return;
  }

  if (target.dataset.nav) {
    navigateToNav(target.dataset.nav);
    return;
  }

  const action = target.dataset.action;
  switch (action) {
    case "google-signin":
      await handleGoogleSignIn();
      break;

    case "close-modal":
      closeModal();
      break;

    case "open-rules-modal":
      openRulesModal();
      break;

    case "open-audit-modal":
      openAuditModal();
      break;

    case "launch-practice":
      await handleLaunchOfflinePractice();
      break;

    case "signout":
      state.mobileMenuOpen = false;
      closeCloudTableListeners();
      clearSubs(shellUnsubs);
      clearSubs(adminUnsubs);
      await services.signOut();
      break;

    case "select-stake":
      state.selectedStakeUgx = Number(target.dataset.stake || 0);
      renderRoot();
      break;

    case "quick-match":
      await runBusy(async () => {
        try {
          const tableId = await repo.quickMatchOnline(state.user, state.selectedStakeUgx);
          sound.playCardTap();
          openCloudTable(tableId);
        } catch (err) {
          sound.playErrorTone();
          showToast(err?.message || "Matchmaking failed", true);
        }
      });
      break;

    case "play-bot":
      await runBusy(async () => {
        try {
          const tier = STAKE_TIERS.find((t) => t.stakeUgx === state.selectedStakeUgx)?.label || "Kampala";
          const tableId = await repo.createTable({
            user: state.user,
            title: `${tier} vs Bot`,
            stakeUgx: state.selectedStakeUgx,
            maxPlayers: 2,
            isPrivate: true,
            isBotMatch: true,
          });
          sound.playCardTap();
          openCloudTable(tableId);
        } catch (err) {
          sound.playErrorTone();
          showToast(err?.message || "Unable to launch table", true);
        }
      });
      break;

    case "open-private-modal":
      openCreatePrivateRoomModal();
      break;

    case "resume-table":
      openCloudTable(target.dataset.tableId);
      break;

    case "join-public-table":
      await runBusy(async () => {
        try {
          const tableId = target.dataset.tableId;
          await repo.joinTableById(state.user, tableId);
          sound.playCardTap();
          openCloudTable(tableId);
        } catch (err) {
          sound.playErrorTone();
          showToast(err?.message || "Could not join table", true);
        }
      });
      break;

    case "set-wallet-mode":
      state.walletMode = target.dataset.mode;
      renderRoot();
      break;

    case "set-wallet-provider":
      state.walletProvider = target.dataset.provider;
      renderRoot();
      break;

    case "set-wallet-amount":
      state.walletAmount = target.dataset.amount;
      renderRoot();
      break;

    case "draft-provider":
      if (state.profileDraft) {
        state.profileDraft.preferredProvider = target.dataset.provider;
        renderRoot();
      }
      break;

    case "draft-avatar":
      if (state.profileDraft) {
        state.profileDraft.avatarKey = target.dataset.avatar;
        renderRoot();
      }
      break;

    case "set-admin-subtab":
      state.adminSubTab = target.dataset.sub;
      renderRoot();
      break;

    case "admin-approve-tx": {
      const tx = state.adminTransactions.find((t) => t.id === target.dataset.txId);
      if (!tx) break;
      await runBusy(async () => {
        try {
          await repo.adminApproveTransaction(tx);
          sound.playCoinChime();
          showToast(`Approved ${tx.referenceCode}`);
        } catch (err) {
          showToast(err?.message || "Approval failed", true);
        }
      });
      break;
    }

    case "admin-reject-tx": {
      const tx = state.adminTransactions.find((t) => t.id === target.dataset.txId);
      if (tx) openRejectTxModal(tx);
      break;
    }

    case "admin-toggle-ban":
      await runBusy(async () => {
        try {
          const ban = target.dataset.ban === "1";
          await repo.adminTogglePlayerBan(target.dataset.uid, ban);
          showToast(ban ? "Player suspended" : "Player reinstated");
        } catch (err) {
          showToast(err?.message || "Action failed", true);
        }
      });
      break;

    case "admin-close-table":
      await runBusy(async () => {
        try {
          await repo.adminCancelTable(target.dataset.tableId);
          showToast("Table closed by Admin");
        } catch (err) {
          showToast(err?.message || "Close failed", true);
        }
      });
      break;

    case "admin-resolve-cheat":
      await runBusy(async () => {
        try {
          await repo.adminResolveCheatLog(target.dataset.logId);
          showToast("Incident marked resolved");
        } catch (err) {
          showToast(err?.message || "Resolve failed", true);
        }
      });
      break;

    case "copy-invite":
      try {
        await navigator.clipboard.writeText(target.dataset.code || "");
        showToast("Copied to clipboard!");
      } catch {
        showToast(`Code: ${target.dataset.code || ""}`);
      }
      break;

    case "start-table":
      await runBusy(async () => {
        try {
          await repo.startWaitingTable(state.activeTable);
          sound.playCardTap();
        } catch (err) {
          sound.playErrorTone();
          showToast(err?.message || "Could not deal match", true);
        }
      });
      break;

    case "cancel-waiting-table":
      await runBusy(async () => {
        try {
          await repo.cancelWaitingTable(state.activeTable);
          closeCloudTableListeners();
          showToast("Table cancelled and stake refunded");
          renderRoot();
        } catch (err) {
          showToast(err?.message || "Cancel failed", true);
        }
      });
      break;

    case "back-to-lobby":
      state.practiceSession = null;
      closeCloudTableListeners();
      renderRoot();
      break;

    case "confirm-forfeit":
      closeModal();
      if (state.practiceSession) {
        state.practiceSession = null;
        renderRoot();
      } else if (state.activeTable) {
        await runBusy(async () => {
          try {
            await repo.forfeitMatch(state.activeTable);
            closeCloudTableListeners();
            showToast("Match forfeited");
            renderRoot();
          } catch (err) {
            showToast(err?.message || "Forfeit failed", true);
          }
        });
      }
      break;

    case "toggle-sort":
      state.sortHand = !state.sortHand;
      renderRoot();
      break;

    case "draw-card":
      await handleDrawAction();
      break;

    case "card-click":
      onHandCardClicked(target.dataset.card);
      break;

    case "confirm-ace-suit": {
      const cardCode = target.dataset.card;
      const suitName = target.dataset.suit;
      closeModal();
      await executePlayMove(cardCode, suitName, false);
      break;
    }

    case "confirm-seven-cut": {
      const cardCode = target.dataset.card;
      const triggerCut = target.dataset.cut === "1";
      closeModal();
      await executePlayMove(cardCode, null, triggerCut);
      break;
    }

    case "open-report-modal":
      if (state.activeTable) openReportAnomalyModal(state.activeTable);
      break;

    default:
      break;
  }
});

document.addEventListener("input", (ev) => {
  const el = ev.target;
  if (el.id === "input-invite-code") {
    state.inviteInput = el.value.toUpperCase();
  } else if (el.id === "input-momo-amount") {
    state.walletAmount = el.value;
  } else if (el.id === "input-momo-phone") {
    state.walletPhone = el.value;
  } else if (el.id === "input-profile-name" && state.profileDraft) {
    state.profileDraft.displayName = el.value;
  } else if (el.id === "input-profile-phone" && state.profileDraft) {
    state.profileDraft.phoneNumber = el.value;
  } else if (el.id === "input-profile-sound" && state.profileDraft) {
    state.profileDraft.soundEnabled = el.checked;
  }
});

document.addEventListener("submit", async (ev) => {
  const form = ev.target;
  if (!form || !form.id) return;
  ev.preventDefault();

  if (form.id === "form-join-invite") {
    const code = $("input-invite-code")?.value || state.inviteInput;
    await runBusy(async () => {
      try {
        const tableId = await repo.joinPrivateTableByCode(state.user, code);
        sound.playCoinChime();
        state.inviteInput = "";
        openCloudTable(tableId);
      } catch (err) {
        sound.playErrorTone();
        showToast(err?.message || "Invalid or closed room code", true);
      }
    });
  } else if (form.id === "form-create-private") {
    const title = $("private-room-title")?.value || "";
    const stakeUgx = Number($("private-room-stake")?.value || 0);
    const maxPlayers = Number($("private-room-seats")?.value || 2);
    closeModal();
    await runBusy(async () => {
      try {
        const tableId = await repo.createTable({
          user: state.user,
          title,
          stakeUgx,
          maxPlayers,
          isPrivate: true,
          isBotMatch: false,
        });
        sound.playCoinChime();
        openCloudTable(tableId);
      } catch (err) {
        sound.playErrorTone();
        showToast(err?.message || "Could not create room", true);
      }
    });
  } else if (form.id === "form-momo-cashier") {
    const amountUgx = Number(state.walletAmount || 0);
    const phone = state.walletPhone || state.user?.phoneNumber || "";
    await runBusy(async () => {
      try {
        if (state.walletMode === "DEPOSIT") {
          const ref = await repo.submitMobileMoneyDeposit({
            userName: state.user.displayName,
            amountUgx,
            provider: state.walletProvider,
            phoneNumber: phone,
          });
          sound.playCoinChime();
          showToast(`Deposit submitted (${ref}). Pending cashier verification.`);
        } else {
          const ref = await repo.submitMobileMoneyWithdrawal({
            userName: state.user.displayName,
            amountUgx,
            provider: state.walletProvider,
            phoneNumber: phone,
          });
          sound.playCoinChime();
          showToast(`Cashout queued (${ref}) to ${phone}.`);
        }
      } catch (err) {
        sound.playErrorTone();
        showToast(err?.message || "Transaction failed", true);
      }
    });
  } else if (form.id === "form-profile") {
    const d = state.profileDraft;
    if (!d) return;
    await runBusy(async () => {
      try {
        await repo.updateProfile(d);
        sound.isEnabled = d.soundEnabled;
        sound.playCoinChime();
        showToast("Profile saved");
      } catch (err) {
        sound.playErrorTone();
        showToast(err?.message || "Save failed", true);
      }
    });
  } else if (form.id === "form-reject-tx") {
    const txId = $("reject-tx-id")?.value;
    const reason = $("reject-reason")?.value || "Rejected by Admin";
    const tx = state.adminTransactions.find((t) => t.id === txId);
    closeModal();
    if (tx) {
      await runBusy(async () => {
        try {
          await repo.adminRejectTransaction(tx, reason);
          showToast(`Rejected ${tx.referenceCode}`);
        } catch (err) {
          showToast(err?.message || "Rejection failed", true);
        }
      });
    }
  } else if (form.id === "form-report-cheat") {
    const tableId = $("report-table-id")?.value;
    const reportedUid = $("report-target-uid")?.value;
    const details = $("report-details")?.value || "Suspicious play reported";
    closeModal();
    await runBusy(async () => {
      try {
        await repo.reportAntiCheatAnomaly({
          tableId,
          reportedUid,
          eventType: "PLAYER_REPORT",
          details,
          severity: "MEDIUM",
        });
        showToast("Anti-cheat report submitted for Admin review");
      } catch (err) {
        showToast(err?.message || "Report failed", true);
      }
    });
  }
});

// Header Controls
$("brand-home-btn")?.addEventListener("click", () => navigateToNav("lobby"));
$("mobile-menu-toggle")?.addEventListener("click", () => {
  state.mobileMenuOpen = !state.mobileMenuOpen;
  syncHeaderControls();
});
$("header-wallet-chip")?.addEventListener("click", () => navigateToNav("wallet"));
$("header-signin-btn")?.addEventListener("click", async () => {
  if (state.authUser) {
    closeCloudTableListeners();
    clearSubs(shellUnsubs);
    clearSubs(adminUnsubs);
    await services.signOut();
  } else {
    await handleGoogleSignIn();
  }
});

$("header-sound-btn")?.addEventListener("click", async () => {
  if (state.practiceSession || !state.user) {
    sound.isEnabled = !sound.isEnabled;
    if (sound.isEnabled) sound.playCardTap();
    renderRoot();
    return;
  }
  const next = !state.user.soundEnabled;
  sound.isEnabled = next;
  if (next) sound.playCardTap();
  await repo
    .updateProfile({
      displayName: state.user.displayName,
      phoneNumber: state.user.phoneNumber || "",
      preferredProvider: state.user.preferredProvider || "MTN_MOMO",
      avatarKey: state.user.avatarKey || "crown_gold",
      soundEnabled: next,
    })
    .catch(() => {});
  renderRoot();
});

// Boot Firebase Auth observer + handle redirect sign-in result
services.getRedirectResult().catch((err) => {
  if (err?.message) {
    state.authError = err.message;
    renderRoot();
  }
});

services.onAuthStateChanged((firebaseUser) => {
  state.authReady = true;
  startCloudSession(firebaseUser);
});

renderRoot();
