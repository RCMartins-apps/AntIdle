(() => {
  'use strict';
  // Only the packaged release enables registration. Development bundles stay fresh.
  const script = document.currentScript;
  const worker = script && script.dataset.serviceWorker;
  if (!worker || !('serviceWorker' in navigator) || !window.isSecureContext) return;
  const status = document.getElementById('offline-status');
  const show = text => { status.hidden = false; status.textContent = text; };
  show('Preparing offline play…');
  (async () => {
    const previous = await navigator.serviceWorker.getRegistration(worker);
    let registration;
    try {
      registration = await navigator.serviceWorker.register(worker, {scope: './', updateViaCache: 'none'});
    } catch (error) {
      if (!previous || !previous.active) throw error;
      registration = previous; // Opening offline must not disable an installed cache.
    }
    let refreshId = 0;
    const refresh = () => {
      const id = ++refreshId;
      if (registration.waiting) {
        show('Update ready · close all game windows to apply');
        return;
      }
      if (!registration.active) {
        show('Preparing offline play…');
        return;
      }

      const channel = new MessageChannel();
      const finish = text => {
        clearTimeout(timeout);
        channel.port1.close();
        if (id === refreshId) show(text);
      };
      const incomplete = 'Offline files incomplete · reopen while online to repair';
      const timeout = setTimeout(() => finish(incomplete), 3000);
      channel.port1.onmessage = event =>
        finish(event.data === true ? 'Available offline' : incomplete);
      registration.active.postMessage('antidle-offline-readiness', [channel.port2]);
    };
    const watch = () => {
      const installing = registration.installing;
      if (installing) installing.addEventListener('statechange', () => {
        if (installing.state === 'redundant' && !registration.active) show('Offline setup failed · reopen while online to retry');
        else refresh();
      });
    };
    registration.addEventListener('updatefound', watch);
    watch();
    navigator.serviceWorker.addEventListener('controllerchange', refresh);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) refresh();
    });
    window.addEventListener('online', refresh);
    refresh();
    navigator.serviceWorker.ready.then(refresh);
    registration.update().catch(() => {}); // Offline update checks are expected to fail.
  })().catch(() => show('Offline setup failed · reopen while online to retry'));
})();
