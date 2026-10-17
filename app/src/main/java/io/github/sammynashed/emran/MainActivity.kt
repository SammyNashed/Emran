package io.github.sammynashed.emran

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Build

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.filled.MenuBook

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.unit.dp

import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import io.github.sammynashed.emran.ui.screens.*
import io.github.sammynashed.emran.ui.theme.*
import io.github.sammynashed.emran.ui.components.cosmicAuroraBackground
import io.github.sammynashed.emran.ui.design.DockTab
import io.github.sammynashed.emran.ui.design.GlassDock
import io.github.sammynashed.emran.ui.design.GlassTier
import io.github.sammynashed.emran.ui.design.SkyDebug
import io.github.sammynashed.emran.ui.design.SkyHost
import io.github.sammynashed.emran.ui.screens.MoreScreen

import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import io.github.sammynashed.emran.ui.viewmodel.QuranViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

val LocalDockVisible = compositionLocalOf { mutableStateOf(true) }

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels()
    private val quranViewModel: QuranViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        takeUpdateFile(intent)
        io.github.sammynashed.emran.data.TimeFormat.ensureLoaded(this)
        runCatching { io.github.sammynashed.emran.service.BootReceiver.ensureDaily(applicationContext) }
        runCatching { io.github.sammynashed.emran.service.AdhkarReminders.scheduleAll(applicationContext) }
        // Transparent bars with no contrast scrim, so the sky runs right under them.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        applyDesignDebugExtras(intent)

        // Pre-warm Quran repository data on a background thread at startup
        lifecycleScope.launch {
            io.github.sammynashed.emran.data.repository.QuranRepository.prewarm(applicationContext)
            
            // Trigger Widget Update to ensure home screen is perfectly synchronized
            try {
                androidx.work.WorkManager.getInstance(applicationContext).enqueue(
                    androidx.work.OneTimeWorkRequestBuilder<io.github.sammynashed.emran.service.EmranWidgetUpdateWorker>().build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            val isOnboardingCompleted by viewModel.onboardingCompleted.collectAsState()
            val themePreset by viewModel.themePreset.collectAsState()
            val themeModeName by viewModel.themeMode.collectAsState()
            val savedLanguage by viewModel.currentLanguage.collectAsState()
            val currentLanguage = SkyDebug.language ?: savedLanguage

            val dockVisibleState = remember { mutableStateOf(true) }

            val layoutDirection = if (currentLanguage == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

            CompositionLocalProvider(
                LocalDockVisible provides dockVisibleState,
                io.github.sammynashed.emran.ui.localization.LocalAppLanguage provides currentLanguage,
                androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
            ) {
                MyApplicationTheme(
                    themePreset = themePreset,
                    themeMode = io.github.sammynashed.emran.ui.theme.ThemeMode.fromName(themeModeName)
                ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val preferences = remember(context) { context.getSharedPreferences("emran_settings", android.content.Context.MODE_PRIVATE) }
                // Saved, so a rotation (or any recreation) never replays the splash.
                var showSplash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }

                // Home (or setup) composes straight away, underneath the splash, so all of its first-frame
                // work is done before the splash animates and nothing hitches mid-motion.
                if (!isOnboardingCompleted || SkyDebug.forceOnboarding) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        currentLanguage = currentLanguage,
                        onLanguageSelect = { lang -> viewModel.setCurrentLanguage(lang) },
                        onFinished = { preset, location, notificationsEnabled, method, school, adhanSound ->
                            if (SkyDebug.forceOnboarding) { SkyDebug.forceOnboarding = false; return@OnboardingScreen }
                            viewModel.setOnboardingCompleted(
                                completed = true,
                                initialTheme = preset,
                                initialLocName = location.name,
                                lat = location.latitude,
                                lon = location.longitude,
                                zone = location.zoneId,
                                notificationsEnabled = notificationsEnabled,
                                initialCalculationMethod = method,
                                initialAsrSchool = school,
                                defaultAdhanSoundVal = adhanSound
                            )
                        }
                    )
                } else {
                    val route = intent.getStringExtra("ROUTE")
                    val targetStart = intent.getStringExtra("START_DESTINATION") ?: "home"
                    val initialRoute = "home"
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: initialRoute

                    var activeTabRoute by remember {
                        mutableStateOf(if (targetStart == "quran" || targetStart == "finetune") targetStart else "home")
                    }
                    LaunchedEffect(currentRoute) {
                        // When transitioning back to main/home from a subscreen, retain the last activeTabRoute
                    }
                    LaunchedEffect(route) {
                        if (route == "more") {
                            navController.navigate("more") { launchSingleTop = true }
                        } else if (route == "PRAYER_TAB" || route == "prayer_times") {
                            activeTabRoute = "home"
                            navController.navigate("prayer_times") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else if (route in setOf("quran", "finetune")) {
                            activeTabRoute = route!!
                        } else if (route?.startsWith("quran_surah/") == true) {
                            // A reminder (al-Kahf on Friday) opens its surah in Tilawah.
                            activeTabRoute = "quran"
                            route.removePrefix("quran_surah/").toIntOrNull()?.let { quranViewModel.openSurah(it) }
                        } else if (route != null) {
                            // Debug: open any screen directly (screenshots on small displays).
                            if (route == "quran_player") activeTabRoute = "quran"
                            runCatching { navController.navigate(route) { launchSingleTop = true } }
                        }
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current

                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                    run {
                        // Screens already redesigned live on the sky; the rest keep their old
                        // background until their stage (the sky is white-on-bright unfriendly to them).
                        // The Quran tab is on the sky for its front pages (how to read, which surah);
                        // the reader itself keeps its page colours, so it is not a sky screen.
                        val quranState by quranViewModel.uiState.collectAsState()
                        val skyScreen = (currentRoute == "home" && activeTabRoute == "home") ||
                            (currentRoute == "home" && activeTabRoute == "quran" &&
                                quranState !is io.github.sammynashed.emran.ui.viewmodel.QuranUiState.SuccessVerses) ||
                            (currentRoute == "home" && activeTabRoute == "finetune") ||
                            currentRoute == "prayer_times" || currentRoute == "more" || currentRoute == "reciters" || currentRoute == "quran_player" || currentRoute == "compass" ||
                            currentRoute == "dhikr" || currentRoute?.startsWith("ayah/") == true || currentRoute == "ayah_pick" ||
                            currentRoute == "testers" || currentRoute == "adhkar" || currentRoute?.startsWith("adhkar_category/") == true ||
                            currentRoute?.startsWith("adhkar_detail/") == true ||
                            currentRoute == "hadith" || currentRoute?.startsWith("hadith_") == true || currentRoute?.startsWith("bookmarks") == true
                        SkyHost(
                            viewModel = viewModel,
                            showSky = skyScreen,
                            fallbackLightBars = AppTheme.colors.isLight,
                        ) {
                            Scaffold(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (skyScreen) {
                                            Modifier
                                        } else if (themePreset == "BLACK_STONE") {
                                            Modifier.background(AppTheme.colors.background)
                                        } else {
                                            Modifier.cosmicAuroraBackground(
                                                accentColor = AppTheme.colors.primary,
                                                secondaryAccent = AppTheme.colors.secondary
                                            )
                                        }
                                    ),
                                containerColor = Color.Transparent
                            ) { paddingValues ->
                                val dockVisible = LocalDockVisible.current.value

                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                ) {
                             if (isLandscape && dockVisible) {
                                LandscapeSideDock(
                                    currentRoute = activeTabRoute,
                                    onNavigateHome = {
                                        activeTabRoute = "home"
                                        if (currentRoute != "home") {
                                            // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                        }
                                    },
                                    onNavigateQuran = {
                                        activeTabRoute = "quran"
                                        if (currentRoute != "home") {
                                            // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                        }
                                    },
                                    onNavigateSettings = {
                                        activeTabRoute = "finetune"
                                        if (currentRoute != "home") {
                                            // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                        }
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = initialRoute,
                                    modifier = Modifier.fillMaxSize(),
                                    // Screens move like iOS: the new one slides in from the edge while the old
                                    // one recedes; going back is the reverse. Soft deceleration, no bounce.
                                    enterTransition = {
                                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300, easing = NavEase)) +
                                            slideIntoContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start,
                                                androidx.compose.animation.core.tween(400, easing = NavEase), initialOffset = { it / 5 })
                                    },
                                    exitTransition = {
                                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220)) +
                                            androidx.compose.animation.scaleOut(targetScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(400, easing = NavEase))
                                    },
                                    popEnterTransition = {
                                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300, easing = NavEase)) +
                                            androidx.compose.animation.scaleIn(initialScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(400, easing = NavEase))
                                    },
                                    popExitTransition = {
                                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220)) +
                                            slideOutOfContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End,
                                                androidx.compose.animation.core.tween(340, easing = NavEase), targetOffset = { it / 5 })
                                    },
                                ) {
                                    composable("home") {
                                        androidx.compose.animation.AnimatedContent(
                                            targetState = activeTabRoute,
                                            transitionSpec = {
                                                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300, delayMillis = 60, easing = NavEase)) +
                                                    androidx.compose.animation.scaleIn(initialScale = 0.965f, animationSpec = androidx.compose.animation.core.tween(380, easing = NavEase))) togetherWith
                                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
                                            },
                                            label = "tabs",
                                        ) { tab ->
                                        when (tab) {
                                            "home" -> {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                HomeScreen(
                                                    viewModel = viewModel,
                                                    onNavigateToPrayers = { navController.navigate("prayer_times") { launchSingleTop = true } },
                                                    onNavigateToQibla = { navController.navigate("compass") { launchSingleTop = true } },
                                                    onNavigateToTasbeeh = { navController.navigate("dhikr") { launchSingleTop = true } },
                                                    onNavigateToMore = { navController.navigate("more") { launchSingleTop = true } },
                                                    onOpenAyah = { surah, ayah ->
                                                        navController.navigate("ayah/$surah/$ayah") { launchSingleTop = true }
                                                    },
                                                 )
                                                 }
                                            }
                                            "quran" -> {
                                                QuranScreen(
                                                    viewModel = quranViewModel,
                                                    onOpenReciters = { navController.navigate("reciters") { launchSingleTop = true } },
                                                    onOpenPlayer = {
                                                        // The player belongs to the Qur'an tab: Back from it must land there.
                                                        activeTabRoute = "quran"
                                                        navController.navigate("quran_player") { launchSingleTop = true }
                                                    },
                                                )
                                            }
                                            "finetune" -> {
                                                FineTuneScreen(
                                                    viewModel = viewModel,
                                                    onOpenAcknowledgements = { navController.navigate("testers") }
                                                )
                                            }
                                        }
                                        }
                                    }
                                     composable("adhkar") {
                                         AdhkarScreen(
                                             onNavigateBack = { navController.popBackStack() },
                                             onNavigateToCategory = { navController.navigate("adhkar_category/$it") },
                                             onSearch = { navController.navigate("hadith_search") },
                                             onBookmarks = { navController.navigate("bookmarks?tab=adhkar") },
                                             onNavigateToDetail = { folderId ->
                                                 navController.navigate("adhkar_detail/$folderId")
                                             }
                                         )
                                     }
                                     composable("adhkar_category/{categoryId}") { entry ->
                                         AdhkarCategoryScreen(
                                             categoryId = entry.arguments?.getString("categoryId") ?: "",
                                             onNavigateBack = { navController.popBackStack() },
                                             onNavigateToDetail = { navController.navigate("adhkar_detail/$it") }
                                         )
                                     }
                                     composable(
                                         "adhkar_detail/{folderId}",
                                         arguments = listOf(androidx.navigation.navArgument("folderId") { type = androidx.navigation.NavType.StringType })
                                     ) { backStackEntry ->
                                         val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                                         AdhkarDetailScreen(
                                             folderId = folderId,
                                             onNavigateBack = { navController.popBackStack() }
                                         )
                                     }
                                     composable("testers") {
                                         TestersScreen(onBack = { navController.popBackStack() })
                                     }
                                     composable("hadith") {
                                         io.github.sammynashed.emran.ui.screens.HadithLibraryScreen(
                                             onBack = { navController.popBackStack() },
                                             onOpenBook = { navController.navigate("hadith_book/$it") },
                                             onSearch = { navController.navigate("hadith_search") },
                                             onBookmarks = { navController.navigate("bookmarks?tab=adhkar") }
                                         )
                                     }
                                     composable("hadith_book/{book}") { entry ->
                                         val book = entry.arguments?.getString("book") ?: ""
                                         io.github.sammynashed.emran.ui.screens.HadithBookScreen(
                                             bookId = book,
                                             onBack = { navController.popBackStack() },
                                             onOpenChapter = { navController.navigate("hadith_chapter/$book/$it") }
                                         )
                                     }
                                     composable(
                                         "hadith_chapter/{book}/{chapter}?n={n}",
                                         arguments = listOf(
                                             androidx.navigation.navArgument("chapter") { type = androidx.navigation.NavType.IntType },
                                             androidx.navigation.navArgument("n") { type = androidx.navigation.NavType.IntType; defaultValue = -1 }
                                         )
                                     ) { entry ->
                                         val n = entry.arguments?.getInt("n") ?: -1
                                         io.github.sammynashed.emran.ui.screens.HadithChapterScreen(
                                             bookId = entry.arguments?.getString("book") ?: "",
                                             chapterId = entry.arguments?.getInt("chapter") ?: 0,
                                             focusNumber = n.takeIf { it >= 0 },
                                             onBack = { navController.popBackStack() }
                                         )
                                     }
                                     composable("hadith_search") {
                                         io.github.sammynashed.emran.ui.screens.HadithSearchScreen(
                                             onBack = { navController.popBackStack() },
                                             onOpenHadith = { book, chapter, number ->
                                                 navController.navigate("hadith_chapter/$book/$chapter?n=$number")
                                             },
                                             onOpenDhikr = { navController.navigate("adhkar_detail/$it") }
                                         )
                                     }
                                     composable(
                                         "bookmarks?tab={tab}",
                                         arguments = listOf(androidx.navigation.navArgument("tab") { type = androidx.navigation.NavType.StringType; defaultValue = "quran" }),
                                     ) { entry ->
                                         io.github.sammynashed.emran.ui.screens.BookmarksScreen(
                                             initialTab = entry.arguments?.getString("tab") ?: "quran",
                                             onBack = { navController.popBackStack() },
                                             onOpenAyah = { surah, ayah ->
                                                 // The reader lives in the Qur'an tab: switch to it, then open the ayah.
                                                 activeTabRoute = "quran"
                                                 navController.popBackStack("home", false)
                                                 quranViewModel.openAyah(surah, ayah)
                                             },
                                             onOpenHadith = { book, chapter, number ->
                                                 navController.navigate("hadith_chapter/$book/$chapter?n=$number")
                                             },
                                             onOpenDhikr = { navController.navigate("adhkar_detail/$it") }
                                         )
                                     }
                                     composable("more") {
                                        MoreScreen(
                                            onBack = { navController.popBackStack() },
                                            onOpenAdhkar = { navController.navigate("adhkar") { launchSingleTop = true } },
                                            onOpenHadith = { navController.navigate("hadith") { launchSingleTop = true } },
                                            onOpenSaved = { navController.navigate("bookmarks?tab=quran") { launchSingleTop = true } },
                                            onOpenReciters = { navController.navigate("reciters") { launchSingleTop = true } },
                                        )
                                    }
                                    composable("quran_player") {
                                        io.github.sammynashed.emran.ui.screens.QuranPlayerScreen(onBack = { navController.popBackStack() })
                                    }
                                    composable("reciters") {
                                        io.github.sammynashed.emran.ui.screens.RecitersScreen(
                                            onBack = { navController.popBackStack() },
                                        )
                                    }

                                     composable("prayer_times") {
                                        io.github.sammynashed.emran.ui.screens.PrayerTimesScreen(
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable("ayah/{surah}/{ayah}") { entry ->
                                        AyahExplainScreen(
                                            surah = entry.arguments?.getString("surah")?.toIntOrNull() ?: 55,
                                            ayah = entry.arguments?.getString("ayah")?.toIntOrNull() ?: 13,
                                            onBack = { navController.popBackStack() },
                                            onChooseAnother = { navController.navigate("ayah_pick") { launchSingleTop = true } },
                                        )
                                    }
                                    composable("ayah_pick") {
                                        val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
                                        io.github.sammynashed.emran.ui.screens.AyahPickerScreen(
                                            onBack = { navController.popBackStack() },
                                            onPick = { s, a ->
                                                // The chosen ayah is today's ayah everywhere (Home, widget) until midnight.
                                                io.github.sammynashed.emran.data.DailyAyat.pick(appContext, s, a)
                                                lifecycleScope.launch { runCatching { io.github.sammynashed.emran.glance.WidgetUpdater.updateAllWidgets(appContext) } }
                                                navController.popBackStack("home", false)
                                                navController.navigate("ayah/$s/$a") { launchSingleTop = true }
                                            },
                                        )
                                    }
                                    composable("dhikr") {
                                        DhikrScreen(
                                             onBack = { navController.popBackStack() }
                                         )
                                    }

                                    composable("compass") {
                                        QiblaCompassScreen(
                                             viewModel = viewModel,
                                             onBack = { navController.popBackStack() }
                                         )
                                    }

                                }

                                if (!isLandscape) {
                                    // FLOATING iOS LIQUID GLASS DOCK OVERLAY
                                    val dockAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (dockVisible) 1f else 0f,
                                        animationSpec = androidx.compose.animation.core.tween(500),
                                        label = "DockAlpha"
                                    )
                                    
                                    if (dockAlpha > 0f) {
                                        val ar = currentLanguage == "ar"
                                        GlassDock(
                                            tabs = listOf(
                                                DockTab(
                                                    "home", if (ar) "الرئيسية" else "Home",
                                                    Icons.Outlined.Home, Icons.Filled.Home,
                                                    Modifier.testTag("nav_home"),
                                                ),
                                                DockTab(
                                                    "quran", if (ar) "القرآن" else "Quran",
                                                    Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook,
                                                    Modifier.testTag("nav_quran"),
                                                ),
                                                DockTab(
                                                    "finetune", if (ar) "الإعدادات" else "Settings",
                                                    Icons.Outlined.Settings, Icons.Filled.Settings,
                                                    Modifier.testTag("nav_finetune"),
                                                ),
                                            ),
                                            selectedKey = activeTabRoute,
                                            onSelect = { key ->
                                                activeTabRoute = key
                                                if (currentRoute != "home") navController.popBackStack("home", false)
                                            },
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                                                .graphicsLayer(alpha = dockAlpha),
                                        )
                                    } // End if (dockAlpha > 0f)
                            } // End if (!isLandscape)
                        } // End inner Box (content weight 1f) (Wait, no, the Box before NavHost)
                    } // row closing
                } // scaffold closing

                // One time, right after setup: why the adhan might not ring on some phones, and the one switch
                // that fixes it, in plain words. Only shown on brands that have such a switch.
                var showAlertsGuide by remember {
                    mutableStateOf(
                        SkyDebug.forceGuide ||
                            io.github.sammynashed.emran.service.NotificationHealth.guideNeeded(context),
                    )
                }
                if (showAlertsGuide) {
                    AlertsGuide(ar = currentLanguage == "ar", onDone = { showAlertsGuide = false; SkyDebug.forceGuide = false })
                }
                // The splash sits on top of Home inside Home's own sky, and paints that same sky texture,
                // so there is one sky to render and fading the splash out reveals Home with no seam.
                if (showSplash) {
                    SplashScreen(onSplashFinished = { showSplash = false })
                }
                
                

                // A small update file opened from Telegram (or picked in Settings) shows over any screen.
                io.github.sammynashed.emran.ui.screens.PatchUpdateHost(ar = currentLanguage == "ar")
                } // my inner box closing
            } // my inner composition local provider
            } // else closing
                // First run: setup has no sky of its own, so the splash brings one.
                if (showSplash && (!isOnboardingCompleted || SkyDebug.forceOnboarding)) {
                    SkyHost(viewModel = viewModel) {
                        SplashScreen(onSplashFinished = { showSplash = false })
                    }
                }
            } // MyApplicationTheme closing
        } // outer CompositionLocalProvider closing
    } // setContent closing
} // onCreate closing

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDesignDebugExtras(intent)
        takeUpdateFile(intent)
    }

    /** An update file handed over by Telegram ("Open with") or by a share. */
    private fun takeUpdateFile(intent: android.content.Intent?) {
        val uri = when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.data
            android.content.Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            }
            else -> null
        } ?: return
        if (uri.scheme != "content" && uri.scheme != "file") return
        io.github.sammynashed.emran.data.update.PatchUpdate.incoming.value = uri
    }

    private var skyTimelapse: kotlinx.coroutines.Job? = null

    /** Moves [SkyDebug.minuteOfDay] along (seconds, minute) points, one step per frame. */
    private fun runSkyPath(points: List<Pair<Double, Double>>, clouds: Float) {
        skyTimelapse?.cancel()
        skyTimelapse = lifecycleScope.launch(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
            SkyDebug.clockSpeed = clouds
            try {
                var start = -1L
                val end = points.last().first
                while (true) {
                    val t = androidx.compose.runtime.withFrameNanos { n ->
                        if (start < 0) start = n
                        ((n - start) / 1e9).coerceAtMost(end)
                    }
                    val i = points.indexOfLast { it.first <= t }.coerceIn(0, points.size - 2)
                    val (t0, m0) = points[i]
                    val (t1, m1) = points[i + 1]
                    val f = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 1.0
                    SkyDebug.minuteOfDay = (m0 + (m1 - m0) * f).mod(1440.0)
                    if (t >= end) break
                }
            } finally {
                SkyDebug.clockSpeed = 1f
            }
        }
    }

    /** Lets a developer pin the sky's hour or the glass tier from adb; see [SkyDebug]. */
    private fun applyDesignDebugExtras(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.hasExtra("SKY_DEBUG_MIN")) {
            skyTimelapse?.cancel()
            SkyDebug.minuteOfDay = intent.getIntExtra("SKY_DEBUG_MIN", -1).takeIf { it in 0..1439 }?.toDouble()
        }
        // Sweep the sky's minute from one time to another for promo recordings, with the clouds
        // sped up to match: adb ... --es SKY_TIMELAPSE 240,1320,20[,40]
        // (04:00 -> 22:00 in 20 s, clouds 40x; wraps past midnight)
        intent.getStringExtra("SKY_TIMELAPSE")?.let { spec ->
            val parts = spec.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size in 3..4 && parts[2] > 0) {
                runSkyPath(listOf(0.0 to parts[0].toDouble(), parts[2].toDouble() to parts[1].toDouble()), parts.getOrElse(3) { 40 }.toFloat())
            }
            intent.removeExtra("SKY_TIMELAPSE")
        }
        // The same with its own pacing, e.g. lingering on Maghrib: "clouds|sec:minute,sec:minute,..."
        // adb ... --es SKY_PATH "8|0:240,20:1000,45:1120,55:1290"   (straight lines between the points)
        intent.getStringExtra("SKY_PATH")?.let { spec ->
            val clouds = spec.substringBefore("|", "40").trim().toFloatOrNull() ?: 40f
            val points = spec.substringAfter("|").split(",").mapNotNull { p ->
                val (t, m) = p.split(":").map { it.trim().toDoubleOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
                if (t == null || m == null) null else t to m
            }.sortedBy { it.first }
            if (points.size >= 2) runSkyPath(points, clouds)
            intent.removeExtra("SKY_PATH")
        }
        if (intent.hasExtra("FORCE_GUIDE")) SkyDebug.forceGuide = intent.getBooleanExtra("FORCE_GUIDE", false)
        intent.getStringExtra("QURAN_SEARCH")?.let { SkyDebug.searchQuery = it }
        if (intent.hasExtra("AYAH_INDEX")) SkyDebug.ayahIndex = intent.getIntExtra("AYAH_INDEX", 0)
        if (intent.hasExtra("WIDGET_DUMP")) lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) { runCatching { io.github.sammynashed.emran.glance.WidgetUpdater.dumpPreviews(applicationContext) } }
        if (intent.hasExtra("FORCE_ONBOARDING")) SkyDebug.forceOnboarding = intent.getBooleanExtra("FORCE_ONBOARDING", false)
        // Debug builds only: fire a prayer's alarm path right now, exactly as AlarmManager would
        // (no TEST flag, so the user's bell switch is honoured): adb ... --es FIRE_PRAYER Isha
        if (BuildConfig.DEBUG) intent.getStringExtra("FIRE_PRAYER")?.let {
            sendBroadcast(android.content.Intent(this, io.github.sammynashed.emran.service.PrayerAlarmReceiver::class.java).putExtra("PRAYER_NAME", it))
            intent.removeExtra("FIRE_PRAYER")
        }
        if (BuildConfig.DEBUG) intent.getStringExtra("FIRE_ADHKAR")?.let {
            sendBroadcast(android.content.Intent(this, io.github.sammynashed.emran.service.AdhkarReminderReceiver::class.java).putExtra("ADHKAR_KIND", it))
            intent.removeExtra("FIRE_ADHKAR")
        }
        if (intent.hasExtra("FORCE_FRIDAY")) SkyDebug.forceFriday = intent.getBooleanExtra("FORCE_FRIDAY", false)
        if (intent.hasExtra("LANG_DEBUG")) {
            SkyDebug.language = intent.getStringExtra("LANG_DEBUG")?.takeIf { it == "ar" || it == "en" }
        }
        if (intent.hasExtra("GLASS_TIER")) {
            SkyDebug.glassTier = runCatching { GlassTier.valueOf(intent.getStringExtra("GLASS_TIER").orEmpty()) }.getOrNull()
        }
    }

    override fun onResume() {
        super.onResume()
        // The phone's own 12/24-hour setting may have changed while we were away.
        io.github.sammynashed.emran.data.TimeFormat.ensureLoaded(this)
    }

    override fun onStart() {
        super.onStart()
        try {
            io.github.sammynashed.emran.service.PrayerAlarmReceiver.muteAdhan()
        } catch (e: Exception) {
            // safe catch
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            try {
                if (io.github.sammynashed.emran.service.PrayerAlarmReceiver.isAdhanPlaying()) {
                    io.github.sammynashed.emran.service.PrayerAlarmReceiver.muteAdhan()
                    return true // Consume key event to mute the Adhan
                }
            } catch (e: java.lang.Exception) {
                // safe catch
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun LandscapeSideDock(
    currentRoute: String,
    onNavigateHome: () -> Unit,
    onNavigateQuran: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Landscape: the dock stands up on the side, a glass pill on the sky with the same three tabs.
    val sky = io.github.sammynashed.emran.ui.design.LocalSky.current
    Box(modifier.width(88.dp).fillMaxHeight().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        io.github.sammynashed.emran.ui.design.GlassPanel(cornerRadius = 36.dp) {
            Column(
                Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                io.github.sammynashed.emran.ui.design.EmranMark(36.dp)
                Spacer(Modifier.height(4.dp))
                listOf(
                    Triple("home", Icons.Outlined.Home to Icons.Filled.Home, onNavigateHome),
                    Triple("quran", Icons.AutoMirrored.Outlined.MenuBook to Icons.AutoMirrored.Filled.MenuBook, onNavigateQuran),
                    Triple("finetune", Icons.Outlined.Settings to Icons.Filled.Settings, onNavigateSettings),
                ).forEach { (key, icons, go) ->
                    val selected = currentRoute == key
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (selected) sky.ink.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(onClick = go)
                            .testTag("nav_${key}_landscape"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (selected) icons.second else icons.first,
                            contentDescription = key,
                            tint = if (selected) sky.ink else sky.inkSoft,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The curve every screen move uses: quick off the mark, long soft landing (Material "emphasized decelerate"). */
private val NavEase = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
