/* A compact mobile projection of the live resource rows, with the original panel underneath. */
(() => {
  'use strict';
  const media = matchMedia('(max-width: 767px)');
  const preview = location.pathname.match(/(?:^|\/)pr-preview\/pr-([1-9][0-9]*)(?:\/|$)/);
  const key = `antidle.ui${preview ? ':pr-preview:' + preview[1] : ''}.resources.expanded`;
  function start(app) {
    const panel = app.querySelector('.resources-column .game-panel');
    if (!panel || panel.dataset.mobileResources) return;
    const header = panel.querySelector('.card-header'), body = panel.querySelector('.card-body');
    if (!header || !body) return;
    panel.dataset.mobileResources = 'ready';
    let expanded = false;
    try { expanded = localStorage.getItem(key) === 'true'; } catch (_) { /* Storage may be unavailable. */ }
    const desktop = document.createElement('span'); desktop.className = 'desktop-resource-heading';
    while (header.firstChild) desktop.append(header.firstChild);
    header.append(desktop);
    const toggle = document.createElement('button'); toggle.type = 'button';
    toggle.className = 'mobile-resource-toggle'; toggle.setAttribute('aria-controls', 'resource-details');
    body.id = 'resource-details';
    const title = document.createElement('span'); title.className = 'resource-summary-heading';
    const label = document.createElement('span'); label.textContent = 'Resources';
    const arrow = document.createElement('span'); arrow.setAttribute('aria-hidden', 'true');
    title.append(label, arrow);
    const summary = document.createElement('span'); summary.className = 'compact-resources'; summary.id = 'compact-resource-summary';
    toggle.setAttribute('aria-describedby', summary.id);
    toggle.append(title, summary); header.append(toggle);
    function apply() {
      panel.classList.toggle('resources-expanded', expanded);
      toggle.setAttribute('aria-expanded', String(expanded));
      if (expanded) toggle.removeAttribute('aria-describedby');
      else toggle.setAttribute('aria-describedby', summary.id);
      toggle.setAttribute('aria-label', expanded ? 'Collapse resource details' : 'Expand resource details');
      arrow.textContent = expanded ? '▴' : '▾';
      // Prevent hidden controls remaining focused across a viewport change/collapse.
      if (media.matches && !expanded && body.contains(document.activeElement)) toggle.focus();
    }
    toggle.addEventListener('click', () => {
      expanded = !expanded; apply();
      try { localStorage.setItem(key, String(expanded)); } catch (_) { /* UI still works. */ }
    });
    let previousView = app.dataset.mobileView;
    new MutationObserver(() => {
      const view = app.dataset.mobileView;
      if (previousView && view !== previousView && media.matches && expanded) {
        expanded = false; apply();
        try { localStorage.setItem(key, 'false'); } catch (_) { /* UI still works. */ }
      }
      previousView = view;
    }).observe(app, {attributes: true, attributeFilter: ['data-mobile-view']});
    const chips = new Map();
    const names = new Set(['Sugar', 'Protein', 'Water', 'Resin', 'Knowledge', 'Tunneling Space', 'Ants']);
    function update() {
      const present = new Set();
      let position = 0;
      for (const row of body.querySelectorAll('.resource-row')) {
        const sourceLabel = row.querySelector('.resource-label');
        const name = sourceLabel?.textContent.trim();
        if (!names.has(name)) continue;
        const amount = row.querySelector('.resource-amount');
        if (!amount) continue;
        present.add(name);
        let chip = chips.get(name);
        if (!chip) {
          chip = document.createElement('span'); chip.className = 'compact-resource';
          chip.dataset.resource = name;
          const icon = sourceLabel.querySelector('.resource-icon');
          if (icon) chip.append(icon.cloneNode(true));
          const accessibleName = document.createElement('span'); accessibleName.className = 'visually-hidden';
          accessibleName.textContent = name + ': '; chip.append(accessibleName);
          const value = document.createElement('span'); value.className = 'compact-resource-value';
          const warning = document.createElement('small'); warning.className = 'compact-resource-warning';
          chip.append(value, warning); chips.set(name, chip); summary.append(chip);
        }
        if (summary.children[position] !== chip) summary.insertBefore(chip, summary.children[position] || null);
        position++;
        const value = chip.querySelector('.compact-resource-value');
        const text = amount.textContent.trim();
        if (value.textContent !== text) value.textContent = text;
        chip.classList.toggle('at-capacity', amount.classList.contains('text-primary'));
        const timing = row.querySelector('.resource-timing > span')?.textContent.trim() || '';
        const draining = timing.startsWith('Empty');
        const compactTiming = timing.replace(/^Empty in ~(.*)$/, '~$1 left').replace(/^Full in ~/, 'Full ~');
        const warning = chip.querySelector('.compact-resource-warning');
        if (warning.textContent !== compactTiming) warning.textContent = compactTiming;
        chip.classList.toggle('running-low', draining);
        const description = `${name}: ${text}${timing ? ', ' + timing : ''}`;
        chip.setAttribute('aria-label', description); chip.title = description;
      }
      for (const [name, chip] of chips) if (!present.has(name)) { chip.remove(); chips.delete(name); }
    }
    // Observe only the source rows, never the summary, so live updates cannot loop.
    new MutationObserver(update).observe(body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ['class'] });
    media.addEventListener('change', apply);
    update(); apply();
  }
  const root = document.getElementById('root');
  const boot = () => { const app = root.querySelector('.antidle-app'); if (app?.querySelector('.resources-column .card-body')) { start(app); observer.disconnect(); } };
  const observer = new MutationObserver(boot);
  observer.observe(root, { childList: true, subtree: true }); boot();
})();
