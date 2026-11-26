/*
 * Dark mode for pages that React does not render: the decorated JSP pages
 * (through common/legacyDarkMode.jsp), pages without a decorator, and pages
 * opened in frames such as TinyMCE plugin dialogs. Load it with a
 * parser-blocking <script> at the end of <head>, after the page's stylesheets
 * and after styles/dark/generated/sheets.js.
 *
 * - Applies the stored mode (`mui-mode`, shared with ui/src/colorScheme.ts)
 *   before first paint, then follows changes made in other documents (storage
 *   events), by this page's React roots, and to the system setting.
 * - In dark mode, loads the generated dark rules for each stylesheet the page
 *   links, including stylesheets linked later, then legacy-dark.css. Light
 *   mode loads none of them.
 * - Marks <html> with data-dark-mode, so the page's React roots follow too.
 *
 * The dark rules are @scope rules. A browser without @scope keeps the page
 * light and marks it data-dark-mode-unsupported instead.
 */
(function () {
  var root = document.documentElement;
  if (typeof window.CSSScopeRule === "undefined") {
    root.classList.add("light");
    root.setAttribute("data-dark-mode-unsupported", "");
    return;
  }
  root.setAttribute("data-dark-mode", "");

  var src = document.currentScript ? document.currentScript.src : location.origin + "/scripts/darkMode.js";
  var query = src.indexOf("?") === -1 ? "" : src.slice(src.indexOf("?"));
  // The webapp root, including any context path.
  var base = new URL("..", src).pathname;
  var sheets = window.RS_DARK_SHEETS || [];
  var loaded = {};
  // The rules for <style> blocks and then the hand-written rules come after
  // those for every stylesheet, as they did before the page linked more.
  var after = null;

  function storedMode() {
    try {
      return window.localStorage.getItem("mui-mode");
    } catch (e) {
      return null; // storage unavailable; follow the system preference
    }
  }

  function systemDark() {
    return window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  /* Adds a dark stylesheet: through the parser while the page loads, so it
   * blocks rendering like the page's own links, otherwise before the rules
   * for <style> blocks and the hand-written ones, so those still come last. */
  function addLink(path) {
    var href = base + "styles/dark/" + path + query;
    if (document.readyState === "loading" && document.currentScript) {
      document.write('<link rel="stylesheet" media="screen" data-dark-mode-sheet href="' + href + '">');
      return;
    }
    var link = document.createElement("link");
    link.rel = "stylesheet";
    link.media = "screen";
    link.href = href;
    link.setAttribute("data-dark-mode-sheet", "");
    if (after && after.parentNode) after.parentNode.insertBefore(link, after);
    else document.head.appendChild(link);
  }

  function loadDarkSheets() {
    var links = document.querySelectorAll('link[rel~="stylesheet"][href]:not([data-dark-mode-sheet])');
    for (var i = 0; i < links.length; i++) {
      var path = new URL(links[i].href, location.href).pathname;
      if (path.indexOf(base) !== 0) continue;
      path = path.slice(base.length);
      if (loaded[path] || sheets.indexOf(path) === -1) continue;
      loaded[path] = true;
      addLink("generated/" + path);
    }
    if (!after) {
      addLink("generated/templates.css");
      addLink("legacy-dark.css");
      after = document.querySelector('link[data-dark-mode-sheet][href*="/styles/dark/generated/templates.css"]');
    }
  }

  function apply(mode) {
    var dark = mode === "dark" || (mode !== "light" && systemDark());
    root.classList.toggle("dark", dark);
    root.classList.toggle("light", !dark);
    if (dark) loadDarkSheets();
  }

  apply(storedMode());

  window.addEventListener("storage", function (event) {
    if (event.key === "mui-mode") apply(event.newValue);
  });
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", function () {
    var mode = storedMode();
    if (mode !== "light" && mode !== "dark") apply(mode);
  });
  // Stylesheets linked after this script runs, by the parser or by scripts.
  new MutationObserver(function (records) {
    if (!root.classList.contains("dark")) return;
    for (var i = 0; i < records.length; i++) {
      var added = records[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        if (added[j].nodeName === "LINK") return loadDarkSheets();
      }
    }
  }).observe(document, { childList: true, subtree: true });
})();
