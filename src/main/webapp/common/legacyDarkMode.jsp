<%--
  Opts a legacy page into dark mode. Include it in the page's <head>, link
  the page's own dark overrides after it, and add the page's path to
  DARK_MODE_PATHS in ui/src/colorScheme.ts.

  The script applies the stored mode before first paint, so the page does not
  flash light while the React bundles load. colorScheme.ts takes over once
  they do; the two must agree on the `mui-mode` key and its values.
--%>
<script>
  (function () {
    var mode = null;
    try {
      mode = window.localStorage.getItem("mui-mode");
    } catch (e) {
      // storage unavailable; follow the system preference
    }
    var dark =
      mode === "dark" ||
      (mode !== "light" && window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.classList.add(dark ? "dark" : "light");
  })();
</script>
<link rel="stylesheet" href="<rst:assetUrl value='/styles/dark/legacy-dark.css'/>" />
