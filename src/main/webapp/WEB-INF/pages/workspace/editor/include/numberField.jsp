<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="number" class="field">
  <td class="field-value">
    <div id="theNumber" style="position:relative;">
      <table>
        <tr>
          <td name="fieldName" class="field-name" id="field-name-${field.id}" style="margin-left:10px;height:22px;">
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
            <input id="${field.id}" class="inputField ${field.id}" name="fieldData" type="text" 
              value="${field.fieldData}" onchange="checkAndMarkNumber(this)"/>
            <p class="plainTextField ${field.id} singleLineFieldInDocViewMode" id="plainText_${field.id}">
              ${field.fieldData}
            </p>
            <p class="lastModified">Last modified: <span>${field.modificationDateAsDate}</span></p>
            
            <input type="hidden" name="wasChanged" value="false"/>
            <input type="hidden" name="changed" value="false"/>
            <input type="hidden" name="lastValue" value="${field.fieldData}"/>
            <input type="hidden" name="defaultValue" value="${field.defaultNumberValue}"/>
            <input type="hidden" name="decimalPlaces" value="${field.decimalPlaces}"/>
            <input type="hidden" name="minValue" value="${field.minNumberValue}"/>
            <input type="hidden" name="maxValue" value="${field.maxNumberValue}"/>
            <input type="hidden" name="fieldId" value="${field.id}"/>
          </td>
         </tr>
      </table>
    </div>
  </td>
</tr>
