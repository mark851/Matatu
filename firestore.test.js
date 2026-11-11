const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const ADMIN_UID = "admin_uid_789";
const ADMIN_EMAIL = "markniaras@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, overrides = {}) {
  return {
    userId: uid,
    displayName: "KampalaAce",
    phoneNumber: "+256772111222",
    preferredProvider: "MTN_MOMO",
    avatarKey: "crown_gold",
    photoUrl: "",
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
    ...overrides,
  };
}

function validTablePayload(hostUid, overrides = {}) {
  return {
    title: "Kampala High Stakes",
    hostId: hostUid,
    stakeUgx: 5000,
    platformFeePct: 5,
    potUgx: 5000,
    payoutUgx: 9500,
    maxPlayers: 2,
    isPrivate: false,
    inviteCode: "",
    isBotMatch: false,
    status: "WAITING",
    playerIds: [hostUid],
    playerNames: { [hostUid]: "Alice" },
    playerAvatars: { [hostUid]: "crown_gold" },
    cardCounts: { [hostUid]: 0 },
    turnDirection: 1,
    currentTurnUid: hostUid,
    cutSuit: "HEARTS",
    cutCard: "9H",
    topCard: "5S",
    demandedSuit: "",
    pendingDrawPenalty: 0,
    drawPile: ["3C", "4D", "6H"],
    discardPile: ["5S"],
    moveSequence: 0,
    lastActionSummary: "Table created",
    deckCommitHash: "abc123456789",
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
    ...overrides,
  };
}

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller cannot read users, tables, or transactions", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("tables").where("isPrivate", "==", false).where("status", "==", "WAITING").get());
  await assertFails(unauth.collection("transactions").where("userId", "==", ALICE_UID).get());
});

test("User registration succeeds with 25,000 UGX starter balance and blocks inflated balance or ghost field", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );

  // Inflated starter balance fails
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, { walletBalanceUgx: 999999 }))
  );

  // Shadow update with ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({ isVerified: true, updatedAt: serverTimestamp() })
  );

  // Self-unban / role escalation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({ isBanned: true, updatedAt: serverTimestamp() })
  );
});

test("PII Blanket Test: Bob cannot read Alice private user profile or transactions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID));
    await adminDb.collection("transactions").doc("tx_alice_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      type: "DEPOSIT",
      amountUgx: 10000,
      feeUgx: 0,
      netAmountUgx: 10000,
      provider: "MTN_MOMO",
      phoneNumber: "+256772111222",
      referenceCode: "MOMO-123456",
      tableId: "",
      status: "PENDING",
      note: "Deposit request",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("transactions").doc("tx_alice_1").get());
  await assertFails(bobDb.collection("transactions").where("userId", "==", ALICE_UID).get());
});

test("Query Trust Test: unfiltered list queries without required constraints fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("transactions").get());
  await assertFails(aliceDb.collection("public_profiles").get());
  await assertSucceeds(
    aliceDb.collection("public_profiles").where("gamesWon", ">=", 0).limit(25).get()
  );
});

test("Email Spoofing Test: unverified admin email is rejected; verified admin is allowed", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("transactions").doc("tx_pending_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      type: "DEPOSIT",
      amountUgx: 20000,
      feeUgx: 0,
      netAmountUgx: 20000,
      provider: "MTN_MOMO",
      phoneNumber: "+256772111222",
      referenceCode: "MOMO-888888",
      tableId: "",
      status: "PENDING",
      note: "MoMo deposit",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const spoofedAdmin = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofedAdmin.collection("transactions").get());
  await assertFails(
    spoofedAdmin.collection("transactions").doc("tx_pending_1").update({
      status: "COMPLETED",
      note: "Approved",
      updatedAt: serverTimestamp(),
    })
  );

  const realAdmin = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(realAdmin.collection("transactions").get());
  await assertSucceeds(
    realAdmin.collection("transactions").doc("tx_pending_1").update({
      status: "COMPLETED",
      note: "Approved by Admin",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Multiplayer table join, private hand isolation, move sequence, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("tables").doc("table_1").set(validTablePayload(ALICE_UID))
  );

  // Bob queries waiting public tables and joins
  await assertSucceeds(
    bobDb.collection("tables").where("isPrivate", "==", false).where("status", "==", "WAITING").get()
  );
  await assertSucceeds(
    bobDb.collection("tables").doc("table_1").update({
      playerIds: [ALICE_UID, BOB_UID],
      playerNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      playerAvatars: { [ALICE_UID]: "crown_gold", [BOB_UID]: "ace_jade" },
      cardCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      potUgx: 10000,
      payoutUgx: 9500,
      updatedAt: serverTimestamp(),
    })
  );

  // Host starts game and deals private hands
  await assertSucceeds(
    aliceDb.collection("tables").doc("table_1").update({
      status: "ACTIVE",
      cardCounts: { [ALICE_UID]: 7, [BOB_UID]: 7 },
      currentTurnUid: ALICE_UID,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("tables").doc("table_1").collection("hands").doc(ALICE_UID).set({
      playerId: ALICE_UID,
      tableId: "table_1",
      cards: ["7H", "2S", "AD", "KC", "9S", "4H", "5D"],
      handPoints: 73,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("tables").doc("table_1").collection("hands").doc(BOB_UID).set({
      playerId: BOB_UID,
      tableId: "table_1",
      cards: ["8C", "JH", "3D", "6S", "10C", "QD", "4C"],
      handPoints: 52,
      updatedAt: serverTimestamp(),
    })
  );

  // Anti-Cheat Hand Isolation: Bob cannot read Alice's hand and Alice cannot read Bob's hand!
  await assertFails(
    bobDb.collection("tables").doc("table_1").collection("hands").doc(ALICE_UID).get()
  );
  await assertFails(
    aliceDb.collection("tables").doc("table_1").collection("hands").doc(BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("tables").doc("table_1").collection("hands").doc(BOB_UID).get()
  );

  // Move increments sequence & Finish locks table permanently
  await assertSucceeds(
    aliceDb.collection("tables").doc("table_1").update({
      status: "FINISHED",
      moveSequence: 1,
      winnerId: ALICE_UID,
      winnerName: "Alice",
      winReason: "EMPTY_HAND",
      payoutSettled: true,
      lastActionSummary: "Alice won the pot!",
      updatedAt: serverTimestamp(),
    })
  );

  // Terminal state lock prevents Bob from changing winner after FINISHED
  await assertFails(
    bobDb.collection("tables").doc("table_1").update({
      winnerId: BOB_UID,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Private room invite code lookup prevents listing while allowing exact code get", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("tables").doc("priv_1").set(
      validTablePayload(ALICE_UID, { isPrivate: true, inviteCode: "AMT742" })
    )
  );
  await assertSucceeds(
    aliceDb.collection("invite_codes").doc("AMT742").set({
      code: "AMT742",
      tableId: "priv_1",
      hostId: ALICE_UID,
      stakeUgx: 5000,
      maxPlayers: 2,
      status: "WAITING",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Bob cannot list invite codes or list private tables
  await assertFails(bobDb.collection("invite_codes").get());
  await assertFails(bobDb.collection("tables").where("isPrivate", "==", true).where("status", "==", "WAITING").get());

  // Bob can get exact invite code AMT742 and read WAITING table doc to join
  await assertSucceeds(bobDb.collection("invite_codes").doc("AMT742").get());
  await assertSucceeds(bobDb.collection("tables").doc("priv_1").get());

  // Once table transitions to ACTIVE without an outsider, outsider Charlie cannot read it
  await assertSucceeds(
    aliceDb.collection("tables").doc("priv_1").update({
      status: "ACTIVE",
      updatedAt: serverTimestamp(),
    })
  );
  const charlieDb = testEnv.authenticatedContext("charlie_999").firestore();
  await assertFails(charlieDb.collection("tables").doc("priv_1").get());
});

test("Admin doc grants admin access and non-existent game_history existence check succeeds for player", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const helperAdminUid = "delegated_admin_1";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(helperAdminUid).set({
      userId: helperAdminUid,
      email: "cashier@kampala.club",
      role: "CASHIER",
      createdAt: serverTimestamp(),
    });
  });

  await assertSucceeds(aliceDb.collection("game_history").doc("hist_new_1").get());
  const delegatedAdminDb = testEnv.authenticatedContext(helperAdminUid).firestore();
  await assertSucceeds(delegatedAdminDb.collection("anti_cheat_logs").get());
});

test("Bot match batch creates table and both hands atomically and enforces per-user settledUids lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const botTableRef = aliceDb.collection("tables").doc("bot_tbl_1");
  const batch = aliceDb.batch();
  batch.set(
    botTableRef,
    validTablePayload(ALICE_UID, {
      isPrivate: true,
      isBotMatch: true,
      status: "ACTIVE",
      playerIds: [ALICE_UID, "kampala_bot"],
      playerNames: { [ALICE_UID]: "Alice", kampala_bot: "Kampala Master Bot" },
      playerAvatars: { [ALICE_UID]: "crown_gold", kampala_bot: "spade_ace" },
      cardCounts: { [ALICE_UID]: 7, kampala_bot: 7 },
    })
  );
  batch.set(botTableRef.collection("hands").doc(ALICE_UID), {
    playerId: ALICE_UID,
    tableId: "bot_tbl_1",
    cards: ["7H", "2S", "AD", "KC", "9S", "4H", "5D"],
    handPoints: 73,
    updatedAt: serverTimestamp(),
  });
  batch.set(botTableRef.collection("hands").doc("kampala_bot"), {
    playerId: "kampala_bot",
    tableId: "bot_tbl_1",
    cards: ["8C", "JH", "3D", "6S", "10C", "QD", "4C"],
    handPoints: 52,
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(batch.commit());

  // Non-participant Bob cannot read bot's hand (host Alice can read bot's hand)
  await assertSucceeds(botTableRef.collection("hands").doc("kampala_bot").get());
  await assertFails(bobDb.collection("tables").doc("bot_tbl_1").collection("hands").doc("kampala_bot").get());

  // Finish match with Alice as winner, then Alice settles payoutSettled & settledUids once
  await assertSucceeds(
    botTableRef.update({
      status: "FINISHED",
      moveSequence: 1,
      winnerId: ALICE_UID,
      winnerName: "Alice",
      winReason: "EMPTY_HAND",
      payoutSettled: false,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    botTableRef.update({
      payoutSettled: true,
      settledUids: [ALICE_UID],
      updatedAt: serverTimestamp(),
    })
  );

  // Alice cannot re-settle a second time
  await assertFails(
    botTableRef.update({
      settledUids: [ALICE_UID, "extra"],
      updatedAt: serverTimestamp(),
    })
  );
});

