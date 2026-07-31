package com.tien.core.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tien.core.di.AppContainer
import com.tien.core.ui.feature.agenda.AgendaViewModel
import com.tien.core.ui.feature.notes.NotesViewModel

/**
 * ViewModel factories wired from the [AppContainer].
 *
 * Constructor injection keeps the ViewModels free of Android context and makes
 * them unit-testable with fakes — the previous `AndroidViewModel` reached for an
 * `Application` purely to resolve a database path.
 */
object TienViewModelFactory {

    fun notes(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            NotesViewModel(
                noteRepository = container.noteRepository,
                preferencesRepository = container.preferencesRepository
            )
        }
    }

    fun agenda(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AgendaViewModel(
                taskRepository = container.taskRepository,
                preferencesRepository = container.preferencesRepository,
                clock = container.clock,
                labels = container.dateTimeLabels
            )
        }
    }

    fun settings(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(container.preferencesRepository)
        }
    }
}

/** Unused today, but keeps the `initializer` lambdas honest about their inputs. */
internal val EmptyExtras: CreationExtras = CreationExtras.Empty
