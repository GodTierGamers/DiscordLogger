# Contributing to DiscordLogger

Thanks for helping improve **DiscordLogger**! This guide explains how we work and gives you one-click links to open PRs with the right template.

## Branch model

- Work happens on feature branches cut from **`dev`**.
- Merge feature PRs into **`dev`**.
- When ready to ship, open a **release PR** from **`dev` → `main`**.  
  The release name/tag/body come from **`.github/release-spec.md`** (not PR text).

## Before you start

- Java 21 (Temurin recommended)
- Build locally:
<!-- CODE BLOCK (bash):
mvn -B -ntp clean package
-->
- Test on a clean Paper/Spigot server when possible.

## Pull Requests

Pick the template that fits your change. All links below target **base = `dev`**.

> **From a branch in this repo:** replace `<your-branch>`  
> **From a fork:** replace `GodTierGamers:dev...YOURUSER:<your-branch>`

- **Feature**  
  https://github.com/GodTierGamers/DiscordLogger/compare/dev...<your-branch>?expand=1&template=feature.md
- **Bug fix**  
  https://github.com/GodTierGamers/DiscordLogger/compare/dev...<your-branch>?expand=1&template=bugfix.md
- **Refactor**  
  https://github.com/GodTierGamers/DiscordLogger/compare/dev...<your-branch>?expand=1&template=refactor.md
- **Docs**  
  https://github.com/GodTierGamers/DiscordLogger/compare/dev...<your-branch>?expand=1&template=docs.md
- **CI/CD**  
  https://github.com/GodTierGamers/DiscordLogger/compare/dev...<your-branch>?expand=1&template=ci-cd.md
- **Maintenance / chore**  
  https://github.com/GodTierGamers/DiscordLogger/compare/dev...<your-branch>?expand=1&template=maintenance.md
- **General**  
  https://github.com/GodTierGamers/DiscordLogger/compare/dev...<your-branch>?expand=1&template=general.md

### PR checklist (quick)

- [ ] Compiles locally
<!-- CODE BLOCK (bash):
mvn -B -ntp clean package
-->
- [ ] Tests/logs show the behavior works
- [ ] Labels match type (feat/bug/refactor/docs/ci/chore)
- [ ] Updated docs/config examples if behavior changed

## Releases (dev → main)

1. On **`dev`**, fill in **`.github/release-spec.md`**:
    - `Release Title:`
    - `Release Tag Number:`
    - `Custom File Name:` (no `.jar`)
    - Tick `[ ] No Changelog` or `[ ] Pre-Release` if needed
    - Put any **manual notes** between `---CONTENT---` and `---END---`.
2. Open PR **`dev` → `main`** (any template is fine; release data comes from the spec file).
3. Merge: the workflow tags, builds, publishes the release, then resets the spec on **`dev`** and **`main`**.

## Issue tracker (quick links)

- **Bug report**  
  https://github.com/GodTierGamers/DiscordLogger/issues/new?template=bug_report.yml
- **Feature request**  
  https://github.com/GodTierGamers/DiscordLogger/issues/new?template=feature_request.yml
- **Docs update**  
  https://github.com/GodTierGamers/DiscordLogger/issues/new?template=docs_update.yml
- **Support / question**  
  https://github.com/GodTierGamers/DiscordLogger/issues/new?template=support.yml

## Coding style & notes

- Be defensive around config: default safely, avoid NPEs; log clear errors.
- Keep messages user-facing and consistent with existing formatting.
- Prefer small, focused PRs.  
