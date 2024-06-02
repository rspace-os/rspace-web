<%@ include file="/common/taglibs.jsp"%>
<fmt:bundle basename="bundles.system.system">
<%--  Dialog contents for promote to PI  dialog in System page
 selectedInfo=UserPublicInfo
--%>


<c:choose>

<%-- when creating form  for promote dialog--%>
<c:when test="${empty newUser}">
<div id="revokePIHelpText">
Removes PI global role for this user.
</div>
<form:form method="POST" modelAttribute="userRoleChangeCmnd">
<%-- this holds any validation errors returning from controller --%>
<span style="color:red;">
<form:errors path="*"></form:errors>
</span>
<p/>
<%@include file="confirmPasswordFragment.jsp"%>
<p/>
<%-- will be set by javascript depending on selection --%>
<form:hidden path="userId"/>

</form:form>
</c:when>
<%--otherwise form submission successful, just confirming and JS will trigger reload--%>
<c:otherwise>
<fmt:message key="system.piToUser.successMsg" var="msgText">
 	<fmt:param value="${newUser.fullName}"></fmt:param>
</fmt:message>
 <p id="formCompleted"> ${msgText}</p>
 <p> 
 <p><fmt:message key="system.pageReloading"/></p>

</c:otherwise>
</c:choose>
</fmt:bundle>