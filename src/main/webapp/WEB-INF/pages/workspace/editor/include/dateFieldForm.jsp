<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="date"  class="field">
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
					<input id="${field.id}" name="dateField_${field.id}" type="text" class ="datepicker" value="${field.defaultDateAsString}"/>
					<input type="hidden" name="format_${field.id}" value="${field.format}"/>
					<input type="hidden" name="minValue" value="${field.minValue}"/>
					<input type="hidden" name="maxValue" value="${field.maxValue}"/>
					<input type="hidden" name="fieldId" value="${field.id}"/>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>
