<%@ include file="/common/taglibs.jsp"%>
<head>
	<title><spring:message code="connect.connected.title"/></title>
</head>

<div class="bootstrap-custom-flat" id="orcidAPIconnectionSuccess">
    <spring:message code="connect.orcid.connectedPrefix"/> <span id="orcidId">${orcid_id}</span>. <spring:message code="connect.orcid.connectedSuffix"/>
    <span id="orcidOptionsId" style="display:none">${orcid_options_id}</span>
</div>
