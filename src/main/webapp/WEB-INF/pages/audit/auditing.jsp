<%@ include file="/common/taglibs.jsp"%>
<%-- JSP to create audit reports --%>
<fmt:bundle basename="bundles.admin.admin">
<head>
    <title><fmt:message key="audit.title"/></title>
    <meta name="heading" content="<fmt:message key='audit.heading'/>"/>
    <meta name="menu" content="MainMenu"/>
    <script src="<c:url value='/scripts/pages/audit.js'/>"></script>
    <script src="<c:url value='/scripts/pages/messaging/messageCreation.js'/>"></script>   
	<link rel="stylesheet" href="<c:url value='/styles/audit.css'/>" />
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<p style="visibility:hidden;">Text</p>
<form style="font-size:1em;">
<shiro:hasRole name="ROLE_USER">
<shiro:lacksRole name="ROLE_PI">
<div id="auditUser" class="auditIt">
	<h3>
		<spring:message code="action.audit"/>: My&nbsp;<spring:message code="audit.activity.hdr"/>
	</h3>
	<h4>
		<spring:message code="audit.activity.filter.label"/>
		by <a href="#" id="domains" class="addRow"><spring:message code="audit.domains.label"/></a>,
		by <a href="#" id="actions" class="addRow"><spring:message code="audit.actions.label"/></a>,
		by <a href="#" id="oids" class="addRow"><spring:message code="audit.identifiers.label"/></a>
		and
		by <a href="#" id="dates" class="addRow"><spring:message code="audit.date.label"/></a>
	</h4>
	<div class="auditRow"></div>
</div>
</shiro:lacksRole>
</shiro:hasRole>

<shiro:hasRole name="ROLE_PI">
<div id="auditPI" class="auditIt">
	<h3>
		<spring:message code="action.audit"/>: <spring:message code="audit.mygroup.label"/> &nbsp; <spring:message code="audit.activity.hdr"/>
	</h3>
  <h4>
		<spring:message code="audit.activity.filter.label"/>
		by <a href="#" id="domains" class="addRow"><spring:message code="audit.domains.label"/></a>,
		by <a href="#" id="actions" class="addRow"><spring:message code="audit.actions.label"/></a>,
		by <a href="#" id="oids" class="addRow"><spring:message code="audit.identifiers.label"/></a>,
		by <a href="#" id="dates" class="addRow"><spring:message code="audit.date.label"/></a>
    and
		by <a href="#" id="users" class="addRow"><spring:message code="audit.users.label"/></a>
	</h4>
	<div class="auditRow"></div>
</div>
</shiro:hasRole>

<shiro:hasRole name="ROLE_ADMIN">
<div id="auditAdmin" class="auditIt">
	<h3>
		<spring:message code="action.audit"/>: <spring:message code="audit.mycommunity.label"/> &nbsp; <spring:message code="audit.activity.hdr"/>
	</h3>
  <h4>
		<spring:message code="audit.activity.filter.label"/>
		by <a href="#" id="domains" class="addRow"><spring:message code="audit.domains.label"/></a>,
		by <a href="#" id="actions" class="addRow"><spring:message code="audit.actions.label"/></a>,
		by <a href="#" id="oids" class="addRow"><spring:message code="audit.identifiers.label"/></a>,
		by <a href="#" id="dates" class="addRow"><spring:message code="audit.date.label"/></a>,
    and
		by <a href="#" id="users" class="addRow"><spring:message code="audit.users.label"/></a>
    or
		by <a href="#" id="groups" class="addRow"><spring:message code="audit.groups.label"/></a>
	</h4>
	<div class="auditRow"></div>
</div>
</shiro:hasRole>
<shiro:hasRole name="ROLE_SYSADMIN">
<div id="auditSysAdmin" class="auditIt">
  <h3>
    <spring:message code="action.audit"/>:  <spring:message code="audit.global.label"/> &nbsp; <spring:message code="audit.activity.hdr"/>
  </h3>
  <h4> 
		<spring:message code="audit.activity.filter.label"/>
		by <a href="#" id="domains" class="addRow"><spring:message code="audit.domains.label"/></a>,
		by <a href="#" id="actions" class="addRow"><spring:message code="audit.actions.label"/></a>,
		by <a href="#" id="oids" class="addRow"><spring:message code="audit.identifiers.label"/></a>,
		by <a href="#" id="dates" class="addRow"><spring:message code="audit.date.label"/></a>,
    and
		by <a href="#" id="users" class="addRow"><spring:message code="audit.users.label"/></a>,
		by <a href="#" id="groups" class="addRow"><spring:message code="audit.groups.label"/></a>
    or by <a href="#" id="communities" class="addRow"><spring:message code="audit.communities.label"/></a>
   </h4>
   <div class="auditRow"></div>
</div>
</shiro:hasRole>
<p class="bootstrap-custom-flat">
	<button class="getAudit btn btn-default" type="submit" role="button" name="getAudit" 
	   value="Get Audit Report" id="getAuditSubmit" style="width:160px; margin: 5px 0 12px 0;" >
	   <span><spring:message code="audit.getreport.button.label"/></span>
	</button>
	<button class="downloadAudit btn btn-default" type="submit" role="button" name="downloadAudit" 
	   value="Download Audit Report" id="downloadAuditSubmit" style="width:160px; margin: 5px 0 12px 0;" >
	   <span>Download Audit Report</span>
	</button>
	(maximum of 10000 events per download)
</p>
</form>
<div id="storedRowSet" style="display:none;">
	<div class="domainsRow auditRow">
	</div>
	<div class="actionsRow auditRow">
	</div>
	<div class="oidsRow auditRow">
	<label>Enter a global id of a document, notebook, or Inventory item e.g. SD12345
    			<input id="oidsEntry" class="oidsSelection" name="oid" type="text">
    		</label>
    			<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
    	</div>
	
	<div class="datesRow auditRow">
		Date range to audit 
		<label> from <input name="dateFrom" type="text" id="datepickerFrom"> </label>
		<label> to <input name="dateTo" type="text" id="datepickerTo"> </label>
		<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x2">X</button>
	</div>
	<div class="usersRow auditRow">
		<label>Enter a user or users to audit 
			<input id="userEntry" class="userSelection" name="users" type="text">
		</label>
			<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
	</div>
	<div class="groupsRow auditRow">
		<label>Enter a LabGroup to audit 
			<input id="groupEntry" class="groupSelection" name="groups">
		</label>
		<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
	</div>
	<div class="communitiesRow auditRow">
		<label>Enter a Community to audit 
			<input id="communityEntry" class="communitySelection" name="communities">
		</label>
		<button class="remover" style="height:19px; width:19px;padding:0px !important;" name="x1">X</button>
	</div>
</div>
<div id="theData"></div>
<jsp:include page="auditSearchResultTemplate.html"></jsp:include>
</fmt:bundle>
