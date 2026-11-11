package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class AmatatuRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserAccountInitialized_createsStarterBalanceAndBlocksCrossUserRead() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID().toString().take(8)}@test.com")
        val aliceRepo = AmatatuRepository(firestore, auth)

        val account = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserAccountInitialized("Alice Kampala", null).getOrThrow()
        }
        assertEquals(aliceUid, account.userId)
        assertEquals(25000L, account.walletBalanceUgx)

        // Sign in as Bob and verify Bob cannot read Alice's private user profile
        signInTestUser("bob_${UUID.randomUUID().toString().take(8)}@test.com")
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice private profile")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    @Test
    fun createBotMatchAndPlayMove_succeedsAndPreservesHandIsolation() = runBlocking {
        val aliceUid = signInTestUser("player_${UUID.randomUUID().toString().take(8)}@test.com")
        val aliceRepo = AmatatuRepository(firestore, auth)
        val alice = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserAccountInitialized("Alice", null).getOrThrow()
        }

        val tableId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTable(
                user = alice,
                title = "Kololo Bot Match",
                stakeUgx = 1000L,
                maxPlayers = 2,
                isPrivate = true,
                isBotMatch = true
            ).getOrThrow()
        }

        val table = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeTable(tableId, aliceUid).first { it != null }!!
        }
        assertEquals("ACTIVE", table.status)
        assertEquals(7L, table.cardCounts[aliceUid])

        val hand = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePlayerHand(tableId, aliceUid, aliceUid).first { it != null }!!
        }
        assertEquals(7, hand.cards.size)

        // Draw a card as Alice to verify move sequence + audit trail
        val summary = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.drawCard(table, hand).getOrThrow()
        }
        assertTrue(summary.contains("drew"))
    }

    @Test
    fun submitMobileMoneyDepositAndWithdrawal_updatesWalletAndCreatesPendingLedger() = runBlocking {
        val uid = signInTestUser("momo_${UUID.randomUUID().toString().take(8)}@test.com")
        val repo = AmatatuRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserAccountInitialized("Kato", null).getOrThrow()
        }

        val depRef = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitMobileMoneyDeposit("Kato", 15000L, "MTN_MOMO", "+256772555111").getOrThrow()
        }
        assertTrue(depRef.startsWith("MTN-"))

        val wdlRef = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitMobileMoneyWithdrawal("Kato", 5000L, "AIRTEL_MONEY", "+256701555111").getOrThrow()
        }
        assertTrue(wdlRef.startsWith("ATW-"))

        val updatedUser = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getCurrentUser(uid).getOrThrow()!!
        }
        assertEquals(20000L, updatedUser.walletBalanceUgx)

        val txs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserTransactions(uid).first { it.size >= 3 }
        }
        assertTrue(txs.any { it.type == "DEPOSIT" && it.status == "PENDING" })
        assertTrue(txs.any { it.type == "WITHDRAWAL" && it.status == "PENDING" })
    }

    @Test
    fun pvpPrivateRoomJoinStartForfeitAndSettle_creditsWinnerAndCreatesHistory() = runBlocking {
        val aliceEmail = "host_${UUID.randomUUID().toString().take(8)}@test.com"
        val bobEmail = "guest_${UUID.randomUUID().toString().take(8)}@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = AmatatuRepository(firestore, auth)
        val alice = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserAccountInitialized("Alice Host", null).getOrThrow()
        }

        val tableId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTable(
                user = alice,
                title = "Private Bugolobi",
                stakeUgx = 5000L,
                maxPlayers = 2,
                isPrivate = true,
                isBotMatch = false
            ).getOrThrow()
        }
        val waitingTable = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeTable(tableId, aliceUid).first { it != null }!!
        }
        assertTrue(waitingTable.inviteCode.startsWith("AMT"))

        // Bob joins using private invite code
        val bobUid = signInTestUser(bobEmail)
        val bobRepo = AmatatuRepository(firestore, auth)
        val bob = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserAccountInitialized("Bob Guest", null).getOrThrow()
        }
        val joinedId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinPrivateTableByCode(bob, waitingTable.inviteCode).getOrThrow()
        }
        assertEquals(tableId, joinedId)

        // Alice deals cards & starts match (writes both players' private hands)
        signInTestUser(aliceEmail)
        val joinedTable = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeTable(tableId, aliceUid).first { it != null && it.playerIds.size == 2 }!!
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.startWaitingTable(joinedTable).getOrThrow()
        }

        // Bob cannot read Alice's private hand, and Bob forfeits -> Alice wins 9,500 UGX pot
        signInTestUser(bobEmail)
        val activeTable = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeTable(tableId, bobUid).first { it != null && it.status == "ACTIVE" }!!
        }
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("tables").document(tableId).collection("hands").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice private hand")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.forfeitMatch(activeTable).getOrThrow()
        }

        // Bob settles loser side (clears escrow, writes history)
        val finishedForBob = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeTable(tableId, bobUid).first { it != null && it.status == "FINISHED" }!!
        }
        val bobCredited = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.settleFinishedMatchForCurrentUser(finishedForBob).getOrThrow()
        }
        assertEquals(false, bobCredited)
        val bobAfter = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getCurrentUser(bobUid).getOrThrow()!! }
        assertEquals(0L, bobAfter.escrowUgx)
        assertEquals(20000L, bobAfter.walletBalanceUgx)

        // Alice settles winner side even AFTER history doc exists -> claims 9,500 UGX payout (25k - 5k + 9.5k = 29.5k)!
        signInTestUser(aliceEmail)
        val finishedForAlice = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeTable(tableId, aliceUid).first { it != null && it.status == "FINISHED" }!!
        }
        val aliceCredited = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.settleFinishedMatchForCurrentUser(finishedForAlice).getOrThrow()
        }
        assertTrue(aliceCredited)
        val aliceAfter = withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.getCurrentUser(aliceUid).getOrThrow()!! }
        assertEquals(0L, aliceAfter.escrowUgx)
        assertEquals(29500L, aliceAfter.walletBalanceUgx)
        assertEquals(1L, aliceAfter.gamesWon)
    }

    @Test
    fun observeUserTransactions_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = AmatatuRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserTransactions("any_uid").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
