<%@ include file="/common/taglibs.jsp"%>
<head>
	<title><fmt:message key="messaging.pageTitle" /></title>
	<meta name="heading" content="<fmt:message key='mainMenu.heading'/>" />
	<meta name="menu" content="MainMenu" />
	<link rel="stylesheet" href="<c:url value='/styles/pages/messaging/dashboard.css'/>" />
	<script src="<c:url value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
	<script src="<c:url value='/scripts/pages/messaging/notifications.js'/>"></script>
	<script src="<c:url value='/scripts/pages/messaging/messages.js'/>"></script>
	<script src="<c:url value='/scripts/pages/messaging/myrequests.js'/>"></script>
	<script src="<c:url value='/scripts/pages/messaging/messageCreation.js'/>"></script>
	<script src="<c:url value='/scripts/pages/messaging/dashboard.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
</head>

<div class="separator"></div>
<div id="sendMessageBar">
	<div class="sendMessage">
		<h3><a id="createRequest" href="#"> <spring:message code="messaging.sendMessageLinkLabel"/></a></h3>
		<spring:message code="messaging.sendMessageLinkHelpText"/>
	</div>
	<div class="createMessage">
		<a href="#" id="msgIconLink"><img src="/images/messageEdited.png" title="Create Message" alt="Create Message"></a>
	</div>

 	<div id="settingsLink">
 		<a href="/userform"><spring:message code="dashboard.msg.header"/><br>
        <img src="/images/icons/messageSettingsSmall.png" title="Message Settings" alt="Message Settings"></a>
 	</div>
</div>

<div id="mainBlock">	
	<br>
	<div id="requestHeader">
		<a class="dashboardOptions" id="mynotifications" href="#"><spring:message code="messaging.newNotificationsTitle"/></a>
		<a class="dashboardOptions" id="mor" href="#"><spring:message code="dashboard.received.header"/></a>
		<a class="dashboardOptions" id="myrequests" href="#"><spring:message code="dashboard.sent.header"/>  </a>
	</div>
	<div class="dashboardContainer notificationList"><jsp:include page="notifications_ajax.jsp"></jsp:include></div>
</div>

<%-- Dialog for creating request --%>
<div id="createRequestDlg" style="display: none">
	<div id="createRequestDlgContent"></div>
</div>