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

### Etapa 10.4 (2026-11-17)

Room v8, migrare aditivă 7→8: `runtimeMinutes`, `certification` (cu țara sistemului de clasificare),
`originCountries` (coduri TMDB cu virgule la capete), `tagline`, `metadataFetched` (implicit 0).
`MatchWorker`/`TitleMatcher` existente completează și titlurile deja potrivite: loturi de 100, după ID,
fără OFFSET și fără catalog complet în memorie; 6 cereri în paralel, limita existentă 30/s, buget 8 minute.
Un răspuns fără câmpuri opționale este tot marcat procesat (nu se cere zilnic). Jobul pornește și la
pornirea procesului; continuările păstrează progresul în DB. `PlaybackActivityState` suspendă lucrul
cât e vizibil PlayerActivity; jobul reîncearcă după 60 s, fără buclă rapidă sau retry-uri consumate.
Cererile deja pornite pot termina. La detalii, numai 404 înseamnă titlu inexistent; alte 4xx sunt erori și păstrează legăturile. Cererile TMDB au plafon de 30 s, lotul respectă bugetul rămas.
Certificare: RO, apoi US, apoi origine/altele; preferă lansarea cinematografică, prefixează țara.
Durata, certificarea și tagline apar în fișă; pagina deschisă se actualizează după reindexare.
10.4 precedă 10.3 deoarece filtrul SUA cere țările de origine (nu deducem țara din limba engleză).

### Etapa 10.3 (2026-11-17)

Filme/Seriale/Căutare → Origine: „Producții SUA” citește `originCountries` (inclusiv coproducții),
„Limba originală engleză” citește `originalLanguage`. Nu confundă limba engleză cu țara SUA și nu confundă
limba originală cu audio disponibil (filtrul strict din 10.2 rămâne separat). Țara necunoscută nu trece
filtrul SUA; rezultatele se completează pe măsură ce rulează 10.4. Fără schemă sau setare persistentă nouă.

### Etapa 10.5 (2026-11-17)

Room v9, migrare aditivă 8→9: `title.imdbRating`, `title.imdbVotes` și `imdb_rating_stage` (cache de lucru,
golit după aplicare/eroare). `ImdbRatingsWorker` este înregistrat în `PlaylistWorkerFactory`; programarea
se face la pornire și după rundele TMDB. O verificare zilnică descarcă numai după șapte zile de la reușită,
sau manual din Setări → Note IMDb. Fără titluri disponibile cu IMDb ID nu descarcă nimic.
Arhiva oficială `datasets.imdbws.com/title.ratings.tsv.gz` este dezarhivată în flux, fără fișier salvat;
loturi de 500 și interogări după indexul IMDb existent, fără lista completă a ID-urilor în memorie.
Cache-ul de lucru păstrează numai intersecția cu playlist-ul; notele vizibile se schimbă într-o singură
tranzacție după EOF valid. Eroarea/anularea păstrează setul anterior. Notele absente din noul set se scot.
Redarea suspendă actualizarea și programează continuarea după 60 s; descărcarea reîncepe, nu se salvează
arhiva parțială. Plafon cerere 8 minute. Setări arată data, numărul de titluri, eroarea și „Actualizează acum”.
Posterele/fișa citesc notele din DB; `notifyCardsChanged()` notifică ecranele fără refacerea rezumatelor/FTS.
Titlurile care primesc IMDb ID după ultima actualizare primesc note la următoarea actualizare (sau manual).

### Etapa 10.6 (2026-11-17)

Room v10, migrare aditivă 9→10: `tmdbBadge`, `imdbBadge`, `hdrBadge`, `isNew`, `watchProgress` în `title`,
plus `badge` în cache-ul de lucru IMDb. Același parcurs al indexerului calculează textele și cel mai bun HDR;
„Nou” = data ultimei adăugări a unei surse în ultimele șapte zile. Amprenta v10 forțează recalcularea inițială.
Într-o zi nouă se expiră doar insignele vechi în DB, fără reindexarea surselor/FTS. IMDb salvează textul odată cu nota.
Progresul se completează la migrare din watch_state/history și se actualizează la salvarea vizionării;
cardurile încărcate primesc actualizarea fără resetarea paginării/focusului și fără cereri pe poster.
Setări → Insigne pe postere: șase opțiuni, toate active implicit; se pot ascunde toate. Se păstrează în
preferințele existente și în backup. `LocalPosterBadges` distribuie opțiunile către UI; posterele citesc DB.

### Etapa 10.7 (2026-11-17)

Room v11, migrare aditivă 10→11: `person_credit` (persoană + tip + ID TMDB), populat numai la deschiderea
actorului. `ActorRepository` cere combined_credits (numai cast), păstrează referințe, nu distribuția întregului
catalog; marcajul TTL de 21 zile folosește discover_cache. Cache limitat la 24 de actori, cu date vechi utile offline.
`ActorViewModel`/`ActorScreen` citesc doar titlurile cu surse prin JOIN pe indexurile TMDB și pagini de 48 după ID,
fără OFFSET. Titlurile noi din playlist apar automat la următoarea citire dacă sunt în filmografia cache-uită.
Cardurile distribuției folosesc Surface touch și ID-ul persoanei; navigarea reține contextul actor/fișă,
inclusiv actori deschiși succesiv, iar grila își reface paginile până la cardul deschis pentru restaurare.

### Etapa 10.8 (2026-11-17)

Room v12, migrare aditivă 11→12: `overviewRo`, `overviewRoFetchedAt`. Numai deschiderea fișei cere TMDB
cu ro-RO (fără append de detalii); descrierea EN rămâne intactă și este fallback dacă RO lipsește.
Cache de 30 zile inclusiv pentru traducerea absentă. Erorile nu marchează cererea reușită; se poate
reîncerca la redeschidere, iar cache-ul existent rămâne util offline. Nu există worker sau cereri RO pe postere.
Etapa 10 este implementată integral; după build se așteaptă testarea proprietarului înainte de etapa 12.

## 6. Ce urmează (din `docs/PROPUNERI.md`)

Ordinea recomandată, **o etapă = un branch + un PR**:

1. **Etapa 10 — implementată**: etichetă DUB (limba originală TMDB vs limbile audio ale surselor, calculat în `CatalogIndexer`; fără etichetă când limba
   e necunoscută), „preferă limba originală”, filtru producții SUA, completare detalii TMDB în fundal (IMDb ID, durată, certificare,
   țară), nota IMDb din `datasets.imdbws.com/title.ratings.tsv.gz` (săptămânal, procesat în flux), insigne pe postere, pagina actorului.
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
