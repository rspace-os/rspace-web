<%@ include file="/common/taglibs.jsp"%>
<%--
  Shared OAuth/credential result page for integrations configured from the React
  Apps page (/apps). It carries no visible content: on load it posts the outcome
  to the integration's BroadcastChannel and closes itself, so the user stays in
  the Apps page SPA. The Apps page card listens on the channel and shows an
  alert. Used for both success and failure.

  Model attributes (all optional except channel + type):
    appName            page title
    connectionChannel  BroadcastChannel name, e.g. rspace.apps.github.connection
    connectionType     message type, e.g. GITHUB_CONNECTED
    connectionAlias    extra payload (raid)
    connectionToken    extra payload (github access token)
    connectionError    when set, the card treats the message as a failure

  Values are emitted as HTML-escaped data-* attributes (not interpolated into the
  script) so provider-supplied error text cannot break out of the JS context.
--%>
<head>
	<title><c:out value="${appName}"/></title>
</head>
<body>
<div id="rs-connection-result"
     data-channel="<c:out value='${connectionChannel}'/>"
     data-type="<c:out value='${connectionType}'/>"
     data-alias="<c:out value='${connectionAlias}'/>"
     data-token="<c:out value='${connectionToken}'/>"
     data-error="<c:out value='${connectionError}'/>"></div>
<p>You may now close this window.</p>
<script>
  window.addEventListener("load", () => {
    const d = document.getElementById("rs-connection-result").dataset;
    const msg = { type: d.type };
    if (d.alias) msg.alias = d.alias;
    if (d.token) msg.authToken = d.token;
    if (d.error) msg.error = d.error;
    const channel = new BroadcastChannel(d.channel);
    channel.postMessage(msg);
    channel.close();
    window.close();
  });
</script>
</body>
