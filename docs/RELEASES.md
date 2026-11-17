# Release-uri, semnare și update din aplicație

## Cum ajunge un APK nou la tine

1. Un PR trece de CI (`.github/workflows/ci.yml`: ktlint, teste, lint, build debug) și e mergeuit în `main`. Asta **nu** mai publică un APK.
2. Când vrem un APK nou, pornim manual `.github/workflows/release.yml` ([Actions → Release → Run workflow](https://github.com/radumariusi/ak_vod_hub/actions/workflows/release.yml)). Construiește APK-ul de release din `main`, semnat cu cheia din secretul `AK_SIGNING_KEY`.
3. APK-ul e publicat în [GitHub Releases](https://github.com/radumariusi/ak_vod_hub/releases) ca `ak-vod-hub-<versiune>.apk`, cu lista de schimbări (titlurile commit-urilor).
4. Se păstrează permanent **v0.1.14 și tagul ei** (baza testată), plus **cea mai nouă altă versiune**. Celelalte release-uri și tag-urile lor pot fi eliminate automat. Tagul `claude-handoff-2026-11-17` și branch-ul `claude-snapshot` rămân protejate.

Versiunea e `<akvod.versionBase>.<număr de commit-uri pe main>` (ex. `0.1.3`), iar `versionCode` e numărul de commit-uri. Ambele cresc la fiecare merge, deci un release făcut mai târziu e mereu „mai nou” decât cel instalat.

De ce manual: din etapa 4, Radu a cerut ca etapele rămase (4–9) să fie făcute una după alta, cu un singur APK la final, nu câte unul după fiecare merge.

**Instrucțiune Radu, 2026-11-17:** livrarea etapelor complete se face prin update în aplicația de pe TV.
Agentul este autorizat să pornească workflow-ul Release pentru această livrare după CI verde și merge.
Excepția autorizată la interdicția modificării workflow-urilor privește exclusiv protejarea bazei v0.1.14
în curățare; semnarea, secretele și schema de versiuni nu se modifică.

## Update în aplicație

- La pornire (doar în build-ul de release) și din **Setări > Caută actualizări**, aplicația citește `https://api.github.com/repos/<akvod.updateRepo>/releases/latest`.
- Dacă versiunea e mai nouă, arată ce e nou și oferă **Actualizează**: descarcă APK-ul și îl instalează prin `PackageInstaller`.
- Prima dată, Android cere permisiunea **„Instalează aplicații necunoscute”** pentru AK VOD Hub. Aplicația deschide singură ecranul respectiv, dacă box-ul îl are.
- Build-ul de test (debug) are alt ID (`.debug`), deci stă alături de cel de release și nu se actualizează automat.

## Contractul APK-urilor pe arhitectură (Etapa 14)

Release-ul pregătit pentru etapa 14 include mereu universalul `ak-vod-hub-<versiune>.apk`, singurul
asset al cărui nume se termină în `.apk`. Updaterele vechi îl găsesc indiferent de ordinea asseturilor.
Cele trei variante `ak-vod-hub-<versiune>-<abi>.apk.bin` conțin APK-uri semnate și pot fi redenumite
cu extensia `.apk` pentru instalare manuală. Updaterul nou încearcă arhitecturile declarate de Android,
apoi universalul dacă dispozitivul acceptă una dintre arhitecturile lui.
Workflow-ul verifică arhitecturile din fișiere, certificatul față de v0.1.14 și dimensiunile înainte de publicare.

## Cheia de semnare

- O singură cheie (PKCS12, alias `akvodhub`), ținută doar în secretul GitHub `AK_SIGNING_KEY`, cu forma `<parolă>:<keystore în base64>`. Nu e niciodată în repo.
- Fără aceeași cheie, Android refuză să instaleze un APK nou peste cel vechi. **Păstrează o copie de rezervă** a cheii în afara GitHub.
- Dacă secretul lipsește, workflow-ul de release nu publică nimic și lasă un avertisment.

Adăugare secret: <https://github.com/radumariusi/ak_vod_hub/settings/secrets/actions/new>, Name `AK_SIGNING_KEY`, Secret = conținutul fișierului primit.

## Dacă repo-ul devine privat

API-ul public GitHub nu mai vede release-urile unui repo privat, deci update-ul din aplicație ar da 404. Soluția simplă: un repo public separat doar pentru release-uri (ex. `ak_vod_hub-releases`), în care workflow-ul publică APK-ul, și `akvod.updateRepo` din `gradle.properties` schimbat pe el. Aplicația nu are nevoie de niciun token.
