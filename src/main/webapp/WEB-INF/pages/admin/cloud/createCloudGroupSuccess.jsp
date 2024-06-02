<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <title><spring:message code="group.created.success.title"/></title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
</head>
	
<jsp:include page="/WEB-INF/pages/admin/admin.jsp"></jsp:include>
<div class="container">
	<h2><spring:message code="group.created.success.heading"/></h2>   
    <c:choose>
		<c:when test="${not empty principalEmail}"> 
			<h4><spring:message code="group.created.success.nominate.msg1" arguments="${groupName},${principalEmail}"/> 
			<c:if test="${not empty emails}"> 
			<spring:message code="group.created.success.nominate.msg2"/>
			</c:if></h4>
		</c:when>
		<c:otherwise>
			<h4><spring:message code="group.created.success.mePi.msg1" arguments="${groupName}"/>
			 <c:if test="${not empty emails}"> <spring:message code="group.created.success.mePi.msg2"/></c:if></h4>
		</c:otherwise>
	</c:choose>
	<br>
	<c:if test="${not empty emails}">
		<div id="inviteesList" style="min-height:2em;width:457px;border:1px solid #DDDDDD;line-height:1.5em;">
		<ul style="list-style-type:none;font-size: 1em;">
			<c:forEach items="${emails}" var="email">
				<li><c:out value="${email}"/></li>
			</c:forEach>
		</ul>
		</div>
	</c:if>
	<br />
	<spring:message code="group.created.success.conclusion1"/>
	<br /><br />
	<c:choose>
	<%--group is created, ie creator is pi --%>
	<c:when test="${not empty group}">
	  <spring:message code="group.created.success.conclusion2a"/><a href="/groups/view/${group.id}"> here.</a>
	</c:when>
	<%-- group is not yet created as we're nominating a PI --%>
	<c:otherwise>
	   <spring:message code="group.created.success.conclusion2b"/> <a href="/userform"> 
	 <spring:message code="menu.profile"/></a> page.
	</c:otherwise>
	</c:choose>
	
	
</div>
</html>