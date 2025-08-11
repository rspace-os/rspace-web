# Creating a new editor plugin

## Background

An Electronic Lab Notebook (ELN) is a digital version of the traditional paper
lab notebook used by scientists to document their experiments, observations, and
results. One of the key features of an ELN is the document editor, which allows
researchers to create, edit, and organize their lab notes and experimental data in a
digital format. The document editor can contain multiple different fields of
varying types, such as text, number, date, and more to provide structure to the
recorded data but by far the most often used field type, for its flexibility, is
the rich text field.

The rich text fields of the document editor allow the researcher to format
their text, add hyperlinks, and embed multimedia content; made possible because
the rich text fields are just HTML documents. The plugins, of which there are
several that can be enabled based on each researcher's workflows, provide a
mechanism for inserting HTML into the document.

TinyMCE is a popular open-source WYSIWYG (What You See Is What You Get) HTML
editor, allowing the researcher to create and edit content in a way that
visually resembles the final output, without them needing to write HTML code
directly. We make extensive use of TinyMCE across the product to provide a rich
text editing capabilities, but particularly for the rich text fields of ELN
documents.

The typical form of a plugin is a JavaScript file that is loaded into the
document editor when it is opened. The plugin can then register itself with
the editor and provides hooks for the insert menu, toolbar icon, and the slash
menu. When invoked, the plugin usually opens a dialog, displays some data for
the user to search, filter, and select, and finally inserts the generated HTML
into the document.


## Step 1: Apps page & Authentication
Each integration that RSpace has with third-party services is enabled and
configured on the apps page. Each integration is represented by a card on the
page, which can be clicked to open a dialog with more information about the
integration, a mechanism to enable it, and where applicable further
configuration options such as authentication. The changes required can be broken
down into two parts: the backend and the frontend.

### Step 1.1: Backend changes
- Deployment properties, where applicable
- Sysadmin toggle
- Enabled state (including how it is exposed for using in section 2.1 below)
- Authentication

### Step 1.2: Frontend changes
The frontend changes required to add a new integration to the apps page are
fully document in [/src/main/webapp/ui/src/eln/apps/AddingANewIntegration.md](/src/main/webapp/ui/src/eln/apps/AddingANewIntegration.md),
but to summarise briefly there is
  1. The API calls to get the state of the integration (whether is available, disabled, or enabled)
  2. The UI for displaying the card and dialog
  3. A unit test to verify 1. and 2. are working correctly

The only part perhaps worth elaborating on further, given it is expanded on
below, is the accented colour of the card and dialog. Each integration on the
apps page utilises the branding colours of the organisation that develops and
maintains the APIs that the integration relies on. This makes it easier to
identify the integration by utilising brand recognition as well as providing an
aesthetic appeal and a visual connection to the dialogs and UI elements that are
made available by enabling the integration. The apps page requires just a single
colour defintion for each integration, expressed as a (hue, saturation,
lightness) triple. If the logo of the organisation has a solid background colour
then simply use that colour, if it does not then pick the colour that is most
associated with the branding of the organisation.


## Step 2: Define a new TinyMCE plugin

### Step 2.1: Registering a plugin
Lets define a script in `src/main/webapp/ui/tinyMCE/plugins/newPlugin/index.js`,
inside of which we will call `tinymce.PluginManager.add("newPlugin", NewPlugin);`,
where `NewPlugin` is a class. This class doesn't need to have any particular
methods, it just needs to have a constructor that takes as its argument a
reference to the editor. The class will be instantiated and the constructor
called when the user goes to edit the rich text field and a new instance of
TinyMCE is created, so whilst `tinymce.PluginManager.add("newPlugin", NewPlugin);`
is to be called on page-load the class will be instantiated for each text field
as it transitions from the viewing to editing modes.

Inside this constructor, we want to set up handles for the insert menu, toolbar
icon, and the slash menu. To do this we call the following code
```
editor.ui.registry.addMenuItem('optNewPlugin', {
  text: 'New Plugin',
  icon: 'newPlugin',
  onAction: () => {
    // do something
  }
});
editor.ui.registry.addButton('newPlugin', {
  tooltip: 'New Plugin',
  icon: 'newPlugin',
  onAction: () => {
    // do something
  }
});
if (!window.insertActions) window.insertActions = new Map();
window.insertActions.set('optNewPlugin', {
  text: 'New Plugin',
  icon: 'newPlugin',
  action: () => {
    // do something
  }
});
```
In each case, the `onAction` or `action` callback is where the actual logic for
the plugin should go. In most cases, this will involve displaying a dialog,
making some API calls, and having the user make a selection before generating
HTML to display, but before we get to that there are a few more initialisation
steps to take care of.
  1. In [/src/main/webapp/ui/webpack.config.js](/src/main/webapp/ui/webpack.config.js) we need to add a new entry point for our new script.
     We load the plugin through a separate entry point so that it is only downloaded
     when the plugin is enabled.
    ```
      tinymceNewPlugin: "./src/tinyMCE/newPlugin/index.js",
    ```
  2. In [/src/main/webapp/scripts/pages/workspace/editor/tinymce5_configuration.js](/src/main/webapp/scripts/pages/workspace/editor/tinymce5_configuration.js),
     we need to add the following code:
     * Check whether the plugin is enabled with
        ```
        const newPluginEnabled = integrations.NEW_PLUGIN.enabled && integrations.NEW_PLUGIN.available;
        ```
     * Register the new plugin's source file and to tell TinyMCE to show the
       toolbar button and menu item when they are registered.
        ```
        if (newPluginEnabled) {
          localTinymcesetup.external_plugins["newPlugin"] = "/ui/dist/tinymceNewPlugin.js";
          addToToolbarIfNotPresent(localTinymcesetup, " | newPlugin");
          addToMenuIfNotPresent(localTinymcesetup, " | optNewPlugin");
        }
        ```
  3. A simple SVG needs to be added to [/src/main/webapp/scripts/tinymce/tinymce516/icons/custom_icons/icons.js](/src/main/webapp/scripts/tinymce/tinymce516/icons/custom_icons/icons.js).

### Step 2.2: Rendering a react component
The three `onAction` handlers should render a react component. Whilst the
document editor is not currently implemented in react, we intend to migrate to a
react-based tech stack in the future and all new development should be forwards
compatible to minimise the disruption of the migration. Furthermore, each of our
recently developed integrations utilise the branding colours of the
organisations that develop and maintain the APIs that provide the source of the
data to help users to visually recognise the point at which RSpace is
interoperating with other systems. This is implemented using an accented theme
that is applied to all of the UI elements, most notably the header where we
consistently provide a link to the documentation for the integration.

In the constructor of our new plugin, we want to define a generator function
that with each call re-renders the react component, allowing us to change the
`open` state.
```
function* renderNewPlugin(domContainer) {
  const root = createRoot(domContainer);
  while (true) {
    const newProps = yield;
    root.render(
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={createAccentedTheme(COLOR)}>
          <NewPluginDialog
            editor={editor}
            open={false}
            onClose={() => {}}
            {...newProps}
          />
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}
```

We then want to add this dialog to the DOM, but initially hidden. The
conditional logic is to make sure the dialog isn't added repeatedly every time
the plugin is instantiated.
```
if (!document.getElementById("tinymce-new-plugin")) {
  const div = document.createElement("div");
  div.id = "tinymce-new-plugin";
  document.body.appendChild(div);
}
const newPluginRenderer = renderNewPlugin(document.getElementById("tinymce-new-plugin"));
newPluginRenderer.next({ open: false });
```

Finally we return to the `onAction`s, where we can call
```
newPluginRenderer.next({
  open: true,
  onClose: () => {
    newPluginRenderer.next({ open: false });
  },
});
```

Note that the imports required at this point are
```
import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "../../accentedTheme";
```

Now we just have two things to define `COLOR` and `NewPluginDialog`.

### Step 2.3: Setting the colour of the accented theme
This is much the same colour as set on the apps page, but with a few variations
to be able to style all of the possible UI elements. There are five colours
which need defining `main`, `darker`, `contrastText`, `background`, and
`backgroundContrastText`. To find out more about how each is used, check out
[/src/main/webapp/ui/src/accentedTheme.js](/src/main/webapp/ui/src/accentedTheme.js). For a initial value, start by using
this defining as a template, setting the hue to the same value used on the apps
page, above.
```
const COLOR = {
  main: {
    hue: HUE,
    saturation: 46,
    lightness: 70,
  },
  darker: {
    hue: HUE,
    saturation: 93,
    lightness: 33,
  },
  contrastText: {
    hue: HUE,
    saturation: 35,
    lightness: 26,
  },
  background: {
    hue: HUE,
    saturation: 25,
    lightness: 71,
  },
  backgroundContrastText: {
    hue: HUE,
    saturation: 11,
    lightness: 24,
  },
};
```

Once we've implemented the dialog and any UI elements within you may need to
tweak the saturation and lightness values to ensure there is sufficient contrast
between all textual elements and the background they are shown on. To find out
more about accessibility and contrast ratios, see the section titled "Colour
Contrast" in [./Accessibility.md](./Accessibility.md). Whilst mentioning accessibility, it is worth
pointing out that by using an accented theme, we automatically get a high
contrast mode that stips out all of the superfluous colour, should the user wish
to enable it (this is mentioned in the section titled "Accented Theme").

### Step 2.4: Displaying a dialog
Now we just need to define `NewPluginDialog`, the UI that will actually be shown
when the user triggers the plugin. This component needs to accept the three
props that we're passing
  * `editor`, a reference the current TinyMCE editor.
  * `open`, a boolean indicating whether the dialog should be shown or not.
  * `onClose`, a function that will be called when the dialog should be closed.

We want `NewPluginDialog` to return something like this:
  ```
    return (
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
        <AppBar
          variant="dialog"
          currentPage="New Plugin"
          helpPage={{
            docLink: docLinks.newPlugin,
            title: "New Plugin help",
          }}
        />
        <DialogTitle>Insert from New Plugin</DialogTitle>
        <DialogContent>
          {/* TODO: Implement the dialog content here. */}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button
            color="callToAction"
            variant="contained"
            onClick={() => {
              editor.execCommand(
                "mceInsertContent",
                false,
                /* TODO: Generate HTML content to be inserted into the editor */
              );
              onClose();
            }}
          >
            Insert
          </Button>
        </DialogActions>
      </Dialog>
    );
  ```
  This gives us a few things
  1. A header, utilising the branding colours as previously defined
  2. A link to the help docs (once created the link will need to be added to [/src/main/webapp/ui/assets/DocLinks.js](/src/main/webapp/ui/assets/DocLinks.js)).
  3. A title, briefly describing the purpose of the dialog. This may seem
     superlfuous but in principle a single plugin, or any other integrations,
     could provide multiple actions with multiple dialogs.
  4. The DialogContent is where the behaviour of the plugins vary; it is here
     that some display a list of options, while others may require user input.
     If you would prefer, this could be the point where RSpace code calls out to
     a separate NPM package, displaying a UI with visual similarity to your main
     product, utilising a shared design system, component library, and be
     written in any compiled-to-JS language. The dialog content could even be an
     iframe if that would be easiest, although of course that comes with
     security implications that would need to be carefully considered.
  5. A cancel button for closing the dialog. No edits are made to the content of
     the editor and it is as if the user never opened the dialog.
  6. An insertion button that actually inserts some HTML content into the editor
     and closes the dialog.

The imports required for the dialog are as follows:
```
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import AppBar from "../../components/AppBar";
import docLinks from "../../assets/DocLinks";
```

## Step 3: Testing
- How will we know if the plugin stops working e.g. due to API changes/services being down?
