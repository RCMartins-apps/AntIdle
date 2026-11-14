# Quiet CI handoff and GitHub auto-merge

The owner prefers blocker/failure/completion updates rather than repeated waiting
messages. Higher-priority platform progress requirements may still apply. GitHub
native auto-merge can remove the need to keep a Codex turn running while CI runs.

## Verified repository settings — 2026-11-12

- Repository: RCMartins-apps/AntIdle; default branch main.
- `allow_auto_merge: true`; squash is the only enabled merge method.
- The active [Main ruleset](https://github.com/RCMartins-apps/AntIdle/rules/27087827)
  targets the default branch with no exclusions. The branch reports protected=true.
- Requires a PR and both `test` and `changes / classify` from GitHub Actions
  (integration 15368), with up-to-date branches required. No bypass actors; the
  current user cannot bypass. Approval count is zero and code-owner/last-push
  approvals are not required. Deletions and force pushes are blocked; linear
  history is required. Repository squash-only settings remain authoritative.
- Classic-protection fields on the branch endpoint still say disabled/off; those
  fields do not describe ruleset requirements. The ruleset details are authoritative.
- The completed PR #65 check runs are named `test` and `changes / classify`, both
  provided by GitHub Actions. Other deploy checks are not PR quality requirements.
- The connector can enable PR auto-merge, but exposes no branch-protection write
  operation. The dedicated protection read endpoint returns 403; public branch
  metadata and ruleset reads supplied the observations above. No settings changed.

## Setup reference — already configured with an active ruleset

No additional classic rule is needed now. The following is an alternative setup,
not a request to add duplicate protection.

1. Open [repository branch settings](https://github.com/RCMartins-apps/AntIdle/settings/branches).
   Add a classic branch protection rule targeting exactly `main`.
2. Enable **Require a pull request before merging**. Review counts are a separate
   owner choice; do not add mandatory approvals just for auto-merge.
3. Enable **Require status checks to pass before merging** and select both
   **test** and **changes / classify**, using GitHub Actions as the expected source
   when available. These exact names have already run and should be selectable.
4. Ensure the rule is enforced for the identity performing auto-merge, including
   administrators if applicable (GitHub labels this “Do not allow bypassing the
   above settings” or “Include administrators”). Save the rule.
5. Confirm the rule is active and both checks appear in a new PR's merge box.
   [General settings](https://github.com/RCMartins-apps/AntIdle/settings) already has
   **Allow auto-merge** enabled under Pull Requests; no change is needed there.

An active branch ruleset with the equivalent PR/check requirements is also valid;
use one configuration rather than adding overlapping rules by accident. The need
for an up-to-date branch is an owner choice; it is not added by these instructions.
Do not require the main deployment job before merging a PR: deployment follows
merge. Docs-only PRs deliberately skip `test`; GitHub treats a skipped check as
satisfying a required check, while `changes / classify` still runs. Keep that
classifier required so a failure to classify cannot silently waive game checks.

## Per authorized PR

After local verification, create the PR and choose **Enable auto-merge → Squash**
in GitHub, or use the connector's enable-auto-merge action. GitHub waits for the
required checks and reviews, if any. Confirm the queued state before ending work.
If already eligible, GitHub may merge immediately; verify the actual result.

Codex can then report the PR URL and pending status and end the turn. It does not
need to poll or send waiting updates. If a check fails, the PR remains unmerged;
use GitHub notifications or return with the failed PR to have it investigated.
No additional bot workflow or write-enabled Actions token is needed.

With strict up-to-date checks enabled, a PR may need its branch updated when main
changes before auto-merge can complete. This is expected and does not require
weakening the rule. Auto-merge is ready to use on the next authorized PR; no live
pending PR was used to exercise a merge during this read-only settings check.

## Runtime limits and superseded runs

Repository-controlled workflows use explicit job budgets rather than GitHub's
six-hour default: PR `test` is 20 minutes, `build-and-deploy` is 15, and the reusable
`classify` job is 5. Reusable-workflow caller jobs do not accept timeout-minutes;
the runner job inside game-changes.yml enforces the classifier budget instead.
Job limits cover the entire running job, not its time waiting for a runner.

Individual steps have tighter bounds: Scala builds and engine tests 10 minutes,
browser suites 5 each, browser installation 8, and deployment 5. Setup, checkout,
packaging and artifact upload also have explicit limits in YAML. Browser suites
remain the same commands but now run in separate steps for clearer timeouts.
A timeout fails the check; it never waives required validation or proceeds to
publish after a failed build. Recent successful runs were roughly six minutes for
PR validation and three for deployment, leaving room for cold setup.

PR and deploy workflows cancel superseded runs for the same PR or branch, grouped
by workflow name plus PR number/ref. Different PRs/workflows remain independent.
Newer main pushes cancel older main deployment workflows, preventing a hung old
build from later publishing over a newer release. Docs-only classification and
existing main/debug deployment gates remain unchanged.

These settings apply to newly started workflow executions after the change is
available; they do not shorten already-running jobs. Those must be cancelled in
Actions. On 2026-11-14, runs 43583940403 and 43582168774 were still in progress
while newer main deployments had succeeded; 43583940403 was stuck specifically
at Build project. No existing run was cancelled by this change.
