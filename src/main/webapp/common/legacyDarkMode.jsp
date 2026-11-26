<%--
  Puts a page that React does not render into dark mode when the user's mode
  is dark. Both page decorators include it after the page's own <head>
  content, and so do the pages that have no decorator. Include it at the end
  of <head>, after the page's stylesheets, which are linked with
  <axt:stylesheet> so their dark overrides come with them.

  Like <axt:stylesheet>, it links the dark rules for <style> blocks and the
  hand-written legacy-dark.css unless the `rs-color-mode` cookie says light.
  scripts/darkMode.js then applies the stored mode before first paint, keeps
  the cookie in step, loads anything the server could not know about, and
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
<c:set var="colorMode" value="${cookie['rs-color-mode'].value}" />
<c:if test="${colorMode ne 'light'}">
  <c:set var="darkMedia" value="${colorMode eq 'dark' ? 'screen' : 'screen and (prefers-color-scheme: dark)'}" />
  <link rel="stylesheet" data-dark-mode-sheet data-dark-mode-last media="${darkMedia}"
    href="<rst:assetUrl value='/styles/dark/generated/templates.css'/>" />
  <link rel="stylesheet" data-dark-mode-sheet data-dark-mode-last media="${darkMedia}"
    href="<rst:assetUrl value='/styles/dark/legacy-dark.css'/>" />
</c:if>
<script src="<rst:assetUrl value='/styles/dark/generated/sheets.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/darkMode.js'/>"></script>
