'use strict';

function comparisonControls(getDraft) {
  const run = document.getElementById('compare');
  const cancel = document.getElementById('cancel-comparison');
  const result = document.getElementById('comparison-result');
  const status = document.getElementById('comparison-status');
  if (!run) return;

  function duration(seconds) {
    if (seconds === undefined) return 'Unreached';
    const total = Math.round(seconds);
    return total >= 60 ? `${Math.floor(total / 60)}m ${total % 60}s` : `${total}s`;
  }

  function show(data) {
    const values = [
      ['Standard workers unlocked', 'standard-workers-unlocked'],
      ['Local Exploration', 'exploration'],
      ['First hex revealed', 'first-hexagon'],
      ['Dry Clearing Outpost', 'outpost'],
      ['First founding', 'founded'],
    ].map(([label, id]) => [label, duration(data.saved.milestones[id]), duration(data.draft.milestones[id])]);
    values.push(['Time without sugar', duration(data.saved.sugarEmpty), duration(data.draft.sugarEmpty)]);
    values.push(['Starvation deaths', data.saved.starvationDeaths, data.draft.starvationDeaths]);
    const table = document.createElement('table');
    const head = table.createTHead().insertRow();
    for (const label of ['Outcome', 'Saved', 'Draft']) {
      const header = document.createElement('th');
      header.scope = 'col';
      header.textContent = label;
      head.append(header);
    }
    const body = table.createTBody();
    for (const [label, saved, draft] of values) {
      const row = body.insertRow();
      const title = document.createElement('th');
      title.scope = 'row';
      title.textContent = label;
      row.append(title);
      row.insertCell().textContent = saved;
      row.insertCell().textContent = draft;
    }
    result.replaceChildren(table);
  }

  run.disabled = false;
  run.addEventListener('click', async () => {
    const draft = getDraft();
    if (!draft) return;
    run.disabled = true;
    cancel.hidden = false;
    cancel.disabled = false;
    result.replaceChildren();
    status.textContent = 'Comparing saved and draft…';
    try {
      const response = await fetch('/api/compare', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(draft),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error);
      if (!data.cancelled) show(data);
      status.textContent = data.cancelled ? 'Comparison cancelled.' : 'Balanced policy · seed 42 · up to 4 simulated hours';
    } catch (error) {
      status.textContent = error.message;
    } finally {
      run.disabled = false;
      cancel.hidden = true;
    }
  });
  cancel.addEventListener('click', async () => {
    cancel.disabled = true;
    try {
      const response = await fetch('/api/compare/cancel', {method: 'POST'});
      if (!response.ok) throw new Error('Could not cancel the comparison.');
    } catch (error) {
      status.textContent = error.message;
      cancel.disabled = false;
    }
  });
}
