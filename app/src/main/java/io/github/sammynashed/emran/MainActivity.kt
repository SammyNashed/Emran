package io.github.sammynashed.emran

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import io.github.sammynashed.emran.ui.tour.tourTarget
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import io.github.sammynashed.emran.ui.screens.*
import io.github.sammynashed.emran.ui.theme.*
import io.github.sammynashed.emran.ui.components.cosmicAuroraBackground
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import io.github.sammynashed.emran.ui.viewmodel.QuranViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

val LocalDockVisible = compositionLocalOf { mutableStateOf(true) }

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels()
    private val quranViewModel: QuranViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            val appIconPreset by viewModel.appIconPreset.collectAsState()
            val currentLanguage by viewModel.currentLanguage.collectAsState()

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
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        appIconPreset = appIconPreset,
                        onSplashFinished = { showSplash = false }
                    )
                } else if (!isOnboardingCompleted) {
                    OnboardingScreen(
                        currentLanguage = currentLanguage,
                        onLanguageSelect = { lang -> viewModel.setCurrentLanguage(lang) },
                        onFinished = { preset, location, notificationsEnabled, method, school, adhanSound ->
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
                        },
                        onThemePreview = { preset ->
                            viewModel.themePreset.value = preset
                        },
                        onThemeConfirmed = { preset ->
                            viewModel.setThemePreset(preset)
                        }
                    )
                } else {
                    val tourManager = remember(preferences) { io.github.sammynashed.emran.ui.tour.TourManager(preferences) }
                    val tourRegistry = remember { io.github.sammynashed.emran.ui.tour.TourTargetRegistry() }

                    LaunchedEffect(Unit) {
                        tourManager.startTour()
                    }

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
                    val isTourActive by tourManager.isActive.collectAsState()
                    val tourStepIndex by tourManager.currentStepIndex.collectAsState()

                    LaunchedEffect(activeTabRoute, currentRoute, tourStepIndex, isTourActive) {
                        if (isTourActive) {
                            val step = tourManager.steps.getOrNull(tourStepIndex)
                            if (step != null && step.type == io.github.sammynashed.emran.ui.tour.TourStepType.INTERACTIVE) {
                                if (step.id == "nav_quran" && activeTabRoute == "quran") {
                                    tourManager.nextStep()
                                } else if (step.id == "nav_finetune" && activeTabRoute == "finetune") {
                                    tourManager.nextStep()
                                } else if (step.id == "today_prayers_widget_card" && currentRoute == "prayer_times") {
                                    tourManager.nextStep()
                                } else if (step.id == "home_widget_qibla" && currentRoute == "compass") {
                                    tourManager.nextStep()
                                } else if (step.id == "home_widget_dhikr" && currentRoute == "dhikr") {
                                    tourManager.nextStep()
                                } else if (step.id == "home_widget_adhkar" && currentRoute == "adhkar") {
                                    tourManager.nextStep()
                                } else if (step.id == "home_widget_hadith" && currentRoute == "hadith") {
                                    tourManager.nextStep()
                                } else if (step.id == "adhkar_first_folder" && currentRoute.startsWith("adhkar_detail")) {
                                    tourManager.nextStep()
                                } else if (step.id == "sub_screen_back_button" && currentRoute == "home") {
                                    tourManager.nextStep()
                                } else if (step.id == "sub_screen_back_button" && step.targetRoute == "adhkar_detail" && currentRoute == "adhkar") {
                                    tourManager.nextStep()
                                }
                            }
                        }
                    }
                    LaunchedEffect(tourStepIndex, isTourActive) {
                        if (isTourActive) {
                            val step = tourManager.steps.getOrNull(tourStepIndex)
                            // True when the user skipped the previous step instead of completing it.
                            val skippedHere = tourManager.consumeSkipFlag()
                            if (step != null) {
                                val expectedTab = when (step.targetRoute) {
                                    "quran" -> "quran"
                                    "finetune" -> "finetune"
                                    else -> "home"
                                }
                                if (activeTabRoute != expectedTab) {
                                    activeTabRoute = expectedTab
                                }

                                val stepRoute = step.targetRoute
                                if (currentRoute != stepRoute) {
                                    // Only auto-navigate to the correct screen if we are not on an interactive click-to-transition step
                                    val isInteractiveTransition = !skippedHere && step.type == io.github.sammynashed.emran.ui.tour.TourStepType.INTERACTIVE && 
                                        (stepRoute == "home" || stepRoute == "adhkar" || stepRoute == "adhkar_detail")
                                    
                                    if (!isInteractiveTransition) {
                                        when (stepRoute) {
                                            "home" -> {
                                                if (currentRoute != "home") {
                                                    // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                                }
                                            }
                                            "prayer_times" -> navController.navigate("prayer_times") { launchSingleTop = true }
                                            "compass" -> navController.navigate("compass") { launchSingleTop = true }
                                            "dhikr" -> navController.navigate("dhikr") { launchSingleTop = true }
                                            "adhkar" -> navController.navigate("adhkar") { launchSingleTop = true }
                                            // "Featured" was a folder that no longer exists; the first folder is "morning".
                                            "adhkar_detail" -> navController.navigate("adhkar_detail/morning") { launchSingleTop = true }
                                            "hadith" -> navController.navigate("hadith") { launchSingleTop = true }
                                            // The Quran and Settings tabs live inside the home route: leave any sub-screen.
                                            "quran", "finetune" -> if (currentRoute != "home") {
                                                // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(route) {
                        if (route == "PRAYER_TAB") {
                            activeTabRoute = "home"
                            navController.navigate("prayer_times") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current

                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                    CompositionLocalProvider(
                        io.github.sammynashed.emran.ui.tour.LocalTourRegistry provides tourRegistry,
                        io.github.sammynashed.emran.ui.tour.LocalTourManager provides tourManager
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (themePreset == "BLACK_STONE") {
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
                                    enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) },
                                    exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) },
                                    popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) },
                                    popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) }
                                ) {
                                    composable("home") {
                                        when (activeTabRoute) {
                                            "home" -> {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                HomeScreen(
                                                    viewModel = viewModel,
                                                    onNavigateToPrayers = {
                                                        navController.navigate("prayer_times") {
                                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    onNavigateToQibla = {
                                                        navController.navigate("compass") {
                                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    onNavigateToDhikr = {
                                                        navController.navigate("dhikr") {
                                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    onNavigateToAdhkar = {
                                                        navController.navigate("adhkar") {
                                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    onNavigateToHadith = {
                                                        navController.navigate("hadith") {
                                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    onNavigateToBookmarks = {
                                                        navController.navigate("bookmarks") {
                                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    onOpenHadith = { book, chapter, number ->
                                                        navController.navigate("hadith_chapter/$book/$chapter?n=$number")
                                                    },
                                                 )
                                                 }
                                            }
                                            "quran" -> {
                                                QuranScreen(viewModel = quranViewModel)
                                            }
                                            "finetune" -> {
                                                FineTuneScreen(
                                                    viewModel = viewModel,
                                                    onReplayTour = {
                                                        activeTabRoute = "home"
                                                        tourManager.restartTour()
                                                    },
                                                    onOpenAcknowledgements = { navController.navigate("testers") }
                                                )
                                            }
                                        }
                                    }
                                     composable("adhkar") {
                                         AdhkarScreen(
                                             onNavigateBack = { navController.popBackStack() },
                                             onNavigateToCategory = { navController.navigate("adhkar_category/$it") },
                                             onSearch = { navController.navigate("hadith_search") },
                                             onBookmarks = { navController.navigate("bookmarks") },
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
                                             onBookmarks = { navController.navigate("bookmarks") }
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
                                     composable("bookmarks") {
                                         io.github.sammynashed.emran.ui.screens.BookmarksScreen(
                                             onBack = { navController.popBackStack() },
                                             onOpenHadith = { book, chapter, number ->
                                                 navController.navigate("hadith_chapter/$book/$chapter?n=$number")
                                             },
                                             onOpenDhikr = { navController.navigate("adhkar_detail/$it") }
                                         )
                                     }
                                     composable("prayer_times") {
                                        io.github.sammynashed.emran.ui.screens.PrayerTimesScreen(
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() }
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
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .graphicsLayer(alpha = dockAlpha)
                                                .background(Color(0xFF040307).copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(36.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(36.dp))
                                                .testTag("app_bottom_nav"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(72.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                            FloatingDockItem(
                                                selected = activeTabRoute == "home",
                                                onClick = {
                                                    activeTabRoute = "home"
                                                    if (currentRoute != "home") {
                                                        // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                                    }
                                                },
                                                icon = Icons.Outlined.Home,
                                                selectedIcon = Icons.Filled.Home,
                                                label = if (currentLanguage == "ar") "الرئيسية" else "Home",
                                                accentColor = AppTheme.colors.primary,
                                                modifier = Modifier.testTag("nav_home").tourTarget("nav_home")
                                            )

                                            QuranDockItem(
                                                selected = activeTabRoute == "quran",
                                                onClick = {
                                                    activeTabRoute = "quran"
                                                    if (currentRoute != "home") {
                                                        // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                                    }
                                                },
                                                modifier = Modifier.testTag("nav_quran").tourTarget("nav_quran")
                                            )

                                            FloatingDockItem(
                                                selected = activeTabRoute == "finetune",
                                                onClick = {
                                                    activeTabRoute = "finetune"
                                                    if (currentRoute != "home") {
                                                        // Plain pop back to Home. The old popUpTo{saveState}+restoreState call restored the very
                                    // sub-screen it had just popped, so "Home" could land on a stale screen.
                                    navController.popBackStack("home", false)
                                                    }
                                                },
                                                icon = Icons.Outlined.Settings,
                                                selectedIcon = Icons.Filled.Settings,
                                                label = if (currentLanguage == "ar") "الإعدادات" else "Settings",
                                                accentColor = AppTheme.colors.secondary,
                                                modifier = Modifier.testTag("nav_finetune").tourTarget("nav_finetune")
                                            )
                                        } // End Row
                                    } // End Box (dock layout)
                                } // End if (dockAlpha > 0f)
                            } // End if (!isLandscape)
                        } // End inner Box (content weight 1f) (Wait, no, the Box before NavHost)
                    } // row closing
                } // scaffold closing
                
                io.github.sammynashed.emran.ui.tour.TourOverlay(tourManager = tourManager, registry = tourRegistry)
                

                } // my inner box closing
            } // my inner composition local provider
            } // else closing
            } // MyApplicationTheme closing
        } // outer CompositionLocalProvider closing
    } // setContent closing
} // onCreate closing

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
private fun RowScope.QuranDockItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "QuranScale"
    )

    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "QuranTextColor"
    )

    val primaryColor = AppTheme.colors.primary

    Box(
        modifier = modifier
            .weight(1.0f)
            .height(72.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        // Center peak glowing indicator bar
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 0.dp)
                    .width(28.dp)
                    .height(3.dp)
                    .background(primaryColor, androidx.compose.foundation.shape.RoundedCornerShape(
                        bottomStart = 3.dp, bottomEnd = 3.dp, topStart = 0.dp, topEnd = 0.dp
                    ))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val quranIcon = rememberQuranIcon(filled = selected)

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = quranIcon,
                    contentDescription = "Quran",
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            val lang = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current
            val label = if (lang == "ar") "القرآن" else "Qur'an"
            Text(
                text = label,
                color = textColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

class ArchShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, size.width / 2f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.width),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(size.width, size.height)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
fun rememberQuranIcon(filled: Boolean): ImageVector {
    return remember(filled) {
        ImageVector.Builder(
            name = "Quran",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                fill = if (filled) androidx.compose.ui.graphics.SolidColor(Color.Black) else null
            ) {
                // Book left
                moveTo(12f, 15f)
                lineTo(12f, 8f)
                quadTo(7.5f, 5.5f, 2f, 8f)
                lineTo(2f, 13.5f)
                quadTo(7.5f, 11f, 12f, 15f)
                
                // Book right
                moveTo(12f, 8f)
                quadTo(16.5f, 5.5f, 22f, 8f)
                lineTo(22f, 13.5f)
                quadTo(16.5f, 11f, 12f, 15f)
            }
            path(
                stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                fill = null
            ) {
                // Rehal legs
                moveTo(12f, 15f)
                lineTo(7f, 21f)
                moveTo(12f, 15f)
                lineTo(17f, 21f)
                moveTo(9f, 18.5f)
                lineTo(15f, 18.5f)
            }
        }.build()
    }
}

@Composable
private fun RowScope.FloatingDockItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)
    )

    val iconColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) accentColor else Color.White.copy(alpha = 0.45f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
    )

    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
    )

    Box(
        modifier = modifier
            .weight(1.0f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 0.dp)
                    .width(28.dp)
                    .height(3.dp)
                    .background(accentColor, androidx.compose.foundation.shape.RoundedCornerShape(
                        bottomStart = 3.dp, bottomEnd = 3.dp, topStart = 0.dp, topEnd = 0.dp
                    ))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

class NavDomeShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val px = density.density
        val width = size.width
        val height = size.height
        val cornerRadius = 28f * px
        
        // Beautiful side elevation height layout
        val sideTopY = 24f * px 

        val path = androidx.compose.ui.graphics.Path().apply {
            // Start at bottom-left corner and walk clockwise
            moveTo(cornerRadius, height)
            
            // Bottom edge
            lineTo(width - cornerRadius, height)
            
            // Bottom Right Corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(width - cornerRadius * 2, height - cornerRadius * 2, width, height),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            
            // Right edge up to the side top height
            lineTo(width, sideTopY + cornerRadius)
            
            // Top Right Corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(width - cornerRadius * 2, sideTopY, width, sideTopY + cornerRadius * 2),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            
            // Masterful continuous wave Bezier curve peaking at (width/2, 0)
            val midX = width / 2f
            cubicTo(
                x1 = width - cornerRadius - (midX - cornerRadius) * 0.45f,
                y1 = sideTopY,
                x2 = midX + 32f * px,
                y2 = 0f,
                x3 = midX,
                y3 = 0f
            )
            
            // Symmetrical drop to top-left sides
            cubicTo(
                x1 = midX - 32f * px,
                y1 = 0f,
                x2 = cornerRadius + (midX - cornerRadius) * 0.45f,
                y2 = sideTopY,
                x3 = cornerRadius,
                y3 = sideTopY
            )
            
            // Top Left Corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, sideTopY, cornerRadius * 2, sideTopY + cornerRadius * 2),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            
            // Left edge down
            lineTo(0f, height - cornerRadius)
            
            // Bottom Left Corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, height - cornerRadius * 2, cornerRadius * 2, height),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
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
    Box(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(Color(0xFF040307).copy(alpha = 0.94f))
            .border(
                width = 1.dp,
                color = AppTheme.colors.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App short icon/logo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ع",
                    color = AppTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation Items
            val homeSelected = currentRoute == "home"
            val homeScale by animateFloatAsState(targetValue = if (homeSelected) 1.15f else 1.0f)
            val homeColor = if (homeSelected) AppTheme.colors.primary else Color.White.copy(alpha = 0.45f)
            IconButton(
                onClick = onNavigateHome,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = homeScale, scaleY = homeScale)
                    .testTag("nav_home_landscape")
            ) {
                Icon(
                    imageVector = if (homeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home",
                    tint = homeColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            val quranSelected = currentRoute == "quran"
            val quranScale by animateFloatAsState(targetValue = if (quranSelected) 1.15f else 1.0f)
            val quranColor = if (quranSelected) AppTheme.colors.primary else Color.White.copy(alpha = 0.45f)
            val quranIcon = rememberQuranIcon(filled = quranSelected)
            IconButton(
                onClick = onNavigateQuran,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = quranScale, scaleY = quranScale)
                    .testTag("nav_quran_landscape")
            ) {
                Icon(
                    imageVector = quranIcon,
                    contentDescription = "Quran",
                    tint = quranColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            val setSelected = currentRoute == "finetune"
            val setScale by animateFloatAsState(targetValue = if (setSelected) 1.15f else 1.0f)
            val setColor = if (setSelected) AppTheme.colors.secondary else Color.White.copy(alpha = 0.45f)
            IconButton(
                onClick = onNavigateSettings,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = setScale, scaleY = setScale)
                    .testTag("nav_finetune_landscape")
            ) {
                Icon(
                    imageVector = if (setSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = setColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
