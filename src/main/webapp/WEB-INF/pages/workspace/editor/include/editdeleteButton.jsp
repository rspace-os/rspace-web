<%@ include file="/common/taglibs.jsp"%>
<%-- code for handling the appearance or otherwise of edit/delete buttons
Because delete of a field leads to a loss of referential integrity
 this is currently disabled for template editing --%>
<button class="editButton btn btn-default" onclick="editField(${field.id})">Edit</button>

<c:if test="${templateOperation eq 'CREATE' or template.publishingState eq 'NEW'}">
	<button class="deleteButton btn btn-default" onclick="deleteField(${field.id})">Delete</button>
</c:if>
