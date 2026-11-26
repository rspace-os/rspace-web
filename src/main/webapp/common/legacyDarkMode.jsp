<%--
  Puts a legacy page into dark mode when the user's mode is dark. Both page
  decorators include it after the page's own <head> content, so every
  decorated JSP page can render dark. The overrides live in styles/dark/.

  The script applies the stored mode before first paint, so the page does not
  flash light while the React bundles load, and marks <html> with
  data-dark-mode so ui/src/colorScheme.ts lets the page's React roots follow.
  The two must agree on the `mui-mode` key and its values.

  The overrides are written as @scope rules. A browser without @scope would
  ignore them and show light panels under dark page text, so such a browser
  keeps the page, and its React roots, light.

  The stylesheets apply to the screen only, so printed pages stay light. The
  print style overrides the color-scheme that colorScheme.ts sets inline.
--%>
<style media="print">
  html {
    color-scheme: light !important;
  }
</style>
<script>
  (function () {
    var mode = null;
    try {
      mode = window.localStorage.getItem("mui-mode");
    } catch (e) {
      // storage unavailable; follow the system preference
    }
    if (typeof window.CSSScopeRule === "undefined") {
      document.documentElement.classList.add("light");
      return;
    }
    var dark =
      mode === "dark" ||
      (mode !== "light" && window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
    var root = document.documentElement;
    root.classList.add(dark ? "dark" : "light");
    root.setAttribute("data-dark-mode", "");

    // legacy-generated.css scopes each stylesheet's overrides to pages that
    // link it, by matching this list. :has(link) would do the same but costs
    // far more on every style recalculation.
    function listStylesheets() {
      var paths = [];
      var links = document.querySelectorAll('link[rel~="stylesheet"][href]');
      for (var i = 0; i < links.length; i++) {
        paths.push(links[i].getAttribute("href").split("?")[0] + " ");
      }
      root.setAttribute("data-stylesheets", paths.join(""));
    }
    listStylesheets();
    // Tags can link stylesheets in the body, after this script runs; their
    // overrides apply from DOMContentLoaded. A stylesheet a script adds later
    // is not listed, so its rules get no dark overrides.
    document.addEventListener("DOMContentLoaded", listStylesheets);
  })();
</script>
<link rel="stylesheet" media="screen" href="<rst:assetUrl value='/styles/dark/legacy-generated.css'/>" />
<link rel="stylesheet" media="screen" href="<rst:assetUrl value='/styles/dark/legacy-dark.css'/>" />
