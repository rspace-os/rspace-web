# Troubleshooting

Here are some notes for investigating bugs/ problems relating to RSpace updates.

## Problems with liquibase updates

### Scenario

Version X was released some time ago and developers are working on the version X+1.
A customer reports problems with the liquibase updates when updating from the version
X-1 to X.

### Reproducing on localhost:

1. Checkout version X-1 from Git
2. Drop your `rspace` database, and recreate.
3. cd into src/main/resources/sqlUpdates/liquibaseConfig and run the script
   `mysql -urspacedbuser -p < rs-dbbaseline-utf8.sql` which will populate the DB
   with the baseline tables.
4. Launch with `mvn clean jetty:run -Denvironment=keepdbintact -Dlog4j2.configurationFile=log4j2-dev.xml -DRS.logLevel=INFO -Dspring.profiles.active=run -Dliquibase.context=run`.
   Note `-Dliquibase.context=run`, which will execute DB scripts from liquibase.
5. Now reproduce the state of the DB to reproduce the bug.
6. Now stop, checkout version X , relaunch as in step 4 - try to reproduce the error.

### After identifying a fix

Repeat relaunch version X - it should now run DB updates OK.

## Explanations for some errors that can be confusing

### Too much data in a single document field:
***The data limit for a document field is 200kb for local development
using Jetty and 2MB for a deployed RSpace instance using Tomcat.***

You can configure the ***local*** form size using the system property:
```
-Dorg.eclipse.jetty.server.Request.maxFormContentSize=2000000 (which gives 2MB, like Tomcat)
```

If a document has too much data in a single field of the document it will fail on autosave (autosaveField
will be visible as an ajax call) and as a result there will be a redirect to /workspace which will then try to serialise
several hibernate entities (StructuredDocument,User,SearchResults and many others).
The attempted serialisation will fail with a JsonMappingException and an error dialog GUI will be
shown in the UI that shows a large json structure. Sometimes, this GUI will be blank
and will only contain the message 'Errors null'. RSpace will log the following
```
[WARNING] /workspace/editor/structuredDocument/ajax/autosaveField org.eclipse.jetty.http.BadMessageException: 400: Unable to parse form content
```
and also:
```
[WARNING] /workspace com.fasterxml.jackson.databind.JsonMappingException: Infinite recursion (StackOverflowError) (through reference chain:....etc
```

Also note, if the limit is
exceeded on a deployed RSpace, the error is different - the UI fails to send
*ANY* data when autosave is attempted and the user sees an error message that required
parameter 'dataView' is missing.

### Inventory TinyMCE editor is blank only when running the Vite dev server with `-DreactDevMode=true`

***Symptom:*** In the Inventory UI, rich-text (TinyMCE) editors do not appear in EDIT mode.
For example, opening a template and clicking EDIT shows no editor for the Description field,
and formatted-text custom fields / Text extra fields show no editor either. Inspecting the DOM
reveals only a hidden backing element such as
`<textarea id="tiny-react_..." style="visibility: hidden;"></textarea>` with no `.tox` editor
next to it. A collateral dev-only console warning also appears in `TagsCombobox`:
`MUI: Unable to find the input element ... useAutocomplete expects an input element`.

***When it happens:*** Only when BOTH of these are true at the same time:

* the Vite dev server is running (`npm run serve`), AND
* RSpace is launched with `-DreactDevMode=true`.

Either one alone is fine. Running in production mode (a launch config WITHOUT
`-DreactDevMode=true`, and not running the Vite server) works correctly.

***Why:*** `-DreactDevMode=true` loads the React app from the Vite dev server, which is the
React **development** build with `StrictMode` active. React 18 StrictMode intentionally
double-invokes the lifecycle in development (mount, then unmount, then remount on first mount).
The `@tinymce/tinymce-react` wrapper does not survive that synthetic unmount/remount: the editor
is created on the first mount, torn down on the StrictMode unmount, and not re-initialised on the
remount, leaving the hidden textarea. The MUI Autocomplete warning is a related, harmless symptom
of the same double-mount. The production React build treats StrictMode as a no-op, so neither
symptom occurs there.

***This is not an application/code bug.*** The crashing components
(`Inventory/Template/Fields/DefaultValueField.tsx`, `components/Inputs/TextField.tsx`,
`components/Inputs/StyledTinyMceEditor.tsx`, `Tags/TagsCombobox.tsx`) are unchanged and the
shipped/production build works. To develop rich-text areas of Inventory locally, run without
`-DreactDevMode=true` (use the prebuilt bundle). A proper fix (hardening the TinyMCE wrapper
against StrictMode, or relaxing StrictMode on the TinyMCE-hosting entries) is tracked in
RSDEV-1159.
