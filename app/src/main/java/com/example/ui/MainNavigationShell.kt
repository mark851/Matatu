package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.audio.SoundEffectsManager
import com.example.data.AmatatuRepository
import com.example.model.UserAccount
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.formatUgx
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.GameTableScreen
import com.example.ui.screens.HistoryAndRulesScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.EmeraldFelt
import com.example.ui.theme.EmeraldFeltLight
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedSage
import com.example.ui.theme.ObsidianMahogany
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WarmOffWhite
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private enum class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LOBBY("lobby", "Tables", Icons.Filled.Casino, Icons.Outlined.Casino),
    WALLET("wallet", "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    HISTORY("history", "History", Icons.Filled.History, Icons.Outlined.History),
    PROFILE("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
    ADMIN("admin", "Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val signedInUser = currentUser

    if (signedInUser == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        AuthenticatedShell(firebaseUser = signedInUser)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(firebaseUser: FirebaseUser) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: AmatatuViewModel = viewModel(
        key = firebaseUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AmatatuViewModel(
                    repository = AmatatuRepository(db, Firebase.auth),
                    soundEffects = SoundEffectsManager(),
                    currentUserId = firebaseUser.uid,
                    initialDisplayName = firebaseUser.displayName ?: "Kampala Player",
                    initialPhotoUrl = firebaseUser.photoUrl?.toString()
                )
            }
        }
    )

    val userState by viewModel.userState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val selectedStakeUgx by viewModel.selectedStakeUgx.collectAsStateWithLifecycle()
    val activeTableId by viewModel.activeTableId.collectAsStateWithLifecycle()
    val activeTableState by viewModel.activeTableState.collectAsStateWithLifecycle()
    val waitingTablesState by viewModel.waitingTablesState.collectAsStateWithLifecycle()
    val myTablesState by viewModel.myTablesState.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val gameHistoryState by viewModel.gameHistoryState.collectAsStateWithLifecycle()
    val adminState by viewModel.adminDashboardState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(MainTab.LOBBY) }
    var historyInitialTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val user = (userState as? UiState.Success)?.data
    val tabs = remember(isAdmin) {
        if (isAdmin) listOf(MainTab.LOBBY, MainTab.WALLET, MainTab.HISTORY, MainTab.PROFILE, MainTab.ADMIN)
        else listOf(MainTab.LOBBY, MainTab.WALLET, MainTab.HISTORY, MainTab.PROFILE)
    }

    if (user == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EmeraldAbyss),
            contentAlignment = Alignment.Center
        ) {
            when (val st = userState) {
                is UiState.Error -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(st.message, color = CrimsonAlert, style = MaterialTheme.typography.bodyLarge)
                    Surface(
                        color = KampalaGold,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            signOutUser(credentialManager, coroutineScope)
                        }
                    ) {
                        Text(
                            "Sign Out & Retry",
                            color = EmeraldAbyss,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = KampalaGold)
                    Text("Loading your Kampala wallet & felt...", color = MutedSage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        return
    }

    if (activeTableId != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            GameTableScreen(
                tableState = activeTableState,
                user = user,
                isBusy = isBusy,
                onPlayCard = { card, suit, cut -> viewModel.playCard(card, suit, cut) },
                onDrawCard = { viewModel.drawCard() },
                onStartTable = { tbl -> viewModel.startTable(tbl) },
                onCancelTable = { tbl -> viewModel.cancelWaitingTable(tbl) },
                onForfeitMatch = { tbl -> viewModel.forfeitActiveMatch(tbl) },
                onReportAnomaly = { tableId, reportedUid, details ->
                    viewModel.reportAnomaly(tableId, reportedUid, details)
                },
                onToggleSound = { viewModel.toggleSoundQuick(user) },
                onBackToLobby = { viewModel.leaveTableView() }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(EmeraldAbyss)
    ) {
        val isWide = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWide) {
                NavigationRail(
                    containerColor = ObsidianMahogany,
                    contentColor = WarmOffWhite,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.size(16.dp))
                    Text(
                        text = "AMT",
                        style = MaterialTheme.typography.titleLarge,
                        color = KampalaGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    tabs.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (tab == MainTab.HISTORY) historyInitialTab = 0
                                currentTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, maxLines = 1) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = EmeraldAbyss,
                                selectedTextColor = KampalaGold,
                                indicatorColor = KampalaGold,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            ),
                            modifier = Modifier.testTag("nav_${tab.route}")
                        )
                    }
                }
            }

            Scaffold(
                containerColor = EmeraldAbyss,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ObsidianMahogany,
                            titleContentColor = WarmOffWhite
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "AMATATU",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = KampalaGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            Surface(
                                color = EmeraldFeltLight,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { currentTab = MainTab.WALLET }
                                    .testTag("topbar_wallet_chip")
                            ) {
                                Text(
                                    text = formatUgx(user.walletBalanceUgx),
                                    fontFamily = MonoFontFamily,
                                    color = KampalaGold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    historyInitialTab = 1
                                    currentTab = MainTab.HISTORY
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_rules_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Amatatu rules guide",
                                    tint = WarmOffWhite
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleSoundQuick(user) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_sound_button")
                            ) {
                                Icon(
                                    imageVector = if (user.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = if (user.soundEnabled) "Mute sound effects" else "Unmute sound effects",
                                    tint = if (user.soundEnabled) KampalaGold else MutedSage
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isWide) {
                        NavigationBar(
                            containerColor = ObsidianMahogany,
                            contentColor = WarmOffWhite,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (tab == MainTab.HISTORY) historyInitialTab = 0
                                        currentTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldAbyss,
                                        selectedTextColor = KampalaGold,
                                        indicatorColor = KampalaGold,
                                        unselectedIconColor = MutedSage,
                                        unselectedTextColor = MutedSage
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 1040.dp)
                            .fillMaxSize()
                    ) {
                        when (currentTab) {
                            MainTab.LOBBY -> LobbyScreen(
                                user = user,
                                selectedStakeUgx = selectedStakeUgx,
                                waitingTablesState = waitingTablesState,
                                myTablesState = myTablesState,
                                leaderboardState = leaderboardState,
                                isBusy = isBusy,
                                isWideLayout = isWide,
                                onSelectStake = viewModel::selectStakeTier,
                                onQuickMatch = { stake -> viewModel.quickMatchOnline(user, stake) },
                                onPlayBot = { stake -> viewModel.startSoloBotMatch(user, stake) },
                                onCreatePrivateRoom = { title, stake, maxSeats ->
                                    viewModel.createPrivateRoom(user, title, stake, maxSeats)
                                },
                                onJoinByCode = { code -> viewModel.joinByInviteCode(user, code) },
                                onJoinPublicTable = { tblId -> viewModel.joinPublicTable(user, tblId) },
                                onResumeTable = viewModel::openTable
                            )

                            MainTab.WALLET -> WalletScreen(
                                user = user,
                                transactionsState = transactionsState,
                                isBusy = isBusy,
                                onDeposit = { amount, provider, phone ->
                                    viewModel.depositMobileMoney(user.displayName, amount, provider, phone)
                                },
                                onWithdraw = { amount, provider, phone ->
                                    viewModel.withdrawMobileMoney(user.displayName, amount, provider, phone)
                                }
                            )

                            MainTab.HISTORY -> HistoryAndRulesScreen(
                                user = user,
                                historyState = gameHistoryState,
                                initialTab = historyInitialTab
                            )

                            MainTab.PROFILE -> ProfileScreen(
                                user = user,
                                isAdmin = isAdmin,
                                isBusy = isBusy,
                                onSaveProfile = { name, phone, provider, avatar, sound ->
                                    viewModel.updateProfile(name, phone, provider, avatar, sound)
                                },
                                onSignOut = {
                                    signOutUser(credentialManager, coroutineScope)
                                }
                            )

                            MainTab.ADMIN -> AdminDashboardScreen(
                                adminState = adminState,
                                isBusy = isBusy,
                                onApproveTx = viewModel::adminApproveTx,
                                onRejectTx = viewModel::adminRejectTx,
                                onToggleBan = viewModel::adminToggleBan,
                                onCancelTable = viewModel::adminCancelTable,
                                onResolveCheatLog = viewModel::adminResolveCheatLog
                            )
                        }
                    }
                }
            }
        }
    }
}
