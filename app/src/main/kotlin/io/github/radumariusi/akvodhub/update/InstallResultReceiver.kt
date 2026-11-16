package io.github.radumariusi.akvodhub.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Primește rezultatul de la PackageInstaller și cere confirmarea utilizatorului când e nevoie. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                InstallEvents.publish(null)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                InstallEvents.publish("Instalarea a eșuat (cod $status). ${message.orEmpty()}".trim())
            }
        }
    }
}

/** Ultimul rezultat de instalare: null = succes; text = eroare. */
object InstallEvents {
    private val mutableEvents = MutableStateFlow<InstallEvent?>(null)
    val events: StateFlow<InstallEvent?> = mutableEvents.asStateFlow()

    fun publish(error: String?) {
        mutableEvents.value = InstallEvent(error, System.nanoTime())
    }
}

data class InstallEvent(
    val error: String?,
    val id: Long,
)
