import {
  ADMIN_EMAIL,
  BOT_AVATAR,
  BOT_NAME,
  BOT_UID,
  applyDraw,
  applyPlayCard,
  calculatePoints,
  canTriggerSevenCut,
  chooseBotCard,
  dealMatch,
  moveDigest,
  parseCard,
} from "./engine.js";

function randomHex(len = 16) {
  const bytes = new Uint8Array(len);
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < len; i += 1) bytes[i] = Math.floor(Math.random() * 256);
  }
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("").slice(0, len);
}

function newDocId(prefix) {
  return `${prefix}_${randomHex(16)}`;
}

function generateInviteCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let code = "AMT";
  for (let i = 0; i < 4; i += 1) {
    code += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  return code;
}

function generateReference(prefix) {
  return `${prefix}-${Math.floor(100000 + Math.random() * 900000)}`;
}

export function timestampToSeconds(ts) {
  if (!ts) return 0;
  if (typeof ts.seconds === "number") return ts.seconds;
  if (typeof ts.toMillis === "function") return Math.floor(ts.toMillis() / 1000);
  if (ts instanceof Date) return Math.floor(ts.getTime() / 1000);
  return 0;
}

function snapWithId(snap) {
  if (!snap || !snap.exists()) return null;
  return { id: snap.id, ...snap.data() };
}

function docsWithId(querySnap) {
  return querySnap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export function createAmatatuRepository(db, auth, fs) {
  const {
    doc,
    collection,
    query,
    where,
    orderBy,
    limit,
    getDoc,
    onSnapshot,
    writeBatch,
    runTransaction,
    serverTimestamp,
  } = fs;

  function requireUserId() {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error("Sign in with Google before accessing Amatatu tables.");
    return uid;
  }

  function docRef(path, ...segments) {
    return doc(db, path, ...segments);
  }

  function colRef(path, ...segments) {
    return collection(db, path, ...segments);
  }

  async function ensureUserAccountInitialized(displayName, photoUrl = null) {
    const uid = requireUserId();
    const userRef = docRef("users", uid);
    const snap = await getDoc(userRef);
    if (snap.exists()) {
      return snapWithId(snap);
    }

    let cleanName = String(displayName || "").trim().slice(0, 36);
    if (cleanName.length < 2) cleanName = "Kampala Player";
    const welcomeTxId = newDocId("tx");
    const batch = writeBatch(db);

    const userPayload = {
      userId: uid,
      displayName: cleanName,
      phoneNumber: "",
      preferredProvider: "MTN_MOMO",
      avatarKey: "crown_gold",
      walletBalanceUgx: 25000,
      escrowUgx: 0,
      gamesPlayed: 0,
      gamesWon: 0,
      totalPointsWon: 0,
      totalEarningsUgx: 0,
      isBanned: false,
      soundEnabled: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    if (photoUrl && String(photoUrl).trim()) {
      userPayload.photoUrl = String(photoUrl).trim().slice(0, 450);
    }

    batch.set(userRef, userPayload);
    batch.set(docRef("public_profiles", uid), {
      userId: uid,
      displayName: cleanName,
      avatarKey: "crown_gold",
      gamesPlayed: 0,
      gamesWon: 0,
      totalEarningsUgx: 0,
      updatedAt: serverTimestamp(),
    });
    batch.set(docRef("transactions", welcomeTxId), {
      userId: uid,
      userName: cleanName,
      type: "WELCOME_BONUS",
      amountUgx: 25000,
      feeUgx: 0,
      netAmountUgx: 25000,
      provider: "SYSTEM",
      phoneNumber: "",
      referenceCode: generateReference("WLC"),
      tableId: "",
      status: "COMPLETED",
      note: "Starter UGX club chips",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });

    await batch.commit();
    const created = await getDoc(userRef);
    return snapWithId(created) || { id: uid, ...userPayload };
  }

  async function checkIsAdmin() {
    const user = auth.currentUser;
    if (!user) return false;
    if (
      user.email &&
      user.email.toLowerCase() === ADMIN_EMAIL.toLowerCase() &&
      user.emailVerified
    ) {
      return true;
    }
    try {
      const adminSnap = await getDoc(docRef("admins", user.uid));
      return adminSnap.exists();
    } catch {
      return false;
    }
  }

  function observeCurrentUser(uid, onData, onError) {
    return onSnapshot(
      docRef("users", uid),
      (snap) => onData(snapWithId(snap)),
      (err) => onError?.(err)
    );
  }

  function observeLeaderboard(onData, onError) {
    const q = query(
      colRef("public_profiles"),
      where("gamesWon", ">=", 0),
      orderBy("gamesWon", "desc"),
      limit(25)
    );
    return onSnapshot(
      q,
      (snap) => onData(docsWithId(snap)),
      (err) => onError?.(err)
    );
  }

  function observePublicWaitingTables(onData, onError) {
    const q = query(
      colRef("tables"),
      where("isPrivate", "==", false),
      where("status", "==", "WAITING")
    );
    return onSnapshot(
      q,
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => timestampToSeconds(b.createdAt) - timestampToSeconds(a.createdAt)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  function observeUserTables(uid, onData, onError) {
    const q = query(colRef("tables"), where("playerIds", "array-contains", uid));
    return onSnapshot(
      q,
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => timestampToSeconds(b.updatedAt) - timestampToSeconds(a.updatedAt)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  function observeTable(tableId, onData, onError) {
    return onSnapshot(
      docRef("tables", tableId),
      (snap) => onData(snapWithId(snap)),
      (err) => onError?.(err)
    );
  }

  function observePlayerHand(tableId, playerUid, onData, onError) {
    return onSnapshot(
      docRef("tables", tableId, "hands", playerUid),
      (snap) => onData(snapWithId(snap)),
      (err) => onError?.(err)
    );
  }

  function observeTableMoves(tableId, uid, onData, onError) {
    const q = query(
      colRef("tables", tableId, "moves"),
      where("playerIds", "array-contains", uid)
    );
    return onSnapshot(
      q,
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => Number(b.moveSequence || 0) - Number(a.moveSequence || 0)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  function observeUserTransactions(uid, onData, onError) {
    const q = query(colRef("transactions"), where("userId", "==", uid));
    return onSnapshot(
      q,
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => timestampToSeconds(b.createdAt) - timestampToSeconds(a.createdAt)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  function observeUserGameHistory(uid, onData, onError) {
    const q = query(colRef("game_history"), where("playerIds", "array-contains", uid));
    return onSnapshot(
      q,
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => timestampToSeconds(b.createdAt) - timestampToSeconds(a.createdAt)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  async function updateProfile({ displayName, phoneNumber, preferredProvider, avatarKey, soundEnabled }) {
    const uid = requireUserId();
    const cleanName = String(displayName || "").trim().slice(0, 36);
    if (cleanName.length < 2) throw new Error("Display name must be at least 2 characters.");
    const cleanPhone = String(phoneNumber || "").trim().slice(0, 20);
    const batch = writeBatch(db);
    batch.update(docRef("users", uid), {
      displayName: cleanName,
      phoneNumber: cleanPhone,
      preferredProvider,
      avatarKey,
      soundEnabled: Boolean(soundEnabled),
      updatedAt: serverTimestamp(),
    });
    batch.update(docRef("public_profiles", uid), {
      displayName: cleanName,
      avatarKey,
      updatedAt: serverTimestamp(),
    });
    await batch.commit();
  }

  async function submitMobileMoneyDeposit({ userName, amountUgx, provider, phoneNumber }) {
    const uid = requireUserId();
    const amt = Math.round(Number(amountUgx || 0));
    if (amt < 1000 || amt > 5000000) {
      throw new Error("Deposit must be between UGX 1,000 and UGX 5,000,000.");
    }
    const cleanPhone = String(phoneNumber || "").trim();
    if (cleanPhone.length < 9) {
      throw new Error("Enter a valid Ugandan Mobile Money number.");
    }
    const txId = newDocId("dep");
    const refCode = generateReference(provider === "MTN_MOMO" ? "MTN" : "AIR");
    const batch = writeBatch(db);
    batch.set(docRef("transactions", txId), {
      userId: uid,
      userName: String(userName || "Player").slice(0, 40),
      type: "DEPOSIT",
      amountUgx: amt,
      feeUgx: 0,
      netAmountUgx: amt,
      provider,
      phoneNumber: cleanPhone.slice(0, 20),
      referenceCode: refCode,
      tableId: "",
      status: "PENDING",
      note: "Mobile Money deposit awaiting cashier verification",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    await batch.commit();
    return refCode;
  }

  async function submitMobileMoneyWithdrawal({ userName, amountUgx, provider, phoneNumber }) {
    const uid = requireUserId();
    const amt = Math.round(Number(amountUgx || 0));
    if (amt < 2000 || amt > 5000000) {
      throw new Error("Withdrawal must be between UGX 2,000 and UGX 5,000,000.");
    }
    const cleanPhone = String(phoneNumber || "").trim();
    if (cleanPhone.length < 9) {
      throw new Error("Enter a valid Ugandan Mobile Money number.");
    }
    const txId = newDocId("wdl");
    const refCode = generateReference(provider === "MTN_MOMO" ? "MTW" : "ATW");
    const userRef = docRef("users", uid);
    const txRef = docRef("transactions", txId);

    await runTransaction(db, async (txn) => {
      const snap = await txn.get(userRef);
      const data = snap.data() || {};
      const balance = Number(data.walletBalanceUgx || 0);
      if (data.isBanned) throw new Error("Account is suspended. Contact Admin.");
      if (balance < amt) throw new Error("Insufficient UGX balance.");

      txn.update(userRef, {
        walletBalanceUgx: balance - amt,
        updatedAt: serverTimestamp(),
      });
      txn.set(txRef, {
        userId: uid,
        userName: String(userName || "Player").slice(0, 40),
        type: "WITHDRAWAL",
        amountUgx: amt,
        feeUgx: 0,
        netAmountUgx: amt,
        provider,
        phoneNumber: cleanPhone.slice(0, 20),
        referenceCode: refCode,
        tableId: "",
        status: "PENDING",
        note: "Mobile Money cashout queued for payout",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    });

    return refCode;
  }

  async function createTable({ user, title, stakeUgx, maxPlayers, isPrivate, isBotMatch }) {
    const uid = requireUserId();
    if (user.isBanned) throw new Error("Account is suspended.");
    const stake = Math.round(Number(stakeUgx || 0));
    if (stake < 0 || stake > 100000) throw new Error("Invalid UGX stake.");
    const seats = Math.min(4, Math.max(2, Number(maxPlayers || 2)));
    const tableId = newDocId("tbl");
    const inviteCode = isPrivate && !isBotMatch ? generateInviteCode() : "";
    const userRef = docRef("users", uid);
    const tableRef = docRef("tables", tableId);

    const deal = isBotMatch ? await dealMatch(tableId, [uid, BOT_UID]) : null;
    const potIfBot = stake * 2;
    const payoutIfBot = stake === 0 ? 0 : Math.floor((potIfBot * 95) / 100);

    let cleanTitle = String(title || "").trim() || "Amatatu Table";
    if (cleanTitle.length < 2) cleanTitle = `${cleanTitle} Table`;
    cleanTitle = cleanTitle.slice(0, 48);

    await runTransaction(db, async (txn) => {
      const uSnap = await txn.get(userRef);
      const uData = uSnap.data() || {};
      const balance = Number(uData.walletBalanceUgx || 0);
      const escrow = Number(uData.escrowUgx || 0);
      if (balance < stake) {
        throw new Error(`Insufficient UGX balance for stake UGX ${stake}`);
      }

      if (stake > 0) {
        txn.update(userRef, {
          walletBalanceUgx: balance - stake,
          escrowUgx: escrow + stake,
          updatedAt: serverTimestamp(),
        });
        txn.set(docRef("transactions", newDocId("esc")), {
          userId: uid,
          userName: String(user.displayName || "Player").slice(0, 40),
          type: "STAKE_ESCROW",
          amountUgx: stake,
          feeUgx: 0,
          netAmountUgx: stake,
          provider: "WALLET_ESCROW",
          phoneNumber: "",
          referenceCode: generateReference("ESC"),
          tableId,
          status: "COMPLETED",
          note: `Table stake locked: ${cleanTitle}`,
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }

      if (isBotMatch && deal) {
        const playerHand = deal.handsByPlayer[uid] || [];
        const botHand = deal.handsByPlayer[BOT_UID] || [];
        txn.set(tableRef, {
          title: cleanTitle,
          hostId: uid,
          stakeUgx: stake,
          platformFeePct: 5,
          potUgx: potIfBot,
          payoutUgx: payoutIfBot,
          maxPlayers: 2,
          isPrivate: true,
          inviteCode: "",
          isBotMatch: true,
          status: "ACTIVE",
          playerIds: [uid, BOT_UID],
          playerNames: {
            [uid]: String(user.displayName || "Player").slice(0, 36),
            [BOT_UID]: BOT_NAME,
          },
          playerAvatars: {
            [uid]: user.avatarKey || "crown_gold",
            [BOT_UID]: BOT_AVATAR,
          },
          cardCounts: {
            [uid]: playerHand.length,
            [BOT_UID]: botHand.length,
          },
          turnDirection: 1,
          currentTurnUid: uid,
          cutSuit: deal.cutSuit.name,
          cutCard: deal.cutCard,
          topCard: deal.topCard,
          demandedSuit: "",
          pendingDrawPenalty: 0,
          drawPile: deal.drawPile,
          discardPile: deal.discardPile,
          moveSequence: 0,
          lastActionSummary: `Match dealt! Cut Suit is ${deal.cutSuit.label}. Your turn.`,
          deckCommitHash: deal.deckCommitHash,
          cutCallerId: "",
          winnerId: "",
          winnerName: "",
          winReason: "",
          payoutSettled: false,
          flaggedCheat: false,
          cheatReason: "",
          revealedHands: {},
          revealedPoints: {},
          settledUids: [],
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
        txn.set(docRef("tables", tableId, "hands", uid), {
          playerId: uid,
          tableId,
          cards: playerHand,
          handPoints: calculatePoints(playerHand),
          updatedAt: serverTimestamp(),
        });
        txn.set(docRef("tables", tableId, "hands", BOT_UID), {
          playerId: BOT_UID,
          tableId,
          cards: botHand,
          handPoints: calculatePoints(botHand),
          updatedAt: serverTimestamp(),
        });
      } else {
        const initPayout = stake === 0 ? 0 : Math.floor((stake * seats * 95) / 100);
        txn.set(tableRef, {
          title: cleanTitle,
          hostId: uid,
          stakeUgx: stake,
          platformFeePct: 5,
          potUgx: stake,
          payoutUgx: initPayout,
          maxPlayers: seats,
          isPrivate: Boolean(isPrivate),
          inviteCode,
          isBotMatch: false,
          status: "WAITING",
          playerIds: [uid],
          playerNames: { [uid]: String(user.displayName || "Player").slice(0, 36) },
          playerAvatars: { [uid]: user.avatarKey || "crown_gold" },
          cardCounts: { [uid]: 0 },
          turnDirection: 1,
          currentTurnUid: uid,
          cutSuit: "",
          cutCard: "",
          topCard: "",
          demandedSuit: "",
          pendingDrawPenalty: 0,
          drawPile: [],
          discardPile: [],
          moveSequence: 0,
          lastActionSummary: "Waiting for players to take seats",
          deckCommitHash: "",
          cutCallerId: "",
          winnerId: "",
          winnerName: "",
          winReason: "",
          payoutSettled: false,
          flaggedCheat: false,
          cheatReason: "",
          revealedHands: {},
          revealedPoints: {},
          settledUids: [],
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });

        if (isPrivate && inviteCode) {
          txn.set(docRef("invite_codes", inviteCode), {
            code: inviteCode,
            tableId,
            hostId: uid,
            stakeUgx: stake,
            maxPlayers: seats,
            status: "WAITING",
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
          });
        }
      }
    });

    return tableId;
  }

  async function joinTableById(user, tableId) {
    const uid = requireUserId();
    if (user.isBanned) throw new Error("Account is suspended.");
    const userRef = docRef("users", uid);
    const tableRef = docRef("tables", tableId);

    await runTransaction(db, async (txn) => {
      const tSnap = await txn.get(tableRef);
      if (!tSnap.exists()) throw new Error("Table not found.");
      const table = { id: tSnap.id, ...tSnap.data() };
      if ((table.playerIds || []).includes(uid)) return;
      if (table.status !== "WAITING") throw new Error("Match already started or closed.");
      if (table.playerIds.length >= Number(table.maxPlayers || 2)) {
        throw new Error("Table is full.");
      }

      const uSnap = await txn.get(userRef);
      const uData = uSnap.data() || {};
      const balance = Number(uData.walletBalanceUgx || 0);
      const escrow = Number(uData.escrowUgx || 0);
      const stake = Number(table.stakeUgx || 0);
      if (balance < stake) {
        throw new Error(`Insufficient UGX balance (need UGX ${stake}).`);
      }

      const newPlayers = [...table.playerIds, uid];
      const newNames = { ...table.playerNames, [uid]: String(user.displayName || "Player").slice(0, 36) };
      const newAvatars = { ...table.playerAvatars, [uid]: user.avatarKey || "crown_gold" };
      const newCounts = { ...table.cardCounts, [uid]: 0 };
      const newPot = stake * newPlayers.length;
      const feePct = Number(table.platformFeePct ?? 5);
      const newPayout = stake === 0 ? 0 : Math.floor((newPot * (100 - feePct)) / 100);

      if (stake > 0) {
        txn.update(userRef, {
          walletBalanceUgx: balance - stake,
          escrowUgx: escrow + stake,
          updatedAt: serverTimestamp(),
        });
        txn.set(docRef("transactions", newDocId("esc")), {
          userId: uid,
          userName: String(user.displayName || "Player").slice(0, 40),
          type: "STAKE_ESCROW",
          amountUgx: stake,
          feeUgx: 0,
          netAmountUgx: stake,
          provider: "WALLET_ESCROW",
          phoneNumber: "",
          referenceCode: generateReference("ESC"),
          tableId,
          status: "COMPLETED",
          note: `Joined table ${table.title}`,
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }

      txn.update(tableRef, {
        playerIds: newPlayers,
        playerNames: newNames,
        playerAvatars: newAvatars,
        cardCounts: newCounts,
        potUgx: newPot,
        payoutUgx: newPayout,
        updatedAt: serverTimestamp(),
      });
    });
  }

  async function quickMatchOnline(user, stakeUgx) {
    const uid = requireUserId();
    const stake = Math.round(Number(stakeUgx || 0));
    const q = query(
      colRef("tables"),
      where("isPrivate", "==", false),
      where("status", "==", "WAITING")
    );
    const snap = await fs.getDocs(q);
    const candidate = docsWithId(snap).find(
      (tbl) =>
        Number(tbl.stakeUgx || 0) === stake &&
        !(tbl.playerIds || []).includes(uid) &&
        (tbl.playerIds || []).length < Number(tbl.maxPlayers || 2)
    );
    if (candidate) {
      await joinTableById(user, candidate.id);
      return candidate.id;
    }
    const label = stake === 0 ? "Open Felt" : `UGX ${stake} Open Table`;
    return createTable({
      user,
      title: label,
      stakeUgx: stake,
      maxPlayers: 2,
      isPrivate: false,
      isBotMatch: false,
    });
  }

  async function joinPrivateTableByCode(user, rawCode) {
    requireUserId();
    const cleanCode = String(rawCode || "").trim().toUpperCase();
    if (cleanCode.length < 4 || cleanCode.length > 12) {
      throw new Error("Enter a valid invite code (e.g. AMT7K2P).");
    }
    const inviteSnap = await getDoc(docRef("invite_codes", cleanCode));
    if (!inviteSnap.exists()) throw new Error(`Room code ${cleanCode} not found.`);
    const invite = inviteSnap.data();
    if (invite.status !== "WAITING") {
      throw new Error(`Room ${cleanCode} is no longer open (${invite.status}).`);
    }
    await joinTableById(user, invite.tableId);
    return invite.tableId;
  }

  async function startWaitingTable(table) {
    const uid = requireUserId();
    if (table.status !== "WAITING") throw new Error("Table is not waiting.");
    if (!(table.playerIds || []).includes(uid)) throw new Error("Only seated players can start.");
    if ((table.playerIds || []).length < 2) throw new Error("Need at least 2 players to start.");

    const deal = await dealMatch(table.id, table.playerIds);
    const counts = {};
    for (const [pUid, cards] of Object.entries(deal.handsByPlayer)) {
      counts[pUid] = cards.length;
    }
    const stake = Number(table.stakeUgx || 0);
    const pot = stake * table.playerIds.length;
    const feePct = Number(table.platformFeePct ?? 5);
    const payout = stake === 0 ? 0 : Math.floor((pot * (100 - feePct)) / 100);

    const batch = writeBatch(db);
    const tableRef = docRef("tables", table.id);
    batch.update(tableRef, {
      status: "ACTIVE",
      cardCounts: counts,
      currentTurnUid: table.playerIds[0],
      cutSuit: deal.cutSuit.name,
      cutCard: deal.cutCard,
      topCard: deal.topCard,
      demandedSuit: "",
      pendingDrawPenalty: 0,
      drawPile: deal.drawPile,
      discardPile: deal.discardPile,
      moveSequence: 0,
      lastActionSummary: `Cards dealt! Cut Suit: ${deal.cutSuit.label}`,
      deckCommitHash: deal.deckCommitHash,
      potUgx: pot,
      payoutUgx: payout,
      updatedAt: serverTimestamp(),
    });
    for (const [pUid, cards] of Object.entries(deal.handsByPlayer)) {
      batch.set(docRef("tables", table.id, "hands", pUid), {
        playerId: pUid,
        tableId: table.id,
        cards,
        handPoints: calculatePoints(cards),
        updatedAt: serverTimestamp(),
      });
    }
    if (table.inviteCode) {
      batch.update(docRef("invite_codes", table.inviteCode), {
        status: "ACTIVE",
        updatedAt: serverTimestamp(),
      });
    }
    await batch.commit();
  }

  async function cancelWaitingTable(table) {
    const uid = requireUserId();
    if (table.status !== "WAITING" || table.hostId !== uid || (table.playerIds || []).length !== 1) {
      throw new Error("Only sole host in waiting lobby can cancel.");
    }
    const userRef = docRef("users", uid);
    const tableRef = docRef("tables", table.id);
    const stake = Number(table.stakeUgx || 0);

    await runTransaction(db, async (txn) => {
      const uSnap = await txn.get(userRef);
      const uData = uSnap.data() || {};
      const balance = Number(uData.walletBalanceUgx || 0);
      const escrow = Number(uData.escrowUgx || 0);

      if (stake > 0) {
        txn.update(userRef, {
          walletBalanceUgx: balance + stake,
          escrowUgx: Math.max(0, escrow - stake),
          updatedAt: serverTimestamp(),
        });
        txn.set(docRef("transactions", newDocId("ref")), {
          userId: uid,
          userName: (table.playerNames && table.playerNames[uid]) || "Player",
          type: "REFUND",
          amountUgx: stake,
          feeUgx: 0,
          netAmountUgx: stake,
          provider: "WALLET_ESCROW",
          phoneNumber: "",
          referenceCode: generateReference("REF"),
          tableId: table.id,
          status: "COMPLETED",
          note: "Table cancelled: stake refunded",
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }

      txn.update(tableRef, {
        status: "CANCELLED",
        lastActionSummary: "Table closed by host",
        updatedAt: serverTimestamp(),
      });
      if (table.inviteCode) {
        txn.update(docRef("invite_codes", table.inviteCode), {
          status: "CANCELLED",
          updatedAt: serverTimestamp(),
        });
      }
    });
  }

  async function reportAntiCheatAnomaly({ tableId, reportedUid, eventType, details, severity = "MEDIUM" }) {
    const reporterUid = requireUserId();
    const batch = writeBatch(db);
    batch.set(docRef("anti_cheat_logs", newDocId("ac")), {
      tableId,
      reportedUid,
      reporterUid,
      eventType: String(eventType || "PLAYER_REPORT").slice(0, 40),
      details: String(details || "Suspicious play reported").slice(0, 240),
      severity,
      resolved: false,
      createdAt: serverTimestamp(),
    });
    await batch.commit();
  }

  async function playCard({ table, hand, cardCode, chosenSuitForAce = null, triggerCut = false, botHandForInstantCut = null }) {
    const uid = requireUserId();
    if (table.currentTurnUid !== uid) {
      await reportAntiCheatAnomaly({
        tableId: table.id,
        reportedUid: uid,
        eventType: "OUT_OF_TURN_ATTEMPT",
        details: `Tried playing ${cardCode} when turn belongs to ${table.currentTurnUid}`,
        severity: "LOW",
      }).catch(() => {});
      throw new Error("Wait for your turn!");
    }

    const playerName = (table.playerNames && table.playerNames[uid]) || "Player";
    const moveRes = applyPlayCard({
      playerId: uid,
      playerName,
      cardCode,
      chosenSuitForAce,
      triggerCut,
      hand: hand.cards || [],
      playerIds: table.playerIds || [],
      topCardCode: table.topCard,
      demandedSuitName: table.demandedSuit,
      pendingDrawPenalty: table.pendingDrawPenalty,
      turnDirection: table.turnDirection,
      cutSuitName: table.cutSuit,
      drawPile: table.drawPile || [],
      discardPile: table.discardPile || [],
    });

    if (!moveRes.valid) throw new Error(moveRes.reason);

    const nextSeq = Number(table.moveSequence || 0) + 1;
    const tableRef = docRef("tables", table.id);
    const handRef = docRef("tables", table.id, "hands", uid);
    const moveRef = docRef("tables", table.id, "moves", `seq_${nextSeq}`);
    const updatedCounts = { ...table.cardCounts, [uid]: moveRes.newHand.length };
    const actionType = moveRes.triggersSevenCutShowdown ? "CUT_SEVEN" : "PLAY";
    const digest = await moveDigest(table.id, nextSeq, uid, actionType, cardCode, moveRes.newTopCard);

    const batch = writeBatch(db);
    batch.update(handRef, {
      cards: moveRes.newHand,
      handPoints: calculatePoints(moveRes.newHand),
      updatedAt: serverTimestamp(),
    });
    batch.set(moveRef, {
      tableId: table.id,
      playerIds: table.playerIds,
      moveSequence: nextSeq,
      playerId: uid,
      playerName: String(playerName).slice(0, 40),
      actionType,
      cardPlayed: cardCode,
      demandedSuit: moveRes.newDemandedSuit,
      drawCount: 0,
      handSizeAfter: moveRes.newHand.length,
      antiCheatDigest: digest,
      createdAt: serverTimestamp(),
    });

    if (moveRes.triggersWinEmptyHand) {
      batch.update(tableRef, {
        status: "FINISHED",
        cardCounts: updatedCounts,
        turnDirection: moveRes.newTurnDirection,
        currentTurnUid: uid,
        topCard: moveRes.newTopCard,
        demandedSuit: "",
        pendingDrawPenalty: 0,
        drawPile: moveRes.newDrawPile,
        discardPile: moveRes.newDiscardPile,
        moveSequence: nextSeq,
        lastActionSummary: moveRes.summary,
        winnerId: uid,
        winnerName: playerName,
        winReason: "EMPTY_HAND",
        payoutSettled: false,
        revealedHands: { [uid]: moveRes.newHand },
        revealedPoints: { [uid]: 0 },
        updatedAt: serverTimestamp(),
      });
    } else if (moveRes.triggersSevenCutShowdown) {
      const myPts = calculatePoints(moveRes.newHand);
      let resolvedBotHand = Array.isArray(botHandForInstantCut) ? botHandForInstantCut : null;
      if (table.isBotMatch && !resolvedBotHand) {
        const botHandSnap = await getDoc(docRef("tables", table.id, "hands", BOT_UID));
        if (botHandSnap.exists()) {
          resolvedBotHand = botHandSnap.data()?.cards || [];
        }
      }
      if (table.isBotMatch && Array.isArray(resolvedBotHand)) {
        const botPts = calculatePoints(resolvedBotHand);
        const playerWon = myPts < botPts;
        const winUid = playerWon ? uid : BOT_UID;
        const winName = playerWon ? playerName : BOT_NAME;
        batch.update(tableRef, {
          status: "FINISHED",
          cardCounts: updatedCounts,
          turnDirection: moveRes.newTurnDirection,
          currentTurnUid: winUid,
          topCard: moveRes.newTopCard,
          demandedSuit: "",
          pendingDrawPenalty: 0,
          drawPile: moveRes.newDrawPile,
          discardPile: moveRes.newDiscardPile,
          moveSequence: nextSeq,
          lastActionSummary: `7-Cut Showdown! ${playerName} (${myPts} pts) vs Bot (${botPts} pts) → ${winName} wins!`,
          winnerId: winUid,
          winnerName: winName,
          winReason: "SEVEN_CUT",
          payoutSettled: false,
          revealedHands: { [uid]: moveRes.newHand, [BOT_UID]: resolvedBotHand },
          revealedPoints: { [uid]: myPts, [BOT_UID]: botPts },
          updatedAt: serverTimestamp(),
        });
      } else {
        batch.update(tableRef, {
          status: "SHOWDOWN",
          cardCounts: updatedCounts,
          topCard: moveRes.newTopCard,
          demandedSuit: "",
          pendingDrawPenalty: 0,
          drawPile: moveRes.newDrawPile,
          discardPile: moveRes.newDiscardPile,
          moveSequence: nextSeq,
          lastActionSummary: moveRes.summary,
          cutCallerId: uid,
          revealedHands: { [uid]: moveRes.newHand },
          revealedPoints: { [uid]: myPts },
          updatedAt: serverTimestamp(),
        });
      }
    } else {
      batch.update(tableRef, {
        cardCounts: updatedCounts,
        turnDirection: moveRes.newTurnDirection,
        currentTurnUid: moveRes.nextTurnUid,
        topCard: moveRes.newTopCard,
        demandedSuit: moveRes.newDemandedSuit,
        pendingDrawPenalty: moveRes.newPendingPenalty,
        drawPile: moveRes.newDrawPile,
        discardPile: moveRes.newDiscardPile,
        moveSequence: nextSeq,
        lastActionSummary: moveRes.summary,
        updatedAt: serverTimestamp(),
      });
    }

    await batch.commit();
    return moveRes.summary;
  }

  async function drawCard(table, hand) {
    const uid = requireUserId();
    if (table.currentTurnUid !== uid) throw new Error("Wait for your turn!");
    const playerName = (table.playerNames && table.playerNames[uid]) || "Player";
    const drawRes = applyDraw({
      playerId: uid,
      playerName,
      hand: hand.cards || [],
      playerIds: table.playerIds || [],
      turnDirection: table.turnDirection,
      pendingDrawPenalty: table.pendingDrawPenalty,
      topCardCode: table.topCard,
      drawPile: table.drawPile || [],
      discardPile: table.discardPile || [],
    });

    const nextSeq = Number(table.moveSequence || 0) + 1;
    const tableRef = docRef("tables", table.id);
    const handRef = docRef("tables", table.id, "hands", uid);
    const moveRef = docRef("tables", table.id, "moves", `seq_${nextSeq}`);
    const updatedCounts = { ...table.cardCounts, [uid]: drawRes.newHand.length };
    const digest = await moveDigest(table.id, nextSeq, uid, "DRAW", "", table.topCard);

    const batch = writeBatch(db);
    batch.update(handRef, {
      cards: drawRes.newHand,
      handPoints: calculatePoints(drawRes.newHand),
      updatedAt: serverTimestamp(),
    });
    batch.set(moveRef, {
      tableId: table.id,
      playerIds: table.playerIds,
      moveSequence: nextSeq,
      playerId: uid,
      playerName: String(playerName).slice(0, 40),
      actionType: "DRAW",
      cardPlayed: "",
      demandedSuit: "",
      drawCount: drawRes.drawnCards.length,
      handSizeAfter: drawRes.newHand.length,
      antiCheatDigest: digest,
      createdAt: serverTimestamp(),
    });
    batch.update(tableRef, {
      cardCounts: updatedCounts,
      currentTurnUid: drawRes.nextTurnUid,
      pendingDrawPenalty: 0,
      drawPile: drawRes.newDrawPile,
      discardPile: drawRes.newDiscardPile,
      moveSequence: nextSeq,
      lastActionSummary: drawRes.summary,
      updatedAt: serverTimestamp(),
    });
    await batch.commit();
    return drawRes.summary;
  }

  async function submitShowdownReveal(table, hand) {
    const uid = requireUserId();
    if (table.status !== "SHOWDOWN") return;
    if (table.revealedPoints && table.revealedPoints[uid] !== undefined) return;
    const tableRef = docRef("tables", table.id);

    await runTransaction(db, async (txn) => {
      const snap = await txn.get(tableRef);
      if (!snap.exists()) return;
      const fresh = snap.data();
      if (fresh.status !== "SHOWDOWN" || (fresh.revealedPoints && fresh.revealedPoints[uid] !== undefined)) return;

      const myPts = calculatePoints(hand.cards || []);
      const newRevealedHands = { ...(fresh.revealedHands || {}), [uid]: hand.cards || [] };
      const newRevealedPts = { ...(fresh.revealedPoints || {}), [uid]: myPts };
      const allRevealed = (fresh.playerIds || []).every((pid) => newRevealedPts[pid] !== undefined);

      if (allRevealed) {
        const cutterId = fresh.cutCallerId;
        const cutterPts = Number(newRevealedPts[cutterId] ?? 9999);
        let bestOther = null;
        for (const [pid, pts] of Object.entries(newRevealedPts)) {
          if (pid !== cutterId && (!bestOther || Number(pts) < bestOther.pts)) {
            bestOther = { pid, pts: Number(pts) };
          }
        }
        const winnerUid = bestOther && cutterPts >= bestOther.pts ? bestOther.pid : cutterId;
        const winnerName = (fresh.playerNames && fresh.playerNames[winnerUid]) || "Player";
        txn.update(tableRef, {
          status: "FINISHED",
          revealedHands: newRevealedHands,
          revealedPoints: newRevealedPts,
          winnerId: winnerUid,
          winnerName,
          winReason: "SEVEN_CUT",
          payoutSettled: false,
          lastActionSummary: `7-Cut verified! ${winnerName} wins with ${newRevealedPts[winnerUid]} pts.`,
          updatedAt: serverTimestamp(),
        });
      } else {
        txn.update(tableRef, {
          revealedHands: newRevealedHands,
          revealedPoints: newRevealedPts,
          updatedAt: serverTimestamp(),
        });
      }
    });
  }

  async function executeBotTurn(table, botHand) {
    const uid = requireUserId();
    if (!table.isBotMatch || table.status !== "ACTIVE" || table.currentTurnUid !== BOT_UID) {
      return "";
    }

    const botCards = botHand.cards || [];
    const { card: chosenCard, suitChoice } = chooseBotCard({
      botHand: botCards,
      topCardCode: table.topCard,
      demandedSuitName: table.demandedSuit,
      pendingDrawPenalty: table.pendingDrawPenalty,
      cutSuitName: table.cutSuit,
    });

    const tableRef = docRef("tables", table.id);
    const botHandRef = docRef("tables", table.id, "hands", BOT_UID);
    const nextSeq = Number(table.moveSequence || 0) + 1;
    const moveRef = docRef("tables", table.id, "moves", `seq_${nextSeq}`);
    const batch = writeBatch(db);

    if (chosenCard) {
      const idx = botCards.indexOf(chosenCard.code);
      const remaining = botCards.filter((_, i) => i !== idx);
      const triggerCut =
        canTriggerSevenCut(chosenCard, botCards, table.cutSuit, table.pendingDrawPenalty) &&
        calculatePoints(remaining) <= 15;

      const moveRes = applyPlayCard({
        playerId: BOT_UID,
        playerName: BOT_NAME,
        cardCode: chosenCard.code,
        chosenSuitForAce: suitChoice,
        triggerCut,
        hand: botCards,
        playerIds: table.playerIds,
        topCardCode: table.topCard,
        demandedSuitName: table.demandedSuit,
        pendingDrawPenalty: table.pendingDrawPenalty,
        turnDirection: table.turnDirection,
        cutSuitName: table.cutSuit,
        drawPile: table.drawPile || [],
        discardPile: table.discardPile || [],
      });

      const counts = { ...table.cardCounts, [BOT_UID]: moveRes.newHand.length };
      const actionType = moveRes.triggersSevenCutShowdown ? "CUT_SEVEN" : "PLAY";
      const digest = await moveDigest(table.id, nextSeq, BOT_UID, actionType, chosenCard.code, moveRes.newTopCard);

      batch.update(botHandRef, {
        cards: moveRes.newHand,
        handPoints: calculatePoints(moveRes.newHand),
        updatedAt: serverTimestamp(),
      });
      batch.set(moveRef, {
        tableId: table.id,
        playerIds: table.playerIds,
        moveSequence: nextSeq,
        playerId: BOT_UID,
        playerName: BOT_NAME,
        actionType,
        cardPlayed: chosenCard.code,
        demandedSuit: moveRes.newDemandedSuit,
        drawCount: 0,
        handSizeAfter: moveRes.newHand.length,
        antiCheatDigest: digest,
        createdAt: serverTimestamp(),
      });

      if (moveRes.triggersWinEmptyHand) {
        batch.update(tableRef, {
          status: "FINISHED",
          cardCounts: counts,
          currentTurnUid: BOT_UID,
          topCard: moveRes.newTopCard,
          demandedSuit: "",
          pendingDrawPenalty: 0,
          drawPile: moveRes.newDrawPile,
          discardPile: moveRes.newDiscardPile,
          moveSequence: nextSeq,
          lastActionSummary: moveRes.summary,
          winnerId: BOT_UID,
          winnerName: BOT_NAME,
          winReason: "EMPTY_HAND",
          payoutSettled: true,
          revealedHands: { [BOT_UID]: moveRes.newHand },
          revealedPoints: { [BOT_UID]: 0 },
          updatedAt: serverTimestamp(),
        });
      } else if (moveRes.triggersSevenCutShowdown) {
        const playerHandSnap = await getDoc(docRef("tables", table.id, "hands", uid));
        const playerCards = (playerHandSnap.exists() && playerHandSnap.data().cards) || [];
        const playerPts = calculatePoints(playerCards);
        const botPts = calculatePoints(moveRes.newHand);
        const botWins = botPts < playerPts;
        const winUid = botWins ? BOT_UID : uid;
        const winName = botWins ? BOT_NAME : (table.playerNames[uid] || "Player");
        batch.update(tableRef, {
          status: "FINISHED",
          cardCounts: counts,
          currentTurnUid: winUid,
          topCard: moveRes.newTopCard,
          demandedSuit: "",
          pendingDrawPenalty: 0,
          drawPile: moveRes.newDrawPile,
          discardPile: moveRes.newDiscardPile,
          moveSequence: nextSeq,
          lastActionSummary: `Bot dropped Cut 7! Bot (${botPts} pts) vs You (${playerPts} pts)`,
          winnerId: winUid,
          winnerName: winName,
          winReason: "SEVEN_CUT",
          payoutSettled: winUid === BOT_UID,
          revealedHands: { [BOT_UID]: moveRes.newHand, [uid]: playerCards },
          revealedPoints: { [BOT_UID]: botPts, [uid]: playerPts },
          updatedAt: serverTimestamp(),
        });
      } else {
        batch.update(tableRef, {
          cardCounts: counts,
          turnDirection: moveRes.newTurnDirection,
          currentTurnUid: moveRes.nextTurnUid,
          topCard: moveRes.newTopCard,
          demandedSuit: moveRes.newDemandedSuit,
          pendingDrawPenalty: moveRes.newPendingPenalty,
          drawPile: moveRes.newDrawPile,
          discardPile: moveRes.newDiscardPile,
          moveSequence: nextSeq,
          lastActionSummary: moveRes.summary,
          updatedAt: serverTimestamp(),
        });
      }

      await batch.commit();
      return moveRes.summary;
    }

    const drawRes = applyDraw({
      playerId: BOT_UID,
      playerName: BOT_NAME,
      hand: botCards,
      playerIds: table.playerIds,
      turnDirection: table.turnDirection,
      pendingDrawPenalty: table.pendingDrawPenalty,
      topCardCode: table.topCard,
      drawPile: table.drawPile || [],
      discardPile: table.discardPile || [],
    });
    const counts = { ...table.cardCounts, [BOT_UID]: drawRes.newHand.length };
    const digest = await moveDigest(table.id, nextSeq, BOT_UID, "DRAW", "", table.topCard);

    batch.update(botHandRef, {
      cards: drawRes.newHand,
      handPoints: calculatePoints(drawRes.newHand),
      updatedAt: serverTimestamp(),
    });
    batch.set(moveRef, {
      tableId: table.id,
      playerIds: table.playerIds,
      moveSequence: nextSeq,
      playerId: BOT_UID,
      playerName: BOT_NAME,
      actionType: "DRAW",
      cardPlayed: "",
      demandedSuit: "",
      drawCount: drawRes.drawnCards.length,
      handSizeAfter: drawRes.newHand.length,
      antiCheatDigest: digest,
      createdAt: serverTimestamp(),
    });
    batch.update(tableRef, {
      cardCounts: counts,
      currentTurnUid: drawRes.nextTurnUid,
      pendingDrawPenalty: 0,
      drawPile: drawRes.newDrawPile,
      discardPile: drawRes.newDiscardPile,
      moveSequence: nextSeq,
      lastActionSummary: drawRes.summary,
      updatedAt: serverTimestamp(),
    });
    await batch.commit();
    return drawRes.summary;
  }

  async function forfeitMatch(table) {
    const uid = requireUserId();
    if (table.status !== "ACTIVE" || !(table.playerIds || []).includes(uid)) {
      throw new Error("Not in active match.");
    }
    const otherUid = (table.playerIds || []).find((p) => p !== uid) || uid;
    const otherName = (table.playerNames && table.playerNames[otherUid]) || "Opponent";
    const myName = (table.playerNames && table.playerNames[uid]) || "Player";

    const batch = writeBatch(db);
    batch.update(docRef("tables", table.id), {
      status: "FINISHED",
      winnerId: otherUid,
      winnerName: otherName,
      winReason: "OPPONENT_FORFEIT",
      payoutSettled: false,
      lastActionSummary: `${myName} forfeited. ${otherName} wins the pot!`,
      updatedAt: serverTimestamp(),
    });
    await batch.commit();
  }

  async function settleFinishedMatchForCurrentUser(table) {
    const uid = requireUserId();
    if (table.status !== "FINISHED" || !(table.playerIds || []).includes(uid)) return false;

    const historyRef = docRef("game_history", `hist_${table.id}`);
    const tableRef = docRef("tables", table.id);
    const userRef = docRef("users", uid);
    const pubRef = docRef("public_profiles", uid);
    const isWinner = table.winnerId === uid;
    let didCredit = false;

    await runTransaction(db, async (txn) => {
      const tSnap = await txn.get(tableRef);
      if (!tSnap.exists()) return;
      const freshTable = { id: tSnap.id, ...tSnap.data() };
      const uSnap = await txn.get(userRef);
      const hSnap = await txn.get(historyRef);
      const uData = uSnap.data() || {};

      const balance = Number(uData.walletBalanceUgx || 0);
      const escrow = Number(uData.escrowUgx || 0);
      const played = Number(uData.gamesPlayed || 0);
      const won = Number(uData.gamesWon || 0);
      const earnings = Number(uData.totalEarningsUgx || 0);
      const ptsWon = Number(uData.totalPointsWon || 0);
      const name = String(uData.displayName || "Player");
      const avatar = String(uData.avatarKey || "crown_gold");

      const currentSettled = freshTable.settledUids || [];
      const alreadySettledUser = currentSettled.includes(uid);
      const updatedSettled = alreadySettledUser ? currentSettled : [...currentSettled, uid];
      const stake = Number(freshTable.stakeUgx || 0);
      const payout = Number(freshTable.payoutUgx || 0);
      const pot = Number(freshTable.potUgx || 0);

      if (isWinner && !freshTable.payoutSettled && !alreadySettledUser) {
        didCredit = true;
        const newEscrow = Math.max(0, escrow - stake);
        txn.update(tableRef, {
          payoutSettled: true,
          settledUids: updatedSettled,
          updatedAt: serverTimestamp(),
        });
        txn.update(userRef, {
          walletBalanceUgx: balance + payout,
          escrowUgx: newEscrow,
          gamesPlayed: played + 1,
          gamesWon: won + 1,
          totalPointsWon: ptsWon + 25,
          totalEarningsUgx: earnings + payout,
          updatedAt: serverTimestamp(),
        });
        txn.update(pubRef, {
          gamesPlayed: played + 1,
          gamesWon: won + 1,
          totalEarningsUgx: earnings + payout,
          displayName: name,
          avatarKey: avatar,
          updatedAt: serverTimestamp(),
        });
        if (payout > 0) {
          txn.set(docRef("transactions", newDocId("win")), {
            userId: uid,
            userName: name.slice(0, 40),
            type: "WIN_PAYOUT",
            amountUgx: payout,
            feeUgx: Math.max(0, pot - payout),
            netAmountUgx: payout,
            provider: "SYSTEM",
            phoneNumber: "",
            referenceCode: generateReference("WIN"),
            tableId: freshTable.id,
            status: "COMPLETED",
            note: `Won pot on ${freshTable.title} (${freshTable.winReason})`,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
          });
        }
      } else if (!isWinner && !alreadySettledUser) {
        const newEscrow = Math.max(0, escrow - stake);
        txn.update(tableRef, {
          settledUids: updatedSettled,
          updatedAt: serverTimestamp(),
        });
        txn.update(userRef, {
          escrowUgx: newEscrow,
          gamesPlayed: played + 1,
          updatedAt: serverTimestamp(),
        });
        txn.update(pubRef, {
          gamesPlayed: played + 1,
          updatedAt: serverTimestamp(),
        });
      }

      if (!hSnap.exists()) {
        const feeUgx = Math.max(0, pot - payout);
        txn.set(historyRef, {
          tableId: freshTable.id,
          tableTitle: freshTable.title,
          stakeUgx: stake,
          potUgx: pot,
          payoutUgx: payout,
          platformFeeUgx: feeUgx,
          playerIds: freshTable.playerIds,
          playerNames: freshTable.playerNames,
          winnerId: freshTable.winnerId,
          winnerName: freshTable.winnerName,
          winReason: freshTable.winReason || "EMPTY_HAND",
          finalScores: freshTable.revealedPoints || {},
          deckCommitHash: freshTable.deckCommitHash || "",
          moveCount: Number(freshTable.moveSequence || 0),
          antiCheatVerified: !freshTable.flaggedCheat,
          flaggedReason: freshTable.cheatReason || "",
          createdAt: serverTimestamp(),
        });
      }
    });

    return didCredit;
  }

  // --- Admin Dashboard ---
  function observeAllUsersForAdmin(onData, onError) {
    return onSnapshot(
      colRef("users"),
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => Number(b.walletBalanceUgx || 0) - Number(a.walletBalanceUgx || 0)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  function observeAllTransactionsForAdmin(onData, onError) {
    return onSnapshot(
      colRef("transactions"),
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => timestampToSeconds(b.createdAt) - timestampToSeconds(a.createdAt)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  function observeAllTablesForAdmin(onData, onError) {
    return onSnapshot(
      colRef("tables"),
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => timestampToSeconds(b.updatedAt) - timestampToSeconds(a.updatedAt)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  function observeAntiCheatLogsForAdmin(onData, onError) {
    return onSnapshot(
      colRef("anti_cheat_logs"),
      (snap) => {
        const list = docsWithId(snap).sort(
          (a, b) => timestampToSeconds(b.createdAt) - timestampToSeconds(a.createdAt)
        );
        onData(list);
      },
      (err) => onError?.(err)
    );
  }

  async function adminApproveTransaction(tx) {
    requireUserId();
    if (tx.status !== "PENDING") throw new Error("Transaction already processed.");
    const txRef = docRef("transactions", tx.id);
    const userRef = docRef("users", tx.userId);

    await runTransaction(db, async (txn) => {
      const tSnap = await txn.get(txRef);
      const tData = tSnap.data() || {};
      if (tData.status !== "PENDING") throw new Error("Already processed.");
      if (tx.type === "DEPOSIT") {
        const uSnap = await txn.get(userRef);
        const bal = Number((uSnap.data() || {}).walletBalanceUgx || 0);
        txn.update(userRef, {
          walletBalanceUgx: bal + Number(tx.netAmountUgx || 0),
          updatedAt: serverTimestamp(),
        });
      }
      txn.update(txRef, {
        status: "COMPLETED",
        note: "Approved & settled by Admin",
        updatedAt: serverTimestamp(),
      });
    });
  }

  async function adminRejectTransaction(tx, reason) {
    requireUserId();
    if (tx.status !== "PENDING") throw new Error("Transaction already processed.");
    const txRef = docRef("transactions", tx.id);
    const userRef = docRef("users", tx.userId);

    await runTransaction(db, async (txn) => {
      const tSnap = await txn.get(txRef);
      const tData = tSnap.data() || {};
      if (tData.status !== "PENDING") throw new Error("Already processed.");
      if (tx.type === "WITHDRAWAL") {
        const uSnap = await txn.get(userRef);
        const bal = Number((uSnap.data() || {}).walletBalanceUgx || 0);
        txn.update(userRef, {
          walletBalanceUgx: bal + Number(tx.amountUgx || 0),
          updatedAt: serverTimestamp(),
        });
      }
      txn.update(txRef, {
        status: "REJECTED",
        note: (String(reason || "").trim() || "Rejected by Admin").slice(0, 180),
        updatedAt: serverTimestamp(),
      });
    });
  }

  async function adminTogglePlayerBan(targetUid, ban) {
    requireUserId();
    const batch = writeBatch(db);
    batch.update(docRef("users", targetUid), {
      isBanned: Boolean(ban),
      updatedAt: serverTimestamp(),
    });
    await batch.commit();
  }

  async function adminCancelTable(tableId) {
    requireUserId();
    const batch = writeBatch(db);
    batch.update(docRef("tables", tableId), {
      status: "CANCELLED",
      flaggedCheat: true,
      cheatReason: "Closed by Admin",
      lastActionSummary: "Table closed by Admin",
      updatedAt: serverTimestamp(),
    });
    await batch.commit();
  }

  async function adminResolveCheatLog(logId) {
    requireUserId();
    const batch = writeBatch(db);
    batch.update(docRef("anti_cheat_logs", logId), {
      resolved: true,
    });
    await batch.commit();
  }

  return {
    ensureUserAccountInitialized,
    checkIsAdmin,
    observeCurrentUser,
    observeLeaderboard,
    observePublicWaitingTables,
    observeUserTables,
    observeTable,
    observePlayerHand,
    observeTableMoves,
    observeUserTransactions,
    observeUserGameHistory,
    updateProfile,
    submitMobileMoneyDeposit,
    submitMobileMoneyWithdrawal,
    createTable,
    joinTableById,
    quickMatchOnline,
    joinPrivateTableByCode,
    startWaitingTable,
    cancelWaitingTable,
    playCard,
    drawCard,
    submitShowdownReveal,
    executeBotTurn,
    forfeitMatch,
    settleFinishedMatchForCurrentUser,
    reportAntiCheatAnomaly,
    observeAllUsersForAdmin,
    observeAllTransactionsForAdmin,
    observeAllTablesForAdmin,
    observeAntiCheatLogsForAdmin,
    adminApproveTransaction,
    adminRejectTransaction,
    adminTogglePlayerBan,
    adminCancelTable,
    adminResolveCheatLog,
  };
}
