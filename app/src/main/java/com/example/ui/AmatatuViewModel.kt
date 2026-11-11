package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffectsManager
import com.example.data.AmatatuRepository
import com.example.data.unwrapFirestoreError
import com.example.engine.AmatatuEngine
import com.example.model.AntiCheatLog
import com.example.model.GameHistoryRecord
import com.example.model.GameTable
import com.example.model.MoveAudit
import com.example.model.PlayerHand
import com.example.model.PlayingCard
import com.example.model.PublicProfile
import com.example.model.StakeTiers
import com.example.model.Suit
import com.example.model.UserAccount
import com.example.model.WalletTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveTableState(
    val table: GameTable? = null,
    val myHand: PlayerHand? = null,
    val botHand: PlayerHand? = null,
    val moves: List<MoveAudit> = emptyList()
)

data class AdminDashboardState(
    val users: List<UserAccount> = emptyList(),
    val transactions: List<WalletTransaction> = emptyList(),
    val tables: List<GameTable> = emptyList(),
    val antiCheatLogs: List<AntiCheatLog> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class AmatatuViewModel(
    private val repository: AmatatuRepository,
    val soundEffects: SoundEffectsManager,
    val currentUserId: String,
    initialDisplayName: String,
    initialPhotoUrl: String?
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _selectedStakeUgx = MutableStateFlow(StakeTiers.ALL[2].stakeUgx) // UGX 2,500 default
    val selectedStakeUgx: StateFlow<Long> = _selectedStakeUgx.asStateFlow()

    private val _activeTableId = MutableStateFlow<String?>(null)
    val activeTableId: StateFlow<String?> = _activeTableId.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    private val settledTableIds = mutableSetOf<String>()
    private var lastBotHandledSeq: Pair<String, Long>? = null
    private var lastRevealTableId: String? = null

    private fun <T> Flow<T>.toSafeUiState(tagLabel: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing $tagLabel", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load $tagLabel"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userState: StateFlow<UiState<UserAccount?>> =
        repository.observeCurrentUser(currentUserId)
            .map<UserAccount?, UiState<UserAccount?>> { account ->
                if (account != null) {
                    soundEffects.isEnabled = account.soundEnabled
                }
                UiState.Success(account)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing user", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load profile"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val waitingTablesState: StateFlow<UiState<List<GameTable>>> =
        repository.observePublicWaitingTables(currentUserId).toSafeUiState("waiting tables")

    val myTablesState: StateFlow<UiState<List<GameTable>>> =
        repository.observeUserTables(currentUserId).toSafeUiState("my tables")

    val leaderboardState: StateFlow<UiState<List<PublicProfile>>> =
        repository.observeLeaderboard(currentUserId).toSafeUiState("leaderboard")

    val transactionsState: StateFlow<UiState<List<WalletTransaction>>> =
        repository.observeUserTransactions(currentUserId).toSafeUiState("transactions")

    val gameHistoryState: StateFlow<UiState<List<GameHistoryRecord>>> =
        repository.observeUserGameHistory(currentUserId).toSafeUiState("game history")

    val activeTableState: StateFlow<ActiveTableState> =
        _activeTableId.flatMapLatest { tableId ->
            if (tableId.isNullOrBlank()) {
                flowOf(ActiveTableState())
            } else {
                combine(
                    repository.observeTable(tableId, currentUserId),
                    repository.observePlayerHand(tableId, currentUserId, currentUserId),
                    repository.observeTableMoves(tableId, currentUserId)
                ) { table, hand, moves ->
                    ActiveTableState(table = table, myHand = hand, moves = moves)
                }.flatMapLatest { base ->
                    val tbl = base.table
                    if (tbl != null && tbl.isBotMatch) {
                        repository.observePlayerHand(tbl.id, AmatatuEngine.BOT_UID, currentUserId)
                            .map { botHand -> base.copy(botHand = botHand) }
                    } else {
                        flowOf(base)
                    }
                }.catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing active table", firestoreError ?: error)
                    emit(ActiveTableState())
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ActiveTableState())

    val adminDashboardState: StateFlow<UiState<AdminDashboardState>> =
        _isAdmin.flatMapLatest { admin ->
            if (!admin) {
                flowOf(UiState.Success(AdminDashboardState()))
            } else {
                combine(
                    repository.observeAllUsersForAdmin(currentUserId),
                    repository.observeAllTransactionsForAdmin(currentUserId),
                    repository.observeAllTablesForAdmin(currentUserId),
                    repository.observeAntiCheatLogsForAdmin(currentUserId)
                ) { users, txs, tables, logs ->
                    val dashboard: UiState<AdminDashboardState> = UiState.Success(
                        AdminDashboardState(
                            users = users,
                            transactions = txs,
                            tables = tables,
                            antiCheatLogs = logs
                        )
                    )
                    dashboard
                }.catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(UiState.Error((firestoreError ?: error).localizedMessage ?: "Admin stream error"))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    init {
        viewModelScope.launch {
            repository.ensureUserAccountInitialized(initialDisplayName, initialPhotoUrl)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Profile initialization failed") }
            _isAdmin.value = repository.checkIsAdmin()
        }

        // Reactive game-table automation: Bot turns, Showdown reveals, and Automatic UGX payouts
        viewModelScope.launch {
            activeTableState.collect { state ->
                val table = state.table ?: return@collect

                if (table.status == "SHOWDOWN" &&
                    state.myHand != null &&
                    !table.revealedPoints.containsKey(currentUserId) &&
                    lastRevealTableId != table.id
                ) {
                    lastRevealTableId = table.id
                    repository.submitShowdownReveal(table, state.myHand)
                }

                if (table.status == "FINISHED" &&
                    !table.settledUids.contains(currentUserId) &&
                    settledTableIds.add(table.id)
                ) {
                    val settleResult = repository.settleFinishedMatchForCurrentUser(table)
                    if (settleResult.isFailure) {
                        settledTableIds.remove(table.id)
                    } else {
                        val credited = settleResult.getOrDefault(false)
                        if (table.winnerId == currentUserId) {
                            soundEffects.playWinFanfare()
                            if (credited && table.payoutUgx > 0L) {
                                delay(350)
                                soundEffects.playCoinChime()
                                _toastEvents.tryEmit("Pot settled: +UGX ${table.payoutUgx} credited to your wallet!")
                            }
                        }
                    }
                }

                if (table.isBotMatch &&
                    table.status == "ACTIVE" &&
                    table.currentTurnUid == AmatatuEngine.BOT_UID &&
                    state.botHand != null
                ) {
                    val key = table.id to table.moveSequence
                    if (lastBotHandledSeq != key) {
                        lastBotHandledSeq = key
                        delay(850)
                        repository.executeBotTurn(table, state.botHand)
                            .onSuccess { summary ->
                                if (summary.contains("stacked")) soundEffects.playAttackTwo()
                                else if (summary.contains("jumped")) soundEffects.playJumpSkip()
                                else if (summary.contains("drew")) soundEffects.playDrawCard()
                                else soundEffects.playCardTap()
                            }
                    }
                }
            }
        }
    }

    fun selectStakeTier(stakeUgx: Long) {
        _selectedStakeUgx.value = stakeUgx
    }

    fun openTable(tableId: String) {
        _activeTableId.value = tableId
    }

    fun leaveTableView() {
        _activeTableId.value = null
    }

    fun startSoloBotMatch(user: UserAccount, stakeUgx: Long) = launchBusy {
        val tierLabel = StakeTiers.ALL.firstOrNull { it.stakeUgx == stakeUgx }?.label ?: "Kampala"
        repository.createTable(
            user = user,
            title = "$tierLabel vs Bot",
            stakeUgx = stakeUgx,
            maxPlayers = 2,
            isPrivate = true,
            isBotMatch = true
        ).onSuccess { tableId ->
            soundEffects.playCardTap()
            _activeTableId.value = tableId
        }.onFailure {
            soundEffects.playErrorTone()
            _toastEvents.tryEmit(it.localizedMessage ?: "Unable to launch table")
        }
    }

    fun quickMatchOnline(user: UserAccount, stakeUgx: Long) = launchBusy {
        repository.quickMatchOnline(user, stakeUgx)
            .onSuccess { tableId ->
                soundEffects.playCardTap()
                _activeTableId.value = tableId
            }
            .onFailure {
                soundEffects.playErrorTone()
                _toastEvents.tryEmit(it.localizedMessage ?: "Matchmaking failed")
            }
    }

    fun createPrivateRoom(user: UserAccount, title: String, stakeUgx: Long, maxPlayers: Int) = launchBusy {
        repository.createTable(
            user = user,
            title = title.ifBlank { "${user.displayName}'s Room" },
            stakeUgx = stakeUgx,
            maxPlayers = maxPlayers,
            isPrivate = true,
            isBotMatch = false
        ).onSuccess { tableId ->
            soundEffects.playCoinChime()
            _activeTableId.value = tableId
        }.onFailure {
            soundEffects.playErrorTone()
            _toastEvents.tryEmit(it.localizedMessage ?: "Could not create private room")
        }
    }

    fun joinPublicTable(user: UserAccount, tableId: String) = launchBusy {
        repository.joinTableById(user, tableId)
            .onSuccess {
                soundEffects.playCardTap()
                _activeTableId.value = tableId
            }
            .onFailure {
                soundEffects.playErrorTone()
                _toastEvents.tryEmit(it.localizedMessage ?: "Could not join table")
            }
    }

    fun joinByInviteCode(user: UserAccount, code: String) = launchBusy {
        repository.joinPrivateTableByCode(user, code)
            .onSuccess { tableId ->
                soundEffects.playCoinChime()
                _activeTableId.value = tableId
            }
            .onFailure {
                soundEffects.playErrorTone()
                _toastEvents.tryEmit(it.localizedMessage ?: "Invalid or closed room code")
            }
    }

    fun startTable(table: GameTable) = launchBusy {
        repository.startWaitingTable(table)
            .onSuccess { soundEffects.playCardTap() }
            .onFailure {
                soundEffects.playErrorTone()
                _toastEvents.tryEmit(it.localizedMessage ?: "Could not start match")
            }
    }

    fun cancelWaitingTable(table: GameTable) = launchBusy {
        repository.cancelWaitingTable(table)
            .onSuccess {
                _toastEvents.tryEmit("Table closed and stake refunded")
                _activeTableId.value = null
            }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Failed to cancel table") }
    }

    fun playCard(
        card: PlayingCard,
        chosenSuitForAce: Suit? = null,
        triggerCut: Boolean = false
    ) {
        val state = activeTableState.value
        val table = state.table ?: return
        val hand = state.myHand ?: return
        viewModelScope.launch {
            repository.playCard(
                table = table,
                hand = hand,
                card = card,
                chosenSuitForAce = chosenSuitForAce,
                triggerCut = triggerCut,
                botHandForInstantCut = state.botHand?.cards
            ).onSuccess { summary ->
                when {
                    triggerCut -> soundEffects.playSevenCut()
                    summary.contains("stacked") -> soundEffects.playAttackTwo()
                    summary.contains("jumped") || summary.contains("skipped") -> soundEffects.playJumpSkip()
                    else -> soundEffects.playCardTap()
                }
            }.onFailure {
                soundEffects.playErrorTone()
                _toastEvents.tryEmit(it.localizedMessage ?: "Invalid move")
            }
        }
    }

    fun drawCard() {
        val state = activeTableState.value
        val table = state.table ?: return
        val hand = state.myHand ?: return
        viewModelScope.launch {
            repository.drawCard(table, hand)
                .onSuccess { soundEffects.playDrawCard() }
                .onFailure {
                    soundEffects.playErrorTone()
                    _toastEvents.tryEmit(it.localizedMessage ?: "Cannot draw right now")
                }
        }
    }

    fun forfeitActiveMatch(table: GameTable) = launchBusy {
        repository.forfeitMatch(table)
            .onSuccess {
                _toastEvents.tryEmit("Match forfeited")
                _activeTableId.value = null
            }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Forfeit failed") }
    }

    fun depositMobileMoney(userName: String, amountUgx: Long, provider: String, phone: String) = launchBusy {
        repository.submitMobileMoneyDeposit(userName, amountUgx, provider, phone)
            .onSuccess { ref ->
                soundEffects.playCoinChime()
                _toastEvents.tryEmit("Deposit submitted ($ref). Pending cashier verification.")
            }
            .onFailure {
                soundEffects.playErrorTone()
                _toastEvents.tryEmit(it.localizedMessage ?: "Deposit failed")
            }
    }

    fun withdrawMobileMoney(userName: String, amountUgx: Long, provider: String, phone: String) = launchBusy {
        repository.submitMobileMoneyWithdrawal(userName, amountUgx, provider, phone)
            .onSuccess { ref ->
                soundEffects.playCoinChime()
                _toastEvents.tryEmit("Cashout queued ($ref) to $phone.")
            }
            .onFailure {
                soundEffects.playErrorTone()
                _toastEvents.tryEmit(it.localizedMessage ?: "Withdrawal failed")
            }
    }

    fun updateProfile(
        displayName: String,
        phoneNumber: String,
        preferredProvider: String,
        avatarKey: String,
        soundEnabled: Boolean
    ) = launchBusy {
        repository.updateProfile(displayName, phoneNumber, preferredProvider, avatarKey, soundEnabled)
            .onSuccess {
                soundEffects.isEnabled = soundEnabled
                soundEffects.playCoinChime()
                _toastEvents.tryEmit("Profile saved")
            }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Save failed") }
    }

    fun toggleSoundQuick(user: UserAccount) {
        val next = !user.soundEnabled
        soundEffects.isEnabled = next
        viewModelScope.launch {
            repository.updateProfile(
                user.displayName,
                user.phoneNumber,
                user.preferredProvider,
                user.avatarKey,
                next
            )
            if (next) soundEffects.playCardTap()
        }
    }

    // --- Admin Actions ---
    fun adminApproveTx(tx: WalletTransaction) = launchBusy {
        repository.adminApproveTransaction(tx)
            .onSuccess {
                soundEffects.playCoinChime()
                _toastEvents.tryEmit("Approved ${tx.referenceCode}")
            }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Approval failed") }
    }

    fun adminRejectTx(tx: WalletTransaction, reason: String) = launchBusy {
        repository.adminRejectTransaction(tx, reason)
            .onSuccess { _toastEvents.tryEmit("Rejected ${tx.referenceCode}") }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Rejection failed") }
    }

    fun adminToggleBan(uid: String, ban: Boolean) = launchBusy {
        repository.adminTogglePlayerBan(uid, ban)
            .onSuccess { _toastEvents.tryEmit(if (ban) "Player suspended" else "Player reinstated") }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Action failed") }
    }

    fun adminCancelTable(tableId: String) = launchBusy {
        repository.adminCancelTable(tableId)
            .onSuccess { _toastEvents.tryEmit("Table closed by Admin") }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Close failed") }
    }

    fun adminResolveCheatLog(logId: String) = launchBusy {
        repository.adminResolveCheatLog(logId)
            .onSuccess { _toastEvents.tryEmit("Alert marked resolved") }
            .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Resolve failed") }
    }

    fun reportAnomaly(tableId: String, reportedUid: String, details: String) = launchBusy {
        repository.reportAntiCheatAnomaly(
            tableId = tableId,
            reportedUid = reportedUid,
            eventType = "PLAYER_REPORT",
            details = details.trim().ifBlank { "Player flagged suspicious table activity" },
            severity = "MEDIUM"
        ).onSuccess {
            _toastEvents.tryEmit("Anti-cheat report submitted for Admin review")
        }.onFailure {
            _toastEvents.tryEmit(it.localizedMessage ?: "Failed to submit report")
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                block()
            } finally {
                _isBusy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "AmatatuVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
