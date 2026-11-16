# AK VOD Hub: plan pe etape

## Viziune

O aplicație Android TV care ia un playlist IPTV (M3U sau Xtream), **păstrează doar VOD-ul** (filme și seriale) și îl transformă într-un hub de tip „Netflix”:

- fiecare film / serial apare **o singură dată**, cu poster, descriere, rating (TMDB);
- sub el sunt **toate sursele din playlist, grupate**: de ex. *Filmul X: 4K în EN și DE, 1080p în alte 7 limbi*;
- **căutare, descoperire și recomandări doar din ce există în playlist**;
- subtitrări (și opțional cataloage / liste) prin **addon-uri Stremio**.

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

### Etapa 1: Scheletul aplicației Android TV
- Proiect Gradle (Kotlin DSL, version catalog), modulele de mai sus, Hilt, Compose for TV.
- Ecran Home gol cu navigare de bază (Home / Căutare / Setări), temă întunecată.
- CI pe GitHub Actions: build + teste unitare + lint pe PR. APK ca artefact **doar la rulare manuală**, `retention-days: 3`.
- **Gata când:** APK-ul de debug se instalează pe Android TV / emulator TV și navighezi cu telecomanda.

### Etapa 2: Import playlist și filtrare VOD
- Adăugare playlist: URL M3U, fișier M3U, cont Xtream (server, user, parolă).
- Xtream: `get_vod_streams`, `get_series`, `get_series_info`. Live TV ignorat complet.
- M3U: parser în flux (nu încarcă tot fișierul în memorie). Detectare VOD: URL cu `/movie/` sau `/series/`, extensii `.mkv/.mp4/.avi`, grupuri de tip „VOD/Filme/Movies/Series”; restul se ignoră.
- Salvare în Room în loturi; reîmprospătare periodică cu WorkManager, cu diferențe (ce s-a adăugat / șters).
- Ecran Setări → Playlist-uri, cu progres la import.
- **Gata când:** un playlist real de 100.000+ intrări se importă în sub ~1 minut pe un box TV, fără erori de memorie.

### Etapa 3: Parser de titluri (normalizare)
- Din nume ca `EN| The Matrix (1999) 4K HDR`, `|DE| Matrix 1999 [MULTI-SUB]`, `RO - Matrix (1999) FHD`, `Breaking Bad S01E02` scoatem: titlu curat, an, sezon/episod, calitate (4K/UHD, FHD/1080p, HD/720p, SD), HDR/DV, codec, limbi (din prefix, grup sau etichete), subtitrări.
- Limba se deduce și din numele categoriei (ex. „DE | Filme 4K”).
- Set mare de teste cu nume reale anonimizate (`core:parser/src/test/resources/testdata/`).
- **Gata când:** peste 95% din cazurile de test sunt parsate corect.

### Etapa 4: Potrivire TMDB și grupare surse
- Cheia TMDB a utilizatorului (gratuită) în Setări; niciodată în repo.
- Ordine de potrivire: (1) `tmdb_id` dat de Xtream, dacă există; (2) căutare TMDB după titlu + an + tip; (3) titluri alternative/traduceri TMDB pentru nume în alte limbi (ex. „Der Pate” → „The Godfather”).
- Scor de încredere; sub prag → „Nepotrivite” (ecran de corectare manuală mai târziu).
- Cache local, limitare de rată, rulează în fundal după import.
- Grupare: toate RawEntry cu același TMDB ID → un Title, cu surse ordonate după calitate, apoi limbă.
- **Gata când:** pe un playlist real, peste 90% din filme sunt potrivite corect, iar duplicatele sunt unite.

### Etapa 5: Hub-ul: Home, Detalii, Căutare
- Home pe rânduri: Continuă vizionarea, Adăugate recent în playlist, Populare (popularitate TMDB, doar din playlist), pe genuri, Filme / Seriale.
- Pagina de detalii: poster, fundal, descriere, rating, distribuție; **selector de surse grupat** (ex. „4K: EN, DE · 1080p: RO, FR, IT, +4”).
- Seriale: sezoane și episoade unite din toate sursele.
- Căutare locală (FTS) după titlu, inclusiv titluri alternative; filtre gen, an, calitate, limbă.
- **Gata când:** găsești și deschizi orice film din playlist în câteva apăsări de telecomandă.

### Etapa 6: Player
- Media3 ExoPlayer, alegere piste audio / subtitrare, reluare de unde ai rămas, următorul episod automat.
- **Fallback automat:** dacă sursa nu pornește, trece la următoarea sursă potrivită.
- **Profil de preferințe:** alegere automată a sursei (ex. „4K EN, altfel 1080p RO”).
- **Gata când:** filmele MKV/HEVC cu audio AC3/EAC3 rulează pe un box obișnuit.

### Etapa 7: Subtitrări prin addon-uri Stremio
- Client Kotlin pentru protocolul Stremio (`manifest.json`, `/subtitles/{type}/{imdbId}.json`).
- Implicit: OpenSubtitles v3. Utilizatorul poate adăuga alte addon-uri după URL.
- Limbi preferate (ex. RO, apoi EN), ajustare decalaj (sync), dimensiune și stil.
- **Gata când:** pornești un film fără subtitrări încorporate și alegi o subtitrare RO din addon.

### Etapa 8: Descoperire și recomandări
- „Similare” și „Recomandate” de TMDB, filtrate la ce există în playlist.
- „Pentru că ai văzut X”: scor local din istoric (genuri, regizori, actori, an).
- Colecții / francize (ex. trilogii), cu ce părți ai disponibile.
- Cataloage și liste din addon-uri Stremio (ex. top-uri, liste MDBList/Trakt), **intersectate cu playlist-ul**.
- **Gata când:** Home are rânduri personalizate care conțin doar titluri disponibile.

### Etapa 9: Integrare Android TV și finisare
- Rândul „Watch Next” / canale pe ecranul principal Android TV; căutare vocală globală.
- Configurare de pe telefon (QR + pagină web locală) ca să nu tastezi URL-uri cu telecomanda.
- Profiluri (inclusiv copii, după certificare), backup / restaurare setări.
- Release APK pe GitHub (păstrăm doar ultimele 2 release-uri).

### Etapa 10 (opțional): addon Stremio propriu
- Un mic server (Node + `stremio-addon-sdk`) care expune catalogul tău potrivit (TMDB/IMDb ID) în Stremio, cu sursele grupate, ca să ai același hub și pe alte dispozitive.
- Necesită găzduire (local sau gratuit); de decis după etapa 8.

## Idei suplimentare

1. **Verificare sănătate surse**: test rapid în fundal al linkurilor; sursele moarte sunt marcate și puse la final.
2. **„Nou în playlist”**: după fiecare reîmprospătare, un rând cu ce a apărut nou (filme și episoade noi la serialele urmărite).
3. **Insignă „Subtitrare RO”** pe postere, verificată din addon (cu cache), ca să știi dinainte.
4. **Mai multe playlist-uri unite**: aceleași filme de la furnizori diferiți apar ca surse în aceeași fișă; dacă un furnizor cade, ai rezervă.
5. **Corectare manuală a potrivirii** („nu e filmul ăsta”) și reguli de ignorare (categorii, limbi pe care nu le vrei).
6. **Ascunde limbile nedorite**: filtru global (ex. arată doar surse EN, RO, DE).
7. **Trakt (opțional)**: sincronizare istoric și watchlist; watchlist-ul afișat doar cu ce e disponibil.
8. **Statistici playlist**: câte filme, câte în 4K, pe limbi, cât s-a potrivit; util pentru a compara furnizori.

## Decizii deschise (pentru Radu)

- **Licența proiectului.** GPL-3.0 ne permite să refolosim cod din NuvioTV / M3UAndroid; Apache-2.0 sau MIT e mai permisivă, dar atunci doar ne inspirăm. Recomandare: GPL-3.0, dacă vrei să poți refolosi cod din ele.
- **Repo public sau privat.** Pentru un repo public, minutele GitHub Actions sunt gratuite nelimitat; pentru unul privat, sunt 2.000 min/lună. Proiectul nu conține playlist-uri sau secrete, deci poate fi public.
- **Addon Stremio propriu (etapa 10):** da / nu / mai târziu.
