# Pull Request

## What’s changed?
<!-- Short, user-facing bullets. Keep it human. -->
-

## Type of change
<!-- Tick all that apply; please add matching labels. -->
- [ ] ✨ Feature (`feat`)
- [ ] 🐛 Fix (`bug`)
- [ ] 🛠 Refactor (`refactor`)
- [ ] 🧰 Maintenance (`chore`)
- [ ] 🧪 Tests (`test`)
- [ ] 📝 Docs (`docs`)
- [ ] ⚙️ CI/CD (`ci`, `build`)
- [ ] 🔥 Breaking change (explain below)
- [ ] Skip changelog (`skip-changelog`)

## Motivation & context
<!-- Why is this needed? Link issues/threads (e.g., Closes #123). -->

## How to test (exact steps)
<!-- Be explicit so reviewers can reproduce locally or on a test server. -->
1.
2.
3.

## Plugin impacts (configs, commands, permissions)
<!-- Note any user/admin actions required. -->
- Config keys added/changed:
- Commands added/changed:
- Permission nodes:
- Data migration:
- Backward-compat:

## Risk & rollout
- Risk level: ☐ Low ☐ Medium ☐ High
- Mitigations / tests:
- Manual rollback steps (if any):

## Screenshots / logs (optional)
<!-- Paste webhook screenshots, console logs, or traces. -->

---

### Checklist
- [ ] Compiles locally
```bash
mvn -B -ntp clean package
```
- [ ] Tests added/updated (if logic changed)
- [ ] Docs updated (README / docs/) if behavior changed
- [ ] Labels match type (feat/bug/refactor/docs/ci/chore/ci)
- [ ] Config defaults are safe (no NPEs on missing keys)
- [ ] Verified on supported MC/Paper/Spigot version(s)

