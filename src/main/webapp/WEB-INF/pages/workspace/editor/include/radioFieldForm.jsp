<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="radio" class="field">
	<td name="fieldName" class="field-name">${field.name}
		<c:choose>
			<c:when test="${field.showAsPickList}">
				<span class="field-type-enum"> (Picklist)</span>
			</c:when>
			<c:otherwise>
				<span class="field-type-enum"> (${field.type.type})</span>
			</c:otherwise>
		</c:choose>
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
					<ul id="radioFieldsFinal">
						<c:forEach var="radioItem" items="${field.radioOptionAsList}">
		    				<li><input id="${field.id}" type="radio" name="fieldDefaultRadioFinal_${field.id}" value="${radioItem}" 
		    				 <c:if test="${field.defaultRadioOption eq radioItem}"> checked </c:if> > 
		    				 <input type="hidden" name="fieldRadioFinal" value="${radioItem}"> ${radioItem}</li>
						</c:forEach>
						<input type="hidden" name="wasChanged" value="false"/>
						<input type="hidden" name="fieldId" value="${field.id}"/>
					</ul>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>
