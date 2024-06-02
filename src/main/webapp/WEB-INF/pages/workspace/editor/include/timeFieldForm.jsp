<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="time" class="field">
	<td  name="fieldName" class="field-name">
		${field.name}
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
			</tr>
			<tr>
				<td class="field-value-inner">
					<input id="${field.id}" name="timeField_${field.id}" type="text" class ="timepicker"  value="${field.defaultTimeAsString}"/>
					<input type="hidden" name="defaultTime" value="${field.defaultTimeAsString}"/>
					<input type="hidden" name="format_${field.id}" value="${field.timeFormat}"/>
					<input type="hidden" name="minTime" value="${field.minTime}"/>
					<input type="hidden" name="maxTime" value="${field.maxTime}"/>
					<input type="hidden" name="minHour" value="${field.minHour}"/>
					<input type="hidden" name="minMinutes" value="${field.minMinutes}"/>
					<input type="hidden" name="maxHour" value="${field.maxHour}"/>
					<input type="hidden" name="maxMinutes" value="${field.maxMinutes}"/>
					<input type="hidden" name="wasChanged" value="false"/>
  					<input type="hidden" name="fieldId" value="${field.id}"/>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>
