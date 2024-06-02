<%@ include file="/common/taglibs.jsp"%>
<fmt:bundle basename="bundles.system.system">
<%--  Dialog contents for promote to PI  dialog in System page
 selectedInfo=UserPublicInfo
--%>


<c:choose>

<%-- when creating form  for promote dialog--%>
<c:when test="${empty newPI}">
<div id="promoteToPIHelpText">
<fmt:message key="system.userToPI.help1"></fmt:message>
<ul>
 <li><fmt:message key="system.userToPI.help2"></fmt:message>
 <li><fmt:message key="system.userToPI.help3"></fmt:message>
 <li><fmt:message key="system.userToPI.help4"></fmt:message>
</ul>

</div>
<form:form method="POST" modelAttribute="userRoleChangeCmnd">
<%-- this hods any validation errors returning from controller --%>
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
<fmt:message key="system.userToPI.successMsg" var="msgText">
 	<fmt:param value="${newPI.fullName}"></fmt:param>
</fmt:message>
 <p id="formCompleted"> ${msgText}</p>
 <p> 
 <p><fmt:message key="system.pageReloading"/></p>

</c:otherwise>
</c:choose>
</fmt:bundle>