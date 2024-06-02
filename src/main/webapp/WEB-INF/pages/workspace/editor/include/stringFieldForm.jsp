<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="string"  class="field">
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
					<c:choose>
						<c:when test="${field.ifPassword}">
							<input id="${field.id}" type="password" name="fieldData" value="${field.defaultStringValue}" />
						</c:when>
						<c:otherwise>
							<input id="${field.id}" type="text" name="fieldData" value="${field.defaultStringValue}" />
						</c:otherwise>
					</c:choose>
					<input type="hidden" name="wasChanged" value="false"/>
					<input type="hidden" name="defaultValue" value="${field.defaultStringValue}"/>
					<input type="hidden" name="isPassword" value="${field.ifPassword}"/>
					<input type="hidden" name="fieldId" value="${field.id}"/>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>
