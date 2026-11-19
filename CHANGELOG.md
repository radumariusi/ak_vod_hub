# Changelog

## 2026-11-19 — Limba audio și rezoluția surselor (C)

- „RO - Titlu” (și secțiunile NL, SE, NO, DK, FI, GR) înseamnă audio original cu subtitrare în limba secțiunii. Limba originală vine de la TMDB: Clanul sau Burebista rămân „Audio RO”, un film american devine „Audio EN · Sub RO”. Fără potrivire TMDB, sursa apare „Audio Original”.
- Țara din coada numelui după an („(TR)”, „(US)”, „(RO)”) dă limba audio. VO/VOST/VOSTFR/OmU = audio original; SUBTITRAT/SUBTITULADO = subtitrare.
- Categoriile cu nume de țară (ITALY, FRANCE, ESPAÑA, ROMANIA…) și QC/LA sunt recunoscute. IN, BE, CH, CA nu mai sunt luate drept limbi (hindi, olandeză, germană).
- Rezoluția scrisă ca 1920x1080 în nume e citită. Dolby Vision e recunoscut și cu litere mici în superscript.
- Episoadele Xtream iau limba audio trimisă de furnizor („rum” → RO). Limbile pistelor audio văzute la redare se salvează pe sursă și au prioritate (migrare 19 → 20).
- Setări → Conținut → „Limba audio pe categorii”: pentru o categorie greșită alegi Automat, Audio original sau Dublat. Sursele se reanalizează în fundal.
- Catalogul salvat se reanalizează o dată în fundal, în loturi, cu regulile noi.

## 2026-11-19 — Propuneri ușoare (B)

- Tasta Play / Play-Pauză a telecomenzii pe un poster (Acasă, Filme, Seriale, Căutare) pornește direct redarea, fără fișă.
- Sursa care a pornit ultima dată pentru un titlu se încearcă prima. O sursă cu 2 eșecuri la rând coboară la final 7 zile și apare cu „⚠ a eșuat recent”. Datele stau într-un singur rând din cache, fără migrare.
- Căutare: ultimele 5 căutări (cele care au dus la o fișă deschisă) apar când câmpul e gol.
- Setări → Avansate → „Diagnostic surse”: câte surse nu au limbă sau calitate și categoriile mari fără limbă. Pagina „Configurare de pe telefon” are „Descarcă diagnostic”: categorii, un eșantion de nume cu ce a dedus parserul și metadatele tehnice Xtream (`info.video` / `info.audio`) ale unui film și ale unui serial, fără adrese, conturi sau parole.

## 2026-11-19 — Corecții de interfață (A)

- Focus mai vizibil: margine de 3 dp aproape albă pe carduri și suprafețe, iar posterele se măresc ușor (5%).
- Insignele și meta nu mai afișează „★ —” / „IMDb —” când nota lipsește; textul insignelor e mai mare.
- Fișa are acțiunile pe un rând (Redă, De la început, Episoade/Surse, Audio și subtitrări, Lista mea, Văzut), ca sursele să rămână pe ecran. Tagline-ul apare doar fără descriere.
- O limbă cu o singură sursă pornește direct („▶ RO”); în dialogul variantelor apare ce le deosebește, nu „varianta N”.
- „OK · Sari intro” apare singur 10 s cât rulează intro-ul marcat al serialului; butoanele ±10 s din comenzi au fost scoase (stânga/dreapta pe bară fac același lucru).
- Etichetele surselor sunt mai scurte („Audio RO · Sub EN · Rezoluție ✓”).

## 2026-11-19 — Identificarea și gruparea surselor

- Citește prefixele compuse și marcajele 4K/HDR din nume și categorii, cu etichetele pistelor audio afișate separat.
- Reprocesează catalogul salvat în loturi și potrivește numele bilingve cu titlul original și traducerea TMDB, pe baza anului.

## 2026-11-19 — Focus și derulare în Filme și Seriale

- Grila se aliniază la rândul cu focus, inclusiv la capătul catalogului. Derularea tactilă se oprește la începutul unui rând.
- OK pe orice poster deschide fișa. O apăsare lungă pe OK mută focusul la Detalii pentru același titlu.

## 2026-11-19 — Pornirea ecranului Acasă

- Acasă afișează rândurile locale pe măsură ce se încarcă. Reindexarea începe după afișarea datelor deja salvate.
- Notificările din timpul încărcării se grupează și actualizează ecranul după citirea în curs.

## 2026-11-19 — Retușuri fișă, sezoane și catalog TV

- Sigla rămâne vizibilă la intrarea în fișă. Descrierea apare o singură dată și se poate extinde.
- Audio și subtitrări listează pistele declarate și detectate pe sursă, inclusiv per episod.
- Sezoanele TMDB includ marcaje pentru disponibilitate și audio. După import se pregătește un lot scurt de seriale.
- Hero folosește un singur fundal, filtrele intră în zona de acțiuni, iar meniul TV se centrează cu marca la stânga.

## 2026-11-18 — Interfață TV panoramică

- Acasă, Filme și Seriale au hero cu sigla titlului, Redă și Detalii. Meniul și posterele folosesc același stil.
- Fișa are acțiuni verticale, acces la surse și piste, iar serialele au un ecran dedicat în două panouri pentru sezoane și episoade.
- Setările și comenzile playerului folosesc aceeași paletă, cu accentul ales de utilizator.

## 2026-11-18 — Sigle mai rapide și recuperarea redării

- Siglele au bugete de încărcare scurte, iar vecinul începe devreme și continuă dacă primește focus. Filme și Seriale folosesc posterele de pe Acasă.
- Playerul reduce bitrate-ul fluxului adaptiv sau încearcă o sursă de rezoluție mai mică la întreruperi repetate, inclusiv la 1080p.
- Alegerea automată afișează clar prioritatea 4K EN, RO sau MULTI și separă regulile personalizate.
- Sus din conținut revine la fila activă. Stânga din opțiuni revine la categoria deschisă din Setări.

## 2026-11-18 — Sigle la navigare și lista episoadelor

- Siglele deja decodate apar direct la revenire. Hero începe încărcarea pe focus și pregătește vecinii direcționali după ce termină.
- Lista episoadelor folosește selector de sezon, miniaturi 16:9, durată și progres. Metadatele se cer pentru episodul pe care rămâne focusul.

## 2026-11-18 — Sigle pentru filme și seriale

- Hero și fișă acceptă sigle TMDB PNG și SVG. Dacă o imagine nu se încarcă, se încearcă următoarea siglă în engleză sau neutră.
- Rezultatele goale vechi se reverifică la acces. Cererea se anulează când utilizatorul părăsește titlul afișat.

## 2026-11-18 — Acasă și redare TV

- Fundal pe tot ecranul pe Acasă, „Continuă vizionarea” pe lat și postere portret fără titlu repetat.
- Confirmare la ieșire și redare/pauză direct cu OK. Buffer mai generos și trecere la o variantă disponibilă la întreruperi repetate.


## 2026-11-18 — Retușuri după testarea v0.1.40

- Previzualizare comună pentru Acasă, Filme și Seriale cu fundalul și sigla TMDB în engleză. Focus stabil și componente TV aliniate.
- Episoadele Xtream apar pe măsură ce sosesc, cu etichete românești pentru numele generice. Fișa păstrează vizionarea în aplicație.
- Subtitrările au coloane interne și externe. Rezoluția confirmată la redare rămâne asociată sursei.


## Etapa 16 — UX și corecții TV

- Refresh Xtream atomic, cu diagnostic pe operație și păstrarea catalogului anterior la eroare.
- Antet, postere și portrete corectate, metadate în engleză și câmpuri separate pentru notele TMDB și IMDb.
- Filtre pe aceeași sursă, sortări explicite, combinații salvate, Acasă configurabilă și progres pe episod.
- Comenzi player pe două niveluri, setări de vizibilitate, tastatură la activare și ferestre de paginare limitate.


## 2026-11-18 — Corecții UI și player TV
- Spațiu de focus, contrast, titluri și distribuție centrate, un singur indicator de filă.
- Comenzi compacte, un singur play/pauză la OK pe bară, subtitrări interne și externe și meniuri întunecate.
- Calitatea și limba necunoscute au etichete distincte. Limbile declarate se completează în fundal, cu Room v14.

## 2026-11-18 — Etapa 14
- Rândurile și grilele catalogului folosesc paginare după cheie. Cache-ul Coil are plafon de 64 MiB și poate decoda posterele JPEG TMDB în RGB565.
- Updaterul selectează APK-ul compatibil cu procesorul și păstrează compatibilitatea cu updaterul vechi prin APK-ul universal.
- Profilele AndroidX de pornire și de derulare provin din rularea pe Android 11 și includ regulile aplicației.

## 2026-11-17 — Etapa 13
- Setări în două panouri, opt categorii, telecomandă și touch; valori curente și explicații în română.
- Preferințe noi funcționale cu backup, statistici SQL în fundal și golire cache imagini. Room v14 neschimbat.

## 2026-11-17 — Etapa 11: player
- Frecvență ecran, passthrough și mod noapte opționale; format imagine și viteză.
- Intro și limbi audio/subtitrare per serial, derulare accelerată, salt la minut, info tehnic, timer și buffer configurabil.
- Preferințe persistente incluse în backup; fără dependențe sau migrare Room. Necesită testarea proprietarului pe TV.

## 2026-11-17 — Etapa 12.8
- Aspect: postere mici/medii/mari și cinci culori de accent, cu salvare și backup; etapa 12 completă pentru testarea pe TV.

## 2026-11-17 — Etapa 12.7
- Rânduri Nou în playlist și Episoade noi la serialele urmărite; refresh Xtream limitat în fundal și paginare după dată/ID.

## 2026-11-17 — Etapa 12.6
- Trailer din fișă în aplicația YouTube, la cerere, cu cache și tratarea lipsei aplicației/trailerului.

## 2026-11-17 — Etapa 12.5
- Fișă cu fundal integral, acțiuni mari, surse compacte pe calitate/limbă și file pentru sezoane.

## 2026-11-17 — Etapa 12.4
- Lista mea salvată în DB, cu paginare după ID; adăugare/scoatere și văzut/nevăzut manual din fișă.

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
