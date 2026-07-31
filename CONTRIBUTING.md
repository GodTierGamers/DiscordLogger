# Contributing to DiscordLogger

Thanks for helping improve **DiscordLogger**! This page covers the practical side of contributing. For the deep dive — architecture, code conventions, the release pipeline — see **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## How changes ship (the short version)

This repo is **trunk-based**: `main` is the only long-lived branch, and everything lands through a pull request.

1. Branch off `main`: `feat/<name>`, `fix/<name>`, `docs/<name>`, etc.
2. Make your change and verify it builds:
   ```bash
   mvn -B -ntp clean package
   ```
3. Open a PR into `main` with a **[Conventional Commits](https://www.conventionalcommits.org/) title** — `feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`, `refactor: ...`, `ci: ...`, `test: ...`. CI rejects any other title format. **Your PR title becomes the changelog entry verbatim**, so write it for the people reading release notes.
4. PRs are **squash-merged** — your PR title becomes the single commit on `main`.

Everything after that is automated: merged work appears in the next **nightly build** (`vX.Y.Z-BETA.N` pre-release) automatically, accumulates in a rolling release PR, and ships in the next **stable release** when the maintainer merges it. You never touch version numbers, `CHANGELOG.md`, or release artifacts — the automation owns those files.

## Before you open a PR

- **Java <!-- dl:sync:java -->25<!-- /dl:sync -->** (Temurin recommended) and Maven.
- `mvn -B -ntp clean package` must pass.
- Test on a real **Paper <!-- dl:sync:paper_display -->26.x<!-- /dl:sync -->** server when your change touches runtime behavior — a clean compile is not a functional test.
- **Config changes travel in lockstep**: if you add or change a `log.*` / `embeds.*` config key, the same PR must update the listener, `src/main/resources/config.yml`, and the website generator data (`docs/assets/configs/v*/options.json` + `config.template.yml`). Run the checker locally:
  ```bash
  python3 scripts/validate-config-generator.py
  ```
  CI runs it too and will fail the PR on a mismatch.
- Be defensive around config: default safely, avoid NPEs, log clear errors.
- Prefer small, focused PRs — one change per PR, since one PR = one changelog line.

## Using AI tools

AI-assisted contributions are welcome — this project itself is developed with AI assistance (see the [AI Disclosure](README.md#-ai-disclosure) in the README). Two expectations apply:

1. **Disclose it.** If AI tools meaningfully assisted your change, say so in the PR description — one sentence is plenty. This is about honesty, not gatekeeping; disclosed AI assistance is never held against a contribution.
2. **Review it yourself, and be able to answer for it.** You are the author of everything you submit. That means you've read every line, you understand what it does and why, and you've actually tested it — not just confirmed the AI said it works. PRs that appear to be unreviewed AI output (code that doesn't match the project's patterns, references to things that don't exist, untested behavior) will be closed.

The maintainer reviews every PR the same way regardless of how it was written.

## Reporting bugs & requesting features

Use the issue templates — they collect the details that make issues actionable:

- [Bug report](https://github.com/GodTierGamers/DiscordLogger/issues/new?template=bug_report.yml)
- [Feature request](https://github.com/GodTierGamers/DiscordLogger/issues/new?template=feature-request.yml)
- [Docs update](https://github.com/GodTierGamers/DiscordLogger/issues/new?template=docs_update.yml)
- [Support / question](https://github.com/GodTierGamers/DiscordLogger/issues/new?template=support.yml)

For bugs on **nightly builds**: please include the full version (e.g. `2.3.0-BETA.4`) — it identifies the exact build. Nightly bug reports are especially valuable; catching things before a stable release is the whole point of the channel.

## Website / docs

The site (`docs/`) is a Jekyll project deployed from `main` via GitHub Pages. To run it locally:

```bash
cd docs && bundle install && bundle exec jekyll serve --livereload --watch
```

Docs-only PRs skip the Java build in CI automatically.
