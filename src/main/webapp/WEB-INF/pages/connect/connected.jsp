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
  script) so provider-supplied error text cannot break out of the JS context. The
  escaping happens once, globally, via the EscapeXmlELResolver registered in
  web.xml, which escapes every ${...} that resolves to a String. Do NOT wrap these
  in <c:out>: that escapes an already-escaped value, so a " becomes &amp;#034; and
  the browser decodes only one layer, corrupting JSON payloads such as Slack's
  OAuth response.
--%>
<head>
  <spring:message code="connect.connected.defaultTitle" var="connectedDefaultTitle"/>
  <title>${empty appName ? connectedDefaultTitle : appName}</title>
</head>
<body>
<div id="rs-connection-result"
     data-channel="${connectionChannel}"
     data-type="${connectionType}"
     data-alias="${connectionAlias}"
     data-token="${connectionToken}"
     data-error="${connectionError}"
     data-response="${connectionResponse}"></div>
<p><spring:message code="connect.connected.closeWindowNotice"/></p>
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
