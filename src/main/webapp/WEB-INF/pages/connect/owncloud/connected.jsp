<%@ include file="/common/taglibs.jsp"%>
<head>
	<title>Connected to ownCloud</title>
</head>
<div id="ownCloudAuthorizationSuccess" class="bootstrap-custom-flat">
    <spring:message code="apps.owncloud.connected.msg1"/>
    <br>
    <spring:message code="apps.owncloud.connected.msg2"/>
</div>

<input id="ownCloudAccessToken" type="hidden" value="${ownCloudAccessToken}">
<input id="ownCloudUsername" type="hidden" value="${ownCloudUsername}">

<%-- This script is an addition to make the new apps page backwards compatible. --%>
<script>
  window.addEventListener("load", () => {
    if(window.opener.document.location.pathname === "/apps") {
      window.opener.dispatchEvent(new Event("OWNCLOUD_CONNECTED"));
      window.close();
    }
  });
</script>

