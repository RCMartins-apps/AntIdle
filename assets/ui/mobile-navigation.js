/* Mobile navigation enhances the existing Bootstrap panels; desktop keeps its tabs. */
(() => {
  'use strict';
  const media = matchMedia('(max-width: 767px)');
  const groups = {
    nest: { label: 'Nest', tabs: ['nest', 'chambers'], path: 'M3 10 12 3l9 7v11H3ZM8 21v-8h8v8' },
    tasks: { label: 'Tasks', tabs: ['tasks'], path: 'M9 9h6v9H9ZM12 3v6M5 6l4 4M19 6l-4 4M3 13h6M15 13h6M5 21l4-5M19 21l-4-5' },
    explore: { label: 'Explore', tabs: ['explore', 'discoveries', 'territory'], path: 'm12 2 9 5v10l-9 5-9-5V7ZM12 8l4 4-4 4-4-4Z' },
    upgrades: { label: 'Upgrades', tabs: ['upgrades', 'purchased'], path: 'm12 3 8 8h-5v10H9V11H4Z' },
    lineage: { label: 'Lineage', tabs: ['lineage', 'milestones'], path: 'm3 7 5 4 4-7 4 7 5-4-2 13H5ZM6 16h12' }
  };
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
    let current = 'nest', group = 'nest', changing = false;
    const tab = id => app.querySelector('#' + id + '-tab');
    const available = id => !!tab(id) && !tab(id).parentElement.hidden;
    const groupFor = id => id === 'milestones' && !available('lineage') ? 'menu' : Object.keys(groups).find(key => groups[key].tabs.includes(id)) || 'menu';
    const nav = document.createElement('nav');
    nav.className = 'mobile-bottom-nav'; nav.setAttribute('aria-label', 'Main navigation');
    for (const [key, spec] of Object.entries(groups)) {
      const button = document.createElement('button');
      button.type = 'button'; button.dataset.destination = key;
      button.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${spec.path}"/></svg><span>${spec.label}</span>`;
      button.addEventListener('click', () => select(available(last[key]) ? last[key] : spec.tabs.find(available)));
      nav.append(button);
    }
    app.append(nav);
    const menu = document.createElement('button');
    menu.type = 'button'; menu.className = 'mobile-menu-button btn btn-outline-secondary';
    menu.textContent = '☰'; menu.setAttribute('aria-label', 'Game menu');
    menu.addEventListener('click', () => select('settings'));
    tabs.parentElement.append(menu);
    // Secondary views share their existing panels without duplicating controls/state.
    function secondary(id, label, parent) {
      const li = document.createElement('li'); li.className = 'nav-item';
      const a = document.createElement('a'); a.id = id + '-tab'; a.href = '#' + parent + 'Content';
      a.className = 'nav-link mobile-secondary'; a.setAttribute('role', 'tab'); a.setAttribute('aria-controls', parent + 'Content'); a.textContent = label;
      a.addEventListener('click', event => { event.preventDefault(); select(id); });
      li.append(a); tabs.append(li);
    }
    secondary('chambers', 'Chambers', 'nest'); secondary('purchased', 'Purchased', 'upgrades');
    function refresh() {
      const chambers = app.querySelector('.nest-chambers');
      tab('chambers').parentElement.hidden = chambers?.style.display === 'none';
      group = groupFor(current);
      if (!available(current)) { select('nest'); return; }
      for (const a of tabs.querySelectorAll('a[role=tab]')) {
        const id = a.id.replace(/-tab$/, '');
        if (labels[id] && !a.querySelector('.mobile-tab-label')) a.innerHTML = `<span class=desktop-tab-label>${a.textContent}</span><span class=mobile-tab-label>${labels[id]}</span>`;
        a.parentElement.dataset.mobileGroup = id === 'milestones' && !available('lineage') ? 'menu' : groupFor(id);
        if (media.matches) {
          a.setAttribute('aria-selected', String(id === current));
          a.classList.toggle('active', id === current);
        }
      }
      app.dataset.mobileGroup = group;
      app.dataset.mobileView = current;
      for (const button of nav.children) {
        const key = button.dataset.destination;
        button.hidden = !groups[key].tabs.some(id => available(id) && !['chambers', 'purchased', 'milestones'].includes(id));
        button.setAttribute('aria-current', key === group ? 'page' : 'false');
      }
    }
    function select(id) {
      if (!id || !available(id)) return;
      if (media.matches) scroll[current] = window.scrollY;
      current = id; group = groupFor(id); last[group] = id;
      if (id === 'purchased') { const purchased = app.querySelector('.purchased-upgrades'); if (purchased) purchased.open = true; }
      const base = id === 'chambers' ? 'nest' : id === 'purchased' ? 'upgrades' : id;
      changing = true;
      bootstrap.Tab.getOrCreateInstance(tab(base)).show();
      changing = false; refresh();
      if (media.matches) requestAnimationFrame(() => window.scrollTo({top: scroll[id] || 0, behavior: 'instant'}));
    }
    tabs.addEventListener('shown.bs.tab', event => {
      if (!changing) {
        const id = event.target.id.replace(/-tab$/, '');
        if (media.matches) scroll[current] = window.scrollY;
        current = id; group = groupFor(id); last[group] = id; refresh();
        if (media.matches) requestAnimationFrame(() => window.scrollTo({top: scroll[id] || 0, behavior: 'instant'}));
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
      const visible = [...tabs.querySelectorAll('a[role=tab]')].filter(a => a.getClientRects().length);
      const index = visible.indexOf(document.activeElement);
      if (index < 0) return;
      event.preventDefault(); event.stopPropagation();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? visible.length - 1 :
        (index + (event.key === 'ArrowRight' ? 1 : -1) + visible.length) % visible.length;
      select(visible[next].id.replace(/-tab$/, '')); visible[next].focus();
    });
    new MutationObserver(refresh).observe(tabs, { childList: true });
    const chambers = app.querySelector('.nest-chambers');
    if (chambers) new MutationObserver(refresh).observe(chambers, { attributes: true, attributeFilter: ['style'] });
    refresh();
  }
  const root = document.getElementById('root');
  const boot = () => { const app = root.querySelector('.antidle-app'); if (app?.querySelector('.card-tabs')) { start(app); observer.disconnect(); } };
  const observer = new MutationObserver(boot);
  observer.observe(root, { childList: true, subtree: true }); boot();
})();
