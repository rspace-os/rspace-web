<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="string"  class="field">
	<td name="fieldName" class="field-name">${field.name}
	<span class="field-type-enum"> (${field.type.type})</span>
	<c:if test="${field.mandatory eq true}">
		<span class="rs-required-marker">*</span>
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
					<input id="${field.id}" type="text" name="fieldData" value="${field.defaultStringValue}" />
					<input type="hidden" name="wasChanged" value="false"/>
					<input type="hidden" name="defaultValue" value="${field.defaultStringValue}"/>
					<input type="hidden" name="fieldId" value="${field.id}"/>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>
