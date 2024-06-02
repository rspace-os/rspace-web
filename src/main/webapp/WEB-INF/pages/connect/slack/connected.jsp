<%@ include file="/common/taglibs.jsp"%>
<head>
	<title>Connected to Slack</title>
</head>

<div id="slackAuthorizationSuccess" class="bootstrap-custom-flat">
   <spring:message code="apps.slack.connected.msg1"/> 
   <br>
   <ol>
     <li><spring:message code="apps.slack.connected.msg2"/> </li> 
     <li><spring:message code="apps.slack.connected.msg3"/> </li>
     <li><spring:message code="apps.slack.connected.msg4"/> </li>
   </ol>
    
</div>

<input id="slackResponse" type="hidden" value="${slackResponse}">