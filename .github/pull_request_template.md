# Pull Request

## What's changed?
<!-- Short, user-facing bullets. Keep it human. -->
-

## Motivation & context
<!-- Why is this needed? Link issues/threads (e.g., Closes #123). -->

## How to test (exact steps)
<!-- Be explicit so reviewers can reproduce locally or on a test server. -->
1.
2.
3.

## Plugin impacts (configs, commands, permissions)
<!-- Note any user/admin actions required. Leave blank if none. -->
- Config keys added/changed:
- Commands added/changed:
- Permission nodes:
- Data migration:

## Screenshots / logs (optional)
<!-- Paste webhook screenshots, console logs, or traces. -->

---

### Checklist
- [ ] **PR title follows Conventional Commits** (`feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`, `refactor: ...`, `ci: ...`, `test: ...`) — this title becomes the changelog entry on release, and CI will reject the PR if it doesn't match.
- [ ] `mvn -B -ntp clean package` builds locally
- [ ] Config defaults are safe (no NPEs on missing keys)
- [ ] If a `log.*`/`embeds.*` config key was added/changed: `EventRegistry`/listener wired up, `config.yml` updated, **and** `docs/assets/configs/v*/options.json` + `config.template.yml` updated to match (CI's `validate-generator-data` job checks this)
- [ ] Docs updated (`README.md` / `docs/`) if user-facing behavior changed
- [ ] Verified on a real Paper server, not just `mvn package`
- [ ] If AI tools meaningfully assisted this change, I've disclosed that above and have personally reviewed, understood, and tested everything submitted (see [CONTRIBUTING.md](../CONTRIBUTING.md))
