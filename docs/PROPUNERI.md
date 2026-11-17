# Propuneri: funcționalități noi, aplicația rămâne „slim”

Status: în implementare; elementele terminate sunt marcate cu ✅.

Punct de plecare (v0.1.14): APK **7,5 MB** (cu FFmpeg pentru 3 arhitecturi), 7 biblioteci mari
(Compose TV, Media3, Room, Hilt, WorkManager, OkHttp, Coil). Playlist-ul lui Radu: ~90.800 filme +
~20.400 seriale, deci orice calcul „pe titlu” se face o dată, la indexare, nu la afișare.

**Regula care ține aplicația ușoară:** nicio bibliotecă nouă dacă se poate scrie în câteva sute de linii;
etichetele și notele se calculează în fundal și se salvează în tabelul `title`, iar ecranele doar le citesc.

Legendă impact: **APK** = cât crește aplicația · **DB** = cât crește baza de date · **Viteză** = efect
asupra derulării / pornirii · 🟢 neglijabil · 🟡 de urmărit · 🔴 greu (nu recomand sau doar opțional).

---

## Etapa 10: Limba originală, note TMDB + IMDb, etichete pe postere (cererile tale)

| # | Funcție | Inspirat din | APK | DB | Viteză |
|---|---------|--------------|-----|----|--------|
| ✅ 10.1 | **Etichetă „DUB”** pe titlurile care au doar surse în altă limbă decât cea originală (ex. film american doar în germană → „DUB DE”) | Stremio (Torrentio flags), TiviMate | 🟢 0 | 🟢 +1 coloană | 🟢 |
| ✅ 10.2 | **Preferă limba originală** (setare): sursele în limba originală trec primele la „Redă”; filtru „Doar în limba originală” în Filme/Seriale | Plex (audio original), Infuse | 🟢 0 | 🟢 | 🟢 |
| 10.3 | **Filtru „Producții SUA”** / limba originală engleză (US) | Jellyfin filtre | 🟢 0 | 🟢 +1 coloană | 🟢 |
| ✅ 10.4 | **Completare detalii TMDB în fundal** pentru toate titlurile: IMDb ID, durată, certificare (PG-13…), țara de origine, tagline | NuvioTV, Plex | 🟢 0 | 🟡 +3–5 MB | 🟡 o singură dată ~1 h în fundal |
| 10.5 | **Nota IMDb** pe postere și în fișă, din fișierul public IMDb `title.ratings` (descărcat săptămânal, ~8 MB, procesat în flux și aruncat) | Kodi (scrapers), Stremio | 🟢 0 | 🟢 +2 coloane | 🟡 ~15–30 s/săpt. în fundal |
| 10.6 | **Insigne pe postere** (configurabile): ★ TMDB, IMDb, 4K/HDR, DUB, bară de progres, „Nou” | TiviMate, Plex, NuvioTV | 🟢 | 🟢 | 🟢 dacă sunt text pre-calculat |
| 10.7 | **Pagina actorului**: din distribuție (care există deja în fișă) deschizi actorul și vezi doar filmele lui din playlist | Plex, Jellyfin | 🟢 | 🟢 cache mic | 🟢 la cerere |
| 10.8 | **Descrieri în română** (TMDB ro-RO, cu engleză dacă lipsește) | Kodi | 🟢 | 🟡 | 🔴 dublează cererile TMDB pentru 110k titluri; recomand doar la cerere, în fișă |

**Cum ar merge 10.1–10.3, concret.** TMDB ne dă deja limba originală (`originalLanguage`), iar parserul
scoate limbile audio din numele surselor (`audioLanguages`). La indexare compar cele două:
- există o sursă în limba originală (sau „MULTI”) → fără etichetă;
- toate sursele au limbă declarată și niciuna nu e cea originală → **DUB + limba** (ex. „DUB DE”);
- sursele nu spun limba → **nicio etichetă** (prefer să nu greșesc decât să pun etichete false).

Limita reală: depinde de cât de bine își etichetează furnizorul sursele (prefixe ca „DE |”, „[RO]”, „MULTI”).
Pentru „SUA” am nevoie de țara de origine, care vine doar din pasul 10.4.

**Despre note IMDb (important):** TMDB nu dă nota IMDb. Variante:
- ✅ **Fișierul oficial IMDb** (`datasets.imdbws.com/title.ratings.tsv.gz`): gratuit, fără cheie, toate notele
  odată, uz personal permis. Recomandat.
- OMDb: cheie gratuită, dar 1.000 cereri/zi, adică 110 zile pentru tot playlist-ul. Nu.
- MDBList: dă și Rotten Tomatoes / Metacritic / Letterboxd, dar cere cheie și are limite zilnice. Opțional, doar în fișă.

Nota IMDb cere IMDb ID-ul fiecărui titlu, pe care acum îl avem doar pentru o parte. De aici pasul 10.4:
~110.000 cereri TMDB o singură dată (~1 h la 30 cereri/s, în fundal, nu în timpul redării), apoi doar titlurile noi.

**Verdict greutate:** nimic din etapa 10 nu îngreunează aplicația. Singurul cost e completarea inițială de ~1 h.

---

## Etapa 11: Player

| # | Funcție | Inspirat din | APK | Viteză |
|---|---------|--------------|-----|--------|
| 11.1 | **Potrivire frecvență ecran** (23,976 / 24 / 25 / 50 Hz): elimină sacadarea la filme. Android 11 o suportă | Kodi, Infuse, Plex | 🟢 0 | 🟢 (opțiune, unele TV-uri clipesc 1–2 s la schimbare) |
| 11.2 | **Passthrough audio** AC3 / E-AC3 / DTS către soundbar/receiver | Kodi, Plex, Jellyfin | 🟢 0 | 🟢 |
| 11.3 | **Mod noapte / dialog mai clar** (compresor de volum) | Plex, Infuse | 🟢 câțiva KB | 🟢 |
| 11.4 | **Format imagine**: potrivit / zoom / întins; **viteză** 0,75×–2× | VLC, MX Player, TiviMate | 🟢 0 | 🟢 |
| 11.5 | **Sari peste intro**: marchezi o dată sfârșitul introului la un serial, butonul „Sari intro” apare apoi la toate episoadele | Netflix, Plex, Jellyfin Intro Skipper | 🟢 | 🟢 (un tabel mic) |
| 11.6 | **Ține minte audio + subtitrarea per serial** (nu doar global) | Plex | 🟢 | 🟢 |
| 11.7 | **Derulare accelerată** când ții apăsat (10 s → 30 s → 1 min → 5 min) și salt la minut | Kodi, TiviMate | 🟢 | 🟢 |
| 11.8 | **Info tehnic** (codec, rezoluție, bitrate, cadre pierdute, buffer) | Kodi („o”), Stremio | 🟢 | 🟢 |
| 11.9 | **Timer de somn** (30/60/90 min, sfârșitul episodului) | TiviMate, Plex | 🟢 | 🟢 |
| 11.10 | **Buffer configurabil** pentru furnizori lenți (mic / normal / mare) | Kodi cache, TiviMate | 🟢 | 🟡 „mare” folosește mai mult RAM pe TV |
| 11.11 | ❌ Previzualizare imagini la derulare (trickplay) | Plex, Jellyfin | – | 🔴 IPTV nu dă miniaturi; generarea lor ar descărca filmul. Nu recomand |

**Verdict greutate:** totul e în Media3 deja inclus. Zero biblioteci noi.

---

## Etapa 12: Revamp interfață

| # | Funcție | Inspirat din | APK | Viteză |
|---|---------|--------------|-----|--------|
| 12.1 | **Acasă cu „hero”**: fundalul (backdrop) și descrierea titlului selectat, sus pe ecran | NuvioTV, Stremio, Netflix | 🟢 | 🟡 o imagine mare (~200 KB) la fiecare focus; o încarc după 300 ms de stat pe titlu, rezoluție 780 px, nu 1280 |
| 12.2 | **Logo-ul filmului** în loc de text în hero și fișă | NuvioTV, Plex | 🟢 | 🟡 o cerere TMDB în plus per titlu, doar la cerere + cache |
| 12.3 | **Meniu sus cu file** (Acasă · Filme · Seriale · Lista mea · Caută · Setări) | Google TV, Stremio | 🟢 | 🟢 |
| 12.4 | **Lista mea** (watchlist) + „Văzut / nevăzut” manual | Plex, Stremio | 🟢 | 🟢 un tabel mic |
| 12.5 | **Fișă nouă**: fundal pe tot ecranul, tagline, durată, certificare, genuri, ★ TMDB + IMDb, butoane mari, surse grupate compact, sezoane ca file, distribuție cu poze | Infuse, Plex | 🟢 | 🟢 |
| 12.6 | **Trailer**: deschide aplicația YouTube a TV-ului | Stremio, Plex | 🟢 0 | 🟢 |
| 12.7 | **Rând „Nou în playlist”** după fiecare reîmprospătare + episoade noi la serialele urmărite | TiviMate, Plex | 🟢 | 🟢 |
| 12.8 | **Mărime postere** (mic / mediu / mare) și **culoare de accent** | TiviMate, Kodi skins | 🟢 | 🟢 postere mici = mai multe pe ecran, mai puțină memorie |
| 12.9 | ❌ Trailer redat automat în fundal (stil Netflix) | Netflix, NuvioTV | 🔴 player YouTube/WebView: +MB și RAM | 🔴 Nu recomand pe TV cu Android 11 |
| 12.10 | ❌ Efecte blur / sticlă pe fundal | Apple TV | – | 🔴 blur-ul nativ cere Android 12; emularea e lentă. Folosesc gradient întunecat |

---

## Etapa 13: Revamp setări

Structură pe două coloane (categorii în stânga, opțiuni în dreapta, fiecare cu valoarea curentă și o frază de explicație), ca setările Android TV și TiviMate:

- **Conținut**: limbi preferate, preferă limba originală, ascunde limbi/categorii nedorite, etichete DUB.
- **Aspect**: insigne pe postere (bifezi ce vrei), mărime postere, culoare, hero pe Acasă da/nu.
- **Redare**: profil surse (existent), frecvență ecran, buffer, viteză implicită, episodul următor automat.
- **Audio**: passthrough, mod noapte, limbă audio preferată.
- **Subtitrări**: (existent) + mărime, stil, întârziere implicită.
- **Surse și playlist-uri**: playlist-uri, reîmprospătare, **statistici** (câte filme, câte 4K, pe limbi, cât s-a potrivit).
- **Servicii**: cheie TMDB, note IMDb (ultima actualizare + „actualizează acum”), addon-uri Stremio.
- **Sistem**: backup/restaurare, configurare de pe telefon, golire cache imagini, update, despre.

APK 🟢, viteză 🟢. E doar reorganizare de ecrane.

---

## Etapa 14: „Slim” și viteză (fără funcții noi vizibile)

| # | Ce | Efect |
|---|----|-------|
| 14.1 | **Derulare rapidă în grile mari** (paginare după cheie în loc de `OFFSET`): la 90k filme, paginile din fund se încarcă la fel de repede ca primele | 🟢 vizibil la „Toate filmele” |
| 14.2 | **Postere cu memorie pe jumătate** (format RGB565, plafon la cache-ul de imagini pe disc) | 🟢 mai puțin RAM pe TV |
| 14.3 | **Baseline Profile** (pornire și derulare mai fluide) | 🟡 ~50–100 KB; cere un emulator în CI, deci build mai lung o dată |
| 14.4 | **APK pe arhitectură** (doar ARM pentru TV) | 🟡 7,5 MB → ~4–5 MB; updater-ul trebuie să aleagă APK-ul potrivit. Câștig mic, nu e prioritar |

---

## Etapa 15 (mai târziu, din lista veche de idei)

Profiluri (inclusiv copii, filtrare după certificarea din 10.4), corectare manuală a potrivirii, verificarea surselor moarte, Trakt.
Detalii în `docs/PLAN.md` → „Idei suplimentare”.

---

## Ce ar îngreuna aplicația (de evitat sau doar opțional)

1. **Trailere redate în aplicație / pe fundal** (YouTube, WebView): megabytes în plus și RAM. → Deschidem aplicația YouTube.
2. **Previzualizare la derulare (trickplay)**: imposibil fără să descarci filmul de la furnizor.
3. **Descrieri RO pentru tot catalogul**: dublează cererile TMDB. → Doar în fișă, la cerere.
4. **Distribuție salvată pentru toate titlurile**: zeci de MB în bază. → Rămâne la cerere, ca acum.
5. **Note de la MDBList/OMDb pentru fiecare poster**: limite zilnice mici. → IMDb din fișierul oficial; altele doar în fișă.
6. **Blur și animații grele** pe Android 11: sacadează derularea.

## Ordinea pe care o recomand

1. **Etapa 10** (ce ai cerut explicit: DUB, limba originală, note TMDB + IMDb, insigne, actori).
2. **Etapa 12** (revamp interfață, folosește datele din 10).
3. **Etapa 11** (player: frecvență ecran, passthrough, intro, per serial).
4. **Etapa 13** (setări noi, adună opțiunile din 10–12).
5. **Etapa 14** oricând, e mică.

Toate etapele 10–14 împreună: **0 biblioteci noi**, APK estimat +0,3–0,6 MB, bază de date +5–8 MB.
