# AK VOD Hub: plan pe etape

## Viziune

O aplicație Android TV care ia un playlist IPTV (M3U sau Xtream), **păstrează doar VOD-ul** (filme și seriale) și îl transformă într-un hub de tip „Netflix”:

- fiecare film / serial apare **o singură dată**, cu poster, descriere, rating (TMDB);
- sub el sunt **toate sursele din playlist, grupate**: de ex. *Filmul X: 4K în EN și DE, 1080p în alte 7 limbi*;
- **căutare, descoperire și recomandări doar din ce există în playlist**;
- subtitrări, cataloage și liste prin **addon-uri Stremio** (aplicația doar le folosește; nu construim un addon propriu).

Ce nu facem: Live TV, EPG, găzduire de conținut. Aplicația nu vine cu niciun playlist; utilizatorul îl adaugă pe al lui.

## Arhitectură

```
┌──────────────────────── Android TV app ────────────────────────┐
│ UI (Compose for TV)  Home · Căutare · Detalii · Player · Setări│
│        │                                                        │
│ Domain: Catalog (Titlu + Surse), Recomandări, Preferințe        │
│        │                                                        │
│ Data:  Importer M3U/Xtream ─► Parser nume ─► Potrivire TMDB     │
│        Room DB (raw entries, titluri, surse, istoric)           │
│        Client Stremio (subtitrări, cataloage) · Client TMDB     │
│ Player: Media3 ExoPlayer (+ decodor FFmpeg pentru audio)        │
└─────────────────────────────────────────────────────────────────┘
```

### Stack

| Zonă | Alegere | De ce |
|---|---|---|
| Limbaj | Kotlin 2.x | Standard Android. |
| UI | Jetpack Compose + `androidx.tv:tv-material` | Navigare cu telecomanda (focus), componente TV gata făcute. |
| Player | Media3 ExoPlayer, cu decodor FFmpeg pentru audio (AC3/EAC3/DTS) | Formatele din IPTV (MKV, HEVC, audio multicanal). |
| Bază de date | Room + FTS4 pentru căutare | Playlist-uri cu 50.000–200.000 de intrări; căutare locală rapidă. |
| Rețea | OkHttp + kotlinx.serialization | Ușor, fără Retrofit dacă nu e nevoie. |
| DI | Hilt | Standard, bine suportat. |
| Background | WorkManager | Reîmprospătare playlist și potrivire TMDB în fundal. |
| Imagini | Coil | Postere TMDB. |
| minSdk / target | 23 / ultimul stabil | Acoperă majoritatea box-urilor Android TV vechi. |

### Module Gradle (puține, ca build-ul să fie rapid)

- `app`: UI TV, navigare, DI.
- `core:parser`: Kotlin pur (fără Android). Parsare M3U, parsare nume de titluri, scor de potrivire. Testat pe JVM.
- `core:data`: Room, importere M3U/Xtream, client TMDB, client Stremio, repository-uri.
- `core:player`: integrarea Media3, alegerea sursei, subtitrări.

### Model de date (simplificat)

- **PlaylistSource**: un playlist (tip M3U/Xtream, URL, credențiale criptate, ultima actualizare).
- **RawEntry**: o intrare din playlist (nume original, grup/categorie, URL, logo, ID furnizor, tip film/serial/episod).
- **ParsedInfo**: ce scoate parserul din nume (titlu curat, an, sezon/episod, calitate, HDR, codec, limbi, subtitrări).
- **Title**: filmul sau serialul unic (ID TMDB, ID IMDb, tip, metadate în cache).
- **Source**: legătura Title ↔ RawEntry, cu calitate și limbă. Grupate în UI după calitate, apoi limbă.
- **Episode**: pentru seriale, sezon/episod ↔ surse.
- **WatchState**: progres, văzut, favorite.

## Etape

Fiecare etapă = un fir de discuție nou, un branch, un PR. Reguli în [WORKFLOW.md](WORKFLOW.md).

### ✅ Etapa 0: Cercetare, plan, igienă repo
Documentele astea, `.gitignore`, workflow de curățenie automată, reguli de lucru.

### ✅ Etapa 1: Scheletul aplicației, CI și update din GitHub
- Proiect Gradle (Kotlin DSL, version catalog), modulele de mai sus, Hilt, Compose for TV.
- Ecran Home gol cu navigare de bază (Home / Căutare / Setări), temă întunecată.
- CI pe GitHub Actions: build + teste unitare + lint pe fiecare PR (fără artefacte).
- **Release automat:** la fiecare merge în `main` (sau tag `v*`), CI construiește APK-ul de release semnat și îl publică în **GitHub Releases**. Se păstrează doar ultimele 2 release-uri; cele vechi și tag-urile lor se șterg automat.
- **Semnare stabilă:** o singură cheie de semnare, ținută în GitHub Secrets (niciodată în repo), ca fiecare APK nou să se instaleze peste cel vechi.
- **Update în aplicație:** la pornire (și din Setări), aplicația verifică ultimul release din GitHub, arată ce e nou și descarcă + instalează APK-ul.
- **Gata când:** APK-ul se instalează pe Android TV, navighezi cu telecomanda, iar un release nou apare în aplicație ca update și se instalează.

### ✅ Etapa 2: Import playlist și filtrare VOD
- Adăugare playlist: URL M3U, fișier M3U, cont Xtream (server, user, parolă).
- Xtream: `get_vod_streams`, `get_series`, `get_series_info`. Live TV ignorat complet.
- M3U: parser în flux (nu încarcă tot fișierul în memorie). Detectare VOD: URL cu `/movie/` sau `/series/`, extensii `.mkv/.mp4/.avi`, grupuri de tip „VOD/Filme/Movies/Series”; restul se ignoră.
- Salvare în Room în loturi; reîmprospătare periodică cu WorkManager, cu diferențe (ce s-a adăugat / șters).
- Ecran Setări → Playlist-uri, cu progres la import.
- **Gata când:** un playlist real de 100.000+ intrări se importă în sub ~1 minut pe un box TV, fără erori de memorie.
- *Cum s-a făcut:* la Xtream, importul complet ia filmele și lista de serialele (2 cereri mari, citite în flux); episoadele unui serial se cer la nevoie (`get_series_info`, un apel pe serial), altfel un cont cu 10.000 de seriale ar însemna 10.000 de cereri la fiecare import. Linkurile Xtream nu se salvează (conțin parola); se construiesc la redare. Datele contului sunt criptate cu o cheie din Android Keystore.

### ✅ Etapa 3: Parser de titluri (normalizare)
- Din nume ca `EN| The Matrix (1999) 4K HDR`, `|DE| Matrix 1999 [MULTI-SUB]`, `RO - Matrix (1999) FHD`, `Breaking Bad S01E02` scoatem: titlu curat, an, sezon/episod, calitate (4K/UHD, FHD/1080p, HD/720p, SD), HDR/DV, codec, limbi (din prefix, grup sau etichete), subtitrări.
- Limba se deduce și din numele categoriei (ex. „DE | Filme 4K”).
- Set mare de teste cu nume reale anonimizate (`core:parser/src/test/resources/testdata/`).
- **Gata când:** peste 95% din cazurile de test sunt parsate corect.
- *Cum s-a făcut:* `TitleParser` (în `core:parser`) taie prefixul de furnizor (`EN|`, `|DE|`, `[RO]`, `RO - `, `|NF|`, `ᴴᴰ`…), apoi caută „ancora” (`S01E02`, an, `1080p`/`4K`/`HEVC`/`MULTI`, paranteză cu etichete); titlul e tot ce e înainte. Limbile sunt coduri ISO 639-1 (`mul` = mai multe), cu limba subtitrării separată (`SUB RO`, `VOSTFR`, `MULTI-SUB`). Numele are prioritate față de categorie. Rezultatul se salvează în `raw_entry` la import (migrare Room 1→2, fără pierderea playlist-urilor; intrările vechi se completează la următoarea reîmprospătare). Setul de teste: `testdata/titles.tsv`, 187 de nume, toate corecte. Limită cunoscută: un an fără paranteză la final e luat drept an (ex. „Wonder Woman 1984” fără `(2020)`); potrivirea TMDB din etapa 4 poate reîncerca fără el.

### ✅ Etapa 4: Potrivire TMDB și grupare surse
- Cheia TMDB a utilizatorului (gratuită) în Setări; niciodată în repo.
- Ordine de potrivire: (1) `tmdb_id` dat de Xtream, dacă există; (2) căutare TMDB după titlu + an + tip; (3) titluri alternative/traduceri TMDB pentru nume în alte limbi (ex. „Der Pate” → „The Godfather”).
- Scor de încredere; sub prag → „Nepotrivite” (ecran de corectare manuală mai târziu).
- Cache local, limitare de rată, rulează în fundal după import.
- Grupare: toate RawEntry cu același TMDB ID → un Title, cu surse ordonate după calitate, apoi limbă.
- **Gata când:** pe un playlist real, peste 90% din filme sunt potrivite corect, iar duplicatele sunt unite.
- *Cum s-a făcut:* `TitleMatcher` (în `core:data`) rulează în fundal (WorkManager) după fiecare import, în trei pași: (1) intrările cu ID TMDB de la furnizor se leagă direct, fără rețea; (2) restul se caută pe TMDB după titlu + an + tip, în loturi de 500; duplicatele (același titlu normalizat, an, tip și limbă) costă **o singură cerere**, iar rezultatul rămâne în cache (`match_cache`), deci reîmprospătările și alte playlist-uri nu mai întreabă TMDB; (3) detaliile (poster, descriere, IMDb) pentru titlurile știute doar după ID. Titlurile în altă limbă se caută în limba lor (ex. „Der Pate” cu `language=de`), apoi se compară cu titlul tradus și cu cel original. Scorul de încredere (`MatchScorer`, în `core:parser`): 70% asemănarea titlului, 20% anul (±1 acceptat, altfel penalizare), 10% poziția în rezultate; prag 0,75. Dacă nu găsește, reîncearcă fără an și cu anul în titlu („Wonder Woman 1984”). Limitare: 30 de cereri/s, 6 în paralel, `Retry-After` la 429. Android oprește un job după 10 minute, deci potrivirea lucrează 8 minute și se reprogramează; progresul se salvează după fiecare lot. Tabele noi: `title` (unic pe tip + ID TMDB, deci duplicatele sunt unite) și `match_cache`; `raw_entry` are `titleId` și `matchState` (migrare 2→3, fără reimport). Gruparea surselor: `SourceGrouping` (4K → 1080p → 720p → SD, apoi limbile preferate, apoi cele cu mai multe surse; HDR primul), cu rezumat „4K: EN, DE · 1080p: RO, FR, IT, +4”. Cheia TMDB se pune în Setări (verificată la salvare, criptată pe dispozitiv). Tot aici: reîmprospătarea playlist-ului nu mai rescrie și nu mai re-parsează intrările neschimbate (doar le marchează „văzute”), iar loturile de import sunt de 2.000. Release-ul APK se pornește doar manual.

### ✅ Etapa 5: Hub-ul: Home, Detalii, Căutare
- Home pe rânduri: Continuă vizionarea, Adăugate recent în playlist, Populare (popularitate TMDB, doar din playlist), pe genuri, Filme / Seriale.
- Pagina de detalii: poster, fundal, descriere, rating, distribuție; **selector de surse grupat** (ex. „4K: EN, DE · 1080p: RO, FR, IT, +4”).
- Seriale: sezoane și episoade unite din toate sursele.
- Căutare locală (FTS) după titlu, inclusiv titluri alternative; filtre gen, an, calitate, limbă.
- **Gata când:** găsești și deschizi orice film din playlist în câteva apăsări de telecomandă.
- *Cum s-a făcut:* meniul lateral are acum Acasă, Filme, Seriale, Căutare, Setări. **Acasă**: sus fișa titlului cu focus (fundal, notă, descriere), dedesubt rânduri orizontale (Continuă vizionarea, Adăugate recent, Filme populare, Seriale populare, Cele mai apreciate, apoi 11 genuri). **Filme / Seriale / Căutare**: grilă cu filtre gen, an (decenii), calitate, limbă audio și ordine (populare, recente, apreciate, alfabetic). **Detalii**: poster, fundal, an, notă, genuri, regizor, distribuție (de la TMDB, la cerere), rezumatul surselor și câte un rând pe calitate cu toate sursele (limbă, HDR, codec, playlist); la seriale, sezoane și episoade unite din toate sursele (la Xtream, lista de episoade se cere de la server la prima deschidere). „Înapoi” revine pe cardul deschis, cu aceeași derulare. *Scalare (~110.000 de titluri):* nicio listă nu se încarcă întreagă: rândurile aduc câte 24 de titluri, grilele câte 48, iar pagina următoare se cere când focusul se apropie de capăt (LIMIT/OFFSET pe indexuri: popularitate, dată, notă, nume). Listele nu fac JOIN cu intrările din playlist: `CatalogIndexer` calculează pe fiecare titlu rezumatele surselor (număr, data adăugării, calitățile, limbile) după fiecare import și rundă de potrivire, doar dacă s-a schimbat ceva. Căutarea folosește FTS4 (`title_search`, fără diacritice, prefixe: „matr relo” găsește „The Matrix Reloaded”) pe titlul TMDB, titlul original și numele din playlist (deci „Der Pate” găsește „The Godfather”). *Orice film:* intrările încă nepotrivite sau negăsite pe TMDB primesc un titlu „local” (nume + an din playlist, logo-ul ca poster), deci apar în hub chiar și fără cheie TMDB; când potrivirea le găsește, trec la fișa TMDB. Imaginile: Coil 3 (singura bibliotecă nouă, standard pentru Compose; cache pe disc și în memorie). Redarea pornește deocamdată într-un player instalat pe TV (ex. VLC); playerul propriu vine în etapa 6. Migrare 3→4 fără reimport. Limitări: titlurile alternative din TMDB (traduceri) nu sunt descărcate, căutarea se bazează pe titlul original și numele din playlist; „Continuă vizionarea” se umple din etapa 6.

### ✅ Etapa 6: Player
- Media3 ExoPlayer, alegere piste audio / subtitrare, reluare de unde ai rămas, următorul episod automat.
- **Fallback automat:** dacă sursa nu pornește, trece la următoarea sursă potrivită.
- **Profil de preferințe:** alegere automată a sursei (ex. „4K EN, altfel 1080p RO”).
- **Gata când:** filmele MKV/HEVC cu audio AC3/EAC3 rulează pe un box obișnuit.
- *Cum s-a făcut:* player propriu (Media3 1.9.0, ecran separat `PlayerActivity`), cu decodorul audio software FFmpeg de la Jellyfin (`org.jellyfin.media3:media3-ffmpeg-decoder`, aceeași versiune ca Media3): decodoarele TV-ului au prioritate, iar AC3/EAC3/DTS/TrueHD pe care TV-ul nu le știe trec prin FFmpeg (în Setări → Redare scrie dacă decodorul e încărcat). HLS (`.m3u8`) inclus; redirecționările http → https permise. *Rezervă automată:* sursele titlului (sau ale episodului) sunt ordonate după profil, apoi calitate și limbă; playerul trece singur la următoarea sursă, **de la aceeași poziție**, când: sursa dă eroare (HTTP 404/403, conexiune, format), nu pornește în 25 s, se blochează peste 45 s, sau TV-ul nu poate decoda video-ul (ex. HEVC 4K pe un box de 1080p; audio nesuportat doar dacă mai sunt surse). La o întrerupere scurtă a rețelei reîncearcă întâi aceeași sursă o dată. Pe ecran apare de ce s-a schimbat sursa; dacă nu merge niciuna, lista cu motivele, „Încearcă din nou” și „Alege sursa”. Din player, meniul „Sursă” schimbă manual sursa (tot de la aceeași poziție). *Profil:* Setări → Redare, până la 4 reguli (calitate + limbă audio, ex. „4K EN, altfel 1080p RO”); limbile din profil aleg și pista audio; „Subtitrări automate” (implicit limba sistemului) sau oprite. *Comenzi:* stânga/dreapta ±10 s, OK arată comenzile (pauză, audio, subtitrări, sursă, episodul următor), butoanele media ale telecomenzii merg. *Reluare:* progresul se salvează la 15 s, la pauză și la ieșire în `watch_state` (umple „Continuă vizionarea”); fișa arată „Continuă de la 12:34” / „De la început”; la seriale „Continuă S01E03”. Peste 94% (sau sub 2 minute rămase) = văzut: filmul iese din „Continuă”, la serial rămâne episodul următor. *Episodul următor* pornește singur după 10 s (Redă acum / Anulează). Clic pe episod = redare directă cu sursa aleasă de profil. APK-ul păstrează doar bibliotecile ARM (+ x86_64 pentru emulator). Logica (profil, rezervă, reluare, ordinea episoadelor) e Kotlin pur, cu teste. De verificat pe TV: un film MKV/HEVC cu AC3/EAC3.

### ✅ Etapa 7: Subtitrări prin addon-uri Stremio
- Client Kotlin pentru protocolul Stremio (`manifest.json`, `/subtitles/{type}/{imdbId}.json`).
- Implicit: OpenSubtitles v3. Utilizatorul poate adăuga alte addon-uri după URL.
- Limbi preferate (ex. RO, apoi EN), ajustare decalaj (sync), dimensiune și stil.
- **Gata când:** pornești un film fără subtitrări încorporate și alegi o subtitrare RO din addon.
- *Cum s-a făcut:* client propriu pentru protocolul Stremio (OkHttp + kotlinx.serialization, fără biblioteci noi): citește `manifest.json` (resurse, tipuri, prefixe de ID), cere `/subtitles/movie/tt….json` sau `/subtitles/series/tt…:sezon:episod.json` la toate addon-urile în paralel (25 s maxim pe cerere) și ține listele în memorie. ID-ul IMDb vine de la potrivirea TMDB; dacă lipsește, se cere o dată de la TMDB și se salvează. Titlurile locale (nepotrivite) nu pot fi căutate. *Addon-uri:* Setări → Subtitrări, implicit OpenSubtitles v3; se adaugă altele după adresă (acceptă și `stremio://`, fără `/manifest.json`), se pot șterge sau muta mai sus. Se acceptă doar addon-uri cu subtitrări sau cataloage (cataloagele se folosesc din etapa 8). *Limbi preferate:* până la 3 (ex. RO, apoi EN), aceleași pentru pistele din sursă și pentru addon-uri; setarea veche cu o singură limbă se păstrează. *Alegere automată:* o dată pe film / episod, după ce se știu pistele sursei și lista din addon-uri: dacă sursa are deja o subtitrare într-o limbă preferată la fel de bună, rămâne aceea; altfel pornește cea mai bună din addon (ex. sursa are EN, addon-ul are RO → RO). Se poate opri („Automat din addon-uri: nu”). *Meniul Subtitrări* din player: Oprite, pistele din sursă, apoi cele din addon-uri („Română 1 · OpenSubtitles v3”…), limbile preferate primele, plus starea căutării. *Afișare:* subtitrarea din addon e desenată de aplicație peste video (nu prin Media3), ca sincronizarea să fie instantă fără să repornim sursa IPTV. Formate: SRT, WebVTT, ASS/SSA (stilurile se scot). Codificări: UTF-8/UTF-16, altfel Windows-1250 la RO (și alte limbi central-europene), 1251 chirilic etc.; diacriticele românești stricate („º”, „þ”, „ã”) se repară. *Ajustează* (în player): sincronizare ±0,1 s / ±1 s (doar pentru addon-uri), mărime 75–200% și stil (alb cu contur, galben cu contur, alb pe fundal negru), aplicate și pistelor din sursă; mărimea și stilul se salvează. Logica (citire SRT/VTT/ASS, codificări, adrese, coduri de limbă, alegerea automată) e Kotlin pur, cu teste. Limitări: subtitrările arhivate (zip) nu sunt acceptate; decalajul nu se păstrează între episoade.

### ✅ Etapa 8: Descoperire și recomandări
- „Similare” și „Recomandate” de TMDB, filtrate la ce există în playlist.
- „Pentru că ai văzut X”: scor local din istoric (genuri, regizori, actori, an).
- Colecții / francize (ex. trilogii), cu ce părți ai disponibile.
- Cataloage și liste din addon-uri Stremio (ex. top-uri, liste MDBList/Trakt), **intersectate cu playlist-ul**.
- **Gata când:** Home are rânduri personalizate care conțin doar titluri disponibile.
- *Cum s-a făcut:* totul e limitat la titlurile cu surse în playlist-uri (`sourceCount > 0`). *Istoric:* tabel nou `watch_history` (progres maxim, terminat, data), scris de player odată cu progresul; pornește din „Continuă vizionarea” existent (migrare 4→5, fără reimport). *Scor local* (Kotlin pur, `Recommender`, cu teste): ponderea fiecărui titlu văzut = cât de recent (înjumătățire la 45 de zile) × cât s-a văzut (abandonat repede ≈ nu contează); profil pe genuri, regizori și actori, an (medie + abatere) și filme/seriale; scorul unui candidat combină genurile (cosinus), persoanele, anul, nota corectată după voturi, popularitatea și semnalele TMDB. *TMDB:* o singură cerere pe titlu (`append_to_response=recommendations,similar,credits`), plus colecția și filmografia regizorului / actorilor principali, ținute în baza locală (`discover_cache`, 3–4 săptămâni). Fără cheie TMDB rămân recomandările după genuri și an. *Acasă:* „Recomandate pentru tine”, „Continuă colecția” (partea următoare disponibilă și nevăzută), „Pentru că ai văzut X” (ultimele 2 titluri văzute serios), apoi câte un rând pentru fiecare catalog din addon-uri; se încarcă în fundal, după rândurile obișnuite. *Detalii:* colecția („Ai 2 din 3 părți · lipsește: …”, cu părțile disponibile), „Recomandate”, „Similare”, „Mai multe de la <regizor>”; „Înapoi” revine la titlul dinainte. *Cataloage Stremio:* se citesc din manifest (doar filme/seriale fără parametri obligatorii, max. 12 pe addon), până la 3 pagini (`skip`), cache 6 ore. Titlurile se găsesc în playlist după ID TMDB (`tmdb:`), IMDb (din fișă sau din legăturile IMDb→TMDB salvate în `imdb_map`), nume + an (indexul de căutare), iar ce rămâne se întreabă la TMDB (`/find`, max. 150 de cereri pe reîmprospătare, rezultatul se păstrează). Addon-urile se adaugă tot din Setări → Subtitrări (lista arată acum „Subtitrări · Cataloage”). Limitări: recomandările personale apar după primul titlu văzut; seriale: „terminat” = un episod văzut până la capăt.

### ✅ Etapa 9: Integrare Android TV și finisare
- Rândul „Watch Next” / canale pe ecranul principal Android TV; căutare vocală globală.
- Configurare de pe telefon (QR + pagină web locală) ca să nu tastezi URL-uri cu telecomanda.
- Profiluri (inclusiv copii, după certificare), backup / restaurare setări.
- *Cum s-a făcut:* *Ecranul principal* (Android 8+, doar pe TV): filmele și serialele din „Continuă vizionarea” apar în rândul sistemului **Watch Next** („Continuă vizionarea” de pe ecranul principal), cu poster, poziție și episod (S01E03; episodul următor apare ca „Următorul”); alegerea pornește direct playerul de unde ai rămas (`akvodhub://play/<id>`). Plus un canal al aplicației, **„AK VOD Hub: adăugate recent”** (ultimele 24 de titluri din playlist-uri), care deschide fișa titlului (`akvodhub://title/<id>`). Sincronizarea e diferențială (`HomeScreenSync`, Kotlin pur, cu teste), la 2 s după o salvare a progresului sau un import; ce scoate utilizatorul din Watch Next nu mai revine cât timp titlul rămâne început. Coloanele `TvContract` sunt scrise direct (fără biblioteca `tvprovider`). *Căutare:* căutarea globală Android TV (inclusiv vocală, de pe butonul de microfon al telecomenzii) primește sugestii din indexul local (`SearchProvider`, `searchable.xml`), deci doar titluri din playlist; „caută X în AK VOD Hub” din Asistent și `ACTION_SEARCH` deschid ecranul Căutare cu textul; în ecranul Căutare e și butonul **Caută vocal** (recunoașterea vocală a TV-ului). Pe unele TV-uri aplicația trebuie bifată în Setări → Preferințe dispozitiv → Căutare → Aplicații căutabile. *Configurare de pe telefon:* Setări → Configurează de pe telefon arată un cod QR și adresa (ex. `http://192.168.1.20:8686/?t=…`); pagina de pe telefon (fără resurse de pe internet) adaugă playlist M3U, cont Xtream, cheia TMDB (verificată), addon Stremio, și descarcă / restaurează **backup-ul setărilor** (playlist-uri cu conturi, cheia TMDB, addon-uri, preferințe de redare și subtitrări; fișierele M3U locale nu se pot muta). Serverul (`PhoneSetupServer`, HTTP minimal propriu, `HttpMessage` testat) merge doar cât e deschis ecranul și cere codul secret din adresă, nou la fiecare deschidere. Bibliotecă nouă: `io.nayuki:qrcodegen` (codul QR, Java pur, ~30 KB, MIT). *Neimplementat:* profiluri (inclusiv copii); rămân pe lista de idei.

### ✅ Etapa 10: limbi și metadate

- ✅ 10.1: etichetă DUB calculată la indexare și salvată în Room v6; fără etichete pentru limbi necunoscute sau surse originale/MULTI.
- ✅ 10.2: preferă audio original la alegerea sursei și pistei; filtru strict din rezumatul salvat, Room v7.
- ✅ 10.3: filtre separate SUA (inclusiv coproducții) și limbă originală engleză, citite din DB.
- ✅ 10.4: metadate TMDB opționale, completate o singură dată prin jobul existent (Room v8), cu pauză la redare.
- ✅ 10.5: note IMDb din setul oficial în flux, aplicare atomică, verificare săptămânală și la cerere (Room v9).
- ✅ 10.6: insigne configurabile precalculate și progres pe postere, cu backup al preferințelor (Room v10).
- ✅ 10.7: pagina actorului, filmografie la cerere intersectată cu playlist-ul, paginare după ID și cache limitat (Room v11).
- ✅ 10.8: descrieri RO la cerere, cache 30 zile și fallback la engleza existentă (Room v12).
- Etapa 10 completă; build și testare pe dispozitiv înainte de etapa 12.

### ✅ Etapa 12: interfață

- ✅ 12.1: hero existent extins cu debounce de 300 ms și backdrop TMDB de 780 px; gradient întunecat, fără blur.

- ✅ 12.2: logo TMDB la cerere în hero/fișă, cache 30 zile (inclusiv lipsa), text la eroare.

- ✅ 12.3: meniu sus cu șase file, telecomandă și touch; conținutul Listei mele urmează în 12.4.

- ✅ 12.4: Lista mea persistentă, pagini de 48 după ID; văzut/nevăzut manual din fișă (Room v13).

- ✅ 12.5: fișă cu fundal pe tot ecranul, logo/metadate, redare mare, grupuri compacte de surse, sezoane ca file și distribuție cu poze.

- ✅ 12.6: trailer TMDB la apăsare, cache limitat; intent YouTube TV/telefon, apoi aplicația disponibilă.

- ✅ 12.7: Nou în playlist și episoade noi la serialele urmărite, fereastră de șapte zile, paginare dată/ID și refresh Xtream țintit (Room v14).

- ✅ 12.8: postere mic/mediu/mare și cinci culori de accent; preferințe locale și backup, fără biblioteci noi.
- Etapa 12 completă (12.1–12.8); 12.9/12.10 respinse. Livrare semnată după CI și merge, apoi testarea proprietarului. Etapa 11 nu începe în acest fir.

## Idei suplimentare

1. **Verificare sănătate surse**: test rapid în fundal al linkurilor; sursele moarte sunt marcate și puse la final.
2. **„Nou în playlist”**: după fiecare reîmprospătare, un rând cu ce a apărut nou (filme și episoade noi la serialele urmărite).
3. **Insignă „Subtitrare RO”** pe postere, verificată din addon (cu cache), ca să știi dinainte.
4. **Mai multe playlist-uri unite**: aceleași filme de la furnizori diferiți apar ca surse în aceeași fișă; dacă un furnizor cade, ai rezervă.
5. **Corectare manuală a potrivirii** („nu e filmul ăsta”) și reguli de ignorare (categorii, limbi pe care nu le vrei).
6. **Ascunde limbile nedorite**: filtru global (ex. arată doar surse EN, RO, DE).
7. **Trakt (opțional)**: sincronizare istoric și watchlist; watchlist-ul afișat doar cu ce e disponibil.
8. **Statistici playlist**: câte filme, câte în 4K, pe limbi, cât s-a potrivit; util pentru a compara furnizori.
9. **Profiluri** (mai mulți utilizatori, profil pentru copii cu filtrare după certificare): istoric, „Continuă” și recomandări separate.

## Decizii luate

- **Licență:** GPL-3.0 (putem refolosi cod din NuvioTV / M3UAndroid, cu atribuire).
- **Repo:** public deocamdată; poate deveni privat mai târziu (atunci CI are 2.000 min/lună, deci build-urile trebuie să rămână scurte).
- **Addon Stremio propriu:** nu. Folosim addon-uri Stremio existente doar pentru subtitrări, cataloage și liste.
- **Livrare:** APK-urile vin din GitHub Releases, cu update direct din aplicație. Claude face merge la PR-uri când CI e verde.
- **Etapele 4–9 una după alta** (Radu, 2026-11-17): fără APK după fiecare etapă; release-ul se pornește manual, o singură dată, după ultima etapă.
