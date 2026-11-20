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
					<c:set var="choiceInputType"
					  value="${field.multipleChoice or fn:length(field.defaultChoiceOptionAsList) gt 1 ? 'checkbox' : 'radio'}" />
					<ul id="choiceFieldsFinal">
				<c:forEach var="fieldChoice" items="${field.choiceOptionAsList}" varStatus="choiceStatus">
					<c:set var="isSelected" value="false" scope="page" />
						<c:forEach var="defaultChoiceOption" items="${field.defaultChoiceOptionAsList}">
							<c:if test="${fieldChoice eq defaultChoiceOption}" >
								<c:set var="isSelected" value="true" scope="page" />
							</c:if>
						</c:forEach>
					<li><label for="choice_${field.id}_${choiceStatus.index}" style="font-weight: normal;">
					<input type="${choiceInputType}" id="choice_${field.id}_${choiceStatus.index}"
					    name="fieldSelectedChoicesFinal_${field.id}" value="${fieldChoice}" 
					<c:if test="${isSelected eq 'true'}">checked</c:if>> 
					${fieldChoice}</label></li>
					
				</c:forEach>
				<input type="hidden" name="wasChanged" value="false"/>
				<input type="hidden" name="fieldId" value="${field.id}"/>
				</ul>
	      		</td>
	       	</tr>
       	</table>
	</td>
</tr>


