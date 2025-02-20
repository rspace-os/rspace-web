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
- See .../../../src/main/webapp/ui/src/eln/apps/AddingANewIntegration.md for the front-end changes needed
  - Pick a colour based on the branding of the organisation that provides the service
  - Even if no authentication is required and plugin is not supported by ResearchSpace,
    adding it to the bottom section will make it visible to researchers
- Something, something, something about required backend changes

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
  onAction: () => {
    // do something
  }
});
```
In each case, the `onAction` callback is where the actual logic for the plugin
should go. In most cases, this will involve displaying a dialog, making some API
calls, and having the user make a selection before generating HTML to display,
but before we get to that there are a few more initialisation steps to take care
of.
  1. In `src/main/webapp/ui/webpack.config.js` we need to add a new entry point for our new script.
     We load the plugin through a separate entry point so that it is only downloaded
     when the plugin is enabled.
    ```
      tinymceNewPlugin: "./src/tinyMCE/newPlugin/index.js",
    ```
  2. In `src/main/webapp/scripts/pages/workspace/editor/tinymce5_configuration.js`,
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
  3. A simple SVG needs to be added to `src/main/webapp/scripts/tinymce/tinymce516/icons/custom_icons/icons.js`

## Step 3: Testing
- How will we know if the plugin stops working e.g. due to API changes/services being down?
