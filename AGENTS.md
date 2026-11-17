# Instrucțiuni pentru agenți AI

Citește întâi `docs/HANDOFF.md` și `docs/JULES_PROMPT.md`. Regulile de acolo sunt obligatorii:
branch + PR pe etapă, fără push direct pe `main`, fără force-push, fără ștergere de tag-uri/release-uri/branch-uri,
nu atinge semnarea, secretele sau `.github/workflows/`, migrări Room explicite, UI în română, aplicația rămâne slim.

## Actualizare de la Radu (2026-11-17): livrarea etapelor complete

Radu cere update direct în aplicația de pe TV și autorizează pornirea workflow-ului Release după CI verde.
Se păstrează permanent release-ul **v0.1.14 și tagul ei**, plus protecțiile claude-handoff-2026-11-17/claude-snapshot.
Curățarea poate elimina celelalte versiuni, păstrând baza și cea mai nouă versiune. Este autorizată numai
ajustarea curățării din release.yml pentru această protecție; semnarea/secretele/versiunea nu se modifică.
Vezi docs/RELEASES.md. APK-ul debug separat nu este livrarea pentru TV.
