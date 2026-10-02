package xyz.adilemree.dersdefteri.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable

// Ekran adresleri. Sekmeler alt çubuktadır; diğerleri üstüne açılır.

@Serializable data object DashboardRoute
@Serializable data object StudentsRoute
@Serializable data class ScheduleRoute(val date: Long? = null)
@Serializable data object PaymentsRoute
@Serializable data object HomeworkRoute

@Serializable data class StudentDetailRoute(val id: String)
@Serializable data class StudentFormRoute(val id: String? = null)
@Serializable data object ArchivedStudentsRoute
@Serializable data class StudentSummaryRoute(val id: String)
@Serializable data class PaymentReminderRoute(val id: String)

@Serializable data class LessonFormRoute(val lessonId: String? = null, val studentId: String? = null, val date: Long? = null)
@Serializable data class QuickLessonRoute(val date: Long? = null)
@Serializable data object UnmarkedLessonsRoute
@Serializable data object RecurringLessonsRoute
@Serializable data class TemplateFormRoute(val templateId: String? = null, val studentId: String? = null)

@Serializable data class PaymentFormRoute(
    val studentId: String? = null,
    val paymentId: String? = null,
    val startWithLessons: Boolean = false,
)
@Serializable data class HomeworkFormRoute(val homeworkId: String? = null, val studentId: String? = null)

@Serializable data object SettingsRoute
@Serializable data object AccountRoute
@Serializable data object SignInRoute
@Serializable data object PaywallRoute

val LocalNav = staticCompositionLocalOf<NavHostController> { error("NavController yok") }

/// Sekme değiştirir: her sekme kendi yığınını korur.
fun NavHostController.switchTab(route: Any, restore: Boolean = true) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

/// Bulunduğu ekranı kapatır (geri).
fun NavHostController.close() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
fun nav(): NavHostController = LocalNav.current
