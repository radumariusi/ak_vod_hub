package io.github.radumariusi.akvodhub.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.radumariusi.akvodhub.BuildConfig
import io.github.radumariusi.akvodhub.data.update.AppUpdate
import io.github.radumariusi.akvodhub.data.update.UpdateCheckResult
import io.github.radumariusi.akvodhub.data.update.UpdateChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(
        val update: AppUpdate,
    ) : UpdateState

    data class Downloading(
        val update: AppUpdate,
        val progress: Float,
    ) : UpdateState

    data class Installing(
        val update: AppUpdate,
    ) : UpdateState

    data class NeedsPermission(
        val update: AppUpdate,
    ) : UpdateState

    data class Failed(
        val message: String,
    ) : UpdateState
}

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        application: Application,
        private val checker: UpdateChecker,
    ) : AndroidViewModel(application) {
        private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
        val state: StateFlow<UpdateState> = mutableState.asStateFlow()

        /** Dialogul de update apare o singură dată pe pornire; „Mai târziu” îl ascunde. */
        private val mutableShowDialog = MutableStateFlow(false)
        val showDialog: StateFlow<Boolean> = mutableShowDialog.asStateFlow()

        val currentVersion: String = BuildConfig.VERSION_NAME

        private var job: Job? = null

        init {
            viewModelScope.launch {
                InstallEvents.events.drop(1).filterNotNull().collect { event ->
                    if (event.error != null) mutableState.value = UpdateState.Failed(event.error)
                }
            }
        }

        fun checkOnStartup() {
            // Build-urile de test (debug) au alt applicationId, deci nu se actualizează automat.
            if (BuildConfig.DEBUG) return
            check(showDialogIfAvailable = true)
        }

        fun check(showDialogIfAvailable: Boolean = false) {
            if (job?.isActive == true) return
            job =
                viewModelScope.launch {
                    mutableState.value = UpdateState.Checking
                    mutableState.value =
                        when (val result = checker.check(currentVersion)) {
                            is UpdateCheckResult.Available -> {
                                if (showDialogIfAvailable) mutableShowDialog.value = true
                                UpdateState.Available(result.update)
                            }

                            UpdateCheckResult.UpToDate -> {
                                UpdateState.UpToDate
                            }

                            is UpdateCheckResult.Failed -> {
                                UpdateState.Failed(result.message)
                            }
                        }
                }
        }

        fun dismissDialog() {
            mutableShowDialog.value = false
        }

        fun downloadAndInstall(update: AppUpdate) {
            if (job?.isActive == true) return
            val context = getApplication<Application>()
            if (!ApkInstaller.canInstall(context)) {
                mutableState.value = UpdateState.NeedsPermission(update)
                ApkInstaller.openInstallPermissionSettings(context)
                return
            }
            job =
                viewModelScope.launch {
                    try {
                        val dir = File(context.cacheDir, "updates").apply { deleteRecursively() }
                        mutableState.value = UpdateState.Downloading(update, 0f)
                        val apk =
                            checker.download(update, File(dir, "ak-vod-hub-${update.version}.apk")) { progress ->
                                mutableState.value = UpdateState.Downloading(update, progress)
                            }
                        mutableState.value = UpdateState.Installing(update)
                        ApkInstaller.install(context, apk)
                    } catch (e: Exception) {
                        mutableState.value =
                            UpdateState.Failed("Update-ul a eșuat: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
        }
    }
