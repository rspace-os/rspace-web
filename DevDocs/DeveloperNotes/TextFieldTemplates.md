# Inserting HTML into text fields programmatically.

HTML that is inserted programmatically needs to be a standard syntax,
because there are some data relationships that are only expressed in
this way - e.g., links between records.

So, all HTML is stored as Velocity templates in
`src/main/resources/velocityTemplates/textFieldElements`.

These templates can be used by regular Java code (e.g., `RichTextUpdater`)
which is also used to manipulate links and content when exporting to an
archive.

To get hold of these contents on the browser, use TemplateController
which will return these Velocity templates converted to Mustache
templates.

## Editing Velocity templates

All parameters need to be strings - this is so that the
TemplateController can replace the Velocity placeholders with Mustache
placeholders for templates that can be added via operations in tinyMCE.

Remember to update documentation on required parameters etc.
