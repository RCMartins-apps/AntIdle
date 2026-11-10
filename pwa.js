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
    const refresh = () => {
      if (registration.waiting) show('Update ready · close all game windows to apply');
      else if (registration.active) show('Available offline');
      else show('Preparing offline play…');
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
    refresh();
    navigator.serviceWorker.ready.then(refresh);
    registration.update().catch(() => {}); // Offline update checks are expected to fail.
  })().catch(() => show('Offline setup failed · reopen while online to retry'));
})();
