export const BOT_UID = "kampala_bot";
export const BOT_NAME = "Kampala Master Bot";
export const BOT_AVATAR = "spade_ace";
export const CUT_THRESHOLD_POINTS = 25;
export const ADMIN_EMAIL = "markniaras@gmail.com";

export const SUITS = [
  { name: "HEARTS", symbol: "♥", label: "Hearts", code: "H", isRed: true },
  { name: "DIAMONDS", symbol: "♦", label: "Diamonds", code: "D", isRed: true },
  { name: "CLUBS", symbol: "♣", label: "Clubs", code: "C", isRed: false },
  { name: "SPADES", symbol: "♠", label: "Spades", code: "S", isRed: false },
];

export const RANKS = [
  { name: "ACE", symbol: "A", points: 15, code: "A" },
  { name: "TWO", symbol: "2", points: 20, code: "2" },
  { name: "THREE", symbol: "3", points: 3, code: "3" },
  { name: "FOUR", symbol: "4", points: 4, code: "4" },
  { name: "FIVE", symbol: "5", points: 5, code: "5" },
  { name: "SIX", symbol: "6", points: 6, code: "6" },
  { name: "SEVEN", symbol: "7", points: 7, code: "7" },
  { name: "EIGHT", symbol: "8", points: 8, code: "8" },
  { name: "NINE", symbol: "9", points: 9, code: "9" },
  { name: "TEN", symbol: "10", points: 10, code: "10" },
  { name: "JACK", symbol: "J", points: 11, code: "J" },
  { name: "QUEEN", symbol: "Q", points: 12, code: "Q" },
  { name: "KING", symbol: "K", points: 13, code: "K" },
];

export const STAKE_TIERS = [
  { id: "practice", label: "Practice Felt", stakeUgx: 0, tag: "FREE", description: "Warm up vs Kampala Bot" },
  { id: "boda", label: "Boda Table", stakeUgx: 1000, tag: "CASUAL", description: "Entry UGX 1,000 • Winner takes 1,900" },
  { id: "nakasero", label: "Nakasero Lounge", stakeUgx: 2500, tag: "POPULAR", description: "Entry UGX 2,500 • Winner takes 4,750" },
  { id: "kololo", label: "Kololo Club", stakeUgx: 5000, tag: "COMPETITIVE", description: "Entry UGX 5,000 • Winner takes 9,500" },
  { id: "serena", label: "Serena Suite", stakeUgx: 10000, tag: "HIGH ROLLER", description: "Entry UGX 10,000 • Winner takes 19,000" },
  { id: "vip", label: "Kampala Crown VIP", stakeUgx: 25000, tag: "VIP", description: "Entry UGX 25,000 • Winner takes 47,500" },
  { id: "royal", label: "Royal Pot", stakeUgx: 50000, tag: "ELITE", description: "Entry UGX 50,000 • Winner takes 95,000" },
];

export const AVATAR_PRESETS = [
  { key: "crown_gold", label: "Royal Crown", glyph: "♛" },
  { key: "spade_ace", label: "Ace of Spades", glyph: "♠" },
  { key: "seven_chop", label: "Lucky Seven", glyph: "7" },
  { key: "lion_crest", label: "Kampala Crest", glyph: "✦" },
  { key: "diamond_vip", label: "Diamond VIP", glyph: "♦" },
  { key: "emerald_shield", label: "Emerald Shield", glyph: "◈" },
];

export function formatUgx(amount) {
  const value = Number(amount || 0);
  return `UGX ${Math.round(value).toLocaleString("en-US")}`;
}

export function suitByName(name) {
  if (!name) return null;
  const upper = String(name).trim().toUpperCase();
  return SUITS.find((s) => s.name === upper) || null;
}

export function suitByCode(code) {
  return SUITS.find((s) => s.code === code) || SUITS[3];
}

export function parseCard(code) {
  if (!code || typeof code !== "string" || code.length < 2) return null;
  const suitCode = code.slice(-1);
  const rankCode = code.slice(0, -1);
  const suit = SUITS.find((s) => s.code === suitCode);
  const rank = RANKS.find((r) => r.code === rankCode);
  if (!suit || !rank) return null;
  return {
    code: `${rank.code}${suit.code}`,
    rank,
    suit,
    points: rank.points,
    displayLabel: `${rank.symbol}${suit.symbol}`,
  };
}

export function calculatePoints(cardCodes) {
  return (cardCodes || []).reduce((sum, code) => {
    const parsed = parseCard(code);
    return sum + (parsed ? parsed.points : 0);
  }, 0);
}

export function fullDeck() {
  const deck = [];
  for (const suit of SUITS) {
    for (const rank of RANKS) {
      deck.push({
        code: `${rank.code}${suit.code}`,
        rank,
        suit,
        points: rank.points,
        displayLabel: `${rank.symbol}${suit.symbol}`,
      });
    }
  }
  return deck;
}

export function shuffleArray(items, rng = Math.random) {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rng() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

export async function sha256(input) {
  const text = String(input);
  const subtle = globalThis.crypto?.subtle;
  if (subtle) {
    const bytes = new TextEncoder().encode(text);
    const hashBuffer = await subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(hashBuffer))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
  }
  // Fallback FNV/deterministic hex when running without WebCrypto
  let h1 = 0x811c9dc5;
  let h2 = 0x9e3779b9;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 0x01000193) >>> 0;
    h2 = Math.imul(h2 ^ ch, 0x85ebca6b) >>> 0;
  }
  return (h1.toString(16).padStart(8, "0") + h2.toString(16).padStart(8, "0")).repeat(4).slice(0, 64);
}

export async function moveDigest(tableId, moveSequence, playerId, actionType, cardPlayed, topCard) {
  const digest = await sha256(`${tableId}:${moveSequence}:${playerId}:${actionType}:${cardPlayed}:${topCard}`);
  return digest.slice(0, 24);
}

export async function dealMatch(tableId, playerIds, rng = Math.random) {
  const shuffled = shuffleArray(fullDeck(), rng);
  const commitHash = await sha256(`AMATATU:${tableId}:${shuffled.map((c) => c.code).join(",")}`);

  let starterIdx = shuffled.findIndex(
    (c) => c.rank.code !== "2" && c.rank.code !== "A" && c.rank.code !== "J" && c.rank.code !== "8"
  );
  if (starterIdx < 0) starterIdx = 0;
  const [starterCard] = shuffled.splice(starterIdx, 1);

  const cutCard = shuffled.pop();

  const handsByPlayer = {};
  for (const uid of playerIds) {
    const cards = [];
    for (let i = 0; i < 7; i += 1) {
      if (shuffled.length > 0) {
        cards.push(shuffled.shift().code);
      }
    }
    handsByPlayer[uid] = cards;
  }

  return {
    handsByPlayer,
    topCard: starterCard.code,
    cutCard: cutCard.code,
    cutSuit: cutCard.suit,
    drawPile: shuffled.map((c) => c.code),
    discardPile: [starterCard.code],
    deckCommitHash: commitHash,
  };
}

export function effectiveSuit(topCardCode, demandedSuitName) {
  const demanded = suitByName(demandedSuitName);
  if (demanded) return demanded;
  return parseCard(topCardCode)?.suit || SUITS[3];
}

export function isCutSevenCard(card, cutSuitName) {
  const cutSuit = suitByName(cutSuitName);
  if (!cutSuit || !card) return false;
  return card.rank.code === "7" && card.suit.name === cutSuit.name;
}

export function canTriggerSevenCut(card, hand, cutSuitName, pendingDrawPenalty) {
  if (Number(pendingDrawPenalty || 0) > 0) return false;
  if (!isCutSevenCard(card, cutSuitName)) return false;
  const idx = hand.indexOf(card.code);
  if (idx === -1) return false;
  const remaining = hand.filter((_, i) => i !== idx);
  return calculatePoints(remaining) <= CUT_THRESHOLD_POINTS;
}

export function isCardPlayable(card, topCardCode, demandedSuitName, pendingDrawPenalty, cutSuitName, hand) {
  const topCard = parseCard(topCardCode);
  if (!topCard || !card) return false;

  if (Number(pendingDrawPenalty || 0) > 0) {
    return card.rank.code === "2" || (card.rank.code === "A" && card.suit.name === "SPADES");
  }

  if (canTriggerSevenCut(card, hand, cutSuitName, pendingDrawPenalty)) {
    return true;
  }

  if (card.rank.code === "A") {
    return true;
  }

  const activeSuit = effectiveSuit(topCardCode, demandedSuitName);
  return card.suit.name === activeSuit.name || card.rank.code === topCard.rank.code;
}

export function advanceTurn(currentUid, playerIds, direction, steps) {
  if (!playerIds || playerIds.length === 0) return currentUid;
  let idx = playerIds.indexOf(currentUid);
  if (idx < 0) idx = 0;
  const size = playerIds.length;
  const stepDelta = Number(direction) >= 0 ? steps : -steps;
  const nextIdx = ((idx + stepDelta) % size + size) % size;
  return playerIds[nextIdx];
}

export function applyPlayCard({
  playerId,
  playerName,
  cardCode,
  chosenSuitForAce = null,
  triggerCut = false,
  hand = [],
  playerIds = [],
  topCardCode = "",
  demandedSuitName = "",
  pendingDrawPenalty = 0,
  turnDirection = 1,
  cutSuitName = "",
  drawPile = [],
  discardPile = [],
}) {
  const card = parseCard(cardCode);
  if (!card) return { valid: false, reason: `Unknown card ${cardCode}` };

  const cardIndex = hand.indexOf(cardCode);
  if (cardIndex === -1) {
    return { valid: false, reason: `Card ${cardCode} is not in your hand` };
  }

  if (!isCardPlayable(card, topCardCode, demandedSuitName, pendingDrawPenalty, cutSuitName, hand)) {
    return { valid: false, reason: `${card.displayLabel} does not match suit or rank` };
  }

  const newHand = [...hand];
  newHand.splice(cardIndex, 1);
  const newDiscard = [...discardPile, cardCode];

  const cutAllowed = canTriggerSevenCut(card, hand, cutSuitName, pendingDrawPenalty);
  if (triggerCut && !cutAllowed) {
    const remainingPts = calculatePoints(newHand);
    return {
      valid: false,
      reason: `7-Cut requires remaining hand <= ${CUT_THRESHOLD_POINTS} pts (yours: ${remainingPts} pts)`,
    };
  }

  if (triggerCut && cutAllowed && newHand.length > 0) {
    return {
      valid: true,
      newHand,
      newTopCard: cardCode,
      newDemandedSuit: "",
      newPendingPenalty: 0,
      newTurnDirection: turnDirection,
      nextTurnUid: playerId,
      newDrawPile: [...drawPile],
      newDiscardPile: newDiscard,
      triggersWinEmptyHand: false,
      triggersSevenCutShowdown: true,
      summary: `${playerName} dropped the Cut ${card.displayLabel}! Showdown!`,
    };
  }

  if (newHand.length === 0) {
    return {
      valid: true,
      newHand: [],
      newTopCard: cardCode,
      newDemandedSuit: "",
      newPendingPenalty: 0,
      newTurnDirection: turnDirection,
      nextTurnUid: playerId,
      newDrawPile: [...drawPile],
      newDiscardPile: newDiscard,
      triggersWinEmptyHand: true,
      triggersSevenCutShowdown: false,
      summary: `${playerName} shed their final card (${card.displayLabel})!`,
    };
  }

  let nextDir = Number(turnDirection) || 1;
  let nextPenalty = Number(pendingDrawPenalty) || 0;
  let nextDemandedSuit = "";
  let steps = 1;
  let actionText = `${playerName} played ${card.displayLabel}`;

  if (nextPenalty > 0 && card.rank.code === "A" && card.suit.name === "SPADES") {
    const demanded = suitByName(chosenSuitForAce?.name || chosenSuitForAce) || card.suit;
    actionText = `${playerName} blocked Pick-${nextPenalty} with A♠ → ${demanded.symbol}`;
    nextPenalty = 0;
    nextDemandedSuit = demanded.name;
  } else if (card.rank.code === "2") {
    nextPenalty += 2;
    actionText = `${playerName} stacked ${card.displayLabel} (Pick ${nextPenalty}!)`;
  } else if (card.rank.code === "A") {
    const demanded = suitByName(chosenSuitForAce?.name || chosenSuitForAce) || card.suit;
    nextDemandedSuit = demanded.name;
    actionText = `${playerName} played ${card.displayLabel} → demanded ${demanded.label}`;
  } else if (card.rank.code === "J" || card.rank.code === "8") {
    steps = 2;
    actionText =
      playerIds.length === 2
        ? `${playerName} jumped with ${card.displayLabel}! Plays again`
        : `${playerName} skipped next player with ${card.displayLabel}!`;
  } else if (card.rank.code === "K" && playerIds.length >= 3) {
    nextDir = -nextDir;
    actionText = `${playerName} reversed direction with ${card.displayLabel}!`;
  }

  const nextTurnUid = advanceTurn(playerId, playerIds, nextDir, steps);

  return {
    valid: true,
    newHand,
    newTopCard: cardCode,
    newDemandedSuit: nextDemandedSuit,
    newPendingPenalty: nextPenalty,
    newTurnDirection: nextDir,
    nextTurnUid,
    newDrawPile: [...drawPile],
    newDiscardPile: newDiscard,
    triggersWinEmptyHand: false,
    triggersSevenCutShowdown: false,
    summary: actionText,
  };
}

export function applyDraw({
  playerId,
  playerName,
  hand = [],
  playerIds = [],
  turnDirection = 1,
  pendingDrawPenalty = 0,
  topCardCode = "",
  drawPile = [],
  discardPile = [],
  rng = Math.random,
}) {
  const penalty = Number(pendingDrawPenalty) || 0;
  const countToDraw = penalty > 0 ? penalty : 1;
  const mutableDraw = [...drawPile];
  let mutableDiscard = [...discardPile];
  const drawnCards = [];

  for (let i = 0; i < countToDraw; i += 1) {
    if (mutableDraw.length === 0 && mutableDiscard.length > 1) {
      const keepTop = mutableDiscard[mutableDiscard.length - 1] || topCardCode;
      const recyclable = shuffleArray(mutableDiscard.slice(0, -1), rng);
      mutableDraw.push(...recyclable);
      mutableDiscard = [keepTop];
    }
    if (mutableDraw.length > 0) {
      drawnCards.push(mutableDraw.shift());
    }
  }

  const newHand = [...hand, ...drawnCards];
  const nextTurnUid = advanceTurn(playerId, playerIds, turnDirection, 1);
  const exhaustedDeck = drawnCards.length === 0 && mutableDraw.length === 0 && mutableDiscard.length <= 1;
  const summary =
    penalty > 0
      ? `${playerName} drew ${drawnCards.length} penalty cards`
      : `${playerName} drew ${drawnCards.length} card`;

  return {
    drawnCards,
    newHand,
    newDrawPile: mutableDraw,
    newDiscardPile: mutableDiscard,
    nextTurnUid,
    exhaustedDeck,
    summary,
  };
}

export function mostCommonSuit(parsedHand, excludingCode) {
  const counts = new Map();
  for (const card of parsedHand) {
    if (card.code === excludingCode) continue;
    counts.set(card.suit.name, (counts.get(card.suit.name) || 0) + 1);
  }
  let best = SUITS[0];
  let bestCount = -1;
  for (const suit of SUITS) {
    const c = counts.get(suit.name) || 0;
    if (c > bestCount) {
      bestCount = c;
      best = suit;
    }
  }
  return best;
}

export function chooseBotCard({
  botHand = [],
  topCardCode = "",
  demandedSuitName = "",
  pendingDrawPenalty = 0,
  cutSuitName = "",
}) {
  const parsed = botHand.map(parseCard).filter(Boolean);
  const playable = parsed.filter((card) =>
    isCardPlayable(card, topCardCode, demandedSuitName, pendingDrawPenalty, cutSuitName, botHand)
  );
  if (playable.length === 0) return { card: null, suitChoice: null };

  if (botHand.length === 1) {
    const first = playable[0];
    return {
      card: first,
      suitChoice: first.rank.code === "A" ? mostCommonSuit(parsed, first.code) : null,
    };
  }

  const cut7 = playable.find((card) => {
    if (!canTriggerSevenCut(card, botHand, cutSuitName, pendingDrawPenalty)) return false;
    const idx = botHand.indexOf(card.code);
    const rem = botHand.filter((_, i) => i !== idx);
    return calculatePoints(rem) <= 15;
  });
  if (cut7) return { card: cut7, suitChoice: null };

  const nonAce = playable.filter((c) => c.rank.code !== "A");
  const jump = nonAce.find((c) => c.rank.code === "J" || c.rank.code === "8");
  const two = nonAce.find((c) => c.rank.code === "2");
  const highestNonAce =
    nonAce.length > 0 ? nonAce.reduce((a, b) => (a.points >= b.points ? a : b)) : null;

  const chosen = jump || two || highestNonAce || playable[0];
  const suitChoice = chosen.rank.code === "A" ? mostCommonSuit(parsed, chosen.code) : null;
  return { card: chosen, suitChoice };
}

export function sortHandCodes(handCodes) {
  const suitOrder = SUITS.map((s) => s.code);
  const rankOrder = RANKS.map((r) => r.code);
  return [...handCodes].sort((a, b) => {
    const pa = parseCard(a);
    const pb = parseCard(b);
    if (!pa || !pb) return String(a).localeCompare(String(b));
    const sDiff = suitOrder.indexOf(pa.suit.code) - suitOrder.indexOf(pb.suit.code);
    if (sDiff !== 0) return sDiff;
    return rankOrder.indexOf(pa.rank.code) - rankOrder.indexOf(pb.rank.code);
  });
}
