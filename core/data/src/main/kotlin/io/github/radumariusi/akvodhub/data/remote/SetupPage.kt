package io.github.radumariusi.akvodhub.data.remote

/** Pagina deschisă pe telefon (fără resurse externe: merge și fără internet, doar în rețeaua locală). */
internal object SetupPage {
    val EXPIRED =
        """
        <!doctype html><html lang="ro"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1"><title>AK VOD Hub</title></head>
        <body style="font-family:sans-serif;background:#111;color:#eee;padding:24px">
        <h2>Link expirat</h2><p>Deschide pe TV <b>Setări → Configurare de pe telefon</b> și scanează din nou codul QR.</p>
        </body></html>
        """.trimIndent()

    val HTML =
        """
        <!doctype html>
        <html lang="ro">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>AK VOD Hub · Configurare</title>
        <style>
        body{font-family:system-ui,sans-serif;background:#111;color:#eee;margin:0;padding:16px;max-width:640px}
        h1{font-size:22px;margin:4px 0 16px}
        section{background:#1d1d1f;border-radius:12px;padding:16px;margin-bottom:16px}
        h2{font-size:17px;margin:0 0 10px}
        label{display:block;font-size:13px;color:#aaa;margin:10px 0 4px}
        input{width:100%;box-sizing:border-box;padding:12px;border-radius:8px;border:1px solid #444;
        background:#000;color:#fff;font-size:16px}
        button,a.btn{display:inline-block;margin-top:12px;padding:12px 18px;border:0;border-radius:8px;
        background:#e50914;color:#fff;font-size:16px;text-decoration:none}
        .msg{margin-top:10px;font-size:14px}
        .ok{color:#6fdc8c}.err{color:#ff8a80}
        small{color:#999}
        a{color:#8ab4f8}
        </style>
        </head>
        <body>
        <h1>AK VOD Hub · Configurare de pe telefon</h1>

        <section>
        <h2>Playlist M3U</h2>
        <form data-api="m3u">
        <label>Nume (opțional)</label><input name="name" autocomplete="off">
        <label>Link M3U</label><input name="url" type="url" required placeholder="http://…/get.php?…">
        <button>Adaugă</button><div class="msg"></div>
        </form>
        </section>

        <section>
        <h2>Cont Xtream</h2>
        <form data-api="xtream">
        <label>Nume (opțional)</label><input name="name" autocomplete="off">
        <label>Server</label><input name="server" required placeholder="http://server:8080">
        <label>Utilizator</label><input name="username" required autocomplete="off" autocapitalize="off">
        <label>Parolă</label><input name="password" type="password" required>
        <button>Adaugă</button><div class="msg"></div>
        </form>
        </section>

        <section>
        <h2>Cheie TMDB</h2>
        <small>Gratuită: cont pe <a href="https://www.themoviedb.org/settings/api" target="_blank">themoviedb.org</a>
        → Settings → API → „API Key”.</small>
        <form data-api="tmdb">
        <label>Cheia API (v3)</label><input name="key" required autocomplete="off" autocapitalize="off">
        <button>Salvează</button><div class="msg"></div>
        </form>
        </section>

        <section>
        <h2>Addon Stremio</h2>
        <small>Subtitrări sau cataloage (ex. https://opensubtitles-v3.strem.io).</small>
        <form data-api="addon">
        <label>Adresa addon-ului</label><input name="url" required autocomplete="off" autocapitalize="off">
        <button>Adaugă</button><div class="msg"></div>
        </form>
        </section>

        <section>
        <h2>Backup setări</h2>
        <small>Playlist-urile (cu parolele conturilor), cheia TMDB, addon-urile și preferințele de redare.
        Păstrează fișierul ca pe o parolă.</small><br>
        <a class="btn" id="dl" href="#">Descarcă backup</a>
        <label>Restaurează dintr-un fișier</label><input id="file" type="file" accept=".json,application/json">
        <button id="restore">Restaurează</button><div class="msg" id="rmsg"></div>
        </section>

        <section>
        <h2>Diagnostic surse</h2>
        <small>Nume de categorii și un eșantion de titluri, cu limba și calitatea deduse, plus metadatele tehnice
        ale unui film și ale unui serial Xtream. Fără adrese, conturi sau parole. Durează câteva secunde.</small><br>
        <a class="btn" id="diag" href="#">Descarcă diagnostic</a>
        </section>

        <script>
        var token = new URLSearchParams(location.search).get('t') || '';
        function show(el, ok, text) { el.className = 'msg ' + (ok ? 'ok' : 'err'); el.textContent = text; }
        function send(api, body, el, done) {
          show(el, true, 'Se trimite la TV…');
          fetch('/api/' + api + '?t=' + encodeURIComponent(token), { method: 'POST', body: body })
            .then(function (r) { return r.json(); })
            .then(function (j) { show(el, j.ok, j.message); if (j.ok && done) done(); })
            .catch(function () { show(el, false, 'TV-ul nu răspunde. Ecranul de configurare e încă deschis?'); });
        }
        document.querySelectorAll('form[data-api]').forEach(function (f) {
          f.addEventListener('submit', function (e) {
            e.preventDefault();
            var data = {};
            new FormData(f).forEach(function (v, k) { data[k] = v; });
            send(f.dataset.api, JSON.stringify(data), f.querySelector('.msg'), function () { f.reset(); });
          });
        });
        document.getElementById('dl').href = '/api/backup?t=' + encodeURIComponent(token);
        document.getElementById('diag').href = '/api/diagnostic?t=' + encodeURIComponent(token);
        document.getElementById('restore').addEventListener('click', function () {
          var file = document.getElementById('file').files[0];
          var el = document.getElementById('rmsg');
          if (!file) { show(el, false, 'Alege întâi fișierul de backup.'); return; }
          var reader = new FileReader();
          reader.onload = function () { send('restore', reader.result, el); };
          reader.readAsText(file);
        });
        </script>
        </body>
        </html>
        """.trimIndent()
}
