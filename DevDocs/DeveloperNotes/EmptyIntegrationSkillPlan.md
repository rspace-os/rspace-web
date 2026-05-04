# Empty Integration Skill — Plan

This document specifies the canonical scaffolding required to add a new
"empty" integration to RSpace: an integration whose only behaviour is

1. registering a sysadmin-level on/off toggle and a per-user on/off toggle in
   the database,
2. appearing as a card on the **Apps** page,
3. registering a TinyMCE toolbar icon that, when clicked, opens an
   otherwise-blank dialog whose title chrome shows the integration's name.

It does **not** cover authentication (OAuth, API keys), per-user configuration
options, deployment-level URLs, backend Java services/controllers/DAOs, or any
domain logic. Those layers are added on top once an empty integration exists.

The plan is derived from a detailed analysis of the seven Galaxy commits
(`e0b2ab0e`, `33b42970`, `8a4c9719`, `3916c38b`, `e6c82abd`, `60021385`,
`d8cb0495`), separating generic-integration scaffolding from Galaxy-specific
work.

It is intended to be implemented as an agent skill (see
`.agents/skills/rspace-empty-integration/`) and read by humans adding
integrations by hand. See also:

- `DevDocs/DeveloperNotes/CreatingNewIntegration.md`
- `src/main/webapp/ui/src/eln/apps/AddingANewIntegration.md`
- `src/main/webapp/ui/src/eln/apps/README.md`

## Inputs

The skill / human implementer needs four pieces of data:

| Variable        | Example         | Notes                                              |
| --------------- | --------------- | -------------------------------------------------- |
| `<Name>`        | `Galaxy`        | PascalCase. Used in display names and class names. |
| `<NAME>`        | `GALAXY`        | UPPERCASE. Used in Java constants and TS keys.     |
| `<name>`        | `galaxy`        | lowercase. Used in URLs, file paths, JSON keys.    |
| `<ticket>`      | `rsdev-1234`    | Liquibase changeset filename suffix.               |
| `<DATE>`        | `2026-04-28`    | Liquibase changeset id prefix (today's date).      |

## Files created (9)

| Path                                                                                | Purpose                                                                 |
| ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `src/main/resources/sqlUpdates/changeLog-<ticket>.xml`                               | Liquibase: App row + sysadmin PropertyDescriptor / SystemProperty.      |
| `src/main/webapp/ui/src/assets/branding/<name>/index.ts`                             | Exports `LOGO_COLOR` HSL.                                               |
| `src/main/webapp/ui/src/assets/branding/<name>/logo.svg`                             | 100×100 placeholder logo.                                               |
| `src/main/webapp/ui/src/eln/apps/integrations/<Name>.tsx`                            | Apps-page card (Chemistry-style simple toggle).                         |
| `src/main/webapp/scripts/externalTinymcePlugins/<name>/plugin.min.js`                | TinyMCE plugin: registers icon + toolbar button + dialog opener.        |
| `src/main/webapp/scripts/externalTinymcePlugins/<name>/dialog.html`                  | Dialog body host that loads the React bundle.                           |
| `src/main/webapp/ui/src/tinyMCE/<name>/index.tsx`                                    | Mounts React component into `#tinymce-<name>`.                          |
| `src/main/webapp/ui/src/tinyMCE/<name>/<Name>.tsx`                                   | Empty React component.                                                  |
| `src/main/webapp/ui/src/tinyMCE/<name>/__tests__/<Name>.test.tsx`                    | Vitest smoke test.                                                      |

## Files modified (16)

| Path                                                                                       | Edit                                                                                                                  |
| ------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| `src/main/java/com/researchspace/service/IntegrationsHandler.java`                         | Insert `String <NAME>_APP_NAME = "<NAME>";` constant in alphabetical order.                                           |
| `src/main/java/com/researchspace/service/SystemPropertyName.java`                          | Add `<NAME>_AVAILABLE("<name>.available"),` enum entry. Without this, `SystemPropertyManagerImpl`'s `@Cacheable(key = "#name.propertyName")` resolves on a null and throws `EL1007E: Property or field 'propertyName' cannot be found on null`, breaking `/integration/allIntegrations`. |
| `src/main/resources/bundles/system/system.properties`                                      | Add `system.property.description.<name>.available=Makes <Name> integration available.`                                |
| `src/main/resources/sqlUpdates/liquibase-master.xml`                                       | Register the new changeset.                                                                                            |
| `src/main/webapp/ui/src/eln/apps/CardListing.tsx`                                          | Add import, `<name>Update` `useCallback`, and conditional render block (alphabetical).                                |
| `src/main/webapp/ui/src/eln/apps/useIntegrationsEndpoint.ts`                               | Add `IntegrationStates` entry, decoder, encoder branch, two switch arms.                                              |
| `src/main/webapp/ui/src/eln/apps/__tests__/allIntegrationsAreDisabled.json`                | Insert `"<NAME>": { … }` fixture object.                                                                              |
| `src/main/webapp/ui/src/assets/DocLinks.ts`                                                | Add `<name>: mkDocLink("TODO_<NAME>_DOC_ID"),`.                                                                       |
| `src/main/webapp/WEB-INF/pages/system/settings_ajax.jsp`                                   | Add `<div id="<name>.available.description">…</div>`.                                                                 |
| `src/main/webapp/scripts/pages/system/settings_mod.js`                                     | Add `_printSettings(['<name>.available']);` under an existing category.                                               |
| `src/main/webapp/scripts/pages/workspace/editor/tinymce5_configuration.js`                 | Add `<name>Enabled` const, plus an `if (<name>Enabled) { ... }` block containing both `localTinymcesetup.external_plugins["<name>"] = "..."` AND `addToToolbarIfNotPresent(localTinymcesetup, " | <name>")` (the second line is required for the toolbar button to appear). |
| `src/main/webapp/scripts/tinymce/tinymce516/icons/custom_icons/icons.js`                   | Register a `<name>` SVG icon entry.                                                                                   |
| `build-resources/resources_to_MD5_rename.txt`                                              | Add `scripts/externalTinymcePlugins/<name>/plugin.min.js` and `ui/dist/tinymce<Name>.js` lines.                       |
| `src/main/webapp/ui/webpack.config.js`                                                     | Add `tinymce<Name>: "./src/tinyMCE/<name>/index.tsx",` entry.                                                         |

## Out of scope (explicitly NOT touched)

The empty-integration skill **does not** modify any of:

- `IntegrationsHandlerImpl.java` — only needed when there are per-user options
  (OAuth tokens or API keys saved via `UserConnectionManager`).
- `IPropertyHolder.java`, `PropertyHolder.java`, `defaultDeployment.properties`
  — only needed for deployment-level URLs.
- `IntegrationController.java`'s endpoint methods — except for the new
  `rc.put(<NAME>_APP_NAME, ...)` in `getAll(User)` (which IS required).
- `WEB-INF/pages/notebookEditor/notebookEditor.jsp`,
  `WEB-INF/pages/workspace/editor/structuredDocument.jsp`,
  `WEB-INF/pages/workspace/editor/include/textField.jsp`,
  `scripts/pages/journal.js` — these are only modified by integrations that
  mount per-text-field React components (e.g. Galaxy's
  `ext-workflows-textfield`).
- The `externalWorkFlows` webpack entry — Galaxy-specific workflow tracking
  bundle.
- `BaseController`, `ControllerExceptionHandler`,
  `IControllerExceptionHandler`, `NotFoundLoggedAsErrorExceptionHandlerVisitor`,
  `JournalController`, `StructuredDocumentController`,
  `DeploymentPropertiesController` — Galaxy-PR-specific exception-handling
  refactors, not generic.
- The `enabledFileRepositories` / `fileRepositoriesMenu` strings and the
  `localTinymcesetup.recordId = recordId` line in `tinymce5_configuration.js`.
- The `addMenuItem('opt<Name>', …)` registration and `window.insertActions`
  population in `plugin.min.js` — those wire the integration into the
  "Insert from…" dropdown menu, which the empty integration omits.

## Conventions

- **Liquibase**: each `<changeSet>` uses `context="run"` and an id of the form
  `<DATE>a`, `<DATE>b`, …
- **`PropertyDescriptor.defaultValue`** = `DENIED`. **`SystemPropertyValue.value`**
  = `DENIED`. (The Galaxy commit used `ALLOWED` for the descriptor; that is an
  outlier and not the convention.)
- **TypeScript credentials** for an empty integration: `Record<string, never>`.
  The encoder emits `options: {}`; the decoder emits `credentials: {}`.
- **Apps-page card**: follows the **Chemistry** simple-toggle pattern. No
  `useState`, no auth form, `update={(newMode) => update({ mode: newMode,
  credentials: {} })}`. Setup section is a two-step `<ol>`. Placeholder strings
  for `explanatoryText`, `usageText`, `helpLinkText`, `website`. Each placeholder
  carries a `// TODO:` comment.
- **TinyMCE dialog**: dialog title comes from
  `windowManager.openUrl({ title: "<Name>", … })`. The React bundle mounts
  into `#tinymce-<name>` but renders an empty fragment.
- **Icon**: 20×20-ish monochrome SVG using `#151515` for the strokes/fills,
  matching the existing custom icon style.
- **Branding logo**: 100×100 SVG. Background is the `LOGO_COLOR` hue at high
  saturation; foreground is white initials of the integration name.
- **Idempotency**: every edit must detect existing entries (e.g.
  `<NAME>:` already in `IntegrationStates`) and refuse to overwrite, so
  re-running on the same name is safe.
- **Insertion order**: insertions into existing files should preserve
  alphabetical ordering wherever the surrounding file is alphabetised.

## Manual follow-up after running the skill

1. Replace placeholder strings in `<Name>.tsx`
   (`explanatoryText`, `usageText`, `helpLinkText`, `website`).
2. Replace `TODO_<NAME>_DOC_ID` in `DocLinks.ts` with the real helpdocs short
   ID once the help page exists.
3. Hand-tune `LOGO_COLOR` in `branding/<name>/index.ts` to match the real
   service's brand.
4. Replace the placeholder `branding/<name>/logo.svg` with the real
   100×100 logo per the conventions in
   `src/main/webapp/ui/src/eln/apps/README.md`.
5. Replace the TinyMCE icon SVG in `icons.js` with a properly-designed icon.
6. Choose the appropriate sysadmin-settings category in `settings_mod.js`
   (the skill defaults to `'Other'`).

