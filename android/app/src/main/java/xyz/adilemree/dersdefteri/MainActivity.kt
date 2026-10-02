package xyz.adilemree.dersdefteri

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.adilemree.dersdefteri.data.TR
import xyz.adilemree.dersdefteri.ui.AccountRoute
import xyz.adilemree.dersdefteri.ui.ArchivedStudentsRoute
import xyz.adilemree.dersdefteri.ui.DashboardRoute
import xyz.adilemree.dersdefteri.ui.HomeworkFormRoute
import xyz.adilemree.dersdefteri.ui.HomeworkRoute
import xyz.adilemree.dersdefteri.ui.LessonFormRoute
import xyz.adilemree.dersdefteri.ui.LocalNav
import xyz.adilemree.dersdefteri.ui.PaymentFormRoute
import xyz.adilemree.dersdefteri.ui.PaymentReminderRoute
import xyz.adilemree.dersdefteri.ui.PaymentsRoute
import xyz.adilemree.dersdefteri.ui.PaywallRoute
import xyz.adilemree.dersdefteri.ui.QuickLessonRoute
import xyz.adilemree.dersdefteri.ui.RecurringLessonsRoute
import xyz.adilemree.dersdefteri.ui.ScheduleRoute
import xyz.adilemree.dersdefteri.ui.SettingsRoute
import xyz.adilemree.dersdefteri.ui.SignInRoute
import xyz.adilemree.dersdefteri.ui.StudentDetailRoute
import xyz.adilemree.dersdefteri.ui.StudentFormRoute
import xyz.adilemree.dersdefteri.ui.StudentSummaryRoute
import xyz.adilemree.dersdefteri.ui.StudentsRoute
import xyz.adilemree.dersdefteri.ui.TemplateFormRoute
import xyz.adilemree.dersdefteri.ui.UnmarkedLessonsRoute
import xyz.adilemree.dersdefteri.ui.account.AccountScreen
import xyz.adilemree.dersdefteri.ui.account.SignInScreen
import xyz.adilemree.dersdefteri.ui.dashboard.DashboardScreen
import xyz.adilemree.dersdefteri.ui.dashboard.UnmarkedLessonsScreen
import xyz.adilemree.dersdefteri.ui.homework.HomeworkFormScreen
import xyz.adilemree.dersdefteri.ui.homework.HomeworkScreen
import xyz.adilemree.dersdefteri.ui.onboarding.OnboardingScreen
import xyz.adilemree.dersdefteri.ui.payments.PaymentFormScreen
import xyz.adilemree.dersdefteri.ui.payments.PaymentsScreen
import xyz.adilemree.dersdefteri.ui.pro.PaywallScreen
import xyz.adilemree.dersdefteri.ui.schedule.LessonFormScreen
import xyz.adilemree.dersdefteri.ui.schedule.QuickLessonScreen
import xyz.adilemree.dersdefteri.ui.schedule.ScheduleScreen
import xyz.adilemree.dersdefteri.ui.settings.SettingsScreen
import xyz.adilemree.dersdefteri.ui.students.ArchivedStudentsScreen
import xyz.adilemree.dersdefteri.ui.students.PaymentReminderScreen
import xyz.adilemree.dersdefteri.ui.students.StudentDetailScreen
import xyz.adilemree.dersdefteri.ui.students.StudentFormScreen
import xyz.adilemree.dersdefteri.ui.students.StudentSummaryScreen
import xyz.adilemree.dersdefteri.ui.students.StudentsScreen
import xyz.adilemree.dersdefteri.ui.switchTab
import xyz.adilemree.dersdefteri.ui.theme.AppTheme
import xyz.adilemree.dersdefteri.ui.theme.DersDefteriTheme
import xyz.adilemree.dersdefteri.ui.theme.Type
import xyz.adilemree.dersdefteri.ui.weekly.RecurringLessonsScreen
import xyz.adilemree.dersdefteri.ui.weekly.TemplateFormScreen

class MainActivity : ComponentActivity() {
    /// Uygulama yalnızca Türkçe: tarih seçici gibi sistem bileşenleri de
    /// telefonun dilinden bağımsız Türkçe ve Pazartesi başlangıçlı görünür.
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(TR)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = app
        // Defter veritabanından okunana kadar açılış ekranı kalır; boş defter bir an görünmez.
        splash.setKeepOnScreenCondition { container.repository.notebook.value == null }
        setContent {
            DersDefteriTheme {
                CompositionLocalProvider(LocalApp provides container) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val app = LocalApp.current
    val onboarded by app.settings.hasCompletedOnboarding.flow.collectAsStateWithLifecycle()

    // Bildirim izni tanıtım bittikten sonra sorulur; uygulamayı daha görmemiş
    // kullanıcının önüne açılışta bir sistem penceresi çıkmasın.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.notifications.resyncSoon()
    }
    LaunchedEffect(onboarded) {
        if (onboarded && Build.VERSION.SDK_INT >= 33 && !app.settings.askedNotificationPermission.value) {
            app.settings.askedNotificationPermission.value = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(AppTheme.colors.paper)) {
        MainNavigation()
        AnimatedVisibility(visible = !onboarded, enter = fadeIn(), exit = fadeOut()) {
            OnboardingScreen()
        }
    }
}

private data class Tab(val route: Any, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(DashboardRoute, "Özet", Icons.Rounded.GridView),
    Tab(StudentsRoute, "Öğrenciler", Icons.Rounded.School),
    Tab(ScheduleRoute(), "Program", Icons.Rounded.CalendarMonth),
    Tab(PaymentsRoute, "Ödemeler", Icons.Rounded.AccountBalanceWallet),
    Tab(HomeworkRoute, "Ödevler", Icons.Rounded.Checklist),
)

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val c = AppTheme.colors
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val current = when {
        destination == null -> 0
        destination.hasRoute<DashboardRoute>() -> 0
        destination.hasRoute<StudentsRoute>() -> 1
        destination.hasRoute<ScheduleRoute>() -> 2
        destination.hasRoute<PaymentsRoute>() -> 3
        destination.hasRoute<HomeworkRoute>() -> 4
        else -> -1
    }
    val showBar = current >= 0

    CompositionLocalProvider(LocalNav provides nav) {
        Scaffold(
            containerColor = c.paper,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = c.card, tonalElevation = 0.dp) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = index == current,
                                onClick = { if (index != current) nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, tab.title) },
                                label = { Text(tab.title, style = Type.caption2) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.accent,
                                    selectedTextColor = c.accent,
                                    indicatorColor = c.accent.copy(alpha = 0.12f),
                                    unselectedIconColor = c.inkSoft,
                                    unselectedTextColor = c.inkSoft,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = DashboardRoute,
                modifier = if (showBar) Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(WindowInsets.navigationBars) else Modifier,
            ) {
                composable<DashboardRoute> { DashboardScreen() }
                composable<StudentsRoute> { StudentsScreen() }
                composable<ScheduleRoute> { ScheduleScreen(it.toRoute<ScheduleRoute>().date) }
                composable<PaymentsRoute> { PaymentsScreen() }
                composable<HomeworkRoute> { HomeworkScreen() }

                composable<StudentDetailRoute> { StudentDetailScreen(it.toRoute<StudentDetailRoute>().id) }
                composable<StudentFormRoute> { StudentFormScreen(it.toRoute<StudentFormRoute>().id) }
                composable<ArchivedStudentsRoute> { ArchivedStudentsScreen() }
                composable<StudentSummaryRoute> { StudentSummaryScreen(it.toRoute<StudentSummaryRoute>().id) }
                composable<PaymentReminderRoute> { PaymentReminderScreen(it.toRoute<PaymentReminderRoute>().id) }

                composable<LessonFormRoute> {
                    val r = it.toRoute<LessonFormRoute>()
                    LessonFormScreen(r.lessonId, r.studentId, r.date)
                }
                composable<QuickLessonRoute> { QuickLessonScreen(it.toRoute<QuickLessonRoute>().date) }
                composable<UnmarkedLessonsRoute> { UnmarkedLessonsScreen() }
                composable<RecurringLessonsRoute> { RecurringLessonsScreen() }
                composable<TemplateFormRoute> {
                    val r = it.toRoute<TemplateFormRoute>()
                    TemplateFormScreen(r.templateId, r.studentId)
                }

                composable<PaymentFormRoute> {
                    val r = it.toRoute<PaymentFormRoute>()
                    PaymentFormScreen(r.studentId, r.paymentId, r.startWithLessons)
                }
                composable<HomeworkFormRoute> {
                    val r = it.toRoute<HomeworkFormRoute>()
                    HomeworkFormScreen(r.homeworkId, r.studentId)
                }

                composable<SettingsRoute> { SettingsScreen() }
                composable<AccountRoute> { AccountScreen() }
                composable<SignInRoute> { SignInScreen() }
                composable<PaywallRoute> { PaywallScreen() }
            }
        }
    }
}

