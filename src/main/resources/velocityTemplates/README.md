Templates used only in emails are authored once as HTML body fragments. Do not add
`html`, `head`, `meta`, or `body` elements: `EmailContentGenerator` adds the common
`html` and `body` elements and sets the `html` element's `lang` attribute from the active
rendering locale. The MIME part declares UTF-8.

The multipart email's plain-text alternative is derived automatically from the rendered
HTML by `EmailHtmlToPlainText`. Hyperlinks retain their target as "text (url)" and
block-level elements become paragraph breaks, so there is no separate `-plaintext.vm`
template to keep synchronized.
