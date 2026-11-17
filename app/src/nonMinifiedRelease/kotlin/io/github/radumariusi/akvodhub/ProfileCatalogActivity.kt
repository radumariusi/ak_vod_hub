package io.github.radumariusi.akvodhub

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.radumariusi.akvodhub.data.catalog.CatalogIndexer
import io.github.radumariusi.akvodhub.data.db.AkVodDatabase
import io.github.radumariusi.akvodhub.data.db.PlaylistSourceEntity
import io.github.radumariusi.akvodhub.data.db.PlaylistType
import io.github.radumariusi.akvodhub.data.playlist.PlaylistImporter
import io.github.radumariusi.akvodhub.data.security.SecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Date sintetice numai în build-ul generatorului, înaintea colectării profilului. */
@AndroidEntryPoint
class ProfileCatalogActivity : ComponentActivity() {
    @Inject lateinit var database: AkVodDatabase

    @Inject lateinit var cipher: SecretCipher

    @Inject lateinit var importer: PlaylistImporter

    @Inject lateinit var indexer: CatalogIndexer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this)
        setContentView(status)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val playlist = filesDir.resolve("catalog-profil.m3u")
                playlist.bufferedWriter().use { writer ->
                    writer.appendLine("#EXTM3U")
                    repeat(192) { index ->
                        val name = "${'A' + index / 26}${'A' + index % 26}"
                        writer.appendLine("#EXTINF:-1 group-title=\"Filme\",Catalog Profil $name")
                        writer.appendLine("https://example.invalid/movie/$index.mp4")
                    }
                }
                val id =
                    database.playlistDao().insertSource(
                        PlaylistSourceEntity(
                            name = "Catalog de profilare",
                            type = PlaylistType.M3U_FILE,
                            label = "Catalog de profilare",
                            location = cipher.encrypt(Uri.fromFile(playlist).toString()),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                importer.import(id) {}
                indexer.refresh()
            }
            status.text = "Catalog de profilare pregătit"
        }
    }
}
