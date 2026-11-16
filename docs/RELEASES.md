# Release-uri, semnare și update din aplicație

## Cum ajunge un APK nou la tine

1. Un PR trece de CI (`.github/workflows/ci.yml`: ktlint, teste, lint, build debug) și e mergeuit în `main`.
2. `.github/workflows/release.yml` construiește APK-ul de release, semnat cu cheia din secretul `AK_SIGNING_KEY`.
3. APK-ul e publicat în [GitHub Releases](https://github.com/radumariusi/ak_vod_hub/releases) ca `ak-vod-hub-<versiune>.apk`, cu lista de schimbări (titlurile commit-urilor).
4. Se păstrează doar **ultimele 2** release-uri; cele vechi și tag-urile lor se șterg.

Versiunea e `<akvod.versionBase>.<număr de commit-uri pe main>` (ex. `0.1.3`), iar `versionCode` e numărul de commit-uri. Ambele cresc la fiecare merge.

## Update în aplicație

- La pornire (doar în build-ul de release) și din **Setări > Caută actualizări**, aplicația citește `https://api.github.com/repos/<akvod.updateRepo>/releases/latest`.
- Dacă versiunea e mai nouă, arată ce e nou și oferă **Actualizează**: descarcă APK-ul și îl instalează prin `PackageInstaller`.
- Prima dată, Android cere permisiunea **„Instalează aplicații necunoscute”** pentru AK VOD Hub. Aplicația deschide singură ecranul respectiv, dacă box-ul îl are.
- Build-ul de test (debug) are alt ID (`.debug`), deci stă alături de cel de release și nu se actualizează automat.

## Cheia de semnare

- O singură cheie (PKCS12, alias `akvodhub`), ținută doar în secretul GitHub `AK_SIGNING_KEY`, cu forma `<parolă>:<keystore în base64>`. Nu e niciodată în repo.
- Fără aceeași cheie, Android refuză să instaleze un APK nou peste cel vechi. **Păstrează o copie de rezervă** a cheii în afara GitHub.
- Dacă secretul lipsește, workflow-ul de release nu publică nimic și lasă un avertisment.

Adăugare secret: <https://github.com/radumariusi/ak_vod_hub/settings/secrets/actions/new>, Name `AK_SIGNING_KEY`, Secret = conținutul fișierului primit.

## Dacă repo-ul devine privat

API-ul public GitHub nu mai vede release-urile unui repo privat, deci update-ul din aplicație ar da 404. Soluția simplă: un repo public separat doar pentru release-uri (ex. `ak_vod_hub-releases`), în care workflow-ul publică APK-ul, și `akvod.updateRepo` din `gradle.properties` schimbat pe el. Aplicația nu are nevoie de niciun token.
