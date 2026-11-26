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
--%>
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
    document.documentElement.classList.add(dark ? "dark" : "light");
    document.documentElement.setAttribute("data-dark-mode", "");
  })();
</script>
<link rel="stylesheet" href="<rst:assetUrl value='/styles/dark/legacy-generated.css'/>" />
<link rel="stylesheet" href="<rst:assetUrl value='/styles/dark/legacy-dark.css'/>" />
