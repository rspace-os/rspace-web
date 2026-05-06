# RSpace Empty Integration Skill — Reference

Detailed templates and edit recipes. Substitute `<Name>`, `<NAME>`, `<name>`,
`<ticket>`, and `<DATE>` throughout.

> **Hashing the LOGO_COLOR hue.** Compute hue as a deterministic hash of the
> integration's lowercase name, modulo 360. A simple djb2-style hash works:
> `let h = 5381; for (const c of "<name>") h = ((h << 5) + h + c.charCodeAt(0)) | 0;
> hue = ((h % 360) + 360) % 360;`. This guarantees distinct but stable defaults.

---

## Files to create

### 1. `src/main/resources/sqlUpdates/changeLog-<ticket>.xml`

```xml
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
         http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.0.xsd">

    <!-- <Name> integration -->
    <changeSet id="<DATE>a" context="run" author="agent">
        <comment>Create a new app - <Name></comment>
        <insert tableName="App">
            <column name="id" type="NUMERIC" value="NULL"/>
            <column name="label" type="STRING" value="<Name>"/>
            <column name="name" type="STRING" value="app.<name>"/>
            <column name="defaultEnabled" type="BOOLEAN" valueBoolean="false"/>
        </insert>
    </changeSet>

    <changeSet id="<DATE>b" context="run" author="agent">
        <comment>Create a new system property for <Name></comment>
        <insert tableName="PropertyDescriptor">
            <column name="id" type="NUMERIC" value="NULL"/>
            <column name="defaultValue" type="STRING" value="DENIED"/>
            <column name="name" type="STRING" value="<name>.available"/>
            <column name="type" type="NUMERIC" value="3"/>
        </insert>
        <insert tableName="SystemProperty">
            <column name="id" type="NUMERIC" value="NULL"/>
            <column name="dependent_id" type="NUMERIC" value="NULL"/>
            <column name="descriptor_id" type="NUMERIC"
                    valueComputed="(select id from PropertyDescriptor where name ='<name>.available')"/>
        </insert>
        <insert tableName="SystemPropertyValue">
            <column name="id" type="NUMERIC" value="NULL"/>
            <column name="value" type="String" value="DENIED"/>
            <column name="property_id" type="NUMERIC"
                    valueComputed="(select sp.id from SystemProperty sp inner join PropertyDescriptor pd on sp.descriptor_id=pd.id where pd.name='<name>.available')"/>
        </insert>
    </changeSet>

</databaseChangeLog>
```

### 2. `src/main/webapp/ui/src/assets/branding/<name>/index.ts`

```ts
/**
 * The colour used in the background of the logo. Hand-tune to match the
 * service's real brand colour.
 */
export const LOGO_COLOR = {
  hue: <HASHED_HUE>,
  saturation: 50,
  lightness: 40,
};
```

### 3. `src/main/webapp/ui/src/assets/branding/<name>/logo.svg`

100×100 placeholder. `<INITIALS>` is the first one or two letters of `<Name>`.

```xml
<svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 100 100">
  <circle cx="50" cy="50" r="48" fill="hsl(<HASHED_HUE>, 50%, 40%)"/>
  <text x="50" y="62" text-anchor="middle"
        font-family="Helvetica, Arial, sans-serif" font-size="42"
        font-weight="700" fill="#ffffff"><INITIALS></text>
</svg>
```

### 4. `src/main/webapp/ui/src/eln/apps/integrations/<Name>.tsx`

```tsx
import Grid from "@mui/material/Grid";
import React from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import <Name>Icon from "../../../assets/branding/<name>/logo.svg";
import { LOGO_COLOR } from "../../../assets/branding/<name>";

type <Name>Args = {
  integrationState: IntegrationStates["<NAME>"];
  update: (newIntegrationState: IntegrationStates["<NAME>"]) => void;
};

/*
 * <Name> integration — empty scaffold. Replace placeholder text below.
 */
function <Name>({ integrationState, update }: <Name>Args): React.ReactNode {
  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="<Name>"
        integrationState={integrationState}
        // TODO: replace placeholder explanatoryText with a real description.
        explanatoryText="<Name> integration. TODO: replace this placeholder."
        image={<Name>Icon}
        color={LOGO_COLOR}
        update={(newMode) => update({ mode: newMode, credentials: {} })}
        // TODO: replace placeholder usageText.
        usageText="TODO: describe how this integration is used inside RSpace."
        // TODO: replace placeholder helpLinkText.
        helpLinkText="<Name> integration docs"
        // TODO: replace placeholder website.
        website="example.com"
        docLink="<name>"
        setupSection={
          <ol>
            <li>Enable the integration.</li>
            <li>
              When editing a document, click the <Name> icon in the text editor
              toolbar to open the dialog.
            </li>
          </ol>
        }
      />
    </Grid>
  );
}

export default React.memo(<Name>);
```

### 5. `src/main/webapp/scripts/externalTinymcePlugins/<name>/plugin.min.js`

```js
tinymce.PluginManager.add('<name>', function (editor, url) {

  editor.addCommand("cmd<Name>", function () {
    editor.windowManager.openUrl({
      title: "<Name>",
      url: url + "/dialog.html",
      width: 1000,
      height: 600,
      buttons: [
        { type: "cancel", id: "close", name: "close", text: "Close" }
      ]
    });
  });

  editor.ui.registry.addButton('<name>', {
    icon: '<name>',
    tooltip: '<Name>',
    onAction: function () {
      editor.execCommand("cmd<Name>");
    }
  });
});
```

### 6. `src/main/webapp/scripts/externalTinymcePlugins/<name>/dialog.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <title><Name></title>
  <script src="/ui/dist/runtime.js"></script>
  <script src="/ui/dist/tinymce<Name>.js"></script>
</head>
<body>
  <div id="tinymce-<name>"></div>
</body>
</html>
```

### 7. `src/main/webapp/ui/src/tinyMCE/<name>/index.tsx`

```tsx
import React from "react";
import { createRoot } from "react-dom/client";
import <Name> from "./<Name>";

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-<name>");
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(<<Name> />);
  }
});
```

### 8. `src/main/webapp/ui/src/tinyMCE/<name>/<Name>.tsx`

```tsx
import React from "react";

/**
 * Empty <Name> dialog body. The dialog title chrome (provided by TinyMCE
 * via `windowManager.openUrl({ title: "<Name>", … })`) supplies the heading.
 */
function <Name>(): React.ReactNode {
  return <></>;
}

export default <Name>;
```

### 9. `src/main/webapp/ui/src/tinyMCE/<name>/__tests__/<Name>.test.tsx`

```tsx
import { describe, it, expect } from "vitest";
import React from "react";
import { render } from "@/__tests__/customQueries";
import <Name> from "../<Name>";

describe("<Name> dialog body", () => {
  it("renders without crashing", () => {
    const { container } = render(<<Name> />);
    expect(container).toBeTruthy();
  });
});
```

---

## Files to modify

> **General rule**: insert each new line/block in the correct alphabetical
> position relative to the existing entries, and bail out if a matching entry
> already exists.

### `IntegrationsHandler.java`

Insert in the constants block, alphabetically:

```java
String <NAME>_APP_NAME = "<NAME>";
```

### `SystemPropertyName.java`

`SystemPropertyManagerImpl` caches `findByName` results with a SpEL key
`"#name.propertyName"`, where `name` is a `SystemPropertyName` enum value. If
`SystemPropertyName.valueOfPropertyName("<name>.available")` returns `null`,
the SpEL evaluation throws `EL1007E: Property or field 'propertyName' cannot
be found on null` and `/integration/allIntegrations` returns a 500-ish error
that crashes the React Apps page on first load.

Add an entry in alphabetical position:

```java
<NAME>_AVAILABLE("<name>.available"),
```

### `IntegrationController.java`

Add a `rc.put(...)` entry inside `getAll(User)`, in alphabetical order alongside the existing entries:

```java
rc.put(<NAME>_APP_NAME, integrationsHandler.getIntegration(user, <NAME>_APP_NAME));
```

Also add the corresponding static import at the top of the file:

```java
import static com.researchspace.service.IntegrationsHandler.<NAME>_APP_NAME;
```

### `IntegrationsHandlerImpl.java`

Without this change, `IntegrationsHandlerImpl.isValidIntegration()` returns
`false` for the new name, causing `checkValidIntegration()` to throw, which
makes `/integration/allIntegrations` return an error and crashes the React Apps
page on first load.

Two edits are required:

**1.** Add a new private method (e.g. after `isSingleOptionSetAppConfigIntegration`):

```java
private boolean isValidEmptyIntegration(String integrationName) {
  switch (integrationName) {
    case <NAME>_APP_NAME:
      return true;
  }
  return false;
}
```

**2.** Find the last line of `isAppConfigIntegration()`:

```java
return isSingleOptionSetAppConfigIntegration(integrationName);
```

Replace it with:

```java
return isValidEmptyIntegration(integrationName)
    || isSingleOptionSetAppConfigIntegration(integrationName);
```

> **Why a separate method?** `isSingleOptionSetAppConfigIntegration` is for
> integrations with exactly one stored credential (e.g. Egnyte domain, Zenodo
> token). An empty integration has no per-user options, so it must not appear
> there. `isValidEmptyIntegration` is the correct home.

### `system.properties`

Add a line in the `system.property.description.*.available` group:

```properties
system.property.description.<name>.available=Makes <Name> integration available.
```

### `sqlUpdates/liquibase-master.xml`

Append (or insert at the appropriate position) inside `<databaseChangeLog>`:

```xml
<include file="changeLog-<ticket>.xml" relativeToChangelogFile="true"/>
```

### `eln/apps/CardListing.tsx`

Three insertions (alphabetical within each group):

1. Import:
   ```ts
   import <Name> from "./integrations/<Name>";
   ```

2. `useCallback`:
   ```ts
   const <name>Update = React.useCallback(
     (newState: IntegrationStates["<NAME>"]) => {
       void runInAction(async () => {
         integrationStates.<NAME> = await update("<NAME>", newState);
       });
     },
     [update, integrationStates.<NAME>],
   );
   ```

3. Conditional render:
   ```tsx
   {integrationStates.<NAME>.mode === mode && (
     <<Name>
       integrationState={integrationStates.<NAME>}
       update={<name>Update}
     />
   )}
   ```

### `eln/apps/useIntegrationsEndpoint.ts`

Five insertions:

1. In the `IntegrationStates` type:
   ```ts
   <NAME>: IntegrationState<emptyObject>;
   ```

   `emptyObject` is the codebase's local type alias for the empty-credentials
   case (already imported at the top of `useIntegrationsEndpoint.ts` from
   `../../util/types`). Match the existing convention rather than emitting
   `Record<string, never>`.

2. A decoder function near the others:
   ```ts
   function decode<Name>(data: FetchedState): IntegrationStates["<NAME>"] {
     return {
       mode: parseState(data),
       credentials: {},
     };
   }
   ```

3. In `decodeIntegrationStates`:
   ```ts
   <NAME>: decode<Name>(data.<NAME>),
   ```

4. In `encodeIntegrationState`:
   ```ts
   if (integration === "<NAME>") {
     return {
       name: "<NAME>",
       available: data.mode !== "UNAVAILABLE",
       enabled: data.mode === "ENABLED",
       options: {},
     };
   }
   ```

5. In **both** trailing `switch (integration)` statements (the `useIntegrationsEndpoint`
   hook has two of these — search for `case "GITHUB":`):
   ```ts
   case "<NAME>":
     return decode<Name>(responseData.data) as IntegrationStates[I];
   // …and in the second switch:
   case "<NAME>":
     return decode<Name>(response.data.data) as IntegrationStates[I];
   ```

### `eln/apps/__tests__/allIntegrationsAreDisabled.json`

Insert object alphabetically:

```json
"<NAME>": {
  "name": "<NAME>",
  "displayName": "<Name>",
  "available": false,
  "enabled": false,
  "oauthConnected": false,
  "options": {}
}
```

### `assets/DocLinks.ts`

Insert alphabetically inside `docLinks`:

```ts
<name>: mkDocLink("TODO_<NAME>_DOC_ID"),
```

### `WEB-INF/pages/system/settings_ajax.jsp`

Add inside the block of `<div id="*.available.description">` siblings:

```jsp
<div id="<name>.available.description"><spring:message code="system.property.description.<name>.available"/></div>
```

### `scripts/pages/system/settings_mod.js`

Add under an existing `_printCategory(...)` block (default to `'Other'` and let
the developer move it):

```js
_printSettings(['<name>.available']);
```

### `scripts/pages/workspace/editor/tinymce5_configuration.js`

Two insertions inside `initTinyMCE`:

1. Next to other `*Enabled` const declarations:
   ```js
   const <name>Enabled = integrations.<NAME>.enabled && integrations.<NAME>.available;
   ```

2. Next to other `if (*Enabled) { ... external_plugins ... }` blocks. The
   block has TWO lines: registering the plugin script, then registering the
   toolbar button name (without this second line, the plugin loads but no
   button appears in the editor toolbar):
   ```js
   if (<name>Enabled) {
     localTinymcesetup.external_plugins["<name>"] = "/scripts/externalTinymcePlugins/<name>/plugin.min.js";
     addToToolbarIfNotPresent(localTinymcesetup, " | <name>");
   }
   ```

> **⚠️ CRITICAL — the ` | ` separator is mandatory.** The argument to
> `addToToolbarIfNotPresent` MUST be `" | <name>"` (space, pipe, space, then
> the name). Passing only `"<name>"` concatenates it directly onto the last
> toolbar token (e.g. `"fullscreennew_test"`), which TinyMCE cannot parse, and
> **no toolbar button will appear** even though the plugin loads successfully.

> **Do not** add `enabledFileRepositories += " <name>"`,
> `fileRepositoriesMenu += " opt<Name>"`, or `addToMenuIfNotPresent` lines.
> Those wire the integration into the "Insert from…" dropdown menu, which the
> empty integration omits in favour of a top-level toolbar button.

### `scripts/tinymce/tinymce5109/icons/custom_icons/icons.js`

Add a new entry inside `tinymce.IconManager.add('custom_icons', { … })`:

```js
'<name>': '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20"><circle cx="12" cy="12" r="10" fill="none" stroke="#151515" stroke-width="2"/><text x="12" y="16" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="11" font-weight="700" fill="#151515"><INITIALS></text></svg>',
```

### `build-resources/resources_to_MD5_rename.txt`

Two additions in their respective alphabetical groups:

```
scripts/externalTinymcePlugins/<name>/plugin.min.js
ui/dist/tinymce<Name>.js
```

> **Do not** add `ui/dist/externalWorkFlows.js`. That bundle is Galaxy-specific.

### `src/main/webapp/ui/webpack.config.mjs`

In the `entry: { … }` block, alongside the other `tinymce*` entries:

```js
tinymce<Name>: "./src/tinyMCE/<name>/index.tsx",
```

> **Do not** add an `externalWorkFlows` entry.

---

## Manual follow-up checklist (print after running)

1. Replace placeholder strings in `<Name>.tsx`: `explanatoryText`,
   `usageText`, `helpLinkText`, `website`.
2. Replace `TODO_<NAME>_DOC_ID` in `DocLinks.ts` with the real helpdocs short ID.
3. Hand-tune `LOGO_COLOR` in `branding/<name>/index.ts` to match the real brand.
4. Replace the placeholder `branding/<name>/logo.svg` with a real
   100×100 logo per the conventions in
   `src/main/webapp/ui/src/eln/apps/README.md`.
5. Replace the TinyMCE icon SVG in `icons.js` with a properly-designed icon.
6. Move the `_printSettings(['<name>.available']);` line in `settings_mod.js`
   under the correct sysadmin-settings category (default placement is `'Other'`).
7. Run `cd src/main/webapp/ui && npm run build` to produce the
   `ui/dist/tinymce<Name>.js` bundle. Without this, the TinyMCE dialog opens
   blank (the React component fails to load). This was already done in step 5
   of the skill workflow; include it here as a reminder for manual runs.
8. Run a clean rebuild and confirm the integration appears in the Apps page,
   the sysadmin settings page, and the TinyMCE toolbar of a structured
   document.

## Anti-patterns (things never to do for an empty integration)

- Adding `<NAME>` to `IntegrationsHandlerImpl.postProcessInfo`,
  `setNewIntegrationInfo`, or `isSingleOptionSetAppConfigIntegration` switches.
  These are only needed for integrations with per-user credentials or
  app-level config options (OAuth tokens, API keys, server URLs). An empty
  integration has no per-user options, so it must not appear in any of these.
  Use the dedicated `isValidEmptyIntegration()` method instead (see recipe
  above).
- Adding `<NAME>_APIKEY` (or any other) `PropertyDescriptor` and
  `AppConfigElementDescriptor` to the changeset.
- Adding deployment URL properties to `PropertyHolder.java` or
  `defaultDeployment.properties`.
- Editing `notebookEditor.jsp`, `structuredDocument.jsp`, `textField.jsp`, or
  `journal.js`.
- Adding an `externalWorkFlows`-shaped webpack entry or `ui/dist` bundle.
- Adding `addMenuItem('opt<Name>', …)` or `window.insertActions.set(…)` blocks
  in `plugin.min.js`.
- Adding `enabledFileRepositories += " <name>"` or
  `fileRepositoriesMenu += " opt<Name>"` in `tinymce5_configuration.js`.
- Setting `localTinymcesetup.<name>_url` / `<name>_web_url` in
  `tinymce5_configuration.js`.





