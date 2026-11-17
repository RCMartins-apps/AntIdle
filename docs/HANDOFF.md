# Handoff

## 2026-11-17 — fix Pages checkout and gate auto-merge on preview

- PR #95 merged as main 54d3e41 after all tests passed; deploy-preview failed
  because the Pages action had no Git checkout. `fix/pages-publishing-checkout`
  starts at that main and adds gh-pages checkout to both publishers before
  artifact download. No PR code executes; checkout credentials disabled.
- Required test check now aggregates validate + eligible preview publication,
  preventing auto-merge before a working preview. Fork/manual runs still require
  validation, docs-only require successful classification and skip game/deploy.
  Required check identities test and changes / classify remain unchanged.
- Actionlint and diff checks passed; workflow-only fix, no game rebuild needed.
  Live publication awaits CI on the follow-up PR; auto-merge authorized as part
  of repairing #95. Ruleset verified active, strict checks, no bypass.
- feature/pr-previews was briefly recreated by the repair push after #95 deleted
  it; the focused follow-up branch is authoritative. No branches deleted.
- Pending queue work remains at polish/safer-queue-controls 07a323b; not merged.
  Preserve unrelated work/ and Python caches.
