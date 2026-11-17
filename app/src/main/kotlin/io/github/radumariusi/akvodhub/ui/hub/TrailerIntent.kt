package io.github.radumariusi.akvodhub.ui.hub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.radumariusi.akvodhub.parser.catalog.Trailer

/** Pornim direct intent-ul: nu depindem de vizibilitatea pachetelor pe Android 11. */
fun openTrailer(context: Context, videoId: String) {
    if (!Trailer.isValidId(videoId)) return
    val uri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
    for (target in listOf("com.google.android.youtube.tv", "com.google.android.youtube", null)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { if (target != null) setPackage(target) })
            return
        } catch (_: ActivityNotFoundException) {
            // Încercăm varianta următoare; ultimul intent permite și browser-ul instalat.
        } catch (_: SecurityException) {
            // Unele launchere restricționează aplicația explicită, dar permit intent-ul generic.
        }
    }
    Toast.makeText(context, "Nu există o aplicație pentru deschiderea trailerului.", Toast.LENGTH_LONG).show()
}
