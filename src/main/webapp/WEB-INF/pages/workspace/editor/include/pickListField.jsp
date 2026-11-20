<%@ include file="/common/taglibs.jsp" %>
<link href="<rst:assetUrl value='/scripts/bower_components/select2/dist/css/select2.min.css'/>" rel="stylesheet" />
<script src="<rst:assetUrl value='/scripts/bower_components/select2/dist/js/select2.min.js' />"></script>
<script>
	$(document).ready(function() {
		const fielID =${field.id};
		$("#"+fielID).select2();
	});
</script>
<tr id="field_${field.id}" name="picklist" class="field"<%@ include file="fieldDisplayAttributes.jspf" %>>
	<td class="field-value">
		<div id="theRadio" style="position:relative;">
			<table>
				<tr>
					<td name="fieldName" class="field-name" id="field-name-${field.id}" style="margin-left:10px;height:22px;">
						<div class="fieldHeaderName">${field.name}</div>
						<span class="fieldNotification"></span>
						<div class="fieldHeaderEditButton bootstrap-custom-flat">
							<button id="edit_${field.id}" title="<spring:message code='common:actions.edit'/>" class="btn btn-primary btn-sm editButton" onclick="editFieldByClassRadio(${field.id})">
								<span class="glyphicon glyphicon-pencil"></span>
							</button>
							<button id="stopEdit_${field.id}" title="<spring:message code='form.field.saveAndViewTitle'/>" class="btn btn-info btn-sm stopEditButton" style="display: none">
								<span class="glyphicon glyphicon-floppy-disk"></span>
							</button>
						</div>
					</td>
				</tr>
				<tr>
					<td class="field-value-inner" style="font-size: 100%">
						<%-- Without data the select shows an empty placeholder, not the first option: the shown value matches the stored one, and picking the first option fires a change. select2 reads data-placeholder and leaves the placeholder out of the dropdown. --%>
						<select id="${field.id}" class="formPickList ${field.id}" style="width: 80%" name="items" onchange="checkAndMarkField(this)"<c:if test="${empty field.fieldData}"> data-placeholder="<spring:message code='form.field.picklistPlaceholder' htmlEscape='true'/>"</c:if>>
							<c:if test="${empty field.fieldData}"><option value="" selected></option></c:if>
							<c:forEach var="radioItem" items="${field.radioOptionAsList}">
								<option class= 'radioLi ${field.id}'
								value="${radioItem}"
								<c:if test="${field.fieldData eq radioItem}"> selected </c:if>
								<input type="hidden" name="fieldRadioFinal" value="${radioItem}">
								${radioItem}</option>
							</c:forEach>
						</select>
						<input type="hidden" name="wasChanged" value="false"/>
						<input type="hidden" name="fieldId" value="${field.id}"/>
						<p class="checkboxText singleLineFieldInDocViewMode" id="radioText_${field.id}">
							${field.fieldData}
						</p>
						<p class="lastModified"><spring:message code="form.field.lastModifiedLabel"/> <span>${field.modificationDateAsDate}</span></p>
					</td>
				</tr>
			</table>
		</div>
	</td>
</tr>