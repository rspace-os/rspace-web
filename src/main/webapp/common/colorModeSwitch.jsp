<%--
  Lets logged-out users choose the colour mode, which logged-in users change
  from the account menu. It stores the choice under the same `mui-mode` key
  as ui/src/colorScheme.ts and applies it as common/legacyDarkMode.jsp does.
  Hidden where the browser cannot show the page dark.
--%>
<div class="colorModeSwitch" hidden>
  <label for="colorModeSwitch"><spring:message code="colorMode.label"/></label>
  <select id="colorModeSwitch">
    <option value="system"><spring:message code="colorMode.system"/></option>
    <option value="light"><spring:message code="colorMode.light"/></option>
    <option value="dark"><spring:message code="colorMode.dark"/></option>
  </select>
</div>
<script>
  (function () {
    var root = document.documentElement;
    if (!root.hasAttribute("data-dark-mode")) return;
    var select = document.getElementById("colorModeSwitch");
    var mode = null;
    try {
      mode = window.localStorage.getItem("mui-mode");
    } catch (e) {
      // storage unavailable; follow the system preference
    }
    select.value = mode === "light" || mode === "dark" ? mode : "system";
    select.parentElement.hidden = false;
    select.addEventListener("change", function () {
      var value = select.value;
      try {
        window.localStorage.setItem("mui-mode", value);
      } catch (e) {
        // storage unavailable; the choice lasts for this page only
      }
      var dark =
        value === "dark" ||
        (value === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);
      root.classList.toggle("dark", dark);
      root.classList.toggle("light", !dark);
      // React roots on the page follow through colorScheme.ts.
      window.dispatchEvent(new StorageEvent("storage", { key: "mui-mode", newValue: value }));
    });
  })();
</script>
