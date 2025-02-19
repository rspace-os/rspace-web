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

## Step 3: Testing
- How will we know if the plugin stops working e.g. due to API changes/services being down?
