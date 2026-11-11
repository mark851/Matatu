import test, { after, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";

import {
  BOT_UID,
  applyDraw,
  applyPlayCard,
  calculatePoints,
  canTriggerSevenCut,
  chooseBotCard,
  dealMatch,
  isCardPlayable,
  moveDigest,
  parseCard,
  sortHandCodes,
} from "../js/engine.js";
import { createAmatatuRepository } from "../js/repository.js";
import {
  PRACTICE_PLAYER_UID,
  createPracticeSession,
  drawPracticeCard,
  playPracticeCard,
  stepPracticeBotTurn,
} from "../js/practice-mode.js";

const require = createRequire(import.meta.url);
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_EMAIL = "markniaras@gmail.com";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

/**
 * Adapts @firebase/rules-unit-testing compat Firestore to the modular v9/v11 fsOps
 * interface consumed by website/js/repository.js.
 */
function createCompatAdapter(rawDb) {
  const wrapSnap = (snap) => ({
    id: snap.id,
    exists: () => snap.exists,
    data: () => snap.data(),
  });

  const makeDocHandle = (rawDoc) => ({ _kind: "doc", _raw: rawDoc });
  const makeColHandle = (rawCol) => ({ _kind: "col", _raw: rawCol });

  const fsOps = {
    doc(db, path, ...segments) {
      const fullPath = [path, ...segments].filter(Boolean).join("/");
      return makeDocHandle(db.doc(fullPath));
    },
    collection(db, path, ...segments) {
      const fullPath = [path, ...segments].filter(Boolean).join("/");
      return makeColHandle(db.collection(fullPath));
    },
    where(field, op, value) {
      return { type: "where", field, op, value };
    },
    orderBy(field, dir = "asc") {
      return { type: "orderBy", field, dir };
    },
    limit(n) {
      return { type: "limit", n };
    },
    query(colHandle, ...constraints) {
      let q = colHandle._raw;
      for (const c of constraints) {
        if (c.type === "where") q = q.where(c.field, c.op, c.value);
        else if (c.type === "orderBy") q = q.orderBy(c.field, c.dir);
        else if (c.type === "limit") q = q.limit(c.n);
      }
      return { _kind: "query", _raw: q };
    },
    async getDoc(docHandle) {
      const s = await docHandle._raw.get();
      return wrapSnap(s);
    },
    async getDocs(queryHandle) {
      return queryHandle._raw.get();
    },
    onSnapshot(target, onNext, onError) {
      if (target._kind === "doc") {
        return target._raw.onSnapshot((s) => onNext(wrapSnap(s)), onError);
      }
      return target._raw.onSnapshot(onNext, onError);
    },
    writeBatch(db) {
      const b = db.batch();
      return {
        set: (docHandle, data) => b.set(docHandle._raw, data),
        update: (docHandle, data) => b.update(docHandle._raw, data),
        delete: (docHandle) => b.delete(docHandle._raw),
        commit: () => b.commit(),
      };
    },
    async runTransaction(db, updateFn) {
      return db.runTransaction(async (compatTxn) => {
        const txnWrapper = {
          get: async (docHandle) => wrapSnap(await compatTxn.get(docHandle._raw)),
          set: (docHandle, data) => compatTxn.set(docHandle._raw, data),
          update: (docHandle, data) => compatTxn.update(docHandle._raw, data),
        };
        return updateFn(txnWrapper);
      });
    },
    serverTimestamp: () => new Date(Date.now() - 1000),
  };

  return { db: rawDb, fsOps };
}

function repoForUser(testEnv, uid, token = {}) {
  const ctx = testEnv.authenticatedContext(uid, token);
  const rawDb = ctx.firestore();
  const { db, fsOps } = createCompatAdapter(rawDb);
  const auth = {
    currentUser: {
      uid,
      email: token.email || null,
      emailVerified: Boolean(token.email_verified),
    },
  };
  return createAmatatuRepository(db, auth, fsOps);
}

// ============================================================================
// Pure Website Engine & Practice Mode Unit Tests
// ============================================================================

test("dealMatch deals 7 cards per player without duplicates and produces SHA-256 commit", async () => {
  const deal = await dealMatch("tbl_unit_1", ["alice", "bob"]);
  assert.equal(deal.handsByPlayer.alice.length, 7);
  assert.equal(deal.handsByPlayer.bob.length, 7);
  assert.equal(deal.deckCommitHash.length, 64);

  const starter = parseCard(deal.topCard);
  assert.ok(!["2", "A", "J", "8"].includes(starter.rank.code), "Starter card cannot be an action card");

  const allDealt = [
    ...deal.handsByPlayer.alice,
    ...deal.handsByPlayer.bob,
    deal.topCard,
    deal.cutCard,
    ...deal.drawPile,
  ];
  assert.equal(allDealt.length, 52);
  assert.equal(new Set(allDealt).size, 52);
});

test("Pick-Two attack stacks and can only be countered by 2 or Ace of Spades", () => {
  const hand = ["2D", "AS", "AH", "9H"];
  assert.equal(isCardPlayable(parseCard("9H"), "2H", "", 2, "CLUBS", hand), false);
  assert.equal(isCardPlayable(parseCard("AH"), "2H", "", 2, "CLUBS", hand), false);
  assert.equal(isCardPlayable(parseCard("2D"), "2H", "", 2, "CLUBS", hand), true);
  assert.equal(isCardPlayable(parseCard("AS"), "2H", "", 2, "CLUBS", hand), true);

  const stack = applyPlayCard({
    playerId: "p1",
    playerName: "Alice",
    cardCode: "2D",
    hand: ["2D", "4C"],
    playerIds: ["p1", "p2"],
    topCardCode: "2H",
    pendingDrawPenalty: 2,
    cutSuitName: "CLUBS",
  });
  assert.equal(stack.valid, true);
  assert.equal(stack.newPendingPenalty, 4);

  const shield = applyPlayCard({
    playerId: "p2",
    playerName: "Bob",
    cardCode: "AS",
    chosenSuitForAce: "DIAMONDS",
    hand: ["AS", "5D"],
    playerIds: ["p1", "p2"],
    topCardCode: "2D",
    pendingDrawPenalty: 4,
    cutSuitName: "CLUBS",
  });
  assert.equal(shield.valid, true);
  assert.equal(shield.newPendingPenalty, 0);
  assert.equal(shield.newDemandedSuit, "DIAMONDS");
});

test("7-Cut Showdown requires <= 25 remaining points and triggers SHOWDOWN", () => {
  const eligible = canTriggerSevenCut(parseCard("7H"), ["7H", "3C", "4D", "10S"], "HEARTS", 0);
  assert.equal(eligible, true);

  const tooHigh = canTriggerSevenCut(parseCard("7H"), ["7H", "KC", "AD"], "HEARTS", 0);
  assert.equal(tooHigh, false);

  const cutPlay = applyPlayCard({
    playerId: "p1",
    playerName: "Alice",
    cardCode: "7H",
    triggerCut: true,
    hand: ["7H", "3C", "4D"],
    playerIds: ["p1", "p2"],
    topCardCode: "9S",
    cutSuitName: "HEARTS",
  });
  assert.equal(cutPlay.valid, true);
  assert.equal(cutPlay.triggersSevenCutShowdown, true);
});

test("Jump J/8 on 2-player returns turn immediately; King reverses 3-player direction; draw recycles discard", () => {
  const jump = applyPlayCard({
    playerId: "p1",
    playerName: "Alice",
    cardCode: "JS",
    hand: ["JS", "4C"],
    playerIds: ["p1", "p2"],
    topCardCode: "9S",
    cutSuitName: "HEARTS",
  });
  assert.equal(jump.nextTurnUid, "p1");

  const reverse = applyPlayCard({
    playerId: "p1",
    playerName: "Alice",
    cardCode: "KH",
    hand: ["KH", "4C"],
    playerIds: ["p1", "p2", "p3"],
    topCardCode: "5H",
    turnDirection: 1,
    cutSuitName: "CLUBS",
  });
  assert.equal(reverse.newTurnDirection, -1);
  assert.equal(reverse.nextTurnUid, "p3");

  const draw = applyDraw({
    playerId: "p1",
    playerName: "Alice",
    hand: ["4C"],
    playerIds: ["p1", "p2"],
    pendingDrawPenalty: 2,
    topCardCode: "2H",
    drawPile: [],
    discardPile: ["6C", "8D", "2H"],
  });
  assert.equal(draw.drawnCards.length, 2);
  assert.deepEqual(draw.newDiscardPile, ["2H"]);
});

test("Offline practice session completes plays, draws, sorting, digests, and bot responses cleanly", async () => {
  const session = await createPracticeSession("Kato");
  assert.equal(session.myHand.cards.length, 7);
  assert.equal(sortHandCodes(session.myHand.cards).length, 7);
  const dig = await moveDigest(session.table.id, 1, PRACTICE_PLAYER_UID, "DRAW", "", session.table.topCard);
  assert.equal(dig.length, 24);

  const playable = session.myHand.cards.find((c) =>
    isCardPlayable(
      parseCard(c),
      session.table.topCard,
      session.table.demandedSuit,
      session.table.pendingDrawPenalty,
      session.table.cutSuit,
      session.myHand.cards
    )
  );
  if (playable) {
    const parsed = parseCard(playable);
    await playPracticeCard(session, playable, parsed.rank.code === "A" ? "SPADES" : null, false);
  } else {
    await drawPracticeCard(session);
  }

  if (session.table.status === "ACTIVE" && session.table.currentTurnUid === BOT_UID) {
    await stepPracticeBotTurn(session);
  }
  assert.ok(session.moves.length >= 1);

  const botPick = chooseBotCard({
    botHand: ["8C", "2H", "AC"],
    topCardCode: "9C",
    cutSuitName: "DIAMONDS",
  });
  assert.equal(botPick.card.code, "8C");
});

// ============================================================================
// Live Firestore Security Rules + Website Repository Integration Tests
// ============================================================================

let testEnv;

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Website Repository: User initialization, MoMo Deposit/Withdraw & Admin Cashier Approve/Reject flow", async () => {
  const aliceRepo = repoForUser(testEnv, "web_alice_1", { email: "alice@example.com", email_verified: true });
  const adminRepo = repoForUser(testEnv, "web_admin_1", { email: ADMIN_EMAIL, email_verified: true });

  const alice = await assertSucceeds(aliceRepo.ensureUserAccountInitialized("Alice Web"));
  assert.equal(alice.walletBalanceUgx, 25000);
  assert.equal(await aliceRepo.checkIsAdmin(), false);
  assert.equal(await adminRepo.checkIsAdmin(), true);

  // Alice requests UGX 10,000 MTN MoMo Deposit
  await assertSucceeds(
    aliceRepo.submitMobileMoneyDeposit({
      userName: alice.displayName,
      amountUgx: 10000,
      provider: "MTN_MOMO",
      phoneNumber: "+256772111222",
    })
  );

  // Alice requests UGX 5,000 Airtel Cashout (deducts upfront into pending withdrawal)
  await assertSucceeds(
    aliceRepo.submitMobileMoneyWithdrawal({
      userName: alice.displayName,
      amountUgx: 5000,
      provider: "AIRTEL_MONEY",
      phoneNumber: "+256752111222",
    })
  );

  const afterWdl = await testEnv.authenticatedContext("web_alice_1").firestore().collection("users").doc("web_alice_1").get();
  assert.equal(afterWdl.data().walletBalanceUgx, 20000);

  // Admin queries all transactions and approves Deposit (+10k) and rejects Withdrawal (refunds +5k)
  const adminTxSnap = await testEnv
    .authenticatedContext("web_admin_1", { email: ADMIN_EMAIL, email_verified: true })
    .firestore()
    .collection("transactions")
    .get();
  const pendingTxs = adminTxSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const depTx = pendingTxs.find((t) => t.type === "DEPOSIT");
  const wdlTx = pendingTxs.find((t) => t.type === "WITHDRAWAL");

  await assertSucceeds(adminRepo.adminApproveTransaction(depTx));
  await assertSucceeds(adminRepo.adminRejectTransaction(wdlTx, "Incorrect MoMo name"));

  const finalAlice = await testEnv.authenticatedContext("web_alice_1").firestore().collection("users").doc("web_alice_1").get();
  assert.equal(finalAlice.data().walletBalanceUgx, 35000); // 20k + 10k deposit + 5k withdrawal refund
});

test("Website Repository: Bot match lifecycle (Create atomic hands, Draw, Forfeit/Settle 9,500 UGX)", async () => {
  const aliceRepo = repoForUser(testEnv, "web_alice_2");
  const alice = await assertSucceeds(aliceRepo.ensureUserAccountInitialized("Web Challenger"));

  const tableId = await assertSucceeds(
    aliceRepo.createTable({
      user: alice,
      title: "Kololo Club vs Bot",
      stakeUgx: 5000,
      maxPlayers: 2,
      isPrivate: true,
      isBotMatch: true,
    })
  );

  const aliceDb = testEnv.authenticatedContext("web_alice_2").firestore();
  const tableSnap = await aliceDb.collection("tables").doc(tableId).get();
  const myHandSnap = await aliceDb.collection("tables").doc(tableId).collection("hands").doc("web_alice_2").get();
  const botHandSnap = await aliceDb.collection("tables").doc(tableId).collection("hands").doc(BOT_UID).get();

  const table = { id: tableSnap.id, ...tableSnap.data() };
  const myHand = { id: myHandSnap.id, ...myHandSnap.data() };
  const botHand = { id: botHandSnap.id, ...botHandSnap.data() };

  assert.equal(table.status, "ACTIVE");
  assert.equal(table.payoutUgx, 9500);
  assert.equal(myHand.cards.length, 7);
  assert.equal(botHand.cards.length, 7);

  // Draw one card (passes turn to Bot) then step Bot turn
  await assertSucceeds(aliceRepo.drawCard(table, myHand));
  const afterDrawTableSnap = await aliceDb.collection("tables").doc(tableId).get();
  const afterDrawTable = { id: afterDrawTableSnap.id, ...afterDrawTableSnap.data() };
  assert.equal(afterDrawTable.currentTurnUid, BOT_UID);
  assert.equal(afterDrawTable.moveSequence, 1);

  await assertSucceeds(aliceRepo.executeBotTurn(afterDrawTable, botHand));
  const afterBotSnap = await aliceDb.collection("tables").doc(tableId).get();
  assert.equal(afterBotSnap.data().moveSequence, 2);
});

test("Website Repository: Cross-player Private Room Invite -> Join -> Start -> Hand Isolation -> Forfeit -> Winner Settlement", async () => {
  const hostRepo = repoForUser(testEnv, "web_host_1");
  const guestRepo = repoForUser(testEnv, "web_guest_1");
  const hostUser = await assertSucceeds(hostRepo.ensureUserAccountInitialized("Kampala Host"));
  const guestUser = await assertSucceeds(guestRepo.ensureUserAccountInitialized("Entebbe Guest"));

  const tableId = await assertSucceeds(
    hostRepo.createTable({
      user: hostUser,
      title: "Private VIP Felt",
      stakeUgx: 5000,
      maxPlayers: 2,
      isPrivate: true,
      isBotMatch: false,
    })
  );

  const hostDb = testEnv.authenticatedContext("web_host_1").firestore();
  const guestDb = testEnv.authenticatedContext("web_guest_1").firestore();

  const createdTable = (await hostDb.collection("tables").doc(tableId).get()).data();
  assert.ok(createdTable.inviteCode.startsWith("AMT"));

  // Guest joins using exact invite code
  const joinedId = await assertSucceeds(guestRepo.joinPrivateTableByCode(guestUser, createdTable.inviteCode));
  assert.equal(joinedId, tableId);

  const waitingTableSnap = await guestDb.collection("tables").doc(tableId).get();
  const waitingTable = { id: waitingTableSnap.id, ...waitingTableSnap.data() };
  assert.equal(waitingTable.potUgx, 10000);

  // Guest or Host deals cards
  await assertSucceeds(hostRepo.startWaitingTable(waitingTable));

  // Hand isolation enforced by Firestore security rules
  await assertFails(guestDb.collection("tables").doc(tableId).collection("hands").doc("web_host_1").get());
  await assertSucceeds(guestDb.collection("tables").doc(tableId).collection("hands").doc("web_guest_1").get());

  // Guest forfeits, Host claims 9,500 UGX pot
  const activeSnap = await guestDb.collection("tables").doc(tableId).get();
  await assertSucceeds(guestRepo.forfeitMatch({ id: activeSnap.id, ...activeSnap.data() }));

  const finishedSnap = await hostDb.collection("tables").doc(tableId).get();
  const finishedTable = { id: finishedSnap.id, ...finishedSnap.data() };
  assert.equal(finishedTable.status, "FINISHED");
  assert.equal(finishedTable.winnerId, "web_host_1");

  const credited = await assertSucceeds(hostRepo.settleFinishedMatchForCurrentUser(finishedTable));
  assert.equal(credited, true);

  const hostFinal = (await hostDb.collection("users").doc("web_host_1").get()).data();
  assert.equal(hostFinal.walletBalanceUgx, 29500); // 25k starter - 5k stake + 9.5k pot payout
  assert.equal(hostFinal.gamesWon, 1);

  const histSnap = await hostDb.collection("game_history").doc(`hist_${tableId}`).get();
  assert.equal(histSnap.exists, true);
  assert.equal(histSnap.data().payoutUgx, 9500);
});

test("Website HTML/CSS render standard website navigation and footer rather than native app framing", () => {
  const html = fs.readFileSync("./website/index.html", "utf8");
  const css = fs.readFileSync("./website/styles.css", "utf8");

  assert.ok(html.includes('<header class="site-header">'));
  assert.ok(html.includes('<footer class="site-footer">'));
  assert.ok(html.includes('id="mobile-web-drawer"'));
  assert.ok(css.includes(".web-nav"));
  assert.ok(css.includes(".mobile-menu-toggle"));

  // Must not include native-app bottom tab bar or standalone PWA app-mode flags
  assert.equal(html.includes("mobile-bottom-nav"), false);
  assert.equal(css.includes("mobile-bottom-nav"), false);
  assert.equal(html.includes("apple-mobile-web-app-capable"), false);
  assert.equal(html.includes("mobile-web-app-capable"), false);
  assert.equal(html.includes('rel="manifest"'), false);
});
