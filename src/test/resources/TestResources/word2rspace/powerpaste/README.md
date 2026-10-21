# Image-heavy Word import fixtures

`PowerPasteTesting_RSpace.odt` and `PowerPasteTesting_RSpace.ott` are derived from
`PowerPasteTesting_RSpace.docx` in this directory using LibreOffice 26.2.5.2,
Ubuntu package `26.2.5.2-0ubuntu0.26.04.1`, on arm64.

To regenerate with LibreOffice in the conversion container, use a fresh temporary
profile and an output directory containing a copy of the source DOCX:

```sh
libreoffice -env:UserInstallation=file:///tmp/fixture-profile --headless \
  --convert-to odt --outdir /tmp/fixtures /tmp/fixtures/PowerPasteTesting_RSpace.docx
libreoffice -env:UserInstallation=file:///tmp/fixture-profile --headless \
  --convert-to ott:writer8_template --outdir /tmp/fixtures /tmp/fixtures/PowerPasteTesting_RSpace.docx
```

The expected content is recorded in `PowerPasteTesting_RSpace.html` and its three
adjacent image files. Each format must retain the heading containing
“Testing PowerPaste by Cutting and Pasting”, formatted text, and three images in
source order. Generated HTML formatting may differ by LibreOffice version.
The conversion browser test checks all three import formats and exported DOCX.

`../../ODP.pptx` was generated from `../../ODP.odp` using the same LibreOffice
version and `--convert-to pptx`. It exercises PowerPoint preview conversion.
