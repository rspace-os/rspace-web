<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="choice"  class="field">
	<td name="fieldName" class="field-name">
		${field.name}
		<span class="field-type-enum">(${field.type.type})</span>
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
					<ul id="choiceFieldsFinal">
				<c:forEach var="fieldChoice" items="${field.choiceOptionAsList}">
					<c:set var="isSelected" value="false" scope="page" />
						<c:forEach var="defaultChoiceOption" items="${field.defaultChoiceOptionAsList}">
							<c:if test="${fieldChoice eq defaultChoiceOption}" >
								<c:set var="isSelected" value="true" scope="page" />
							</c:if>
						</c:forEach>
					<li><input type="checkbox" id="${field.id}" 
					    name="fieldSelectedChoicesFinal_${field.id}" value="${fieldChoice}" 
					<c:if test="${isSelected eq 'true'}">checked</c:if>> 
					${fieldChoice}</li>
					
				</c:forEach>
				<input type="hidden" name="wasChanged" value="false"/>
				<input type="hidden" name="fieldId" value="${field.id}"/>
				</ul>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>


