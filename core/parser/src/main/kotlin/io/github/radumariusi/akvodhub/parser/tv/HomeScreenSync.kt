package io.github.radumariusi.akvodhub.parser.tv

/** Un rând deja publicat de aplicație pe ecranul principal (Watch Next sau canal): ID-ul nostru și al sistemului. */
data class PublishedRow(
    val key: String,
    val rowId: Long,
    /** false = utilizatorul l-a scos de pe ecranul principal. */
    val browsable: Boolean,
)

/** Ce trebuie făcut ca rândurile publicate să fie exact cele dorite. */
data class SyncPlan(
    val insert: List<String>,
    /** Cheie → ID-ul rândului sistemului, de actualizat. */
    val update: Map<String, Long>,
    val delete: List<Long>,
)

/**
 * Sincronizarea cu ecranul principal Android TV, ca diferență (fără să ștergem și să rescriem tot la fiecare
 * salvare a progresului). Ce a scos utilizatorul din „Watch Next” nu mai punem la loc cât timp titlul rămâne
 * în „Continuă vizionarea”; dacă dispare de acolo (terminat), rândul se șterge și poate reveni mai târziu.
 */
object HomeScreenSync {
    fun plan(
        wanted: List<String>,
        published: List<PublishedRow>,
    ): SyncPlan {
        val wantedSet = wanted.toSet()
        val byKey = HashMap<String, PublishedRow>()
        val delete = ArrayList<Long>()
        for (row in published) {
            // Rânduri dublate (ex. după o eroare): păstrăm unul singur.
            if (row.key !in wantedSet || byKey.containsKey(row.key)) {
                delete += row.rowId
            } else {
                byKey[row.key] = row
            }
        }
        val insert = ArrayList<String>()
        val update = LinkedHashMap<String, Long>()
        for (key in wanted.distinct()) {
            val row = byKey[key]
            when {
                row == null -> insert += key
                row.browsable -> update[key] = row.rowId
                else -> Unit
            }
        }
        return SyncPlan(insert, update, delete)
    }
}
