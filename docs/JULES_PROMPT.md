# Prompt pentru Jules (copy-paste)

Copiază textul de mai jos în Jules, pe repo-ul `radumariusi/ak_vod_hub`, branch de bază `main`.
La fiecare sarcină nouă poți adăuga la final: „Lucrează acum la etapa X.Y din docs/PROPUNERI.md”.

---

You are taking over the Android TV app **AK VOD Hub** (Kotlin, Compose for TV) temporarily.

**Before writing any code, read in full:** `docs/HANDOFF.md`, `docs/PROPUNERI.md`, `docs/WORKFLOW.md`, `docs/PLAN.md`.
HANDOFF section 5 ("Capcane importante") lists rules that, if broken, break the app on the owner's TV.

**Task:** implement the roadmap in `docs/PROPUNERI.md` in the recommended order (stage 10 → 12 → 11 → 13 → 14),
**one stage (or one numbered item, e.g. 10.1) per branch and per pull request**. Check open PRs and `claude/*` branches first
so you don't duplicate work already started. Do not start the next item until the previous PR is merged.

**Hard rules (never break these):**
1. Never push to `main` directly. Work on a branch named `jules/<stage>-<short-name>`, open a PR to `main`, and **merge it yourself (squash) only after CI on the PR is green**. One stage/item per PR, so any PR can be reverted on its own.
2. Never force-push, rewrite history, or delete tags, releases or branches (especially tag `claude-handoff-2026-11-17` and branch `claude-snapshot`).
3. Never touch signing, secrets, `.github/workflows/*`, the signing config in `app/build.gradle.kts`, or the version scheme. The owner starts the Release workflow himself to get an APK.
4. Never commit APKs, build outputs, files > 1 MB, real playlists, API keys or credentials.
5. Database changes: bump the Room version and add an explicit `Migration`. Never use destructive migration.
6. Keep the app slim: no new library unless it cannot be done in a few hundred lines; justify every new dependency in the PR. minSdk stays 23; target device is Android 11 TV.
7. Performance: the real playlist has ~111,000 titles. Compute per-title data once at index time (`CatalogIndexer`) and store it; screens only read from the DB. No large `OFFSET` queries, no loading full lists into memory.
8. Touch: in UI code import the wrappers from `app/src/main/kotlin/io/github/radumariusi/akvodhub/ui/touch/TouchComponents.kt`, not Button/Card/Surface/ListItem/NavigationDrawerItem from `androidx.tv.material3`.
9. All user-facing text is in **Romanian**. Keep the existing UI style.
10. Put pure logic in `core:parser` (pure JVM) with unit tests.

**Before opening each PR:** run `./gradlew :core:parser:test testDebugUnitTest lintDebug assembleDebug` and ktlint (if the Android SDK is not
available in your environment, run at least `./gradlew :core:parser:test` and say so in the PR). CI on the PR must be green.

**Each PR must contain:** a short entry at the top of `CHANGELOG.md` (date, stage/item, what changed, in Romanian, 1–5 lines);
a PR description listing what was done, what was not done, how it was tested, and any impact on APK size / DB size / speed;
and an update of `docs/PROPUNERI.md` marking the item as done.

**Working style, so the owner can review everything on Thursday (keep / fix / revert per PR):**
- Small, focused PRs: one stage item per PR, no unrelated changes mixed in.
- No mass refactors, renames, moves or reformatting of existing code. Follow the existing architecture, package layout and conventions; extend, don't rewrite.
- PR description sections: **Ce** (what), **De ce** (why), **Fișiere** (main files touched), **Cum am testat**, **Impact** (APK / DB / speed), **Nefăcut / riscuri**.
- Every PR updates `CHANGELOG.md` and, if something structural changed (new table, module, worker, setting), the relevant section of `docs/HANDOFF.md`.
- Room schema only grows: new version number + additive `Migration` (new columns with defaults, new tables). Never drop/rename columns with user data.
- `versionCode` / version must keep increasing; it is computed automatically from the commit count on `main`, so don't touch it.
- If a PR turns out wrong, open a new PR that reverts or fixes it; never rewrite history.

If something is unclear or would require breaking a rule above, stop and write the question in the PR description instead of guessing.
