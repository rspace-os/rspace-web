<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="text"  class="field">
	<td name="fieldName" class="field-name">${field.name}
	<span class="field-type-enum"> (${field.type.type})</span>
		<c:if test="${field.mandatory eq true}">
			<span style="color: red">*</span>
		</c:if>
	</td>
	<td class="field-value">
		<table>
			 <tr>
				<td class="icon-field bootstrap-custom-flat" >
				<%@ include file="editdeleteButton.jsp"%>		
				</td> 
			<tr>
				<td class="field-value-inner">
					<textarea id="${field.id}" name="fieldRtfData" class="tinymce">${field.defaultValue}</textarea>
					<input type="hidden" name="fieldId" value="${field.id}"/>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>
