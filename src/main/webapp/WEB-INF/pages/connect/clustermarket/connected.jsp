<%@ include file="/common/taglibs.jsp"%>
<head>
<title>You are connected to Clustermarket</title>
<script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
</head>

<div id="clustermarketAuthorizationSuccess" class="bootstrap-custom-flat">
	Success - RSpace was authorized to access data from Clustermarket.
	<p class="font-weight-bold">
	 You can
	close this window now or configure <a href="/apps"> more Apps</a>.
	</p>
	<p>If you want to revoke RSpace's access to your Clustermarket account,
	    please click 'Disconnect':
	</p>
	<form action="<c:url value="/apps/clustermarket/connect" />" method="POST"
		style="width: 80%">
		<input type="hidden" name="_method" value="delete" />
		<button class="btn btn-primary" type="submit" id="rs-app-clustermarket-disconnectbutton">Disconnect</button>
	</form>
</div>

<%-- This script is an addition to make the new apps page backwards compatible. --%>
<script>
  window.addEventListener("load", () => {
    if(window.opener.document.location.pathname === "/apps") {
      window.opener.dispatchEvent(new Event("CLUSTERMARKET_CONNECTED"));
      window.close();
    }
  });
</script>

