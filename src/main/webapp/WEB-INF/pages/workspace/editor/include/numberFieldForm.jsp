<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="number"  class="field">
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
					<input id="${field.id}" name="fieldData" type="text" value="${field.defaultNumberValue}" readonly="true"/>
					<input type="hidden" name="wasChanged" value="false"/>
					<input type="hidden" name="changed" value="false"/>
					<input type="hidden" name="defaultValue" value="${field.defaultNumberValue}"/>
					<input type="hidden" name="decimalPlaces" value="${field.decimalPlaces}"/>
					<input type="hidden" name="minValue" value="${field.minNumberValue}"/>
					<input type="hidden" name="maxValue" value="${field.maxNumberValue}"/>
					<input type="hidden" name="fieldId" value="${field.id}"/>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>
