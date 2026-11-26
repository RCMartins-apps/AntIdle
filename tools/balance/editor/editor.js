'use strict';

const el = id => document.getElementById(id);
const form = el('upgrade');
const viewport = el('viewport');
const board = el('board');
const labels = {
  tunnelingSpace: 'Tunneling Space',
  idleWorkers: 'Available workers',
  minimumRevealedSites: 'Revealed sites',
  minimumSmallWorkers: 'Small workers',
  completedMissions: 'Completed expeditions',
  matureWorkers: 'Standard workers raised',
  nursery: 'Nursery built',
  outpost: 'Dry Clearing Outpost built',
  aging: 'Aging active',
  proteinDiscovered: 'Protein discovered',
  earlyResearch: 'Early research access',
  beyondWoodland: 'Beyond the Woodland inherited',
  territoryContests: 'Completed territory contests',
};
const labelFor = name => labels[name] ?? name.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, c => c.toUpperCase());
const equal = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const nodeWidth = 206;
const nodeHeight = 101;
const columnGap = 23;
const sidePadding = 28;
const rowCapacity = 4;
const paperWidth = rowCapacity * nodeWidth + (rowCapacity - 1) * columnGap + 2 * sidePadding;
let unit, discoveries, icons, savedRevision, ids, defaults, saved, draft, selected;
let scale = 1;
let layout, saving = false;

function element(tag, value, className) {
  const item = document.createElement(tag);
  if (className) item.className = className;
  if (value !== undefined) item.textContent = value;
  if (tag === 'button') item.type = 'button';
  return item;
}

function resourceIcon(key) {
  const icon = icons[key];
  const namespace = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(namespace, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.classList.add('resource-icon', 'resource-' + icon.id);
  const path = document.createElementNS(namespace, 'path');
  path.setAttribute('d', icon.path);
  svg.append(path);
  return svg;
}

function status(text, error = false) {
  el('status').textContent = text;
  el('status').classList.toggle('error', error);
}

function formatUnits(amount) {
  const digits = String(unit).length - 1;
  const whole = BigInt(amount) / BigInt(unit);
  const remainder = BigInt(amount) % BigInt(unit);
  return remainder ? String(whole) + '.' + String(remainder).padStart(digits, '0').replace(/0+$/, '') : String(whole);
}

function parseUnits(text) {
  const digits = String(unit).length - 1;
  const match = /^(\d*)(?:\.(\d+))?$/.exec(text.trim());
  if (!match || (match[2]?.length ?? 0) > digits) {
    throw new Error('Use a non-negative amount with at most ' + digits + ' decimal places.');
  }
  const amount = BigInt(match[1] || '0') * BigInt(unit) + BigInt((match[2] ?? '').padEnd(digits, '0'));
  if (amount > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('Amount is too large to store precisely.');
  return Number(amount);
}

function updateButtons(message) {
  const changed = ids.filter(id => !equal(saved[id], draft[id])).length;
  el('save').disabled = saving || changed === 0 || Boolean(layout?.error);
  el('revert').disabled = saving || changed === 0;
  if (layout?.error) status(layout.error, true);
  else status(message ?? (changed ? changed + (changed === 1 ? ' upgrade' : ' upgrades') + ' with unsaved changes' : 'No unsaved changes'));
}

function parentEdges(id) {
  return draft[id].requirements.upgrades.map(parent => ({parent}));
}

function makeLayout() {
  const depths = new Map();
  const active = new Set();
  const parents = new Map(ids.map(id => [id, parentEdges(id)]));
  function visit(id) {
    if (depths.has(id)) return depths.get(id);
    if (active.has(id)) throw new Error('Dependency cycle reaches ' + draft[id].title + '. Remove a prerequisite before saving.');
    active.add(id);
    const depth = Math.max(-1, ...parents.get(id).map(edge => visit(edge.parent))) + 1;
    active.delete(id);
    depths.set(id, depth);
    return depth;
  }
  let error;
  try {
    ids.forEach(visit);
  } catch (problem) {
    error = problem.message;
    ids.forEach(id => depths.set(id, 0));
  }

  const bands = new Map();
  for (const id of ids) {
    const depth = depths.get(id);
    if (!bands.has(depth)) bands.set(depth, []);
    bands.get(depth).push(id);
  }
  const positions = new Map();
  let y = 30;
  for (const [, band] of [...bands].sort(([a], [b]) => a - b)) {
    for (let offset = 0; offset < band.length; offset += rowCapacity) {
      const row = band.slice(offset, offset + rowCapacity);
      const rowWidth = row.length * nodeWidth + (row.length - 1) * columnGap;
      row.forEach((id, col) => positions.set(id, {x: (paperWidth - rowWidth) / 2 + col * (nodeWidth + columnGap), y}));
      y += nodeHeight + 38;
    }
    y += 15;
  }
  return {parents, positions, height: y, error};
}

function paintLinks() {
  const canvas = el('links');
  const ratio = Math.min(2, window.devicePixelRatio || 1);
  canvas.width = Math.round(paperWidth * ratio);
  canvas.height = Math.round(layout.height * ratio);
  canvas.style.width = paperWidth + 'px';
  canvas.style.height = layout.height + 'px';
  if (layout.error) return;
  const ctx = canvas.getContext('2d');
  ctx.scale(ratio, ratio);
  const draw = focused => {
    for (const [id, edges] of layout.parents) {
      for (const {parent} of edges) {
        const highlighted = parent === selected || id === selected;
        if (highlighted !== focused) continue;
        const from = layout.positions.get(parent);
        const to = layout.positions.get(id);
        const x1 = from.x + nodeWidth / 2;
        const y1 = from.y + nodeHeight;
        const x2 = to.x + nodeWidth / 2;
        const y2 = to.y;
        const bend = Math.min(58, (y2 - y1) / 2);
        ctx.beginPath();
        ctx.lineWidth = highlighted ? 2.3 : 1.2;
        ctx.strokeStyle = highlighted ? '#bdd7ab' : '#637f65';
        ctx.moveTo(x1, y1);
        ctx.bezierCurveTo(x1, y1 + bend, x2, y2 - bend, x2, y2);
        ctx.stroke();
      }
    }
  };
  draw(false);
  draw(true);
}

function resizeBoard() {
  if (!layout) return;
  board.style.width = paperWidth + 'px';
  board.style.height = layout.height + 'px';
  board.style.transform = 'scale(' + scale + ')';
  el('sheet').style.width = paperWidth * scale + 'px';
  el('sheet').style.height = layout.height * scale + 'px';
}

function shortGates(row) {
  return Object.entries(row.requirements).flatMap(([key, value]) => {
    if (key === 'upgrades' || !value) return [];
    if (key === 'discovery') return [discoveries[value]];
    return [typeof value === 'number' ? value + ' ' + labelFor(key).toLowerCase() : labelFor(key)];
  }).join(' · ');
}

function matches(id) {
  const search = el('search').value.trim().toLowerCase();
  return draft[id].title.toLowerCase().includes(search) || id.toLowerCase().includes(search);
}

function renderGraph() {
  const keptFocus = document.activeElement?.dataset.id;
  layout = makeLayout();
  resizeBoard();
  paintLinks();
  const nodes = el('nodes');
  nodes.replaceChildren();
  const related = new Set([selected, ...layout.parents.get(selected).map(edge => edge.parent)]);
  for (const id of ids) {
    if (layout.parents.get(id).some(edge => edge.parent === selected)) related.add(id);
    const row = draft[id];
    const node = element('button', undefined, 'node');
    const position = layout.positions.get(id);
    node.dataset.id = id;
    node.style.top = position.y + 'px';
    node.style.left = position.x + 'px';
    node.style.width = nodeWidth + 'px';
    node.style.height = nodeHeight + 'px';
    node.setAttribute('aria-pressed', String(id === selected));
    node.classList.toggle('related', related.has(id));
    node.classList.toggle('dimmed', !matches(id) && !related.has(id));
    const costs = Object.entries(row.cost).filter(([, amount]) => amount);
    const price = element('span', costs.length ? undefined : 'Free', 'price');
    for (const [key, amount] of costs) {
      const item = element('span', undefined, 'price-item');
      item.title = formatUnits(amount) + ' ' + labelFor(key);
      item.append(resourceIcon(key), document.createTextNode(formatUnits(amount)),
        element('span', ' ' + labelFor(key), 'visually-hidden'));
      price.append(item);
    }
    const gates = shortGates(row);
    node.append(element('strong', row.title), price);
    if (gates) node.append(element('span', gates, 'gates'));
    node.title = [row.title, costs.map(([key, amount]) => formatUnits(amount) + ' ' + labelFor(key)).join(' · '), gates]
      .filter(Boolean).join('\n');
    node.addEventListener('click', () => select(id));
    nodes.append(node);
  }
  el('matches').textContent = ids.filter(matches).length + ' / ' + ids.length;
  if (keptFocus) nodes.querySelector('[data-id="' + CSS.escape(keptFocus) + '"]')?.focus({preventScroll: true});
}

function centerNode(id, centeredY = true) {
  const {x, y} = layout.positions.get(id);
  viewport.scrollLeft = (x + nodeWidth / 2) * scale - viewport.clientWidth / 2;
  if (centeredY) viewport.scrollTop = (y + nodeHeight / 2) * scale - viewport.clientHeight / 2;
}

function select(id, center = false) {
  if (saving || !form.reportValidity()) return;
  selected = id;
  renderForm();
  renderGraph();
  updateButtons();
  if (center) centerNode(id);
}

function zoom(factor) {
  if (!layout) return;
  scale = Math.max(.38, Math.min(1.5, factor));
  resizeBoard();
  centerNode(selected);
}

function field(name, control, wide = false, icon) {
  const wrapper = element('label', undefined, wide ? 'field wide' : 'field');
  const caption = element('span', name, 'field-name');
  if (icon) caption.prepend(icon);
  control.setAttribute('aria-label', name);
  wrapper.append(caption, control);
  return wrapper;
}

function input(type, value) {
  const control = element('input');
  control.type = type;
  control.value = value;
  return control;
}

function option(value, title, selectedOption = false) {
  const item = element('option', title);
  item.value = value;
  item.selected = selectedOption;
  return item;
}

function section(title, content, isOpen = true) {
  const details = element('details', undefined, 'form-section');
  details.open = isOpen;
  details.append(element('summary', title), content);
  return details;
}

function touch() {
  el('record-title').textContent = draft[selected].title;
  el('restore-defaults').disabled = equal(draft[selected], defaults[selected]);
  renderGraph();
  renderConnections();
  updateButtons();
}

function renderConnections() {
  const list = el('connections');
  list.replaceChildren();
  const children = ids.filter(id => layout.parents.get(id).some(edge => edge.parent === selected));
  if (!children.length) return;
  list.append(element('span', 'Leads to', 'field'));
  const tags = element('div', undefined, 'tags');
  for (const id of children) {
    const button = element('button', draft[id].title);
    button.addEventListener('click', () => select(id, true));
    tags.append(button);
  }
  list.append(tags);
}

function renderForm() {
  const row = draft[selected];
  const heading = element('header', undefined, 'record');
  const caption = element('div');
  const title = element('h2', row.title);
  title.id = 'record-title';
  caption.append(title, element('code', selected));
  const restore = element('button', 'Restore defaults');
  restore.id = 'restore-defaults';
  restore.disabled = equal(row, defaults[selected]);
  restore.addEventListener('click', () => {
    draft[selected] = structuredClone(defaults[selected]);
    renderForm();
    touch();
  });
  heading.append(caption, restore);

  const costs = element('div', undefined, 'fields');
  for (const [name, amount] of Object.entries(row.cost)) {
    const control = input('text', formatUnits(amount));
    control.inputMode = 'decimal';
    control.addEventListener('input', () => {
      try {
        row.cost[name] = parseUnits(control.value);
        control.setCustomValidity('');
        touch();
      } catch (error) {
        control.setCustomValidity(error.message);
        status(error.message, true);
      }
    });
    costs.append(field(labelFor(name), control, false, resourceIcon(name)));
  }

  const requirements = element('div', undefined, 'fields');
  const tags = element('div', undefined, 'tags wide');
  for (const id of row.requirements.upgrades) {
    const link = element('button', draft[id].title);
    link.addEventListener('click', () => select(id, true));
    const remove = element('button', '×');
    remove.setAttribute('aria-label', 'Remove prerequisite ' + draft[id].title);
    remove.addEventListener('click', () => {
      if (!form.reportValidity()) return;
      row.requirements.upgrades = row.requirements.upgrades.filter(parent => parent !== id);
      renderForm();
      touch();
    });
    const group = element('span', undefined, 'tags');
    group.append(link, remove);
    tags.append(group);
  }
  const add = element('select');
  add.append(option('', 'Choose an upgrade'));
  for (const id of ids) {
    if (id !== selected && !row.requirements.upgrades.includes(id)) add.append(option(id, draft[id].title));
  }
  add.addEventListener('change', () => {
    if (!add.value || !form.reportValidity()) return;
    row.requirements.upgrades.push(add.value);
    renderForm();
    touch();
    form.querySelector('select').focus();
  });
  requirements.append(field('Add prerequisite upgrade', add, true), tags);

  for (const [key, value] of Object.entries(row.requirements)) {
    if (key === 'upgrades') continue;
    if (key === 'discovery') {
      const menu = element('select');
      menu.append(option('', 'None', value === null),
        ...Object.entries(discoveries).sort((a, b) => a[1].localeCompare(b[1]))
          .map(([id, name]) => option(id, name, value === id)));
      menu.addEventListener('change', () => {
        row.requirements[key] = menu.value || null;
        touch();
      });
      requirements.append(field('Discovery', menu, true));
    } else if (typeof value === 'boolean') {
      const control = input('checkbox', '');
      control.checked = value;
      control.addEventListener('change', () => {
        row.requirements[key] = control.checked;
        touch();
      });
      const toggle = element('label', undefined, 'field toggle wide');
      toggle.append(control, document.createTextNode(labelFor(key)));
      requirements.append(toggle);
    } else if (typeof value === 'number') {
      const control = input('number', value);
      control.min = '0';
      control.max = '2147483647';
      control.step = '1';
      control.required = true;
      control.addEventListener('input', () => {
        if (!control.validity.valid) return;
        row.requirements[key] = Number(control.value);
        touch();
      });
      requirements.append(field(labelFor(key), control));
    }
  }
  const copy = element('div', undefined, 'fields');
  const name = input('text', row.title);
  name.required = true;
  name.addEventListener('input', () => {
    name.setCustomValidity(name.value.trim() ? '' : 'Enter an upgrade title.');
    if (name.validity.valid) {
      row.title = name.value;
      touch();
    }
  });
  const description = element('textarea');
  description.value = row.description;
  description.addEventListener('input', () => {
    row.description = description.value;
    touch();
  });
  copy.append(field('Title', name, true), field('Help text', description, true));

  const bonus = element('div', undefined, 'bonus-list');
  const bonuses = row.bonuses ?? [];
  for (const [index, effect] of bonuses.entries()) {
    const controls = element('div', undefined, 'fields');
    const source = element('select');
    for (const [key, label] of [['sugarCollectors', 'Sugar Collectors'], ['scrapsGatherers', 'Scraps Gatherers']]) {
      source.append(option(key, label, key === effect.source));
    }
    source.addEventListener('change', () => {
      effect.source = source.value;
      touch();
    });
    const percent = input('number', effect.percent);
    percent.min = '1';
    percent.max = '100';
    percent.step = '1';
    percent.required = true;
    percent.addEventListener('input', () => {
      if (!percent.validity.valid) return;
      effect.percent = Number(percent.value);
      touch();
    });
    const remove = element('button', 'Remove bonus');
    remove.type = 'button';
    remove.setAttribute('aria-label', `Remove bonus ${index + 1}`);
    remove.addEventListener('click', () => {
      bonuses.splice(index, 1);
      renderForm();
      touch();
    });
    controls.append(field('Production source', source), field('Production bonus (%)', percent));
    bonus.append(controls, remove);
  }
  const addBonus = element('button', 'Add production bonus');
  addBonus.type = 'button';
  addBonus.addEventListener('click', () => {
    row.bonuses = [...bonuses, {type: 'productionPercent', source: 'sugarCollectors', percent: 5}];
    renderForm();
    touch();
  });
  bonus.append(addBonus);

  const neighbours = element('div', undefined, 'connections');
  neighbours.id = 'connections';
  form.replaceChildren(heading, section('Costs', costs), section('Bonuses', bonus), section('Prerequisites', requirements), section('Title and help', copy, false), neighbours);
  form.hidden = false;
  if (layout) renderConnections();
}

function loadCatalog(catalog) {
  unit = catalog.unit;
  discoveries = catalog.discoveries;
  icons = catalog.icons;
  savedRevision = catalog.revision;
  ids = catalog.upgrades.map(entry => entry.id);
  defaults = Object.fromEntries(catalog.upgrades.map(entry => [entry.id, entry.base]));
  saved = Object.fromEntries(catalog.upgrades.map(entry => [entry.id, entry.current]));
  draft = structuredClone(saved);
  selected = ids.includes(selected) ? selected : ids[0];
  renderGraph();
  renderForm();
}

async function api(options) {
  const response = await fetch('/api/catalog', options);
  const data = await response.json();
  if (!response.ok) throw new Error(data.error);
  return data;
}

el('save').addEventListener('click', async () => {
  if (!form.reportValidity() || layout.error || saving) return;
  saving = true;
  form.inert = true;
  updateButtons('Saving…');
  let message = 'Saved to src/main/balance/upgrades.json. Rebuild the game or simulator to use these values.';
  let failure;
  try {
    const catalog = await api({
      method: 'PUT',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({revision: savedRevision, catalog: draft}, (key, value) =>
        key === 'bonuses' && value.length === 0 ? undefined : value),
    });
    loadCatalog(catalog);
  } catch (error) {
    failure = error.message;
  } finally {
    saving = false;
    form.inert = false;
    updateButtons(message);
    if (failure) status(failure, true);
  }
});

el('revert').addEventListener('click', () => {
  draft = structuredClone(saved);
  renderGraph();
  renderForm();
  updateButtons();
});
el('search').addEventListener('input', () => { if (ids) renderGraph(); });
el('search').addEventListener('keydown', event => {
  if (event.key !== 'Enter' || !ids) return;
  const match = ids.find(matches);
  if (match) select(match, true);
});
el('zoom-in').addEventListener('click', () => zoom(scale * 1.18));
el('zoom-out').addEventListener('click', () => zoom(scale / 1.18));
el('fit').addEventListener('click', () => zoom(viewport.clientWidth / paperWidth));
form.addEventListener('submit', event => event.preventDefault());
window.addEventListener('beforeunload', event => {
  if (!saved || equal(saved, draft)) return;
  event.preventDefault();
  event.returnValue = '';
});

api().then(data => {
  loadCatalog(data);
  scale = Math.max(.73, Math.min(1, viewport.clientWidth / paperWidth));
  resizeBoard();
  centerNode(selected, false);
  updateButtons();
}).catch(error => status(error.message, true));
