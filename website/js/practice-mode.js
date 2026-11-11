import {
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
} from "./engine.js";

export const PRACTICE_PLAYER_UID = "practice_guest";

export async function createPracticeSession(playerName = "Guest Player") {
  const cleanName = String(playerName || "Guest Player").trim().slice(0, 32) || "Guest Player";
  const tableId = `practice_${Date.now()}`;
  const playerIds = [PRACTICE_PLAYER_UID, BOT_UID];
  const deal = await dealMatch(tableId, playerIds);

  const playerCards = deal.handsByPlayer[PRACTICE_PLAYER_UID] || [];
  const botCards = deal.handsByPlayer[BOT_UID] || [];

  const table = {
    id: tableId,
    title: "Kampala Practice Felt (Offline)",
    hostId: PRACTICE_PLAYER_UID,
    stakeUgx: 0,
    platformFeePct: 5,
    potUgx: 0,
    payoutUgx: 0,
    maxPlayers: 2,
    isPrivate: true,
    inviteCode: "",
    isBotMatch: true,
    isOfflinePractice: true,
    status: "ACTIVE",
    playerIds,
    playerNames: {
      [PRACTICE_PLAYER_UID]: cleanName,
      [BOT_UID]: BOT_NAME,
    },
    playerAvatars: {
      [PRACTICE_PLAYER_UID]: "crown_gold",
      [BOT_UID]: BOT_AVATAR,
    },
    cardCounts: {
      [PRACTICE_PLAYER_UID]: playerCards.length,
      [BOT_UID]: botCards.length,
    },
    turnDirection: 1,
    currentTurnUid: PRACTICE_PLAYER_UID,
    cutSuit: deal.cutSuit.name,
    cutCard: deal.cutCard,
    topCard: deal.topCard,
    demandedSuit: "",
    pendingDrawPenalty: 0,
    drawPile: deal.drawPile,
    discardPile: deal.discardPile,
    moveSequence: 0,
    lastActionSummary: `Practice dealt! Cut Suit is ${deal.cutSuit.label}. Your turn.`,
    deckCommitHash: deal.deckCommitHash,
    cutCallerId: "",
    winnerId: "",
    winnerName: "",
    winReason: "",
    payoutSettled: true,
    flaggedCheat: false,
    cheatReason: "",
    revealedHands: {},
    revealedPoints: {},
    settledUids: [PRACTICE_PLAYER_UID],
  };

  return {
    table,
    myHand: {
      playerId: PRACTICE_PLAYER_UID,
      tableId,
      cards: playerCards,
      handPoints: calculatePoints(playerCards),
    },
    botHand: {
      playerId: BOT_UID,
      tableId,
      cards: botCards,
      handPoints: calculatePoints(botCards),
    },
    moves: [],
  };
}

export async function playPracticeCard(session, cardCode, chosenSuitForAce = null, triggerCut = false) {
  const { table, myHand, botHand } = session;
  if (table.status !== "ACTIVE") throw new Error("Practice match has ended.");
  if (table.currentTurnUid !== PRACTICE_PLAYER_UID) throw new Error("Wait for Kampala Bot's turn!");

  const myName = table.playerNames[PRACTICE_PLAYER_UID] || "Guest Player";
  const res = applyPlayCard({
    playerId: PRACTICE_PLAYER_UID,
    playerName: myName,
    cardCode,
    chosenSuitForAce,
    triggerCut,
    hand: myHand.cards,
    playerIds: table.playerIds,
    topCardCode: table.topCard,
    demandedSuitName: table.demandedSuit,
    pendingDrawPenalty: table.pendingDrawPenalty,
    turnDirection: table.turnDirection,
    cutSuitName: table.cutSuit,
    drawPile: table.drawPile,
    discardPile: table.discardPile,
  });

  if (!res.valid) throw new Error(res.reason);

  const nextSeq = table.moveSequence + 1;
  const actionType = res.triggersSevenCutShowdown ? "CUT_SEVEN" : "PLAY";
  const digest = await moveDigest(table.id, nextSeq, PRACTICE_PLAYER_UID, actionType, cardCode, res.newTopCard);

  myHand.cards = res.newHand;
  myHand.handPoints = calculatePoints(res.newHand);
  table.cardCounts = { ...table.cardCounts, [PRACTICE_PLAYER_UID]: res.newHand.length };
  table.turnDirection = res.newTurnDirection;
  table.topCard = res.newTopCard;
  table.drawPile = res.newDrawPile;
  table.discardPile = res.newDiscardPile;
  table.moveSequence = nextSeq;

  session.moves = [
    {
      id: `seq_${nextSeq}`,
      moveSequence: nextSeq,
      playerId: PRACTICE_PLAYER_UID,
      playerName: myName,
      actionType,
      cardPlayed: cardCode,
      demandedSuit: res.newDemandedSuit,
      drawCount: 0,
      handSizeAfter: res.newHand.length,
      antiCheatDigest: digest,
    },
    ...session.moves,
  ];

  if (res.triggersWinEmptyHand) {
    table.status = "FINISHED";
    table.currentTurnUid = PRACTICE_PLAYER_UID;
    table.demandedSuit = "";
    table.pendingDrawPenalty = 0;
    table.winnerId = PRACTICE_PLAYER_UID;
    table.winnerName = myName;
    table.winReason = "EMPTY_HAND";
    table.revealedHands = { [PRACTICE_PLAYER_UID]: res.newHand };
    table.revealedPoints = { [PRACTICE_PLAYER_UID]: 0 };
    table.lastActionSummary = res.summary;
  } else if (res.triggersSevenCutShowdown) {
    const myPts = calculatePoints(res.newHand);
    const botPts = calculatePoints(botHand.cards);
    const playerWon = myPts < botPts;
    table.status = "FINISHED";
    table.currentTurnUid = playerWon ? PRACTICE_PLAYER_UID : BOT_UID;
    table.demandedSuit = "";
    table.pendingDrawPenalty = 0;
    table.winnerId = playerWon ? PRACTICE_PLAYER_UID : BOT_UID;
    table.winnerName = playerWon ? myName : BOT_NAME;
    table.winReason = "SEVEN_CUT";
    table.revealedHands = { [PRACTICE_PLAYER_UID]: res.newHand, [BOT_UID]: botHand.cards };
    table.revealedPoints = { [PRACTICE_PLAYER_UID]: myPts, [BOT_UID]: botPts };
    table.lastActionSummary = `7-Cut Showdown! You (${myPts} pts) vs Bot (${botPts} pts) → ${table.winnerName} wins!`;
  } else {
    table.currentTurnUid = res.nextTurnUid;
    table.demandedSuit = res.newDemandedSuit;
    table.pendingDrawPenalty = res.newPendingPenalty;
    table.lastActionSummary = res.summary;
  }

  return table.lastActionSummary;
}

export async function drawPracticeCard(session) {
  const { table, myHand } = session;
  if (table.status !== "ACTIVE") throw new Error("Practice match has ended.");
  if (table.currentTurnUid !== PRACTICE_PLAYER_UID) throw new Error("Wait for Kampala Bot's turn!");

  const myName = table.playerNames[PRACTICE_PLAYER_UID] || "Guest Player";
  const drawRes = applyDraw({
    playerId: PRACTICE_PLAYER_UID,
    playerName: myName,
    hand: myHand.cards,
    playerIds: table.playerIds,
    turnDirection: table.turnDirection,
    pendingDrawPenalty: table.pendingDrawPenalty,
    topCardCode: table.topCard,
    drawPile: table.drawPile,
    discardPile: table.discardPile,
  });

  const nextSeq = table.moveSequence + 1;
  const digest = await moveDigest(table.id, nextSeq, PRACTICE_PLAYER_UID, "DRAW", "", table.topCard);

  myHand.cards = drawRes.newHand;
  myHand.handPoints = calculatePoints(drawRes.newHand);
  table.cardCounts = { ...table.cardCounts, [PRACTICE_PLAYER_UID]: drawRes.newHand.length };
  table.currentTurnUid = drawRes.nextTurnUid;
  table.pendingDrawPenalty = 0;
  table.drawPile = drawRes.newDrawPile;
  table.discardPile = drawRes.newDiscardPile;
  table.moveSequence = nextSeq;
  table.lastActionSummary = drawRes.summary;

  session.moves = [
    {
      id: `seq_${nextSeq}`,
      moveSequence: nextSeq,
      playerId: PRACTICE_PLAYER_UID,
      playerName: myName,
      actionType: "DRAW",
      cardPlayed: "",
      demandedSuit: "",
      drawCount: drawRes.drawnCards.length,
      handSizeAfter: drawRes.newHand.length,
      antiCheatDigest: digest,
    },
    ...session.moves,
  ];

  return drawRes.summary;
}

export async function stepPracticeBotTurn(session) {
  const { table, myHand, botHand } = session;
  if (table.status !== "ACTIVE" || table.currentTurnUid !== BOT_UID) return "";

  const { card: chosenCard, suitChoice } = chooseBotCard({
    botHand: botHand.cards,
    topCardCode: table.topCard,
    demandedSuitName: table.demandedSuit,
    pendingDrawPenalty: table.pendingDrawPenalty,
    cutSuitName: table.cutSuit,
  });

  const nextSeq = table.moveSequence + 1;

  if (chosenCard) {
    const idx = botHand.cards.indexOf(chosenCard.code);
    const remaining = botHand.cards.filter((_, i) => i !== idx);
    const triggerCut =
      canTriggerSevenCut(chosenCard, botHand.cards, table.cutSuit, table.pendingDrawPenalty) &&
      calculatePoints(remaining) <= 15;

    const res = applyPlayCard({
      playerId: BOT_UID,
      playerName: BOT_NAME,
      cardCode: chosenCard.code,
      chosenSuitForAce: suitChoice,
      triggerCut,
      hand: botHand.cards,
      playerIds: table.playerIds,
      topCardCode: table.topCard,
      demandedSuitName: table.demandedSuit,
      pendingDrawPenalty: table.pendingDrawPenalty,
      turnDirection: table.turnDirection,
      cutSuitName: table.cutSuit,
      drawPile: table.drawPile,
      discardPile: table.discardPile,
    });

    const actionType = res.triggersSevenCutShowdown ? "CUT_SEVEN" : "PLAY";
    const digest = await moveDigest(table.id, nextSeq, BOT_UID, actionType, chosenCard.code, res.newTopCard);

    botHand.cards = res.newHand;
    botHand.handPoints = calculatePoints(res.newHand);
    table.cardCounts = { ...table.cardCounts, [BOT_UID]: res.newHand.length };
    table.turnDirection = res.newTurnDirection;
    table.topCard = res.newTopCard;
    table.drawPile = res.newDrawPile;
    table.discardPile = res.newDiscardPile;
    table.moveSequence = nextSeq;

    session.moves = [
      {
        id: `seq_${nextSeq}`,
        moveSequence: nextSeq,
        playerId: BOT_UID,
        playerName: BOT_NAME,
        actionType,
        cardPlayed: chosenCard.code,
        demandedSuit: res.newDemandedSuit,
        drawCount: 0,
        handSizeAfter: res.newHand.length,
        antiCheatDigest: digest,
      },
      ...session.moves,
    ];

    if (res.triggersWinEmptyHand) {
      table.status = "FINISHED";
      table.currentTurnUid = BOT_UID;
      table.demandedSuit = "";
      table.pendingDrawPenalty = 0;
      table.winnerId = BOT_UID;
      table.winnerName = BOT_NAME;
      table.winReason = "EMPTY_HAND";
      table.revealedHands = { [BOT_UID]: res.newHand };
      table.revealedPoints = { [BOT_UID]: 0 };
      table.lastActionSummary = res.summary;
    } else if (res.triggersSevenCutShowdown) {
      const myPts = calculatePoints(myHand.cards);
      const botPts = calculatePoints(res.newHand);
      const botWon = botPts < myPts;
      table.status = "FINISHED";
      table.currentTurnUid = botWon ? BOT_UID : PRACTICE_PLAYER_UID;
      table.demandedSuit = "";
      table.pendingDrawPenalty = 0;
      table.winnerId = botWon ? BOT_UID : PRACTICE_PLAYER_UID;
      table.winnerName = botWon ? BOT_NAME : table.playerNames[PRACTICE_PLAYER_UID];
      table.winReason = "SEVEN_CUT";
      table.revealedHands = { [BOT_UID]: res.newHand, [PRACTICE_PLAYER_UID]: myHand.cards };
      table.revealedPoints = { [BOT_UID]: botPts, [PRACTICE_PLAYER_UID]: myPts };
      table.lastActionSummary = `Bot dropped Cut 7! Bot (${botPts} pts) vs You (${myPts} pts)`;
    } else {
      table.currentTurnUid = res.nextTurnUid;
      table.demandedSuit = res.newDemandedSuit;
      table.pendingDrawPenalty = res.newPendingPenalty;
      table.lastActionSummary = res.summary;
    }
    return table.lastActionSummary;
  }

  const drawRes = applyDraw({
    playerId: BOT_UID,
    playerName: BOT_NAME,
    hand: botHand.cards,
    playerIds: table.playerIds,
    turnDirection: table.turnDirection,
    pendingDrawPenalty: table.pendingDrawPenalty,
    topCardCode: table.topCard,
    drawPile: table.drawPile,
    discardPile: table.discardPile,
  });

  const digest = await moveDigest(table.id, nextSeq, BOT_UID, "DRAW", "", table.topCard);
  botHand.cards = drawRes.newHand;
  botHand.handPoints = calculatePoints(drawRes.newHand);
  table.cardCounts = { ...table.cardCounts, [BOT_UID]: drawRes.newHand.length };
  table.currentTurnUid = drawRes.nextTurnUid;
  table.pendingDrawPenalty = 0;
  table.drawPile = drawRes.newDrawPile;
  table.discardPile = drawRes.newDiscardPile;
  table.moveSequence = nextSeq;
  table.lastActionSummary = drawRes.summary;

  session.moves = [
    {
      id: `seq_${nextSeq}`,
      moveSequence: nextSeq,
      playerId: BOT_UID,
      playerName: BOT_NAME,
      actionType: "DRAW",
      cardPlayed: "",
      demandedSuit: "",
      drawCount: drawRes.drawnCards.length,
      handSizeAfter: drawRes.newHand.length,
      antiCheatDigest: digest,
    },
    ...session.moves,
  ];

  return drawRes.summary;
}
