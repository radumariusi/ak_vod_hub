# Predare proiect (2026-11-17): AK VOD Hub

Document de predare pentru orice agent (ex. Google Jules) care preia temporar proiectul.
Punct de salvare: tag-ul **`claude-handoff-2026-11-17`** și branch-ul **`claude-snapshot`** (aceeași stare ca `main` la predare).

## 1. Ce e aplicația

Aplicație **Android TV** (merge și pe telefon, pentru test) care primește un playlist IPTV (M3U sau Xtream),
păstrează **doar VOD** (filme + seriale), potrivește titlurile cu **TMDB**, grupează toate sursele aceluiași
titlu (după calitate, apoi limbă) și oferă căutare, descoperire și recomandări **doar din conținutul playlist-ului**.
Subtitrări (și cataloage/liste) prin protocolul **addon Stremio**, aplicația fiind doar client (OpenSubtitles v3 implicit).
Nu facem addon Stremio propriu.

- Interfața e **în română**. Orice text nou pentru utilizator se scrie în română.
- Utilizatorul își pune propria cheie TMDB (nu se comite niciodată).
- Licență GPL-3.0.

## 2. Stack

Kotlin · Compose for TV (`androidx.tv.material3`) · Media3 ExoPlayer + decodor audio FFmpeg · Room + FTS ·
Hilt · WorkManager · OkHttp + kotlinx.serialization · Coil. Versiunile sunt în `gradle/libs.versions.toml`
(AGP 9.x, Kotlin 2.4, Gradle 9.8). **minSdk 23**, TV-ul real al lui Radu are **Android 11**.

## 3. Module

| Modul | Rol |
|---|---|
| `core:parser` | JVM pur, fără Android: parser titluri (`title/`), M3U/Xtream (`playlist/`), potrivire TMDB (`match/`), catalog/grupare surse (`catalog/`), recomandări (`discover/`), subtitrări (`subtitle/`), Stremio (`stremio/`), player helpers, `tv/HomeScreenSync`, `remote/HttpMessage`. **Aici se pune logica testabilă**, cu teste unitare. |
| `core:data` | Room (`db/`), import playlist (`playlist/`, `playlist/xtream`), TMDB (`tmdb/`), `catalog/CatalogIndexer` + `HubRepository`, Stremio client (`stremio/`), descoperire (`discover/`), backup setări (`backup/`), server configurare de pe telefon (`remote/PhoneSetupServer`, port 8686), updater (`update/`), criptare cont (`security/`). |
| `core:player` | Integrare Media3/FFmpeg, alegerea și fallback-ul surselor. |
| `app` | UI Compose (`ui/`, `ui/hub`, `ui/player`, `ui/touch`), ViewModel-uri (`hub/`, `player/`, `playlist/`, `catalog/`), Hilt (`di/`), integrare TV (`tv/`: Watch Next, canal, căutare globală, deep links `akvodhub://title/<id>` și `akvodhub://play/<id>`), updater din GitHub Releases (`update/`). |

Fluxul de date: import playlist → `raw_entry` (titlu/an/calitate/limbi parsate) → `MatchWorker` rulează
`CatalogIndexer` (titluri locale, imediat) și apoi runde de potrivire TMDB → tabelul `title` → ecranele citesc doar din DB.

## 4. Ce s-a făcut

| Etapă | Ce | PR / release |
|---|---|---|
| 0 | Plan (`docs/PLAN.md`) | #1 |
| 1 | Schelet aplicație, CI, release semnat, updater în aplicație | #2, v0.1.3 |
| 2 | Import playlist M3U/Xtream + filtru VOD, refresh zilnic | #3, v0.1.4 |
| 3 | Parser titluri (187 cazuri de test în `core/parser/src/test/resources/testdata/titles.tsv`) | #4, v0.1.5 |
| 4 | Potrivire TMDB + grupare surse | #5 |
| 5 | Hub UI: Acasă, Detalii, Căutare, Filme/Seriale | #6 |
| 6 | Player (Media3 + FFmpeg, fallback surse, progres vizionare) | #7 |
| 7 | Subtitrări prin addon-uri Stremio | #8 |
| 8 | Descoperire și recomandări (istoric, colecții, cataloage Stremio mapate pe playlist) | #9 |
| 9 | Integrare Android TV: Watch Next, canal, căutare globală + vocală, configurare de pe telefon (QR), backup | #10, v0.1.11 |
| — | Suport touch pentru telefon | #11 |
| — | Atingerile chiar funcționează (wrappere touch) | #12, v0.1.13 |
| — | Hub-ul arată playlist-ul imediat după import (indexer înainte de TMDB) | #13, v0.1.14 |
| — | Propuneri etapele 10–15 (`docs/PROPUNERI.md`) | #14 |

Release-uri păstrate acum: **v0.1.14** (ultimul, testat) și v0.1.13.

## 5. Capcane importante (citește înainte de cod)

1. **Touch:** componentele `androidx.tv.material3` (Button, Card, Surface, ListItem, NavigationDrawerItem) **ignoră atingerile**.
   În cod nou importă wrapper-ele din `app/.../ui/touch/TouchComponents.kt`, nu pe cele din `androidx.tv.material3`.
2. **Performanță:** playlist-ul lui Radu are **~90.800 filme + ~20.400 seriale (≈111k titluri)**. Orice calcul „pe titlu”
   se face o dată, la indexare (`CatalogIndexer`), și se salvează în DB. Ecranele doar citesc. Fără `OFFSET` mare, fără
   încărcarea întregii liste în memorie, fără interogări pe fiecare poster.
3. **minSdk 23 / Android 11 TV:** alege versiuni de biblioteci compatibile cu minSdk 23. Fără blur și animații grele.
4. **Baza de date:** fiecare schimbare de schemă = versiune nouă + `Migration` explicită. **Niciodată** `fallbackToDestructiveMigration`
   (ar șterge datele lui Radu).
5. **Slim:** APK-ul are 7,5 MB. Nicio bibliotecă nouă dacă se poate scrie în câteva sute de linii; orice bibliotecă nouă se justifică în PR.
6. **Semnare și release:** APK-ul e semnat cu secretul GitHub `AK_SIGNING_KEY` (cert SHA-256 începe cu `DD:DD:81:5F`).
   Pierderea sau schimbarea cheii = update-urile nu se mai instalează peste aplicația existentă. **Nu atinge** secretele,
   `.github/workflows/*` sau configurarea de semnare din `app/build.gradle.kts`. Release-ul se pornește **doar manual de Radu**
   (Actions → Release → Run workflow); workflow-ul păstrează doar ultimele 2 release-uri.
7. **Versiune** = `0.1.<numărul de commit-uri pe main>`, calculată automat. Nu o modifica manual.
8. **GitHub ușor:** fără APK/build outputs/fișiere > 1 MB/playlist-uri reale/secrete în repo (vezi `docs/WORKFLOW.md`).
9. **Build:** CI rulează `ktlint` + `./gradlew :core:parser:test testDebugUnitTest lintDebug assembleDebug`. Dacă mediul tău nu poate
   descărca Android SDK, rulează măcar `:core:parser:test` și lasă CI-ul din PR să confirme restul. Un PR fără CI verde nu se mergeuiește.

### Etapa 10.1 (2026-11-17)

Room v6: coloana nullable `title.dubLabel`, migrare aditivă 5→6. `CatalogIndexer` compară în flux limba
originală TMDB cu fiecare sursă (inclusiv episoade), păstrând doar limbile distincte. Original/MULTI sau
o limbă necunoscută suprimă eticheta. Posterele și fișa citesc rezultatul salvat. Cheia amprentei v6
forțează prima recalculare fără reimport; amprenta include și ultima rundă de import pentru schimbările limbilor audio. Eliminat fallback-ul distructiv; migrările 1→6 sunt explicite.

### Etapa 10.2 (2026-11-17)

Room v7: `title.hasOriginalAudio` (boolean, implicit 0), migrare aditivă 6→7. Același parcurs al indexerului
calculează dacă o sursă declară explicit originalul; `MULTI` singur nu trece filtrul strict. Prima reindexare
este forțată de amprenta v7. Setări → Redare: „Preferă limba originală” (implicit nu), salvată și în backup.
Când e activă: original → MULTI → restul, iar profilul existent decide în interiorul fiecărei categorii.
Se aplică și pistelor audio, episoadelor următoare și rezervelor; sursa aleasă manual are prioritate.
Filmul/serialul fără limbă originală cunoscută păstrează comportamentul anterior.

## 6. Ce urmează (din `docs/PROPUNERI.md`)

Ordinea recomandată, **o etapă = un branch + un PR**:

1. **Etapa 10**: etichetă DUB (limba originală TMDB vs limbile audio ale surselor, calculat în `CatalogIndexer`; fără etichetă când limba
   e necunoscută), „preferă limba originală”, filtru producții SUA, completare detalii TMDB în fundal (IMDb ID, durată, certificare,
   țară), nota IMDb din `datasets.imdbws.com/title.ratings.tsv.gz` (săptămânal, procesat în flux), insigne pe postere, pagina actorului.
   *Notă: 10.1 (eticheta DUB) poate fi deja început de Claude; verifică PR-urile deschise și branch-urile `claude/*` înainte.*
2. **Etapa 12**: revamp interfață (fundal hero cu debounce, logo-uri, tab-uri sus, listă de urmărit, fișă nouă, trailer prin intent YouTube, rândul „Nou”).
3. **Etapa 11**: player (potrivire frecvență ecran, passthrough, mod noapte, format/viteză, intro per serial marcat manual, memorie audio/subtitrare per serial, derulare accelerată, sleep timer).
4. **Etapa 13**: setări în două panouri.
5. **Etapa 14**: viteză (paginare după cheie, postere RGB565, baseline profile, APK pe arhitectură).
6. **Etapa 15**: profiluri/copii, corectare manuală potrivire, surse moarte, Trakt.

Respinse ca grele: trailere în aplicație, trickplay, descrieri RO pentru tot catalogul, blur.
**Mai târziu (nu acum):** „Sari intro/recap” automat printr-un API public.

## 7. Cum se lucrează

Vezi `docs/WORKFLOW.md` și `docs/JULES_PROMPT.md`. Pe scurt: un branch + un PR pe etapă, mergeuit de agent doar cu CI verde (ca Radu să poată scoate APK din `main`), niciodată push direct pe `main`,
fără force-push, fără ștergere de tag-uri/release-uri/branch-uri, intrare scurtă în `CHANGELOG.md` la fiecare PR.

## 8. Restaurare (pentru Radu, joi)

- Ce s-a schimbat de la predare: `https://github.com/radumariusi/ak_vod_hub/compare/claude-handoff-2026-11-17...main`
- Se trec PR-urile lui Jules unul câte unul: păstrăm, corectăm sau facem revert la un PR anume.
- Dacă totul e stricat: `main` se readuce la tag-ul `claude-handoff-2026-11-17` (îl face Claude, cu acordul lui Radu).
