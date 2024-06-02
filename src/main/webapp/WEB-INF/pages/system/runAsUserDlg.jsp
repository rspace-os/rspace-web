<%@ include file="/common/taglibs.jsp"%>

<%--  Dialog contents for runAsUser dialog in System page

--%>
<c:choose>

<%-- when creating form  for promote dialog--%>
<c:when test="${empty completed}">
<p/>
<spring:message code="system.runAs.help1"/>
<br/>
<spring:message code="system.runAs.help2"/>


<form:form method="POST"  modelAttribute="runAsUserCommand">
<%-- this holds any validation errors returning from controller --%>
<span style="color:red;">
<form:errors path="*"></form:errors>
</span>
<p/>
<label for="runAsUsername"><spring:message code="system.runAs.label"/></label>
<form:input path="runAsUsername" placeholder="Enter a name, email or username..."/>
<p/>
<label for="incognito"><spring:message code="system.runAs.incognito.label"/></label>
<form:checkbox path="incognito"/>
<p/>
<c:choose>
	<c:when test="${isVerificationPwdRequired}">
		<label for="sysadminPassword"><fmt:message key="system.admin.reauthenticateMsgVerifcnPwd"/></label><br/>
	</c:when>
	<c:otherwise>
		<label for="sysadminPassword"><fmt:message key="system.admin.reauthenticateMsg"/></label><br/>
	</c:otherwise>
</c:choose>
<form:password path="sysadminPassword"/>


</form:form>
</c:when>
<%-- just  a label that page is OK and can redirect--%>
<c:otherwise>
<p id="formCompleted"> <fmt:message key="general.pageReloading"/></p>
</c:otherwise>
</c:choose>
