<c:choose>
	<c:when test="${isVerificationPwdRequired}">
		<label for="sysadminPassword"><spring:message code="system.admin.reauthenticateWithVerificationPassword"/></label><br/>
	</c:when>
	<c:otherwise>
		<label for="sysadminPassword"><spring:message code="system.admin.reauthenticate"/></label><br/>
	</c:otherwise>
</c:choose>
<spring:message code="system.admin.confirmPasswordTitle" var="confirmPasswordTitle"/>
<form:password path="sysadminPassword" maxlength="128" title="${confirmPasswordTitle}" autofocus="true" />