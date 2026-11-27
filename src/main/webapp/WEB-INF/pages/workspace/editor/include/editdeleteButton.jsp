<%@ include file="/common/taglibs.jsp"%>
<%-- Delete is hidden for copied fields on an edited existing form, but allowed for fields added to
that draft. --%>
<button class="editButton btn btn-default" data-form-field-action="edit" data-field-id="${field.id}"><spring:message code="common:actions.edit"/></button>

<c:if test="${templateOperation eq 'CREATE' or not copiedTemporaryFieldIds.contains(field.id)}">
	<button class="deleteButton btn btn-default" data-form-field-action="delete" data-field-id="${field.id}"><spring:message code="common:actions.delete"/></button>
</c:if>
