// Guarda la app en el teléfono: abre al instante y casi sin datos.
const V = 'esto-v1.5';
self.addEventListener('install', e => { e.waitUntil(caches.open(V).then(c => c.addAll(['./', 'index.html', 'manifest.json', 'logo.png', 'icono-192.png']))); self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(k => Promise.all(k.filter(x => x !== V).map(x => caches.delete(x))))); self.clients.claim(); });
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin) return;   // datos siempre frescos del servidor
  e.respondWith(caches.match(e.request).then(r => r || fetch(e.request)));
});

// Avisos push: "se liberó un lugar de lavado"
self.addEventListener('push', e => {
  let d = {}; try { d = e.data ? e.data.json() : {}; } catch (x) { d = { title: 'ER Operadores', body: e.data && e.data.text() }; }
  e.waitUntil(self.registration.showNotification(d.title || 'ER Operadores', {
    body: d.body || '', icon: 'icono-192.png', badge: 'icono-192.png', tag: d.tag, renotify: true, vibrate: [200, 100, 200], data: { url: d.url || './#lavado' } }));
});
self.addEventListener('notificationclick', e => {
  e.notification.close();
  const url = new URL(e.notification.data.url, self.registration.scope).href;
  e.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(ws => {
    for (const w of ws) if ('focus' in w) { w.navigate(url); return w.focus(); }
    return clients.openWindow(url);
  }));
});
