<%@ include file="/common/taglibs.jsp" %>
<tr id="field_${field.id}" name="radio" class="field">
  <td class="field-value">
    <div id="theRadio" style="position:relative;">
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
          <td class="field-value-inner">
            <ul id="radioFieldsFinal" class="hideBulletPoint">
              <c:forEach var="radioItem" items="${field.radioOptionAsList}">
                <li class= 'radioLi ${field.id}'>
                  <input id="${field.id}" class="${field.id}" type="radio" name="fieldDefaultRadioFinal_${field.id}" 
                    value="${radioItem}" onchange="checkAndMarkField(this)" 
                   <c:if test="${field.fieldData eq radioItem}"> checked </c:if> >
                  <input type="hidden" name="fieldRadioFinal" value="${radioItem}"> ${radioItem}
                </li>
              </c:forEach>
              <input type="hidden" name="wasChanged" value="false"/>
              <input type="hidden" name="fieldId" value="${field.id}"/>
              <p class="checkboxText singleLineFieldInDocViewMode" id="radioText_${field.id}">
                ${field.fieldData}
              </p>
            </ul>
            <p class="lastModified">Last modified: <span>${field.modificationDateAsDate}</span></p>
          </td>
        </tr>
      </table>
    </div>
  </td>
</tr>
