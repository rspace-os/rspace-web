<%@ include file="/common/taglibs.jsp"%>
<head>
	<title>Connected to Nextcloud</title>
</head>
<div id="nextCloudAuthorizationSuccess" class="bootstrap-custom-flat">
    <spring:message code="apps.nextcloud.connected.msg1"/>
    <br>
    <spring:message code="apps.nextcloud.connected.msg2"/>
</div>
<input id="nextCloudAccessToken" type="hidden" value="${nextCloudAccessToken}">
<input id="nextCloudUsername" type="hidden" value="${nextCloudUsername}">

<%-- This script is an addition to make the new apps page backwards compatible. --%>
<script>
  window.addEventListener("load", () => {
    if(window.opener.document.location.pathname === "/apps") {
      window.opener.dispatchEvent(new Event("NEXTCLOUD_CONNECTED"));
      window.close();
    }
  });
</script>

