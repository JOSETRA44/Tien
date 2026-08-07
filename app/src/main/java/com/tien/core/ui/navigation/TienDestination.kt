package com.tien.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations.
 *
 * A sealed hierarchy rather than the previous `selectedTab: Int`: an `Int` gave
 * no compile-time guarantee that a screen existed for it, and `if (tab == 0)`
 * checks were spread across the UI with no single place to see the structure.
 *
 * `route` is the Navigation identifier; the icon pair follows the Material
 * convention of filled-when-selected, outlined otherwise.
 */
sealed class TienDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Label for the FAB while this destination is showing. */
    val createLabel: String
) {
    data object Notes : TienDestination(
        route = "notes",
        label = "Notas",
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description,
        createLabel = "Nueva nota"
    )

    data object Agenda : TienDestination(
        route = "agenda",
        label = "Agenda",
        selectedIcon = Icons.Filled.Today,
        unselectedIcon = Icons.Outlined.Today,
        createLabel = "Nueva tarea"
    )

    /** The wall: ideas on paper, arranged in space rather than in a list. */
    data object Board : TienDestination(
        route = "board",
        label = "Pizarra",
        selectedIcon = Icons.Filled.PushPin,
        unselectedIcon = Icons.Outlined.PushPin,
        createLabel = "Clavar idea"
    )

    /** The UNSA aula virtual: real deadlines, from the university itself. */
    data object Dutic : TienDestination(
        route = "dutic",
        label = "Aula",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School,
        // The aula virtual is read-only from here — assignments are published
        // by teachers, so a "create" action would be a button that lies.
        createLabel = "Nueva nota"
    )

    companion object {
        val bottomBarItems = listOf(Notes, Agenda, Board, Dutic)

        /** Destinations where the create FAB means something. */
        val creatable = setOf(Notes.route, Agenda.route, Board.route)

        val startRoute: String = Notes.route

        fun fromRoute(route: String?): TienDestination =
            bottomBarItems.firstOrNull { it.route == route } ?: Notes
    }
}
