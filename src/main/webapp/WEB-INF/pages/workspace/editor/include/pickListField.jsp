<%@ include file="/common/taglibs.jsp" %>
<link href="/scripts/bower_components/select2/dist/css/select2.min.css" rel="stylesheet" />
<script src="/scripts/bower_components/select2/dist/js/select2.min.js"/>></script>
<script>
	$(document).ready(function() {
		const fielID =${field.id};
		$("#"+fielID).select2();
	});
</script>
<tr id="field_${field.id}" name="picklist" class="field">
	<td class="field-value">
		<div id="theRadio" style="position:relative;">
			<table>
				<tr>
					<td name="fieldName" class="field-name" id="field-name-${field.id}" style="margin-left:10px;height:22px;">
						<div class="fieldHeaderName">${field.name}</div>
						<span class="fieldNotification"></span>
						<div class="fieldHeaderEditButton bootstrap-custom-flat">
							<button id="edit_${field.id}" title="Edit" class="btn btn-primary btn-sm editButton" onclick="editFieldByClassRadio(${field.id})">
								<span class="glyphicon glyphicon-pencil"></span>
							</button>
							<button id="stopEdit_${field.id}" title="Save and View" class="btn btn-info btn-sm stopEditButton" style="display: none">
								<span class="glyphicon glyphicon-floppy-disk"></span>
							</button>
						</div>
					</td>
				</tr>
				<tr>
					<td class="field-value-inner" style="font-size: 100%">
						<select id="${field.id}" class="formPickList ${field.id}" style="width: 80%" name="items" onchange="checkAndMarkField(this)" >
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
						<p class="lastModified">Last modified: <span>${field.modificationDateAsDate}</span></p>
					</td>
				</tr>
			</table>
		</div>
	</td>
</tr>