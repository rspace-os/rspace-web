<%--
  Puts a page that React does not render into dark mode when the user's mode
  is dark. Both page decorators include it after the page's own <head>
  content, and so do the pages that have no decorator. Include it at the end
  of <head>, after the page's stylesheets.

  scripts/darkMode.js applies the stored mode before first paint, loads the
  dark overrides from styles/dark/ for the stylesheets the page links, and
  marks <html> with data-dark-mode so ui/src/colorScheme.ts lets the page's
  React roots follow. The two must agree on the `mui-mode` key and its values.

  The overrides apply to the screen only, so printed pages stay light. The
  print style overrides the color-scheme that colorScheme.ts sets inline.
--%>
<style media="print">
  html {
    color-scheme: light !important;
  }
</style>
<script src="<rst:assetUrl value='/styles/dark/generated/sheets.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/darkMode.js'/>"></script>
