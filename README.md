# AK VOD Hub

Aplicație Android TV care extrage **doar VOD-ul** (filme și seriale) dintr-un playlist IPTV (M3U sau Xtream) și îl transformă într-un hub cu căutare, descoperire și recomandări. Fiecare titlu apare o singură dată, potrivit cu TMDB, cu toate sursele din playlist grupate pe calitate și limbă (ex. *4K în EN și DE, 1080p în alte 7 limbi*). Subtitrări prin addon-uri Stremio.

Aplicația nu conține și nu oferă conținut. Utilizatorul își adaugă propriul playlist.

## Documente

- [Plan pe etape](docs/PLAN.md)
- [Reguli de lucru și igienă GitHub](docs/WORKFLOW.md)
- [Cercetare: proiecte similare](docs/RESEARCH.md)
- [Release-uri, semnare și update](docs/RELEASES.md)

Licență: [GPL-3.0](LICENSE).

## Instalare

Ultimul APK: [Releases](https://github.com/radumariusi/ak_vod_hub/releases/latest). După prima instalare, aplicația se actualizează singură din GitHub (Setări > Caută actualizări).

## Build local

Ai nevoie de JDK 21 și Android SDK (Android Studio). Apoi:

```
./gradlew assembleDebug        # APK de test: app/build/outputs/apk/debug/
./gradlew :core:parser:test testDebugUnitTest lintDebug
```

Module: `app` (UI TV), `core:parser` (Kotlin pur), `core:data` (rețea, date, updater), `core:player` (Media3 + FFmpeg audio).

## Stare

Toate etapele (0–9) sunt gata: import de playlist-uri (M3U link/fișier, Xtream) doar cu filme și seriale, parser de titluri, potrivire TMDB cu unirea duplicatelor și gruparea surselor pe calitate și limbă, hub-ul (Acasă, Filme, Seriale, Căutare, Detalii), player propriu (Media3 + FFmpeg audio) cu trecere automată între surse, subtitrări din addon-uri Stremio, recomandări doar din playlist, rândul Watch Next și un canal pe ecranul principal Android TV, căutare globală / vocală și configurare de pe telefon (cod QR) cu backup al setărilor.
