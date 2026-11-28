(() => {
  'use strict';
  // Only the packaged release enables registration. Development bundles stay fresh.
  const script = document.currentScript;
  const worker = script && script.dataset.serviceWorker;
  if (!worker || !('serviceWorker' in navigator) || !window.isSecureContext) return;
  const status = document.getElementById('offline-status');
  const updateButton = document.getElementById('app-update');
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
    let busy = false;
    let applying = false;
    let applyTimeout;
    const unlock = message => {
      clearTimeout(applyTimeout);
      applying = false;
      busy = false;
      updateButton.disabled = false;
      updateButton.textContent = registration.waiting ? 'Save and install update' : 'Check for updates';
      show(message);
    };
    updateButton.hidden = false;
    const refresh = () => {
      if (busy) return;
      const id = ++refreshId;
      updateButton.textContent = registration.waiting ? 'Save and install update' : 'Check for updates';
      if (registration.waiting) {
        show('Update ready');
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
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (applying) {
        clearTimeout(applyTimeout);
        window.location.reload();
      } else refresh();
    });
    updateButton.addEventListener('click', async () => {
      if (busy) return;
      busy = true;
      ++refreshId;
      updateButton.disabled = true;
      const waiting = registration.waiting;
      if (waiting) {
        const app = document.querySelector('.antidle-app');
        if (!app) {
          unlock('The game is still loading · try again');
          return;
        }
        show('Saving colony…');
        app.dispatchEvent(new CustomEvent('antidle-save-for-update', {detail: saved => {
          if (!saved) {
            unlock('Colony save failed · update not installed');
            return;
          }
          app.inert = true;
          const cancel = message => {
            app.inert = false;
            app.dispatchEvent(new Event('antidle-resume-after-update'));
            unlock(message);
          };
          const channel = new MessageChannel();
          applying = true;
          show('Installing update…');
          applyTimeout = setTimeout(() => {
            channel.port1.close();
            cancel('Update did not finish · reopen the app or try again');
          }, 15000);
          channel.port1.onmessage = event => {
            channel.port1.close();
            if (event.data === 'other-windows') cancel('Close other Ant Idle windows and try again');
            else if (event.data !== true) cancel('Update could not be installed · try again while online');
          };
          waiting.postMessage('antidle-install-update', [channel.port2]);
        }}));
        return;
      }

      show('Checking for updates…');
      try {
        await registration.update();
        const installing = registration.installing;
        if (installing && !['installed', 'redundant'].includes(installing.state)) {
          await new Promise(resolve => {
            installing.addEventListener('statechange', () => {
              if (['installed', 'redundant'].includes(installing.state)) resolve();
            });
          });
        }
        unlock(registration.waiting ? 'Update ready' :
          installing?.state === 'redundant' ? 'Update download failed · try again' : 'No new release available');
      } catch (error) {
        unlock('Could not check for updates · try again while online');
      }
    });
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) refresh();
    });
    window.addEventListener('online', refresh);
    refresh();
    navigator.serviceWorker.ready.then(refresh);
    registration.update().catch(() => {}); // Offline update checks are expected to fail.
  })().catch(() => show('Offline setup failed · reopen while online to retry'));
})();
