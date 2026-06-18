<%@ include file="/common/taglibs.jsp"%>
<%--
  Shared OAuth/credential result page for integrations configured from the React
  Apps page (/apps). On load it posts the outcome to the integration's
  BroadcastChannel and closes itself, so the user stays in the Apps page SPA.
  The fallback body text is visible only if the window cannot close itself. Used
  for both success and failure.

  Model attributes (all optional except channel + type):
    appName            page title
    connectionChannel  BroadcastChannel name, e.g. rspace.apps.github.connection
    connectionType     message type, e.g. GITHUB_CONNECTED
    connectionAlias    extra payload (raid)
    connectionToken    extra payload (github access token)
    connectionError    when set, the card treats the message as a failure
    connectionResponse raw response payload (e.g. Slack OAuth JSON)

  Values are emitted as HTML-escaped data-* attributes (not interpolated into the
  script) so provider-supplied error text cannot break out of the JS context.
--%>
<head>
  <title><c:out value="${appName}" default="RSpace connection result"/></title>
</head>
<body>
<div id="rs-connection-result"
     data-channel="<c:out value='${connectionChannel}'/>"
     data-type="<c:out value='${connectionType}'/>"
     data-alias="<c:out value='${connectionAlias}'/>"
     data-token="<c:out value='${connectionToken}'/>"
     data-error="<c:out value='${connectionError}'/>"
     data-response="<c:out value='${connectionResponse}'/>"></div>
<p>You may now close this window.</p>
<script>
  window.addEventListener("load", () => {
    const result = document.getElementById("rs-connection-result");
    const d = result ? result.dataset : {};
    if (d.channel && d.type && "BroadcastChannel" in window) {
      const msg = { type: d.type };
      if (d.alias) msg.alias = d.alias;
      if (d.token) msg.authToken = d.token;
      if (d.response) msg.response = d.response;
      if (d.error) msg.error = d.error;
      let channel;
      try {
        channel = new BroadcastChannel(d.channel);
        channel.postMessage(msg);
      } catch (e) {
        console.warn("Unable to broadcast integration connection result", e);
      } finally {
        if (channel) channel.close();
      }
    }
    window.close();
  });
</script>
</body>
