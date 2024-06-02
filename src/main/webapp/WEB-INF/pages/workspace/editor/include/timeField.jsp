<%@ include file="/common/taglibs.jsp"%>
<tr id="field_${field.id}" name="time" class="field">
  <td class="field-value">
    <div id="theTime" style="position: relative;">
      <table>
        <tr>
          <td name="fieldName" class="field-name" id="field-name-${field.id}">
            <div class="fieldHeaderName">
              ${field.name}
              <c:if test="${field.mandatory eq true}">
                <span style="color: red">*</span>
              </c:if>
            </div>
            <span class="fieldNotification"></span>
            <div class="fieldHeaderEditButton bootstrap-custom-flat">
              <button id="edit_${field.id}" title="Edit" class="btn btn-primary btn-sm editButton" onclick="editInputField(${field.id})">
                <span class="glyphicon glyphicon-pencil"></span>
              </button>
              <button id="stopEdit_${field.id}" title="Save and View" class="btn btn-info btn-sm stopEditButton" style="display: none">
                <span class="glyphicon glyphicon-floppy-disk"></span>
              </button>
            </div>
          </td>
        </tr>
        <tr>
          <td class="field-value-inner">
            <input id="${field.id}" name="timeField_${field.id}" type="text" class="timepicker inputField ${field.id}"
              onchange="checkAndMarkField(this)" value="${field.fieldData}" />
            <p class="plainTextField ${field.id} singleLineFieldInDocViewMode" id="plainText_${field.id}">
              ${field.fieldData}
            </p>
            <p class="lastModified">Last modified: <span>${field.modificationDateAsDate}</span></p>
            <input type="hidden" name="defaultTime" value="${field.defaultTimeAsString}" /> 
            <input type="hidden" name="format_${field.id}" value="${field.timeFormat}" /> 
            <input type="hidden" name="minTime" value="${field.minTime}" /> 
            <input type="hidden" name="maxTime" value="${field.maxTime}" /> 
            <input type="hidden" name="minHour" value="${field.minHour}" /> 
            <input type="hidden" name="minMinutes" value="${field.minMinutes}" /> 
            <input type="hidden" name="maxHour" value="${field.maxHour}" /> 
            <input type="hidden" name="maxMinutes" value="${field.maxMinutes}" /> 
            <input type="hidden" name="wasChanged" value="false" /> 
            <input type="hidden" name="fieldId" value="${field.id}" />
          </td>
        </tr>
      </table>
    </div>
  </td>
</tr>

