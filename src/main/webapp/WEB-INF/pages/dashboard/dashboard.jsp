<%@ include file="/common/taglibs.jsp"%>
<head>
	<title><spring:message code="common:appBar.messaging"/></title>
	<meta name="heading" content="<spring:message code='mainMenu.heading'/>" />
	<meta name="menu" content="MainMenu" />
	<link rel="stylesheet" href="<rst:assetUrl value='/styles/pages/messaging/dashboard.css'/>" />
	<script src="<rst:assetUrl value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
	<script src="<rst:assetUrl value='/scripts/pages/messaging/notifications.js'/>"></script>
	<script src="<rst:assetUrl value='/scripts/pages/messaging/messages.js'/>"></script>
	<script src="<rst:assetUrl value='/scripts/pages/messaging/myrequests.js'/>"></script>
	<script src="<rst:assetUrl value='/scripts/pages/messaging/messageCreation.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/workspace/calendarDialog.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/jqueryFileTree/jqueryFileTree.js'/>"></script>
  <link href="<rst:assetUrl value='/scripts/jqueryFileTree/jqueryFileTree.css'/>" rel="stylesheet" />
	<script src="<rst:assetUrl value='/scripts/pages/messaging/dashboard.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
</head>

<div class="separator"></div>
<div id="sendMessageBar">
	<div class="sendMessage">
		<h3><a id="createRequest" href="#"> <spring:message code="messaging.sendMessageLinkLabel"/></a></h3>
		<spring:message code="messaging.sendMessageLinkHelpText"/>
	</div>
	<spring:message code="dashboard.createMessage.label" var="createMessageLabel"/>
	<div class="createMessage">
		<a href="#" id="msgIconLink"><img src="/images/messageEdited.png" title="${createMessageLabel}" alt="${createMessageLabel}"></a>
	</div>

	<spring:message code="dashboard.messages.header" var="messageSettingsLabel"/>
	<div id="settingsLink">
		<a href="/userform"><spring:message code="dashboard.messages.header"/><br>
        <img src="/images/icons/messageSettingsSmall.png" title="${messageSettingsLabel}" alt="${messageSettingsLabel}"></a>
	</div>
</div>

<div id="mainBlock">
	<br>
	<div id="requestHeader">
		<a class="dashboardOptions" id="mynotifications" href="#">
		    <spring:message code="messaging.newNotificationsTitle"/>
	<c:if test="${not empty notificationList}">
                <span class="badge" id="notificationsBadge">${fn:length(notificationList)}</span>
            </c:if>
		</a>
		<a class="dashboardOptions" id="mor" href="#">
		<spring:message code="dashboard.received.header"/>
	<c:if test="${not empty messages}">
                <span class="badge" id="messagesBadge">${fn:length(messages)}</span>
            </c:if>
		</a>
		<a class="dashboardOptions" id="myrequests" href="#"><spring:message code="dashboard.sent.header"/>  </a>
    <a class="dashboardOptions" id="createCalendarEntryDlgLink" href="#"><spring:message code="dashboard.new.calendar.entry"/>  </a>
    <jsp:include page="../workspace/calendarDialog.jsp" />
	</div>
	<div class="dashboardContainer notificationList"><jsp:include page="notifications_ajax.jsp"></jsp:include></div>
</div>

<%-- Dialog for creating request --%>
<div id="createRequestDlg" style="display: none">
	<div id="createRequestDlgContent"></div>
</div>
