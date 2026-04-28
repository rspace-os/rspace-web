---
name: rspace-empty-integration
description: Scaffold a new "empty" RSpace integration end-to-end (Liquibase migration, sysadmin/user toggles, Apps-page card, TinyMCE toolbar icon opening a blank-bodied dialog with the integration's name as the chrome title). Use when a user asks to add, scaffold, create, or generate a new integration in the rspace-web codebase, mentions an integration name (e.g. "add a Galaxy/Foo/Bar integration"), or refers to "empty integration", "integration skeleton", or "new App". Do not use this skill when the request requires authentication (OAuth/API key), per-user configuration, deployment URLs, or backend Java services — those layers must be added by hand on top of the empty scaffold.
---

# RSpace Empty Integration Skill

Scaffold a new RSpace integration with the minimum surface area: database
toggles, an Apps-page card, and a TinyMCE toolbar icon that opens a blank
dialog whose title chrome is the integration name. Authentication, deployment
URLs, and domain logic are out of scope.

## Quick start

Inputs to gather from the user:

- `<Name>` — PascalCase (e.g. `Galaxy`)
- `<ticket>` — Liquibase changeset suffix (e.g. `rsdev-1234`)

Derived: `<NAME>` = uppercase, `<name>` = lowercase, `<DATE>` = today's date in
`YYYY-MM-DD`.

Workspace root: `rspace-web/` (a Java/Spring + React/TypeScript monorepo).

## Workflow

Work through this checklist in order. **Do not skip steps.** All edits must be
idempotent: detect existing entries and refuse to overwrite.

### 1. Read the spec

Read `DevDocs/DeveloperNotes/EmptyIntegrationSkillPlan.md` for full context
on what is and isn't in scope, and re-read it before each ambiguous decision.

### 2. Verify prerequisites

- Confirm `rspace-web/pom.xml` exists at the workspace root.
- Confirm `<NAME>` is not already a key in
  `src/main/webapp/ui/src/eln/apps/useIntegrationsEndpoint.ts` `IntegrationStates`
  type. If it is, abort with a clear message — the integration already exists.

### 3. Create new files (9)

For exact file contents and templates, see [REFERENCE.md](REFERENCE.md):

- [ ] `src/main/resources/sqlUpdates/changeLog-<ticket>.xml`
- [ ] `src/main/webapp/ui/src/assets/branding/<name>/index.ts`
- [ ] `src/main/webapp/ui/src/assets/branding/<name>/logo.svg`
- [ ] `src/main/webapp/ui/src/eln/apps/integrations/<Name>.tsx`
- [ ] `src/main/webapp/scripts/externalTinymcePlugins/<name>/plugin.min.js`
- [ ] `src/main/webapp/scripts/externalTinymcePlugins/<name>/dialog.html`
- [ ] `src/main/webapp/ui/src/tinyMCE/<name>/index.tsx`
- [ ] `src/main/webapp/ui/src/tinyMCE/<name>/<Name>.tsx`
- [ ] `src/main/webapp/ui/src/tinyMCE/<name>/__tests__/<Name>.test.tsx`

### 4. Modify existing files (13)

Each edit must preserve existing alphabetical ordering. See
[REFERENCE.md](REFERENCE.md) for the exact insertion patterns.

- [ ] `src/main/java/com/researchspace/service/IntegrationsHandler.java`
- [ ] `src/main/java/com/researchspace/service/SystemPropertyName.java`
- [ ] `src/main/java/com/researchspace/service/impl/IntegrationsHandlerImpl.java`
- [ ] `src/main/java/com/researchspace/webapp/controller/IntegrationController.java`
- [ ] `src/main/resources/bundles/system/system.properties`
- [ ] `src/main/resources/sqlUpdates/liquibase-master.xml`
- [ ] `src/main/webapp/ui/src/eln/apps/CardListing.tsx`
- [ ] `src/main/webapp/ui/src/eln/apps/useIntegrationsEndpoint.ts`
- [ ] `src/main/webapp/ui/src/eln/apps/__tests__/allIntegrationsAreDisabled.json`
- [ ] `src/main/webapp/ui/src/assets/DocLinks.ts`
- [ ] `src/main/webapp/WEB-INF/pages/system/settings_ajax.jsp`
- [ ] `src/main/webapp/scripts/pages/system/settings_mod.js`
- [ ] `src/main/webapp/scripts/pages/workspace/editor/tinymce5_configuration.js`
- [ ] `src/main/webapp/scripts/tinymce/tinymce5109/icons/custom_icons/icons.js`
- [ ] `build-resources/resources_to_MD5_rename.txt`
- [ ] `src/main/webapp/ui/webpack.config.mjs`

> **Verify every edit takes effect.** Some edit tools may report success
> without actually applying the change. After each modification, run a quick
> `grep` for the inserted string in the target file. If it isn't present,
> re-apply the edit using a deterministic Python script
> (`content.replace(needle, replacement, 1)`) rather than retrying the same
> tool call.

### 5. Verify

Run the frontend type-check and lint to catch typos in the generated edits:

```bash
cd src/main/webapp/ui && npm run tsc && npm run lint
```

Optionally run the smoke test for the new TinyMCE component:

```bash
cd src/main/webapp/ui && npx vitest run src/tinyMCE/<name>/__tests__/<Name>.test.tsx
```

### 6. Report manual follow-ups

After the scaffold is in place, output a checklist of work the developer must
complete by hand: replace placeholder card strings, replace
`TODO_<NAME>_DOC_ID` in `DocLinks.ts`, hand-tune `LOGO_COLOR`, replace the
placeholder logo and TinyMCE icon SVGs, and pick the right category in
`settings_mod.js`.

## Out of scope

This skill does **not** handle: OAuth, API-key auth, per-user options,
deployment URLs, backend services/controllers/DAOs, archive export,
per-text-field React components (the `externalWorkFlows` bundle pattern), or
the `enabledFileRepositories` / `fileRepositoriesMenu` / "Insert from…" menu
plumbing. If any of these are needed, scaffold the empty integration first,
then add those layers by hand following
`DevDocs/DeveloperNotes/CreatingNewIntegration.md` and
`src/main/webapp/ui/src/eln/apps/AddingANewIntegration.md`.




