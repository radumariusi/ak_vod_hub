# Cercetare: aplicații și proiecte similare

Rezumat al proiectelor existente din care ne putem inspira (idei și arhitectură, nu cod copiat). Verificat în noiembrie 2026.

## Aplicații Android TV / IPTV open source

| Proiect | Ce face | Stack | Licență | Ce luăm de acolo |
|---|---|---|---|---|
| [NuvioTV](https://github.com/NuvioMedia/NuvioTV) | Client Android TV pentru addon-uri Stremio, cu bibliotecă, artwork, subtitrări, reluare redare | Kotlin, Compose + TV Material 3, Media3 | GPL-3.0 | Cel mai apropiat de UI-ul pe care îl vrem (rânduri, pagină detalii, alegere sursă). Cum consumă protocolul Stremio din Kotlin. |
| [ARVIO](https://github.com/ProdigyV21/ARVIO) | Hub Android TV: Jellyfin/Plex/Emby + addon-uri Stremio + IPTV M3U/Xtream + Trakt | Media3 + extensia FFmpeg | Apache-2.0 | Unifică mai multe surse într-un singur hub. Metadate din TMDB/Cinemeta, subtitrări OpenSubtitles. Licență permisivă. |
| [M3UAndroid](https://github.com/oxyroid/M3UAndroid) | Player IPTV M3U/Xtream, UI adaptiv telefon + TV | Kotlin, Compose, MVVM, Room, Hilt, ExoPlayer + FFmpeg | GPL-3.0 | Parsare M3U și Xtream, structura modulelor, Room pentru playlist-uri mari. |
| [OwnTV](https://github.com/oerd/OwnTV) | Player IPTV pentru Android TV | Kotlin | — | UI TV simplu pentru playlist-uri. |

Concluzie: niciuna nu face exact ce vrem. Playerele IPTV arată playlist-ul „așa cum e” (pe categorii ale furnizorului), iar clientele Stremio arată cataloage globale. **Noutatea noastră** este: extragem doar VOD-ul, îl potrivim cu TMDB și **grupăm toate sursele aceluiași film** (4K EN, 4K DE, 1080p în alte limbi) sub o singură fișă, iar căutarea/descoperirea/recomandările sunt limitate la ce există în playlist.

## Addon-uri Stremio relevante

| Proiect | Relevanță |
|---|---|
| [stremio-addon-sdk](https://github.com/Stremio/stremio-addon-sdk) + [protocolul](https://unpkg.com/stremio-addon-sdk@1.6.10/docs/protocol.md) | Protocolul e simplu (HTTP + JSON): `manifest.json`, `/catalog/`, `/meta/`, `/stream/`, `/subtitles/{type}/{id}.json`. Îl putem consuma direct din Kotlin, fără SDK. |
| OpenSubtitles v3 (addon oficial Stremio, [npm](https://www.npmjs.com/package/stremio-opensubtitles)) | Subtitrări după ID IMDb (`tt123` sau `tt123:sezon:episod`). Sursa implicită de subtitrări. |
| [stremio-community-subtitles](https://github.com/skoruppa/stremio-community-subtitles), [StremioSubMaker](https://github.com/xtremexq/StremioSubMaker) | Alte addon-uri de subtitrări (comunitate, traducere automată). Utilizatorul le poate adăuga după URL. |
| [AIOMetadata](https://github.com/cedya77/aiometadata) | Addon de metadate/cataloage (TMDB, liste). Exemplu de cataloage și liste pe care le putem intersecta cu playlist-ul. |
| Xtremio / „m3u-iptv” / „IPTV Bridge” ([exemplu cod](https://huggingface.co/spaces/mikmc/xtremio-plus/blob/main/addon.js)) | Addon-uri care expun Xtream în Stremio. **Folosesc ID-urile furnizorului, fără potrivire TMDB/IMDb**, deci nu grupează sursele. Exact golul pe care îl acoperim noi. |

## Xtream Codes API (ce folosim)

`player_api.php?username=..&password=..&action=`:

- `get_vod_categories`, `get_vod_streams` (lista de filme, uneori cu `tmdb` / `tmdb_id`)
- `get_vod_info&vod_id=` (detalii, des cu `tmdb_id`, durată, an)
- `get_series_categories`, `get_series`, `get_series_info&series_id=` (sezoane și episoade)
- URL redare: `{server}/movie/{user}/{pass}/{id}.{ext}` și `{server}/series/{user}/{pass}/{id}.{ext}`

Live TV (`get_live_*`) **nu îl folosim**: aplicația e doar VOD.

## Parsare nume de titluri

Nu există o bibliotecă Kotlin matură. Referințe pentru reguli și cazuri de test: [parse-torrent-title (Python)](https://pypi.org/project/parse-torrent-title/), [parse-torrent-filename (JS)](https://cdn.jsdelivr.net/npm/parse-torrent-filename@1.0.2/readme.md), [go-parse-torrent-name](https://pkg.go.dev/github.com/middelink/go-parse-torrent-name). Scriem un parser propriu în Kotlin, adaptat la formatul IPTV (prefixe de țară/limbă ca `EN|`, `|DE|`, `RO -`, etichete `4K`, `FHD`, `MULTI-SUB`), cu un set mare de teste.
