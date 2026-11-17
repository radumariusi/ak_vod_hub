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

Bază protejată permanent: **v0.1.14 și tagul ei**. Politica de livrare păstrează baza și cea mai nouă altă versiune; vezi `docs/RELEASES.md`.

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
   `.github/workflows/*` sau configurarea de semnare din `app/build.gradle.kts`. La cererea lui Radu (2026-11-17), agentul poate porni Release
   după CI verde pentru update direct în aplicația TV. Workflow-ul păstrează permanent **v0.1.14 și tagul ei**,
   plus cea mai nouă altă versiune. Este autorizată numai modificarea curățării pentru protejarea bazei.
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
2. **Etapa 12 — implementată, așteaptă testarea proprietarului**: revamp interfață (fundal hero cu debounce, logo-uri, tab-uri sus, listă de urmărit, fișă nouă, trailer prin intent YouTube, rândul „Nou”).
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

### Etapa 12.2
Logo-uri TMDB cerute numai pentru hero stabil și fișă. Cache `logo:<tip>:<tmdbId>` în `discover_cache`, 30 zile inclusiv absența, plafon 128. Fără migrare; offline folosește cache-ul vechi sau text. Alegere RO → EN → neutru → original, testată în parser.

### Etapa 12.3
AppRoot folosește file orizontale sus, cu wrapper-ele touch și stare selectată accesibilă. Păstrează SaveableStateProvider, stiva fișelor și restaurarea cardului. Destinația Lista mea este pregătită pentru 12.4.

### Etapa 12.4
Room v13, migrare aditivă 12→13: `watchlist(titleId, addedAt)`. Lista mea citește numai titlurile disponibile, pagini de 48 după ID. Apartenența rămâne salvată când sursele dispar; potrivirea local→TMDB copiază lista și istoricul în aceeași tranzacție cu legarea surselor. Titlurile locale cu listă/istoric nu se curăță automat. Fișa permite adăugare/scoatere și văzut/nevăzut: acțiunea manuală setează explicit istoricul la 100%/0%, scoate reluarea anterioară și actualizează progresul/posterele/Watch Next. Nu inventează episodul vizionat; un serial marcat văzut are semnificație la nivel de titlu. Backup-ul existent rămâne pentru setări, nu catalog/istoric/listă.

### Etapa 12.6
Buton Trailer în fișă: TMDB videos la apăsare (en-US, ca metadatele existente), doar YouTube/Trailer și chei validate strict. Preferă RO dacă răspunsul o oferă, apoi EN/original și oficial în aceeași limbă. Cache `trailer:<tip>:<tmdbId>` în discover_cache, 30 zile inclusiv absența, maximum 128. Erorile permit reîncercarea. Intent către YouTube TV, YouTube mobil, apoi handler-ul HTTPS disponibil; mesaj dacă nu există handler. Fără redare în aplicație sau cereri pe postere.

### Etapa 12.7
Room v14, migrare aditivă 13→14: `title.playlistAddedAt`, `title.newEpisodeAt` și indexurile lor. Prima reindexare este forțată prin amprenta v14. Data locală de apariție a titlului = MIN(firstSeenAt); noutatea episodului se grupează după sezon/număr (MIN între variante, apoi MAX pe titlu), prin cursor SQL în indexer, fără listă completă în RAM. Acasă: Nou în playlist și Episoade noi la serialele urmărite, șapte zile, pagini de 24 după dată/ID. Episoadele trebuie să fie mai noi decât adăugarea în listă sau ultima vizionare salvată. Fără număr de sezon/episod nu inventăm noutatea.
După importul reușit, `PlaylistNewsWorker` reîmprospătează numai serialele Xtream din listă/istoric/reluare, loturi de 8 după ID; buget de două minute + cererea deja pornită (max. 30 s), continuare cu cursor în WorkManager, pauză 60 s la redare. Erorile păstrează episoadele și se reîncearcă la următorul refresh. Prima citire stabilește baza la data apariției serialului, evitând anunțarea episoadelor vechi. Importul episoadelor este serializat cu deschiderea fișei; moștenirea titlului vizează doar serialul încărcat. Rezumatele se refac la sfârșitul lotului/rulării; fără biblioteci sau cereri pe postere. Dacă toate variantele unui episod dispar și acesta reapare ulterior, reapariția poate fi noutate locală.

### Etapa 12.8 și predare
Setări → Aspect: postere Mic/Mediu/Mare (92/112/144 dp, raport 2:3) și accent Roșu/Albastru/Verde/Violet/Auriu. Valorile implicite păstrează aspectul vechi: Mediu și Roșu. Preferințele existente `playback` salvează `poster_size`/`accent_color` și le includ în backup; valorile necunoscute au fallback sigur. `LocalPosterSize` aplică dimensiunile în toate rândurile/grilele, inclusiv actori și Lista mea; posterele mici cer w185, restul w342. Tema hub/player și indicatorul filei active folosesc accentul ales. Fără schemă DB nouă în 12.8, fără biblioteci.
Toate 12.1–12.8 sunt implementate, 12.9/12.10 rămân respinse. După CI verde și merge la ultimul PR: workflow-ul Release existent din main, verificare APK semnat/updater și păstrare v0.1.14 + tag. Apoi se așteaptă testarea proprietarului pe Android 11 TV; aceasta nu este considerată efectuată. Nu se începe etapa 11 în acest fir.

### Etapa 11 — player (11.1–11.10)

Implementarea extinde exclusiv playerul existent; hub-ul etapei 12 este păstrat. Room rămâne **v14**,
fără migrare sau dependențe noi. `FrameRateMatch`, `NightCompressor`, `SeriesPlayback`, `SeekNavigation`,
`SleepTimer` și `BufferPreset` sunt logică JVM pură în core:parser, cu teste.

Opțiunile sunt în comenzile playerului, accesibile cu telecomanda/touch. Preferințele `match_frame_rate`,
`audio_passthrough`, `night_mode`, `buffer_preset` se salvează în SharedPreferences `playback` și în backup.
Implicit: frecvență/passthrough/noapte oprite, buffer normal. Valorile de backup necunoscute au fallback sigur.
Frecvența solicită numai un multiplu compatibil al FPS-ului, la aceeași rezoluție; sistemul poate refuza.
La pauză/ieșire se restaurează modul inițial. Este posibil un ecran negru temporar pe TV-uri care schimbă modul.

Passthrough folosește capabilitățile ieșirii Media3, fără forțarea formatelor nesuportate. Dezactivarea îl
blochează în sink, păstrând decodoarele hardware/FFmpeg. `NightAudioProcessor` comprimă PCM 16 biți cu
anvelopă comună canalelor și limitare a vârfurilor; nu izolează vocile. Cât este activ, passthrough este suspendat.
Passthrough, noapte și buffer se aplică **la redeschiderea playerului** (mesaj explicit în UI).
Buffer mic/normal/mare: 15/50/120 s maxime și ținte de 12/32/64 MiB pentru allocator; nu sunt limite ale întregului proces.
Formatul imaginii și viteza sunt pe sesiune; viteza depinde de suportul ieșirii. Panoul tehnic arată bitrate-ul
**declarat**, nu o măsurătoare a rețelei; passthrough este indicat ca solicitat, nu confirmat pe receiver.

Setările per serial sunt chei `series.<identitate>.intro|audio|text` în același mecanism de backup validat.
Identitate: ID TMDB pozitiv; pentru seriale locale, SHA-256 din numele original normalizat + an (fără catalog în RAM).
Schimbarea identității la potrivirea local→TMDB sau corectarea numelui unui serial local cere refacerea preferinței.
Intro: sfârșit manual între 1 s și 10 min, numai înaintea finalului; „Sari intro” apare în comenzi cât poziția
este înainte de marcaj. Marcajul este comun serialului, nu pe sezon; se poate șterge/remarca.
Audio: limba selectată manual. Subtitrare: oprite sau limbă + încorporată/addon; nu se păstrează URL-uri, indecși
sau fișiere între episoade. Pistele fără limbă declarată nu pot fi memorate. Limbile globale sunt fallback când
alegerea nu există. Butonul de resetare șterge numai memoria audio/subtitrare a serialului, nu și introul.

Apăsarea lungă trece la 30 s după 1 s, 1 min după 3 s, 5 min după 6 s. Saltul la minut cere o durată cunoscută
și o sursă care permite derularea. Timerul folosește ceas monoton, continuă în pauză, nu se persistă și se
anulează la închiderea playerului. La expirare salvează progresul, oprește numărătoarea episodului următor,
pune pauză și închide ecranul de redare; nu stinge televizorul. „Sfârșitul episodului” are prioritate față de autoplay.

**Testare Android 11 TV — de efectuat de proprietar, neconfirmată:**
1. 23,976/24/25/50 FPS, opțiune oprită/pornită, păstrarea rezoluției și restaurare după pauză/Home/ieșire.
2. AC3/E-AC3/DTS cu și fără receiver, deconectare HDMI, passthrough oprit și fallback hardware/FFmpeg.
3. Noapte cu dialog/explozii, stereo/5.1, lipsă distorsiuni și suspendare passthrough.
4. Potrivit/zoom/întins și 0,75×–2×, cu subtitrări încorporate și addon/sincronizare.
5. Intro marcat, alt episod, repornire aplicație, ștergere și episod mai scurt decât marcajul.
6. Audio/subtitrare RO/EN/oprite, alt episod/sursă, limbă absentă, resetare și backup/restaurare.
7. Telecomandă ținută 1/3/6 s, bara de progres, salt la 0/minut valid/invalid, sursă fără seek.
8. Info tehnic pe surse diferite, buffer/cadre pierdute, închidere și focusul telecomenzii.
9. Timer 30/60/90 min, anulare, pauză/Home, final episod fără autoplay, păstrarea reluării.
10. Buffer mic/normal/mare după redeschidere, furnizor lent, sursă 4K, fallback și memoria pe TV.
11. Regresii: reluare, sursă manuală/rezervă, episod următor, subtitrări și interfața etapei 12.

Nu se consideră testate pe TV funcțiile validate numai în CI. Etapa 13 nu începe în acest fir.

### Etapa 13 — setări

Opt categorii în stânga, opțiuni în dreapta: Conținut, Aspect, Redare, Audio, Subtitrări,
Surse și playlist-uri, Servicii, Sistem. Dreapta intră în opțiuni; Stânga/Înapoi revine la categoria selectată.
Dialogurile și ecranele de adăugare/configurare păstrează propriul Înapoi. Derularea se păstrează per categorie.
Wrappere touch existente; fără biblioteci noi sau migrare, Room **v14** verificat în cod.

Inventar refolosit 10–12: profil surse și original, insigne, postere/accent, subtitrări (limbi/automat/mărime/stil),
frecvență/passthrough/noapte/buffer, playlist-uri/import/refresh/ștergere, TMDB/reîncercări, IMDb și addon-uri.
Backup/restaurare și configurarea telefonului sunt accesibile în Sistem, prin serverul local existent.

Chei noi în `playback`, incluse în backup și validate la restaurare: `content_languages`, `hidden_languages`,
`hidden_categories`, `home_hero` (true), `default_speed` (1×), `auto_next` (true), `audio_language`,
`subtitle_delay_ms` (0). Nicio cheie veche nu este redenumită. Preferințele goale păstrează fallback-ul existent.
Ascunderea este pe titlul întreg când **oricare** sursă declară o limbă/categorie blocată; categoriile sunt nume
exacte, separate prin virgulă (numele cu virgulă nu pot fi selectate individual). Reindexarea actualizează
vizibilitatea în rezumatul `sourceCount`; intrările, istoricul și lista rămân intacte. Nu este control parental.
Limba audio per serial și originalul au prioritate; limba audio globală nu schimbă profilul surselor.
Întârzierea implicită este pentru addon-uri; ajustarea din player rămâne pe episod. Viteza implicită se aplică
la deschiderea playerului; viteza aleasă în player rămâne pe sesiune. Autoplay oprit păstrează pornirea manuală.

Statistici la deschiderea categoriei, în fundal: titluri vizibile unice, 4K, potrivire TMDB; surse pe limbi
inclusiv cele ascunse, MULTI separat, maximum 30 de limbi afișate. Nu se recalculează la recompoziție.
Cache imagini: memoria și discul Coil, la cerere; fără ștergerea DB sau a metadatelor.
`claude-handoff-2026-11-17` absent la verificare; nu a fost recreat. `claude-snapshot` și tagul v0.1.14 păstrate.

Testarea pe TV nu este efectuată. Proprietarul verifică: toate cele opt categorii cu telecomanda și touch;
Dreapta/Stânga/Înapoi, dialoguri, derulare și revenire din adăugare playlist/telefon; persistență după repornire;
backup vechi și nou/restaurare; ascundere/dezascundere fără pierderi; hero și insigne; viteză/autoplay,
audio original/per serial, passthrough/noapte/buffer; subtitrări addon cu decalaj; statistici după import;
golire cache și reîncărcare postere; updater și instalare peste versiunea existentă. Testele hardware ale etapei 11
rămân neconfirmate. Nu se începe etapa 14 în acest fir.

### Etapa 14 — performanță

- Filme, Seriale, Căutare și rândurile de catalog de pe Acasă continuă după cheia de sortare și ID. La
  căutarea după popularitate, potrivirile de prefix au prioritate inclusiv când popularitatea este NULL.
  Segmentul NULL are continuare după ID. Interogările folosesc indexurile existente și comparații compatibile
  cu SQLite de pe API 23. Se folosesc cardurile încărcate și cheile ID pentru revenirea din fișă.
  O actualizare a catalogului reîncarcă grilele conform fluxului existent.
- Coil permite RGB565 numai pentru JPEG TMDB din carduri. Formatul final depinde de decodor. Logo-urile,
  fundalurile, distribuția și imaginile furnizorilor folosesc configurația existentă. Cache-ul pe disc are plafon
  de 64 MiB, cu același director și aceeași opțiune de golire din Setări.
- Workflow-ul Baseline Profile folosește AndroidX Macrobenchmark pe emulator x86_64 API 30. Generatorul
  verifică pornirea pe Acasă și trecerea la a doua pagină din Filme cu date sintetice. Verificarea caută regulile
  aplicației, profilul din APK și rezultatul instalării AndroidX. Datasetul și bibliotecile de măsurare sunt
  în build-urile de profilare. AndroidX ProfileInstaller intră în release pentru instalarea profilului și la sideload;
  impactul său asupra APK-ului rămâne de măsurat. Profilele din artefactul CI 44196527542 sunt în
  `app/src/main/generated/baselineProfiles/`. Regulile versionate vizează pachetele aplicației.
- Updaterul citește `Build.SUPPORTED_ABIS` în ordinea dispozitivului. Acceptă variantele `armeabi-v7a`,
  `arm64-v8a` și `x86_64` denumite `ak-vod-hub-<versiune>-<abi>.apk.bin`. Conținutul este APK semnat,
  descărcat local cu extensia APK. Pentru dispozitivele cu cel puțin o arhitectură inclusă în universal, fallback-ul
  este `ak-vod-hub-<versiune>.apk`. Universalul rămâne singurul asset cu extensia `.apk` pentru updaterul vechi.
  Workflow-ul verifică arhitecturile și certificatul v0.1.14 înaintea publicării. Dimensiunile se măsoară separat.
- Room v14, fără migrare. Retestarea pe televizor rămâne necesară.

### Corecții UI și player TV (2026-11-18)

Vezi [planul corecțiilor și pașii de retestare](CORECTII_TV.md). Fir separat de roadmap, din baza
`16ec696`. Suprapuneri cu PR-ul draft #36 în cardurile hub, ecranul Filme/Seriale/Căutare și documentație.

La import, parserul tratează separat canalele audio, limba și subtitrările. MatchWorker actualizează
audio/subtitrări pentru intrările existente în tranzacții de cel mult 500 după ID, cu cursor reluabil în
preferințele indexerului și pauză la redare. Indexerul reface rezumatele după parcurgere. Room rămâne
la v14. Actualizarea metadatelor păstrează legăturile titlurilor, istoricul, Lista mea și preferințele.

Playerul păstrează preferințele audio și sincronizarea subtitrărilor. Opțiunea Automat reaplică
preferința globală de subtitrare și șterge doar alegerea manuală de subtitrare a serialului. Alegerea
unei subtitrări specifice folosește pista sau rezultatul addon asociat opțiunii.
Retestarea pe Android 11 TV și instalarea peste versiunea existentă sunt în așteptare.
