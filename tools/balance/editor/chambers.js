'use strict';

const form = document.querySelector('.chamber-editor');
const chamber = document.getElementById('chamber');
const prices = document.getElementById('prices');
const capacity = document.getElementById('capacity');
const status = document.getElementById('status');
const resources = {
  sugar: 'Sugar', protein: 'Protein', water: 'Water', tunnelingSpace: 'Tunneling Space',
  knowledge: 'Knowledge', resin: 'Resin',
};
let catalog, unit, revision, selected, bonusFields;

function field(name, value, step, max, optional = false) {
  const label = document.createElement('label');
  label.append(name);
  const input = document.createElement('input');
  input.type = 'number';
  input.required = !optional;
  input.min = name === 'Multiplier' ? '1' : '0';
  input.max = String(max);
  input.step = String(step);
  input.value = value ?? '';
  label.append(input);
  return label;
}

function render() {
  selected = chamber.value;
  const fields = bonusFields[selected] ?? [];
  const definition = catalog[selected];
  capacity.replaceChildren();
  if (fields.length) {
    const heading = document.createElement('h2');
    heading.textContent = 'Capacity per level';
    capacity.append(heading);
    for (const {kind, targetKey, target, valueKey, label, scale, max} of fields) {
      const bonus = definition.bonuses?.find(item => item.type === kind && item[targetKey] === target);
      capacity.append(field(label, (bonus?.[valueKey] ?? 0) / scale, 1 / scale, max / scale));
    }
  }
  prices.replaceChildren(...Object.entries(resources).map(([key, name]) => {
    const rule = definition.costs[key];
    const row = document.createElement('fieldset');
    row.className = 'fields';
    row.dataset.resource = key;
    const title = document.createElement('legend');
    title.textContent = name;
    const step = 1 / unit;
    const max = Number.MAX_SAFE_INTEGER / unit;
    row.append(title,
      field('Base', (rule?.base ?? 0) / unit, step, max),
      field('Multiplier', rule?.multiplier ?? 1, 'any', 10),
      field('Per level', (rule?.perLevel ?? 0) / unit, step, max),
      field('Cap base', rule?.cap ? rule.cap.base / unit : null, step, max, true),
      field('Cap per level', (rule?.cap?.perLevel ?? 0) / unit, step, max),
    );
    return row;
  }));
}

function storeCurrent() {
  const rules = {};
  for (const row of prices.children) {
    const [base, multiplier, perLevel, capBase, capPerLevel] = row.querySelectorAll('input');
    const fixed = input => Math.round(input.valueAsNumber * unit);
    if (fixed(base) || fixed(perLevel)) {
      const rule = {base: fixed(base)};
      if (multiplier.valueAsNumber !== 1) rule.multiplier = multiplier.valueAsNumber;
      if (fixed(perLevel)) rule.perLevel = fixed(perLevel);
      if (capBase.value !== '') rule.cap = {base: fixed(capBase), perLevel: fixed(capPerLevel)};
      rules[row.dataset.resource] = rule;
    }
  }
  const definition = catalog[selected];
  definition.costs = rules;
  const fields = bonusFields[selected];
  if (fields) {
    const inputs = capacity.querySelectorAll('input');
    const bonuses = fields.flatMap(({kind, targetKey, target, valueKey, scale}, index) => {
      const value = Math.round(inputs[index].valueAsNumber * scale);
      return value ? [{type: kind, [targetKey]: target, [valueKey]: value}] : [];
    });
    if (bonuses.length) definition.bonuses = bonuses;
    else delete definition.bonuses;
  }
}

async function request(options) {
  const response = await fetch('/api/chambers', options);
  const data = await response.json();
  if (!response.ok) throw new Error(data.error);
  return data;
}

chamber.addEventListener('change', () => {
  if (!form.reportValidity()) {
    chamber.value = selected;
    return;
  }
  storeCurrent();
  render();
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  storeCurrent();
  form.inert = true;
  try {
    const data = await request({
      method: 'PUT',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({catalog, revision}),
    });
    revision = data.revision;
    status.textContent = 'Saved chamber settings. Rebuild the game or simulator to use them.';
  } catch (error) {
    status.textContent = error.message;
  } finally {
    form.inert = false;
  }
});

request().then(data => {
  ({catalog, unit, revision, bonusFields} = data);
  for (const id of Object.keys(catalog)) {
    const label = id.replace(/([a-z])([A-Z])/g, '$1 $2');
    chamber.add(new Option(label, id));
  }
  render();
  form.hidden = false;
  status.textContent = 'Prices are in game units. Base is the cost of the first level.';
}).catch(error => { status.textContent = error.message; });
