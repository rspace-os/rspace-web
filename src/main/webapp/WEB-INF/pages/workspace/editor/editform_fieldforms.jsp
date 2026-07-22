<%-- 
	This page renders the form input pages for each field type. 
	
	It determines what to render based on the type of field.
	It will populate a field if a field position is provided as a param.

--%>

<%@ include file="/common/taglibs.jsp" %>
<%@ taglib prefix="f" uri="http://researchspace.com/functions" %>


<fieldset class="form_field">


<legend><spring:message code="form.fieldEditor.title"/></legend>
<table id="field-edit-table"  style="overflow-wrap: break-word; width: 100%; table-layout: fixed;">

<tr>
<th style="width:50%;">
	<label for="field_name"><spring:message code="workspace.list.name.header"/></label></th>

<th style="width:50%">
	<input type="text" name="fieldName" id="field_name" class="required" value="${fieldTemplate.name}"/>
	<input type="hidden" name="EditfieldId" id="fieldFormId" value="${fieldTemplate.id}" >
	<input type="hidden" name="EditfieldType" id="fieldFormType" value="${fieldTemplate.type.type}" >
</th>
</tr>

<c:choose>
	<c:when test='${fieldTemplate.type.type eq "Number"}'>
	
		<tr>
			<td>
				<label for="field_decimalplaces"><spring:message code="form.fieldEditor.decimalPlaces"/></label>
			</td>
			<td>
				<input type="text" name="fieldDecimalPlaces" id="field_decimalplaces" value="${fieldTemplate.decimalPlaces}"/>
			</td>
		</tr>
		<tr>
			<td>
				<label for="field_defaultvalue"><spring:message code="form.fieldEditor.defaultValue"/></label>
			</td>
			<td>
				<input type="text" name="fieldDefaultValue" id="field_defaultvalue" value="${fieldTemplate.defaultNumberValue}" />
			</td>
		</tr>

		<tr>
			<td>
				<label for="field_minvalue"><spring:message code="form.fieldEditor.minValue"/></label>
			</td>
			<td>
				<input type="text" name="fieldMinValue" id="field_minvalue" value="${fieldTemplate.minNumberValue}" />
			</td>
		</tr>

		<tr>
			<td>
				<label for="field_maxvalue"><spring:message code="form.fieldEditor.maxValue"/></label>
			</td>
			<td>
				<input type="text" name="fieldMaxValue" id="field_maxvalue" value="${fieldTemplate.maxNumberValue}" />
			</td>
		</tr>
		

	</c:when>
	<c:when test='${fieldTemplate.type.type eq "String"}'>
		<tr>
			<td>
				<label for="field_defaultvaluestring"><spring:message code="form.fieldEditor.defaultValue"/></label>
			</td>
			<td>
				<input type="text" name="fieldDefaultValue" id="field_defaultvaluestring" value="${fieldTemplate.defaultStringValue}" />
			</td>
		</tr>

		<tr>
			<td>
				<label><spring:message code="form.fieldEditor.isPassword"/> </label>
			</td>
			<td>
				<input type="radio" name="fieldIfPassword" value="yes" <c:if test="${fieldTemplate.ifPassword eq true}"> checked </c:if> > <spring:message code="common:actions.yes"/><br>
				<input type="radio" name="fieldIfPassword" value="no" <c:if test="${fieldTemplate.ifPassword eq false}"> checked </c:if> > <spring:message code="common:actions.no"/><br>
			</td>
		</tr>
	
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "Text"}'>
		<tr>
			<td>
				<label for="field_defaultvaluestring"><spring:message code="form.fieldEditor.defaultValue"/></label>
			</td>
			<td>
				<textarea id="field_defaultvaluestring" name="fieldDefaultValue" class="tempform" rows="4" cols="50">${fieldTemplate.defaultValue}</textarea>
			</td>
		</tr>
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "Radio"}'>
		<tr><td>
			<label for="alphabeticSortChoice"> <spring:message code="form.fieldEditor.sortAlphabetically"/> </label>

			<input  ${fieldTemplate.sortAlphabetic == true ?"checked":""} type="checkbox" id="alphabeticSortChoice" name="alphabeticSortChoice" value="${fieldTemplate.sortAlphabetic}"/></td>
			<td>
				<label for="radioAsPickListChoice"> <spring:message code="form.fieldEditor.displayAsPicklist"/> </label>
				<input ${fieldTemplate.showAsPickList == true ?"checked":""}  type="checkbox" id="radioAsPickListChoice" name="showAsPickList" value="${fieldTemplate.showAsPickList}"/>
			</td></tr>
		<tr><td><spring:message code="form.fieldEditor.dragToSortHint"/></td><td></td></tr>
		<tr>
		<tr>
			<td>
				<label for="addRadioName"> <spring:message code="form.fieldEditor.typeAndClickAddNew"/></label>
			</td>
			<td>

				<input type="text" id="addRadioName"/>
				<button type="button" class="btn btn-default" id="addRadio"> <spring:message code="form.fieldEditor.addNew"/></button>
			</td>
		</tr>
		<tr>
		<spring:message code="form.fieldEditor.uploadFileInfo" var="uploadFileInfoText"/>
		<spring:message code="common:help.formsRadiosAndPicklists" var="formsHelpSlug"/>
		<td>
			<label for="uploadRadioChooser"> <spring:message code="form.fieldEditor.orUploadFromFile"/>
				<a rel="noreferrer" href="${f:helpDocsUrl(formsHelpSlug)}" target="_blank">
				<img class="infoImg" src="/images/info.svg" alt="${uploadFileInfoText}"
																		 title="${uploadFileInfoText}"style="top:5px"/></a></label>
		</td>
			<td>
			<input name="file" type="file" id="uploadRadioChooser" accept=".txt,.csv"/>
		</td>
		</tr>
		<tr>
			<td>
				<label for="uploadRadioChooser"> <spring:message code="form.fieldEditor.readDataFromUploaded"/> </label>

			</td>
			<td>
				<button type="button" class="btn btn-default" id="uploadRadioButton" disabled="disabled"> <spring:message code="form.fieldEditor.readFile"/></button>
			</td>
		</tr>
		<tr>
			<td >
				<ul id="radioFields">
					<c:forEach var="fieldRadio" items="${fieldTemplate.radioOptionAsList}">
						<li style="white-space: normal;cursor: pointer"><input type="radio" name="fieldDefaultRadio" value="${fieldRadio}"
						<c:if test="${fieldTemplate.defaultRadioOption eq fieldRadio}"> checked </c:if> >
							<input type="hidden" name="fieldRadios" value="${fieldRadio}">
								${fieldRadio}
							<a href="#" style="font-weight:bold;" class="deleteRadio" id='del_radio_${fieldRadio}'><spring:message code="form.fieldEditor.deleteOption"/> </a>
						</li>
					</c:forEach>
				</ul>
			</td>
			<td></td>
		</tr>
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "Choice"}'>
		<tr>
			<td>
				<label><spring:message code="form.fieldEditor.multiple"/></label>
			</td>
			<td>
				<input type="radio" name="fieldMultipleAllowed" value="yes" <c:if test="${ true eq multipleChoice}"> checked </c:if> > <spring:message code="form.fieldEditor.multipleAllowedYes"/> <br/>
				<input type="radio" name="fieldMultipleAllowed" value="no" <c:if test="${ false eq multipleChoice}"> checked </c:if> > <spring:message code="form.fieldEditor.multipleAllowedNo"/>
			</td>
		</tr>
		<tr>
			<td>
			</td>
			<td>
				<input type="text" id="addChoiceName"/>
				<span class="bootstrap-custom-flat">
				    <button type="button" class="btn btn-default" id="addChoice"><spring:message code="form.fieldEditor.addNew"/></button>
				</span>
				<ul id="choiceFields">
				<c:forEach var="fieldChoice" items="${fieldTemplate.choiceOptionAsList}">
					<c:set var="isSelected" value="false" scope="page" />
						<c:forEach var="fieldSelected" items="${fieldTemplate.defaultChoiceOptionAsList}">
							<c:if test="${fieldChoice eq fieldSelected}" >
								<c:set var="isSelected" value="true" scope="page" />
							</c:if>
						</c:forEach>
					<li><input type="checkbox" name="fieldSelectedChoices" value="${fieldChoice}" <c:if test="${isSelected eq 'true'}">checked</c:if>> <input type="hidden" name="fieldChoices" value="${fieldChoice}"> ${fieldChoice} <a href="#" style="font-weight:bold;" class="deleteChoice"><spring:message code="form.fieldEditor.deleteOption"/></a></li>
				</c:forEach>
				</ul>
			</td>
		</tr>
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "Date"}'>
		<tr>
			<td>
				<label><spring:message code="form.fieldEditor.dateFormat"/></label>
			</td>
			<td>
			<c:set var="dateFormats" value="dd/MM/yyyy,dd MM yyyy,dd-MM-yyyy,dd MMM yyyy,yyyy/MM/dd,yyyy MM dd,yyyy-MM-dd,yyyy MMM dd" />

				<select id="field_dateformat" name="fieldDateFormat">
				<c:forEach var="dateFormat" items="${dateFormats}">
					<option value="${dateFormat}" <c:if test="${dateFormat eq fieldTemplate.format}"> SELECTED </c:if>>
					   ${dateFormat}
					</option>
				</c:forEach>
				</select>
			</td>
		</tr>
		<tr>
			<td>
				<label><spring:message code="form.fieldEditor.defaultDate"/></label>
			</td>
			<td>
				<input type="text" class ="datepickerAddNew" name="fieldDefaultDate" id="field_defaultdate" value="${fieldTemplate.defaultDateAsString}"/>
				<input type="hidden" class ="datepickerAddNew" name="fieldHiddenDefaultDate" id="fieldHidden_defaultdate" value="${fieldTemplate.defaultDate}"/>
			</td>
		</tr>
		<tr>
			<td>
				<label><spring:message code="form.fieldEditor.minValue"/></label>
			</td>
			<td>
				<input type="text" class ="datepickerAddNew" name="fieldMinValue" id="field_minvalue"  value="${fieldTemplate.minDateAsString}" />
				<input type="hidden" class ="datepickerAddNew" name="fieldHiddenMinValue" id="fieldHidden_minvalue" value="${fieldTemplate.minValue}"/>
			</td>
		</tr>
		<tr>
			<td>
				<label><spring:message code="form.fieldEditor.maxValue"/></label>
			</td>
			<td>
				<input type="text" class ="datepickerAddNew" name="fieldMaxValue" id="field_maxvalue"  value="${fieldTemplate.maxDateAsString}" />
				<input type="hidden" class ="datepickerAddNew" name="fieldHiddenMaxValue" id="fieldHidden_maxvalue" value="${fieldTemplate.maxValue}"/>
			</td>
		</tr>
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "Time"}'>

	<tr>
			<td>
				<label><spring:message code="form.fieldEditor.timeFormat"/></label>
			</td>
			<td>
				<select id="field_timeFormat" name="fieldTimeFormat">
					<option value="hh:mm a" <c:if test="${fieldTemplate.timeFormat eq 'hh:mm a'}">selected</c:if>><spring:message code="form.fieldEditor.time12Hour"/></option>
					<option value="HH:mm" <c:if test="${fieldTemplate.timeFormat eq 'HH:mm'}">selected</c:if>><spring:message code="form.fieldEditor.time24Hour"/></option>
				</select>
			</td>
	</tr>
	<tr>
		<td>
			<label><spring:message code="form.fieldEditor.defaultTime"/></label>
		</td>
		<td>
			<input type="text" class="timePickerForm" name="fieldDefaultTime" id="field_defaulttime" value="${fieldTemplate.defaultTimeAsString}"/>
		</td>
	</tr>
	<tr>
		<td>
			<label><spring:message code="form.fieldEditor.minValue"/></label>
		</td>
		<td>
			<input type="text" class="timePickerForm" name="fieldMinValue" id="field_minvalue" value="${fieldTemplate.minTimeAsString}"/>
		</td>
	</tr>
	<tr>
		<td>
			<label><spring:message code="form.fieldEditor.maxValue"/></label>
		</td>
		<td>
			<input type="text" class="timePickerForm" name="fieldMaxValue" id="field_maxvalue" value="${fieldTemplate.maxTimeAsString}"/>
		</td>
	</tr>
	
	
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "reference"}'>
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "checkbox"}'>
		<tr>
			<td>
				<label><spring:message code="form.fieldEditor.checked"/></label>
			</td>
			<td>
				<input type="radio" name="fieldChecked" value="true" <c:if test="${fieldTemplate.fieldChecked eq 'true'}"> checked </c:if> > <spring:message code="form.fieldEditor.checkedValue"/><br>
				<input type="radio" name="fieldChecked" value="false" <c:if test="${fieldTemplate.fieldChecked eq 'false'}"> checked </c:if> > <spring:message code="form.fieldEditor.uncheckedValue"/><br>
			</td>
		</tr>
	</c:when>
	<c:when test='${fieldTemplate.type.type eq "attachment"}'>
    </c:when>
    <c:when test='${fieldTemplate.type.type eq "resource"}'>
    </c:when>
	<c:otherwise>
		<b style="color:red"><spring:message code="form.fieldEditor.noEditorAssociated"/></b>
	</c:otherwise>
</c:choose>

  <tr>
    <td>
      <label><spring:message code="form.fieldEditor.required"/></label>
      <input  ${fieldTemplate.mandatory == true ?"checked":""} type="checkbox" id="mandatoryCheckbox" name="mandatoryCheckbox" value="${fieldTemplate.mandatory}"/></td>
    </td>
  </tr>

</table>
	</fieldset>
