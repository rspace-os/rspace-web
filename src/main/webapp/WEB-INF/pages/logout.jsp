<html>
<body>
<%-- EscapeXmlELResolver escapes the destination for this quoted HTML attribute. --%>
<script type="text/javascript" data-redirect-location="${redirectLocation}">
  window.rspaceCrossTabLogoutStarted = true;
  const mode = "${logoutMode eq 'sso' ? 'sso' : 'standalone'}";
  const at = Date.now();
  try {
    window.sessionStorage.removeItem("id_token");
  } catch (error) {
    // Continue signing out when browser storage is unavailable.
  }
  try {
    window.localStorage.setItem("rspace.logout", JSON.stringify({ at, mode }));
  } catch (error) {
    // BroadcastChannel can still notify the other tabs.
  }
  try {
    const channel = new BroadcastChannel("rspace-session");
    channel.postMessage({ type: "logout", at, mode });
    channel.close();
  } catch (error) {
    // Tabs can receive the storage event when BroadcastChannel is unavailable.
  }
  window.location.href = document.currentScript.dataset.redirectLocation;
</script>
</body>
</html>
