package com.tien.core.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tien.core.domain.model.Task

/**
 * How close a task is to its deadline.
 *
 * This is the app's signature idea: urgency is a *visual* property, so the
 * agenda can be read at a glance without parsing a single date. Deriving it
 * once, here, keeps every surface that renders a task in agreement.
 */
enum class Urgency {
    /** Finished. Recedes — done work should not compete for attention. */
    DONE,

    /** Deadline has passed. */
    OVERDUE,

    /** Due before the day is out. */
    TODAY,

    /** Due within the next 24 hours. */
    SOON,

    /** Comfortably ahead. */
    SCHEDULED;

    companion object {
        /**
         * Classifies [task] against [nowEpochSeconds] and [todayEndEpochSeconds]
         * (the exclusive end of the current calendar day).
         *
         * Order matters: the checks run most-urgent first, so a task that is both
         * overdue and due today resolves to OVERDUE.
         */
        fun of(
            task: Task,
            nowEpochSeconds: Long,
            todayEndEpochSeconds: Long
        ): Urgency = when {
            task.isDone -> DONE
            task.dueAt < nowEpochSeconds -> OVERDUE
            task.dueAt < todayEndEpochSeconds -> TODAY
            task.isDueSoon(nowEpochSeconds) -> SOON
            else -> SCHEDULED
        }
    }
}

/**
 * Colour roles Material's [androidx.compose.material3.ColorScheme] has no slot
 * for, exposed through the theme rather than hard-coded at call sites.
 */
@Immutable
data class TienExtendedColors(
    val overdue: Color,
    val overdueContainer: Color,
    val today: Color,
    val todayContainer: Color,
    val scheduled: Color,
    val scheduledContainer: Color,
    val muted: Color,
    val hairline: Color
) {
    /** Foreground for the urgency rail and priority text. */
    fun accentFor(urgency: Urgency): Color = when (urgency) {
        Urgency.OVERDUE -> overdue
        Urgency.TODAY -> today
        Urgency.SOON -> today
        Urgency.SCHEDULED -> scheduled
        Urgency.DONE -> muted
    }

    /** Low-emphasis background pairing for the same role. */
    fun containerFor(urgency: Urgency): Color = when (urgency) {
        Urgency.OVERDUE -> overdueContainer
        Urgency.TODAY -> todayContainer
        Urgency.SOON -> todayContainer
        Urgency.SCHEDULED -> scheduledContainer
        Urgency.DONE -> hairline
    }
}

internal val LightExtendedColors = TienExtendedColors(
    overdue = Clay600,
    overdueContainer = Clay100,
    today = Ochre600,
    todayContainer = Ochre100,
    scheduled = Moss600,
    scheduledContainer = Moss100,
    muted = Graphite300,
    hairline = PaperEdge
)

internal val DarkExtendedColors = TienExtendedColors(
    overdue = ClayDark,
    overdueContainer = Color(0xFF43201A),
    today = OchreDark,
    todayContainer = Color(0xFF3D2C10),
    scheduled = MossDark,
    scheduledContainer = Color(0xFF23301A),
    muted = Graphite400,
    hairline = Graphite700
)

/**
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the value only
 * changes when the whole theme changes, so there is no benefit to tracking
 * reads — and a real cost to doing so on every card.
 */
val LocalTienExtendedColors = staticCompositionLocalOf { LightExtendedColors }
