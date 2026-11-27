/* Mobile navigation enhances the existing Bootstrap panels; desktop keeps its tabs. */
(() => {
  'use strict';
  const media = matchMedia('(max-width: 767px)');
  const groups = {
    nest: { label: 'Nest', tabs: ['nest', 'tasks', 'chambers'] },
    explore: { label: 'Explore', tabs: ['explore', 'discoveries', 'territory'], path: 'm12 2 9 5v10l-9 5-9-5V7ZM12 8l4 4-4 4-4-4Z' },
    upgrades: { label: 'Upgrades', tabs: ['upgrades', 'purchased'], path: 'm12 3 8 8h-5v10H9V11H4Z' },
    lineage: { label: 'Lineage', tabs: ['lineage', 'milestones'], path: 'm3 7 5 4 4-7 4 7 5-4-2 13H5ZM6 16h12' },
    settings: { label: 'Settings', tabs: ['settings', 'wiki'], path: 'M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3ZM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z' }
  };
  const nestIcons = {
    nest: 'M20 14c0 5-3.6 8-8 8s-8-3-8-8S8 2 12 2s8 7 8 12ZM9 7l-2 4',
    tasks: 'M12 3a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5ZM12 9a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM12 14a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7ZM10 4 8 2M14 4l2-2M10 10 5 8l-2 2M14 10l5-2 2 2M10 12l-5 1-2 3M14 12l5 1 2 3M9 17l-3 1v3M15 17l3 1v3',
    chambers: 'M3 21 16 8M5 6c7-6 14-4 17 4l-6-2-4-5M5 6l7-3'
  };
  function decorateNestTab(a, id) {
    if (!nestIcons[id] || a.querySelector('.nest-subtab-icon')) return;
    // Preserve original node parents: Laminar updates the live count in this anchor.
    a.insertAdjacentHTML('afterbegin', `<svg class="nest-subtab-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${nestIcons[id]}"/></svg>`);
  }
  function start(app) {
    const tabs = app.querySelector('.card-tabs');
    if (!tabs || app.dataset.mobileNavigation) return;
    app.dataset.mobileNavigation = 'ready';
    const last = {}, scroll = {};
    const labels = {nest: 'Brood', explore: 'Map', upgrades: 'Available', lineage: 'Founding'};
    for (const [id, label] of Object.entries(labels)) {
      const a = app.querySelector('#' + id + '-tab');
      if (a) a.innerHTML = `<span class=desktop-tab-label>${a.textContent}</span><span class=mobile-tab-label>${label}</span>`;
    }
    let current = 'nest', group = 'nest', changing = false, returnView = 'nest';
    let restoreFrame = 0;
    function restoreScroll(id) {
      cancelAnimationFrame(restoreFrame);
      if (!media.matches) return;
      const top = scroll[id] || 0;
      window.scrollTo({top, behavior: 'instant'});
      // Resource collapse and Bootstrap tab changes can clamp scroll during layout.
      restoreFrame = requestAnimationFrame(() => {
        if (media.matches && current === id) window.scrollTo({top, behavior: 'instant'});
      });
    }
    const tab = id => app.querySelector('#' + id + '-tab');
    let availableTabs = new Set(['nest', 'settings', 'wiki', 'milestones']);
    const available = id => availableTabs.has(id);
    const groupFor = id => id === 'milestones' && !available('lineage') ? 'settings' : Object.keys(groups).find(key => groups[key].tabs.includes(id)) || 'settings';
    const nav = document.createElement('nav');
    nav.className = 'mobile-bottom-nav'; nav.setAttribute('aria-label', 'Main navigation');
    for (const [key, spec] of Object.entries(groups)) {
      const button = document.createElement('button');
      button.type = 'button'; button.dataset.destination = key;
      button.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${key === 'nest' ? app.dataset.nestIconPath : spec.path}"/></svg><span>${spec.label}</span>`;
      button.addEventListener('click', () => select(available(last[key]) ? last[key] : spec.tabs.find(available)));
      nav.append(button);
    }
    app.append(nav);
    app.addEventListener('keydown', event => {
      if (media.matches && group === 'settings' && event.key === 'Escape') {
        event.preventDefault();
        select(available(returnView) && groupFor(returnView) !== 'settings' ? returnView : 'nest');
        nav.querySelector(`[data-destination="${group}"]`)?.focus({preventScroll: true});
      }
    });
    // Secondary views share their existing panels without duplicating controls/state.
    function secondary(id, label, parent) {
      const li = document.createElement('li'); li.className = 'nav-item';
      const a = document.createElement('a'); a.id = id + '-tab'; a.href = '#' + parent + 'Content';
      a.className = 'nav-link mobile-secondary'; a.setAttribute('role', 'tab'); a.setAttribute('aria-controls', parent + 'Content'); a.textContent = label;
      a.addEventListener('click', event => { event.preventDefault(); select(id); });
      li.append(a); tabs.append(li);
    }
    secondary('chambers', 'Chambers', 'nest'); secondary('purchased', 'Purchased', 'upgrades');
    let renderedView = current;
    function refresh() {
      tab('chambers').parentElement.hidden = !available('chambers');
      tab('purchased').parentElement.hidden = !available('purchased');
      group = groupFor(current);
      if (!available(current)) { select('nest'); return; }
      for (const a of tabs.querySelectorAll('a[role=tab]')) {
        const id = a.id.replace(/-tab$/, '');
        if (labels[id] && !a.querySelector('.mobile-tab-label')) a.innerHTML = `<span class=desktop-tab-label>${a.textContent}</span><span class=mobile-tab-label>${labels[id]}</span>`;
        decorateNestTab(a, id);
        a.parentElement.dataset.mobileGroup = id === 'milestones' && !available('lineage') ? 'settings' : groupFor(id);
        if (media.matches) {
          a.setAttribute('aria-selected', String(id === current));
          a.classList.toggle('active', id === current);
        }
      }
      app.dataset.mobileGroup = group;
      app.dataset.mobileView = current;
      if (renderedView !== current) {
        renderedView = current;
        app.dispatchEvent(new Event('antidle-view-changed'));
      }
      app.dataset.mobileSubtabs = String([...tabs.children].filter(li => !li.hidden && li.dataset.mobileGroup === group).length);
      for (const button of nav.children) {
        const key = button.dataset.destination;
        button.hidden = !groups[key].tabs.some(id => available(id) && !['chambers', 'purchased', 'milestones'].includes(id));
        button.setAttribute('aria-current', key === group ? 'page' : 'false');
      }
    }
    function select(id) {
      if (!id || !available(id) || id === current) return;
      if (media.matches) scroll[current] = window.scrollY;
      if (groupFor(id) === 'settings' && group !== 'settings') returnView = current;
      current = id; group = groupFor(id); last[group] = id;
      if (id === 'purchased') { const purchased = app.querySelector('.purchased-upgrades'); if (purchased) purchased.open = true; }
      const base = id === 'chambers' ? 'nest' : id === 'purchased' ? 'upgrades' : id;
      changing = true;
      bootstrap.Tab.getOrCreateInstance(tab(base)).show();
      changing = false; refresh();
      restoreScroll(id);
    }
    tabs.addEventListener('show.bs.tab', () => {
      // Bootstrap can handle an anchor before our click listener runs.
      // Record the outgoing position before Bootstrap hides its panel.
      if (!changing && media.matches) scroll[current] = window.scrollY;
    });
    tabs.addEventListener('shown.bs.tab', event => {
      if (!changing) {
        const id = event.target.id.replace(/-tab$/, '');
        if (groupFor(id) === 'settings' && group !== 'settings') returnView = current;
        current = id; group = groupFor(id); last[group] = id; refresh();
        restoreScroll(id);
      }
    });
    // Bootstrap ignores a click on an already-active base anchor; handle secondary→base too.
    tabs.addEventListener('click', event => {
      const a = event.target.closest('a[role=tab]');
      if (media.matches && a && !a.classList.contains('mobile-secondary')) {
        event.preventDefault(); event.stopPropagation(); select(a.id.replace(/-tab$/, ''));
      }
    });
    media.addEventListener('change', () => {
      const base = current === 'chambers' ? 'nest' : current === 'purchased' ? 'upgrades' : current;
      if (!media.matches) {
        for (const a of tabs.querySelectorAll('a[role=tab]')) {
          const active = a === tab(base); a.classList.toggle('active', active);
          a.setAttribute('aria-selected', String(active));
        }
      }
      refresh();
    });
    tabs.addEventListener('keydown', event => {
      if (!media.matches || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      const visible = [...tabs.querySelectorAll('a[role=tab]')].filter(a => available(a.id.replace(/-tab$/, '')) && groupFor(a.id.replace(/-tab$/, '')) === group);
      const index = visible.indexOf(document.activeElement);
      if (index < 0) return;
      event.preventDefault(); event.stopPropagation();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? visible.length - 1 :
        (index + (event.key === 'ArrowRight' ? 1 : -1) + visible.length) % visible.length;
      select(visible[next].id.replace(/-tab$/, '')); visible[next].focus({preventScroll: true});
    }, true);
    app.addEventListener('antidle-tabs-changed', event => {
      availableTabs = new Set(event.detail);
      queueMicrotask(refresh);
    });
    app.dispatchEvent(new Event('antidle-navigation-ready'));
    refresh();
  }
  const root = document.getElementById('root');
  const boot = () => { const app = root.querySelector('.antidle-app'); if (app?.querySelector('.card-tabs')) { start(app); observer.disconnect(); } };
  const observer = new MutationObserver(boot);
  observer.observe(root, { childList: true, subtree: true }); boot();
})();
