<%-- 
	Dialog that lists templates and forms in separate dialogs
--%>
<%@ attribute name="forms" required="true" type="java.util.List" %>
<%@ attribute name="createFromFormURL" required="true" type="java.lang.String" %>
<%@ attribute name="formsForCreateMenuPagination" required="true" type="java.util.List" %>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<axt:paginate paginationList="${formsForCreateMenuPagination}"></axt:paginate>
<table>
	<tr>
		<th><spring:message code="workspace.list.name.header"/></th>
	</tr>
	<c:forEach items="${forms}" var="form">
		<tr>
			<td>
			<form method="POST" class="createDocument" action="${createFromFormURL}">
    			<div style="float:left">
				<img  src="/image/getIconImage/${form.iconId}" alt="<spring:message code='dialogs.createFromForm.iconAlt'/>" height="32" width="32" />
                </div>
    			<div style="float:left;padding-top:12px;padding-left:5px;" >
    				<a href="#" style="color:blue;" class="createSDFromFormLink">${form.name}</a>
    			</div>
    			<input type="hidden" name="template" value="${form.id}">
			</form>
			</td>
		</tr>
	</c:forEach>
</table>
