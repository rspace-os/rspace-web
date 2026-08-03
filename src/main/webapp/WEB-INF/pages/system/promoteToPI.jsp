<%@ include file="/common/taglibs.jsp"%>
<%--  Dialog contents for promote to PI  dialog in System page
 selectedInfo=UserPublicInfo
--%>


<c:choose>

<%-- when creating form  for promote dialog--%>
<c:when test="${empty newPI}">
<div id="promoteToPIHelpText">
<spring:message code="system.userToPi.consequencesIntro"/>
<ul>
 <li><spring:message code="system.userToPi.labGroupRemovalConsequence"/>
 <li><spring:message code="system.userToPi.newLabGroupConsequence"/>
 <li><spring:message code="system.userToPi.collaborationGroupConsequence"/>
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
 <p id="formCompleted"> <spring:message code="system.userToPi.success" arguments="${newPI.fullName}"/></p>
 <p>
 <p><spring:message code="system.pageReloading"/></p>

</c:otherwise>
</c:choose>