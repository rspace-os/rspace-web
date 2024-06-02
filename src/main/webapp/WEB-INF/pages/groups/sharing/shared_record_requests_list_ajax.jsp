<%@ include file="/common/taglibs.jsp"%>

<c:if test="${empty requests}">
	<p>You have no shared document requests to manage.
</c:if>

<c:if test="${not empty requests}">
	<table class="table">
		<tr>
			<th>Document name</th>
			<th>Global Id</th>
			<th>Shared with</th>
			<th>Permission</th>
			<th>Actions</th>
		</tr>
		<c:forEach items="${requests}" var="request">
			<c:choose>
				<c:when test="${request.record.structuredDocument}">
					<c:url var="loadDoc" value="/workspace/editor/structuredDocument/${request.record.id}"></c:url>
				</c:when>
				<c:when test="${request.record.notebook}">
					<c:url var="loadDoc" value="/notebookEditor/${request.record.id}"></c:url>
				</c:when>
			</c:choose>
			<c:url value="/globalId/${request.record.globalIdentifier}" var="globalURL"></c:url>
			<tr>
				<td><a href="${loadDoc}">${request.record.name}</a></td>
				<td><a href="${globalURL}">${request.record.globalIdentifier}</a></td>
				<td>${request.email}</td>
				<td><span class='permType'>${fn:toUpperCase(request.permission)}</span></td>
				<td><a data-requestid="${request.requestId}" class="cancel" href="#">Cancel</a></td>
			</tr>
		</c:forEach>
	</table>
</c:if>