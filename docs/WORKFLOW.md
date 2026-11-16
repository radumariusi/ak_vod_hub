# Cum lucrăm (reguli de lucru)

Regulile astea se aplică în fiecare etapă. Scopul: muncă ordonată pe etape și un repo GitHub care rămâne mic, ca să nu atingem limitele contului gratuit.

## Etape

1. Munca e împărțită pe etape (vezi [PLAN.md](PLAN.md)). **Un fir de discuție nou pentru fiecare etapă.**
2. Fiecare etapă = **un branch + un PR**. Branch-ul se șterge imediat după merge.
3. O etapă e gata când: PR-ul e aprobat și mergeuit, CI e verde, iar `docs/PLAN.md` are bifa la etapa respectivă.
4. Nu începem etapa următoare până nu e mergeuită cea curentă (excepție: lucruri independente, cu acordul lui Radu).

## GitHub ușor

| Ce | Regulă |
|---|---|
| Build outputs (APK, AAB, `build/`, `node_modules/`) | **Nu se comit niciodată.** Sunt în `.gitignore`. |
| Fișiere mari / binare | Fără fișiere > 1 MB în repo. Fără Git LFS. Imaginile din UI se țin mici (WebP / vector). |
| Playlist-uri reale | **Nu se comit** (conțin user/parolă). Pentru teste folosim exemple anonimizate în `testdata/`. |
| Secrete (cheie TMDB, keystore) | Doar local (`local.properties`) sau în GitHub Secrets. Niciodată în cod. |
| Branch-uri | Se șterg după merge (setarea „Automatically delete head branches” activă). Branch-urile vechi nefolosite se șterg. |
| Artefacte CI | `retention-days: 3`. APK-ul se urcă ca artefact doar la rulare manuală, nu la fiecare push. |
| Rulări CI vechi, cache-uri | Șterse automat săptămânal de `.github/workflows/cleanup.yml` (artefacte > 3 zile, rulări > 14 zile, cache-uri nefolosite > 7 zile). |
| Release-uri | Păstrăm doar **ultimele 2** release-uri cu APK. Cele vechi (și tag-urile lor) se șterg. |
| Istoric | Commit-uri mici și clare. PR-urile se pot face squash la merge, ca istoria să rămână curată. |
| CI | Rulează doar pe PR și pe `main`, cu `concurrency` (anulează rulările vechi) și timeout. |

## Setări GitHub de făcut o singură dată (manual, de Radu)

- **Settings → General → Pull Requests:** bifează „Automatically delete head branches” și (opțional) lasă doar „Allow squash merging”.
- **Settings → Actions → General → Artifact and log retention:** setează **3 zile**.
- **Settings → Actions → General → Workflow permissions:** „Read and write permissions” (necesar pentru workflow-ul de curățenie).

## Cod

- Kotlin, stil oficial (`ktlint`). Logica pură (parser, potrivire titluri) are teste unitare JVM, rapide.
- Fără dependențe grele adăugate „pentru orice eventualitate”. Fiecare bibliotecă nouă se justifică în PR.
- Nu copiem cod din proiecte GPL decât dacă alegem și noi GPL (vezi decizia de licență în PLAN.md). Ne inspirăm din idei, nu copiem.
