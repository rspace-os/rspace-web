<%@ include file="/common/taglibs.jsp"%>
<%-- JSP to create audit reports --%>
<head>
    <title><spring:message code="audit.title"/></title>
    <meta name="heading" content="<spring:message code='audit.heading'/>"/>
    <meta name="menu" content="MainMenu"/>
    <script src="<rst:assetUrl value='/scripts/pages/audit.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/messaging/messageCreation.js'/>"></script>
	<link rel="stylesheet" href="<rst:assetUrl value='/styles/audit.css'/>" />
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<p style="visibility:hidden;" aria-hidden="true"></p>
<form style="font-size:1em;">
<spring:message code="audit.domains.label" var="domainsLabel"/>
<spring:message code="audit.actions.label" var="actionsLabel"/>
<spring:message code="inventory:formSections.identifiers" var="identifiersLabel"/>
<spring:message code="audit.date.label" var="dateLabel"/>
<spring:message code="system:usersPage.title" var="usersLabel"/>
<spring:message code="common:profile.groups.title" var="groupsLabel"/>
<spring:message code="audit.communities.label" var="communitiesLabel"/>
<c:set var="domainsLink"><a href="#" id="domains" class="addRow">${domainsLabel}</a></c:set>
<c:set var="actionsLink"><a href="#" id="actions" class="addRow">${actionsLabel}</a></c:set>
<c:set var="identifiersLink"><a href="#" id="oid" class="addRow">${identifiersLabel}</a></c:set>
<c:set var="datesLink"><a href="#" id="dates" class="addRow">${dateLabel}</a></c:set>
<c:set var="usersLink"><a href="#" id="users" class="addRow">${usersLabel}</a></c:set>
<c:set var="groupsLink"><a href="#" id="groups" class="addRow">${groupsLabel}</a></c:set>
<c:set var="communitiesLink"><a href="#" id="communities" class="addRow">${communitiesLabel}</a></c:set>
<shiro:hasRole name="ROLE_USER">
<shiro:lacksRole name="ROLE_PI">
<div id="auditUser" class="auditIt">
	<h3>
		<spring:message code="action.audit"/>: <spring:message code="audit.my"/>
	</h3>
	<h4>
		<spring:message code="audit.activity.filter.user">
			<spring:argument value="${domainsLink}"/>
			<spring:argument value="${actionsLink}"/>
			<spring:argument value="${identifiersLink}"/>
			<spring:argument value="${datesLink}"/>
		</spring:message>
	</h4>
	<div class="auditRow"></div>
</div>
</shiro:lacksRole>
</shiro:hasRole>

<shiro:hasRole name="ROLE_PI">
<div id="auditPI" class="auditIt">
	<h3>
		<spring:message code="action.audit"/>: <spring:message code="audit.myGroup.label"/>
  </h3>
  <h4>
		<spring:message code="audit.activity.filter.pi">
			<spring:argument value="${domainsLink}"/>
			<spring:argument value="${actionsLink}"/>
			<spring:argument value="${identifiersLink}"/>
			<spring:argument value="${datesLink}"/>
			<spring:argument value="${usersLink}"/>
		</spring:message>
	</h4>
	<div class="auditRow"></div>
</div>
</shiro:hasRole>

<shiro:hasRole name="ROLE_ADMIN">
<div id="auditAdmin" class="auditIt">
	<h3>
		<spring:message code="action.audit"/>: <spring:message code="audit.myCommunity.label"/>
  </h3>
  <h4>
		<spring:message code="audit.activity.filter.admin">
			<spring:argument value="${domainsLink}"/>
			<spring:argument value="${actionsLink}"/>
			<spring:argument value="${identifiersLink}"/>
			<spring:argument value="${datesLink}"/>
			<spring:argument value="${usersLink}"/>
			<spring:argument value="${groupsLink}"/>
		</spring:message>
	</h4>
	<div class="auditRow"></div>
</div>
</shiro:hasRole>
<shiro:hasRole name="ROLE_SYSADMIN">
<div id="auditSysAdmin" class="auditIt">
  <h3>
    <spring:message code="action.audit"/>: <spring:message code="audit.global.label"/>
  </h3>
  <h4>
		<spring:message code="audit.activity.filter.sysadmin">
			<spring:argument value="${domainsLink}"/>
			<spring:argument value="${actionsLink}"/>
			<spring:argument value="${identifiersLink}"/>
			<spring:argument value="${datesLink}"/>
			<spring:argument value="${usersLink}"/>
			<spring:argument value="${groupsLink}"/>
			<spring:argument value="${communitiesLink}"/>
		</spring:message>
   </h4>
   <div class="auditRow"></div>
</div>
</shiro:hasRole>
<p class="bootstrap-custom-flat">
	<button class="getAudit btn btn-default" type="submit" role="button" name="getAudit"
	   value="<spring:message code='audit.getReport.button.label'/>" id="getAuditSubmit" style="width:160px; margin: 5px 0 12px 0;" >
	   <span><spring:message code="audit.getReport.button.label"/></span>
	</button>
	<button class="downloadAudit btn btn-default" type="submit" role="button" name="downloadAudit"
	   value="<spring:message code='audit.downloadReport.button.label'/>" id="downloadAuditSubmit" style="width:160px; margin: 5px 0 12px 0;" >
	   <span><spring:message code="audit.downloadReport.button.label"/></span>
	</button>
	<spring:message code="audit.downloadReport.limit"/>
</p>
</form>
<div id="storedRowSet" style="display:none;">
	<div class="domainsRow auditRow">
	</div>
	<div class="actionsRow auditRow">
	</div>
	<div class="oidRow auditRow">
	<label><spring:message code="audit.filter.globalIdPrompt"/>
			<input id="oidsEntry" class="oidsSelection" name="oid" type="text">
		</label>
			<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
	</div>

	<div class="datesRow auditRow">
		<spring:message code="audit.filter.dateRangePrompt"/>
		<label> <spring:message code="audit.filter.from"/> <input name="dateFrom" type="text" id="datepickerFrom"> </label>
		<label> <spring:message code="audit.filter.to"/> <input name="dateTo" type="text" id="datepickerTo"> </label>
		<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x2">X</button>
	</div>
	<div class="usersRow auditRow">
		<label><spring:message code="audit.filter.usersPrompt"/>
			<input id="userEntry" class="userSelection" name="users" type="text">
		</label>
			<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
	</div>
	<div class="groupsRow auditRow">
		<label><spring:message code="audit.filter.labGroupPrompt"/>
			<input id="groupEntry" class="groupSelection" name="groups">
		</label>
		<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
	</div>
	<div class="communitiesRow auditRow">
		<label><spring:message code="audit.filter.communityPrompt"/>
			<input id="communityEntry" class="communitySelection" name="communities">
		</label>
		<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
	</div>
</div>
<div id="theData"></div>
<jsp:include page="auditSearchResultTemplate.html"></jsp:include>
