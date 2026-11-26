/*
 * Dark mode for pages that React does not render: the decorated JSP pages
 * (through common/legacyDarkMode.jsp), pages without a decorator, and pages
 * opened in frames such as TinyMCE plugin dialogs. Load it with a
 * parser-blocking <script> at the end of <head>, after the page's stylesheets
 * and after styles/dark/generated/sheets.js.
 *
 * - Applies the stored mode (`mui-mode`, shared with ui/src/colorScheme.ts)
 *   before first paint, and copies it to the `rs-color-mode` cookie, from
 *   which the server renders the dark stylesheets (<axt:stylesheet>).
 * - Adds any dark stylesheet the server could not know about: for stylesheets
 *   linked by scripts, on pages without a server (frames), or when the mode
 *   differs from the cookie. Each goes straight after its source stylesheet;
 *   the rules for <style> blocks and the hand-written legacy-dark.css stay
 *   after every other stylesheet.
 * - Switches to dark only once those stylesheets have loaded, so the page
 *   never shows light panels under dark text. React roots on the page wait
 *   for the same moment through window.rsDarkMode.whenReady.
 * - Follows changes made in other documents (storage events), by this page's
 *   React roots, and to the system setting, and marks <html> with
 *   data-dark-mode so the page's React roots follow too.
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
  var LAST = ["generated/templates.css", "legacy-dark.css"];

  function storedMode() {
    try {
      return window.localStorage.getItem("mui-mode");
    } catch (e) {
      return null; // storage unavailable; follow the system preference
    }
  }

  function isDark(mode) {
    return mode === "dark" || (mode !== "light" && window.matchMedia("(prefers-color-scheme: dark)").matches);
  }

  function saveCookie(mode) {
    var value = mode === "light" || mode === "dark" ? mode : "system";
    document.cookie = "rs-color-mode=" + value + "; path=/; max-age=31536000; SameSite=Lax";
  }

  function pathOf(link) {
    var path = new URL(link.href, location.href).pathname;
    return path.indexOf(base) === 0 ? path.slice(base.length) : null;
  }

  function darkLink(path, last) {
    var link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = base + "styles/dark/" + path + query;
    link.setAttribute("data-dark-mode-sheet", "");
    if (last) link.setAttribute("data-dark-mode-last", "");
    return link;
  }

  /*
   * Makes sure every dark stylesheet the page needs exists and applies to the
   * screen, and returns the ones still loading.
   */
  function ensureDarkSheets() {
    var have = {};
    var dark = document.querySelectorAll("link[data-dark-mode-sheet]");
    for (var i = 0; i < dark.length; i++) {
      dark[i].media = "screen";
      have[pathOf(dark[i])] = dark[i];
    }
    var sources = document.querySelectorAll('link[rel~="stylesheet"][href]:not([data-dark-mode-sheet])');
    for (var j = 0; j < sources.length; j++) {
      var path = pathOf(sources[j]);
      if (path === null || sheets.indexOf(path) === -1) continue;
      var darkPath = "styles/dark/generated/" + path;
      if (!have[darkPath]) {
        have[darkPath] = darkLink("generated/" + path, false);
        sources[j].after(have[darkPath]);
      }
    }
    var others = document.querySelectorAll('link[rel~="stylesheet"]:not([data-dark-mode-last])');
    var tail = others.length ? others[others.length - 1] : document.head.lastChild;
    for (var k = 0; k < LAST.length; k++) {
      var lastPath = "styles/dark/" + LAST[k];
      var link = have[lastPath] || darkLink(LAST[k], true);
      have[lastPath] = link;
      // Keep the <style>-block and hand-written rules after every stylesheet.
      if (!link.parentNode || link.compareDocumentPosition(tail) & Node.DOCUMENT_POSITION_FOLLOWING) {
        tail.after(link);
      }
      tail = link;
    }
    var pending = [];
    for (var key in have) {
      if (!have[key].sheet) pending.push(have[key]);
    }
    return pending;
  }

  /* Calls back once the links have loaded (or failed), or after 3 seconds. */
  function whenLoaded(links, callback) {
    var left = links.length;
    var done = false;
    function finish() {
      if (done) return;
      done = true;
      callback();
    }
    if (!left) return finish();
    for (var i = 0; i < links.length; i++) {
      links[i].addEventListener("load", function () {
        if (--left === 0) finish();
      });
      links[i].addEventListener("error", function () {
        if (--left === 0) finish();
      });
    }
    setTimeout(finish, 3000);
  }

  function setDark(dark) {
    root.classList.toggle("dark", dark);
    root.classList.toggle("light", !dark);
  }

  /* Applies a mode. In dark mode, the class changes once the styles are ready. */
  function apply(mode, callback) {
    if (!isDark(mode)) {
      setDark(false);
      if (callback) callback();
      return;
    }
    whenLoaded(ensureDarkSheets(), function () {
      setDark(true);
      if (callback) callback();
    });
  }

  window.rsDarkMode = {
    /** Calls back once `mode` can be shown without light panels flashing. */
    whenReady: function (mode, callback) {
      if (isDark(mode)) whenLoaded(ensureDarkSheets(), callback);
      else callback();
    },
  };

  var mode = storedMode();
  saveCookie(mode);
  if (isDark(mode)) {
    // Stylesheets the server rendered block rendering by themselves; any the
    // page has to add do not, so it stays hidden until all have loaded.
    setDark(true);
    var pending = ensureDarkSheets();
    if (pending.length) {
      root.style.visibility = "hidden";
      whenLoaded(pending, function () {
        root.style.visibility = "";
      });
    }
  } else {
    setDark(false);
  }

  window.addEventListener("storage", function (event) {
    if (event.key !== "mui-mode") return;
    saveCookie(event.newValue);
    apply(event.newValue);
  });
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", function () {
    var current = storedMode();
    if (current !== "light" && current !== "dark") apply(current);
  });
  // Stylesheets the parser links later come with the server's dark links,
  // which follow them; once parsing ends, the <style>-block and hand-written
  // rules move after them all.
  document.addEventListener("DOMContentLoaded", function () {
    if (root.classList.contains("dark")) ensureDarkSheets();
  });
  // Stylesheets that scripts link later.
  new MutationObserver(function (records) {
    if (document.readyState === "loading" || !root.classList.contains("dark")) return;
    for (var i = 0; i < records.length; i++) {
      var added = records[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        if (added[j].nodeName === "LINK" && !added[j].hasAttribute("data-dark-mode-sheet")) {
          ensureDarkSheets();
          return;
        }
      }
    }
  }).observe(document, { childList: true, subtree: true });
})();
