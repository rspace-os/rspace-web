---
name: rspace-empty-integration
description: Scaffold an empty RSpace integration with Liquibase toggles, an Apps card, and a TinyMCE button that opens a blank dialog. Use for new integration skeletons. Do not use when authentication, deployment URLs, per-user configuration, or backend services are required.
---

# RSpace empty integration

Create database toggles, an Apps card, and a TinyMCE toolbar button opening a
blank dialog titled with the integration name.

Get `<Name>` in PascalCase and `<ticket>` for the Liquibase changeset suffix.
Derive `<NAME>` as uppercase, `<name>` as lowercase, and `<DATE>` as today's
`YYYY-MM-DD` date. Work from the repository root containing `pom.xml` and `mvnw`.

## Workflow

1. Read `DevDocs/DeveloperNotes/EmptyIntegrationSkillPlan.md` for scope;
   consult it again for ambiguous decisions.
2. Confirm `pom.xml` exists and `<NAME>` is absent from `IntegrationStates` in
   `src/main/webapp/ui/src/eln/apps/useIntegrationsEndpoint.ts`. Stop if it exists.
3. Follow [REFERENCE.md](REFERENCE.md) to create all nine files and modify all
   sixteen listed files. Preserve alphabetical ordering. Detect existing files
   and entries; refuse to overwrite them. Verify each insertion with `rg`.
4. Run `pnpm run build`. The dialog loads `tinymce<Name>.js`; without the bundle,
   it opens blank with a failed resource request.
5. Run the frontend checks from the root. Regenerate the i18n types first:
   the card's `t()` keys and `docLink="<name>"` are typed from the generated
   `resources.d.ts`, so `tsc` fails until the new `apps` and `help` keys are
   in it.

   ```bash
   pnpm run i18n:types
   pnpm run i18n:lint
   pnpm run tsc
   pnpm run lint
   pnpm run test src/tinyMCE/<name>/__tests__/<Name>.test.tsx
   ```

6. Verify backend wiring against a running database:

   ```bash
   mvn test -Dtest=IntegrationControllerMVCIT#getAllIntegrations
   ```

   Alternatively, start the app and confirm `<NAME>` in
   `GET /integration/allIntegrations`. Follow `AGENTS.md` for live verification.
7. Report the reference's manual follow-ups: card text in `apps.json`, website,
   `TODO_<NAME>_DOC_ID` in `common.json`, `LOGO_COLOR`, logo and toolbar SVGs,
   and the category in `settings_mod.js`.

## Scope

Exclude OAuth, API keys, per-user options, deployment URLs, new backend
services/controllers/DAOs, archive export, per-text-field React components,
`externalWorkFlows`, and `enabledFileRepositories` / `fileRepositoriesMenu` /
"Insert from…" menu wiring. For later implementation, follow
`DevDocs/DeveloperNotes/CreatingNewIntegration.md` and
`src/main/webapp/ui/src/eln/apps/AddingANewIntegration.md`.
