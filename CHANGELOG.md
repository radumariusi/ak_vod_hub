# Changelog

## 2026-11-17 — Etapa 12.3
- Meniu sus: Acasă, Filme, Seriale, Lista mea, Caută și Setări; navigare cu telecomanda și touch.

## 2026-11-17 — Etapa 12.2
- Logo TMDB în hero și fișă, la cerere, cu cache limitat și titlu text de rezervă.

## 2026-11-17 — Etapa 12.1
- Hero Acasă: fundal la 780 px, încărcat după 300 ms de focus stabil.

Intrări noi sus. Una scurtă pentru fiecare PR (dată, etapă, ce s-a schimbat).

## 2026-11-17: etapa 10 — livrare prin update TV
- Release-ul și tagul v0.1.14 sunt protejate permanent; curățarea păstrează baza și cea mai nouă versiune.
- Livrarea pentru TV folosește release-ul semnat și updaterul existent, la cererea lui Radu.

## 2026-11-17: etapa 10.8 — descrieri în română
- Descriere TMDB ro-RO numai la deschiderea fișei, cu engleza existentă dacă traducerea lipsește; cache 30 zile și migrare Room 11→12.
- Etapa 10 este implementată integral; fără descărcarea descrierilor RO pentru tot catalogul.

## 2026-11-17: etapa 10.7 — pagina actorului
- Din distribuție se deschide actorul cu filmele/serialele disponibile în playlist, paginare după ID și întoarcere la fișă.
- Filmografie doar la cerere, cache pentru 24 de actori, migrare aditivă Room 10→11; fără biblioteci noi.

## 2026-11-17: etapa 10.6 — insigne configurabile
- Setări pentru TMDB, IMDb, 4K/HDR, DUB, progres și „Nou”; preferințe incluse în backup.
- Texte precalculate, progres salvat și migrare aditivă Room 9→10; fără interogări pe poster.

## 2026-11-17: etapa 10.5 — note IMDb
- Note și voturi IMDb pe postere/fișă, actualizate săptămânal din arhiva oficială, citită în flux și nesalvată.
- Migrare Room 8→9; aplicare atomică din cache de lucru limitat la catalog, pauză la redare și actualizare manuală în Setări.

## 2026-11-17: etapa 10.3 — filtre de origine
- Filtre separate „Producții SUA” și „Limba originală engleză”, din metadatele TMDB salvate; fără cereri pe postere sau schimbări de schemă.

## 2026-11-17: etapa 10.4 — completare TMDB în fundal
- Completare reluabilă pentru IMDb ID, durată, certificare, țări de origine și tagline; migrare Room 7→8, fără reimport.
- Loturi paginate după ID și progres salvat; pauză în timpul redării, fără biblioteci sau worker nou.

## 2026-11-17: etapa 10.2 — limba originală
- Setare pentru surse și piste audio originale înaintea profilului, cu rezervă MULTI și alegere manuală păstrate.
- Filtru „Doar în limba originală” din date precalculate; migrare Room 6→7, preferință inclusă în backup.

## 2026-11-17: etapa 10.1 — eticheta DUB
- Etichetă DUB pe postere și în fișă, numai când toate sursele declară audio într-o altă limbă decât originalul TMDB.
- Calcul în flux la indexare, salvat în DB; migrare explicită 5→6, fără ștergerea datelor.

## 2026-11-17: predare temporară
- Adăugat `docs/HANDOFF.md` (stare proiect, capcane, plan) și `docs/JULES_PROMPT.md`.
- Punct de salvare: tag `claude-handoff-2026-11-17` și branch `claude-snapshot`.
