/*
 * Service worker de Stock T24 (web).
 * - app.html: la app completa, guardada por t24-web.js (caché "t24-contenido").
 * - Archivos de esta página: se guardan para abrir sin internet.
 * - Librerías externas (Bootstrap, íconos, fuentes, jsPDF…): se guardan la primera vez que se usan.
 * Los pedidos al servidor (Apps Script) NUNCA se guardan: siempre van directo.
 */
var VERSION = 'v2';
var CASCARA = 't24-cascara-' + VERSION;
var EXTERNOS = 't24-externos';
var CONTENIDO = 't24-contenido';
var ARCHIVOS = ['./', 'index.html', 't24-web.js', 'manifest.webmanifest', 'icono-192.png', 'icono-512.png'];

self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CASCARA).then(function (c) { return c.addAll(ARCHIVOS); }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (claves) {
      return Promise.all(claves.map(function (k) {
        if (k.indexOf('t24-cascara-') === 0 && k !== CASCARA) return caches.delete(k);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

function esServidor(url) {
  return /(^|\.)script\.google(usercontent)?\.com$/.test(url.hostname);
}

self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);
  if (esServidor(url)) return;

  // La app guardada.
  if (url.origin === location.origin && /\/app\.html$/.test(url.pathname)) {
    e.respondWith(
      caches.open(CONTENIDO).then(function (c) { return c.match('app.html'); }).then(function (r) {
        return r || Response.redirect('./', 302);
      })
    );
    return;
  }

  // Formularios públicos: siempre la versión nueva (si no hay internet, la guardada).
  if (url.origin === location.origin && /\/formulario\.html$/.test(url.pathname)) {
    e.respondWith(
      caches.open(CASCARA).then(function (c) {
        return fetch(req, { cache: 'no-store' }).then(function (res) {
          if (res && res.ok) c.put(req, res.clone());
          return res;
        }).catch(function () { return c.match(req, { ignoreSearch: true }); });
      })
    );
    return;
  }

  // Archivos de esta página: lo guardado primero, y se refresca por detrás.
  if (url.origin === location.origin) {
    e.respondWith(
      caches.open(CASCARA).then(function (c) {
        return c.match(req, { ignoreSearch: true }).then(function (guardado) {
          var red = fetch(req).then(function (res) {
            if (res && res.ok) c.put(req, res.clone());
            return res;
          }).catch(function () { return guardado; });
          return guardado || red;
        });
      })
    );
    return;
  }

  // Librerías externas conocidas: lo guardado primero (abre sin internet), y se refresca por detrás.
  // Cualquier otra cosa (imágenes de Drive, etc.) va directo, sin guardar.
  if (!/^(cdn\.jsdelivr\.net|cdnjs\.cloudflare\.com|cdn\.sheetjs\.com|unpkg\.com|fonts\.googleapis\.com|fonts\.gstatic\.com)$/.test(url.hostname)) return;
  e.respondWith(
    caches.open(EXTERNOS).then(function (c) {
      return c.match(req).then(function (guardado) {
        var red = fetch(req).then(function (res) {
          if (res && (res.ok || res.type === 'opaque')) c.put(req, res.clone());
          return res;
        }).catch(function () { return guardado || Response.error(); });
        return guardado || red;
      });
    })
  );
});
