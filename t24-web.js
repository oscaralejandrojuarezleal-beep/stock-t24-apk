/*
 * Stock T24 — versión web (GitHub Pages).
 *
 * La app es la MISMA que usa la APK: se baja completa desde Apps Script (T24_apiAppApk)
 * y queda guardada en el navegador. Así abre al instante y no pasa por el link de Google.
 * Los datos siguen yendo y viniendo de Apps Script como siempre.
 *
 * Este archivo lo usan la página de carga (index.html) y la app guardada (app.html):
 *   - T24Web.bajar()   baja la app y la guarda.
 *   - Revisa cada 10 minutos si hay una versión nueva publicada y la deja lista.
 */
(function () {
  'use strict';

  var API_URL = 'https://script.google.com/macros/s/AKfycbwqzbbx6GfSuEy-WbwyNfVb--T3Gnru87hpetct_S53qsCtn7QcMngn4mNoip8e4nS-/exec';
  var CACHE = 't24-contenido';
  var CADA_MS = 10 * 60 * 1000;

  // La app usa esta dirección para hablar con el servidor (igual que la APK).
  window.T24_API_URL = API_URL;
  try { localStorage.setItem('T24_API_URL', API_URL); } catch (e) {}

  function leer(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } }
  function guardar(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }

  function llamar(fn, ms) {
    var control = typeof AbortController === 'function' ? new AbortController() : null;
    var reloj = setTimeout(function () { if (control) control.abort(); }, ms || 5 * 60 * 1000);
    return fetch(API_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: JSON.stringify({ fn: fn, args: [] }),
      redirect: 'follow',
      cache: 'no-store',
      signal: control ? control.signal : undefined
    }).then(function (res) {
      if (!res.ok) throw new Error('El servidor respondió ' + res.status);
      return res.text();
    }).then(function (texto) {
      clearTimeout(reloj);
      var d;
      try { d = JSON.parse(texto); } catch (e) { throw new Error('Respuesta inválida del servidor'); }
      if (!d || d.ok !== true) throw new Error((d && d.error) || 'Error del servidor');
      if (!d.r || d.r.ok === false) throw new Error((d.r && d.r.error) || 'Error del servidor');
      return d.r;
    }, function (e) {
      clearTimeout(reloj);
      if (e && e.name === 'AbortError') throw new Error('El servidor tardó demasiado');
      throw e;
    });
  }

  // La app guardada lleva este mismo archivo, para revisar versiones nuevas sola.
  function prepararHtml(html) {
    var etiqueta = '<script src="t24-web.js"></script>';
    if (/<head[^>]*>/i.test(html)) return html.replace(/<head[^>]*>/i, function (m) { return m + etiqueta; });
    return etiqueta + html;
  }

  var bajando = null;
  function bajar() {
    if (bajando) return bajando;
    bajando = llamar('T24_apiAppApk', 7 * 60 * 1000).then(function (r) {
      if (!r.html || r.html.length < 1000) throw new Error('La app llegó vacía');
      var html = prepararHtml(r.html);
      var listo = window.caches
        ? caches.open(CACHE).then(function (c) {
            return c.put('app.html', new Response(html, { headers: { 'Content-Type': 'text/html; charset=utf-8' } }));
          })
        : Promise.resolve();
      return listo.then(function () {
        guardar('T24_WEB_BUILD', r.build || '');
        return { build: r.build || '', html: html };
      });
    });
    bajando.then(function () { bajando = null; }, function () { bajando = null; });
    return bajando;
  }

  function tieneApp() {
    if (!window.caches) return Promise.resolve(false);
    return caches.open(CACHE).then(function (c) { return c.match('app.html'); }).then(function (r) { return !!r; }, function () { return false; });
  }

  // ---------- Aviso de versión nueva (sólo dentro de la app) ----------
  function cartel() {
    if (document.getElementById('t24WebNueva')) return;
    var d = document.createElement('div');
    d.id = 't24WebNueva';
    d.setAttribute('role', 'status');
    d.style.cssText = 'position:fixed;left:12px;right:12px;bottom:calc(12px + env(safe-area-inset-bottom));z-index:2147483647;' +
      'max-width:520px;margin:0 auto;background:#003d32;color:#fff;border-radius:16px;padding:12px 14px;' +
      'box-shadow:0 10px 30px rgba(0,0,0,.35);display:flex;gap:10px;align-items:center;font:600 14px system-ui,-apple-system,Segoe UI,Roboto,sans-serif';
    d.innerHTML = '<span style="flex:1">Hay una versión nueva de la app.</span>' +
      '<button type="button" id="t24WebDespues" style="border:0;background:transparent;color:#cfe;font-weight:700;padding:8px">Después</button>' +
      '<button type="button" id="t24WebAhora" style="border:0;border-radius:10px;background:#fff;color:#003d32;font-weight:800;padding:9px 14px">Actualizar</button>';
    document.body.appendChild(d);
    document.getElementById('t24WebDespues').onclick = function () { d.remove(); };
    document.getElementById('t24WebAhora').onclick = function () { location.reload(); };
  }

  var ultima = 0, revisando = false;
  function revisar(forzar) {
    if (revisando || navigator.onLine === false) return;
    var ahora = Date.now();
    if (!forzar && ahora - ultima < CADA_MS) return;
    ultima = ahora;
    revisando = true;
    llamar('T24_apiHuellaApp', 2 * 60 * 1000).then(function (r) {
      var actual = leer('T24_WEB_BUILD');
      if (!r.huella || r.huella === actual) return;
      return bajar().then(cartel);
    }).catch(function () { /* sin internet o servidor ocupado: se reintenta más tarde */ })
      .then(function () { revisando = false; });
  }

  window.T24Web = { apiUrl: API_URL, bajar: bajar, tieneApp: tieneApp, revisar: revisar };

  // Dentro de la app guardada: revisar al rato de abrir, cada 10 min y al volver a la pestaña.
  if (/app\.html(\?|#|$)/.test(location.pathname + location.search + location.hash) || /app\.html$/.test(location.pathname)) {
    setTimeout(function () { revisar(true); }, 8000);
    setInterval(function () { revisar(false); }, CADA_MS);
    document.addEventListener('visibilitychange', function () { if (!document.hidden) revisar(false); });
  }
})();
