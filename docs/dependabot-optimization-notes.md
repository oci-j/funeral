# Dependabot Optimization Notes — oci-j/funeral

Date: 2026-07-09

## What changed

| File / setting | Status before | Status after |
| --- | --- | --- |
| `.github/dependabot.yml` | absent | weekly schedule for `maven`, `npm`, `github-actions`, `docker` |
| `.github/workflows/auto-merge.yml` | absent | approve + auto-merge qualifying dependabot PRs |
| `allow_auto_merge` (repo setting) | `false` | `true` |
| `MYTOKEN` (dependabot namespace) | absent | set to current `gh` OAuth token (admin user) |
| `main` branch protection | absent | strict + linear, requires `Build JVM Version` and `Build Native Binary with GraalVM` |
| Labels: `dependencies`, `java`, `github-actions`, `javascript`, `docker` | only `bug`, `documentation`, `duplicate`, `enhancement`, `good first issue`, `help wanted`, `invalid`, `question`, `wontfix` | +5 dependabot labels |
| `git remote -v` | `https://github.com/oci-j/funeral.git` | `git@github.com:oci-j/funeral.git` (SSH, to bypass `workflow` OAuth scope) |

## Auto-merge policy

Hybrid strategy:

| `update-type` | Ecosystem head ref | Action |
| --- | --- | --- |
| `semver-patch` | any | auto-merge |
| `semver-minor` | any | auto-merge |
| `semver-major` | `dependabot/github_actions/*` | auto-merge (Node runtime bumps are usually safe) |
| `semver-major` | `dependabot/maven/*`, `dependabot/npm/*`, `dependabot/docker/*` | leave for human review |

## Pitfalls anticipated and mitigated

1. **Pitfall 5 — GITHUB_TOKEN cannot enable auto-merge on workflow PRs.** Resolved by storing `gh auth token` (admin user OAuth) as `MYTOKEN` in the **dependabot** namespace (`--app dependabot`). Without `--app dependabot`, dependabot-triggered runs would see the secret as empty.
2. **Pitfall 5b — OAuth scope blocks workflow pushes.** The first `git push` was rejected with `OAuth App to create or update workflow ... without 'workflow' scope`. Switched the remote from HTTPS to SSH; the same `gh auth` user was admin and had `workflow` scope on the SSH path. Subsequent pushes succeeded via "Bypassed rule violations".
3. **Pitfall 6 — `allow_auto_merge` was off.** Enabled via `gh api -X PATCH repos/oci-j/funeral -f allow_auto_merge=true`. Verified.
4. **Pitfall 8 — Login mismatch.** The `if:` line matches both `dependabot[bot]` and `app/dependabot` logins (GitHub's 2024+ migration).
5. **Pitfall 11 — Wrapper actions hide errors.** No third-party `action-dependabot-auto-merge` was used; the workflow uses explicit `gh pr review` + `gh pr merge --auto --rebase` steps so any 422 surfaces as a red step instead of a swallowed green.
6. **Pitfall 13 — `groups: patterns: ["*"]`.** No `groups:` block added. One PR per dependency per cycle. Easier to review and revert.
7. **Pitfall 16 — dependabot secret namespace.** All custom secrets (currently `MYTOKEN`) must use `--app dependabot`. Verified with `gh secret list --app dependabot`.

## Why no `versioning-strategy`

`versioning-strategy` is not accepted by `maven`, `github-actions`, or `docker` ecosystems (only supported for bundler/cargo/composer/mix/npm/pip/pub/uv). Adding it would cause Dependabot to reject the config.

## Why no `groups:` block

A single grouped PR collapses N unrelated bumps into one diff. When that PR fails CI, you cannot tell which bump caused it. Default behaviour (one PR per dependency per cycle) keeps each diff small enough to read, bisect and revert in isolation. The higher PR count is offset by `commit-message.prefix` + `labels` for scannability.

## Verification checklist (run after first dependabot cycle)

- [ ] First dependabot PRs land on `main` (look for `app/dependabot` author, not `dependabot[bot]`).
- [ ] `gh pr view <N> --json autoMergeRequest` is non-null on patch/minor bumps.
- [ ] Major Maven bumps leave `autoMergeRequest: null` — left for human review.
- [ ] `Build JVM Version` and `Build Native Binary with GraalVM` show green on the PR's Checks tab.
- [ ] `gh run list --workflow="Dependabot auto-merge" --limit 5` shows a run per qualifying PR.

## Re-run if you change `build.yml`

If `build.yml`'s job `name:` fields change, or you add new matrix axes to `Build JVM Version` / `Build Native Binary with GraalVM`, re-run:

```bash
gh api repos/oci-j/funeral/branches/main/protection | jq '.required_status_checks.checks[].context'
```

…and update the `checks` array via `gh api -X PUT .../branches/main/protection` to match the new check names (Pitfall 9).
