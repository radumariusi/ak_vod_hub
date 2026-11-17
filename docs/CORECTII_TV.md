# Corecții UI și player pentru Android TV

Fir separat de roadmap, bazat pe `main` la `16ec696`. PR-ul #36 al etapei 14 era draft la pornire.
Există suprapunere în `Cards.kt`, `BrowseScreen.kt`, CHANGELOG și HANDOFF. La integrarea etapei 14,
păstrați spațiul de focus și culorile din acest PR împreună cu paginarea și optimizarea imaginilor din #36.

## Plan și verificare pe Android 11 TV

1. **Margini și derulare.** Parcurge primul și ultimul element din meniul principal, filtre, surse,
   sezoane, distribuție, recomandări și comenzi. Repetă pentru liste scurte și lungi, inclusiv în dialoguri.
2. **Contrast.** Verifică Acasă, Filme, fișa, dialogurile și playerul peste o scenă luminoasă.
   Compară textul și pictogramele unui control normal, focalizat și selectat.
3. **Surse.** Compară fișa, selectorul Sursă și eticheta din player cu numele și categoria de la furnizor.
   Adaugă un exemplu cu MULTI audio, unul cu EN/ENG 5.1, unul cu MULTI-SUB și unul fără metadate.
   Verifică o sursă existentă după procesarea în fundal, fără reimport. Calitatea și limba pot lipsi independent.
4. **Distribuție.** Verifică centru, focus, margini și casete egale pentru nume/personaje lungi sau absente.
5. **Postere.** În Aspect, alege pe rând Mic, Mediu și Mare. Focalizează postere cu titlu de două rânduri
   în Acasă, Filme, Seriale, Căutare, Lista mea, recomandări și filmografia actorului.
6. **Comenzi.** Derulează rândul până la capăt cu telecomanda și prin atingere. Verifică pictogramele și
   descrierile la focus. Verifică redare, derulare, audio, subtitrări, sursă și opțiunile avansate ale etapei 11.
7. **Bara de progres.** Apasă și ține OK, Enter sau DPAD_CENTER pe bară. Fiecare apăsare comută o singură
   dată. Tastele media funcționează pe bară, Stânga/Dreapta accelerează, iar atingerea și tragerea doar derulează.
   OK pe Audio/Subtitrări deschide selectorul fără să comute redarea.
8. **Indicatoare.** Focusează o filă activă și una inactivă în meniul principal și în sezoane. Verifică un singur
   indicator pe fila activă și focusul distinct.
9. **Subtitrări.** Deschide o sursă cu piste încorporate și rezultate din cel puțin un addon. Verifică grupurile
   Interne/Externe, numele addon-ului, selecția curentă, Oprite/Automat și preferințele după schimbarea episodului.
   Alege o pistă la începutul și la sfârșitul fiecărui grup și verifică limba și sincronizarea.
10. **Meniuri player.** Verifică Audio, Subtitrări, Sursă, Salt la minut, Info tehnic, Ajustează, eroarea sursei și
    episodul următor. Testează lista lungă și contrastul peste video luminos.
11. **Regresii conexe.** Verifică subtitrări cu comenzile deschise, Înapoi din Ajustează, eroare, numărătoare,
    fișă și filmografie. În saltul la minut, verifică mesajul pentru o valoare invalidă. Pe ecran îngust,
    verifică accesul la acțiunile din fișă și la butoanele de sincronizare și stil.

## Livrare

După CI verde și squash merge, pornește Release din `main`. Compară dimensiunea APK și amprenta SHA-256
a certificatului cu baza v0.1.14, verifică endpointul public folosit de updater și prezența release-ului/tagului
v0.1.14 și a branch-ului `claude-snapshot`. Pe TV, actualizează din Setări → Caută actualizări fără dezinstalare.
După instalare confirmă playlist-urile, progresul, Lista mea și preferințele. Retestarea pe dispozitiv este
în așteptarea feedbackului proprietarului.
