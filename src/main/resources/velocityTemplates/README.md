Templates only used in emails should have an HTML and a plain text version. This is so we can send a MultiPart mail message that satisfies spam filters.

Plain text version should be as similar as possible in content to the HTML version to avoid the email being detected as spam.
 
Plaintext variant template file names should be suffixed  with '-plaintext.vm'.


E.g. if `velocityEmailTemplate.vm` is an HTML email template, there should be a file `velocityEmailTemplate-plaintext.vm` with content as similar as possible, but no markup.

This convention is useful as:

* Code can be simplified to programmatically search for text alternative
* We can easily validate that plain-text templates lack HTML by searching over *-plaintext.vm files for '<' characters for example.

There is a unit test 'VelocityTemplateRenderingTest' enabling any template rendering to be quickly tested by eye
in a simple JUnit.