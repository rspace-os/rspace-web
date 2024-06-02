<c:choose>
	<c:when test="${isVerificationPwdRequired}">
		<label for="sysadminPassword"><fmt:message key="system.admin.reauthenticateMsgVerifcnPwd"/></label><br/>
	</c:when>
	<c:otherwise>
		<label for="sysadminPassword"><fmt:message key="system.admin.reauthenticateMsg"/></label><br/>
	</c:otherwise>
</c:choose>
<form:password path="sysadminPassword" maxlength="100" title="Confirm password" autofocus="true" />