<%@ include file="/common/taglibs.jsp"%>
<head>
<title>You are connected to Figshare</title>
<script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
</head>

<div id="figshareAuthorizationSuccess" class="bootstrap-custom-flat">
	Success - RSpace was authorized to access Figshare.
	<p class="font-weight-bold">
	 You can
	close this window now or configure <a href="/apps"> more Apps</a>.
	</p>
	<p>If you want to revoke RSpace's access to your Figshare account,
	    please click 'Disconnect':
	</p>
	<form action="<c:url value="/apps/figshare/connect" />" method="POST"
		style="width: 80%">
		<input type="hidden" name="_method" value="delete" />
		<button class="btn btn-primary" type="submit">Disconnect</button>
	</form>

  <%-- This script is an addition to make the new apps page backwards compatible. --%>
  <script>
    window.addEventListener("load", () => {
      if(window.opener.document.location.pathname === "/apps") {
        window.opener.dispatchEvent(new Event("FIGSHARE_CONNECTED"));
        window.close();
      }
    });
  </script>

</div>
