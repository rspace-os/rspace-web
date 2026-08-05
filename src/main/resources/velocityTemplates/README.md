Templates only used in emails are authored once, as HTML body fragments. Do not add
`html`, `head`, `meta`, or `body` elements: `EmailContentGenerator` adds the common
`html` and `body` elements, with the rendered message's locale on the `html` element's
`lang` attribute, and the MIME part declares UTF-8. The plain-text part of the MultiPart
mail message (needed to satisfy spam filters) is derived automatically from the rendered
HTML by `EmailHtmlToPlainText`. Hyperlinks keep their target visible as "text (url)" and
block-level elements become paragraph breaks, so there is no separate `-plaintext.vm`
template to keep in sync.

Prefer catalogue values that are continuous sentences with `{0}`-style slots for dynamic
values (names, dates, URLs) over fragments welded together in the template; see
`server.email.*.json` under the frontend locale directory.

There is a unit test 'VelocityTemplateRenderingTest' enabling any template rendering to be quickly tested by eye
in a simple JUnit.
