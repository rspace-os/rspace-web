<%@ include file="/common/taglibs.jsp"%>
<tr id="field_${field.id}" name="choice" class="field">
  <td class="field-value">
    <div id="theChoice" style="position: relative;">
      <table>
        <tr>
          <td name="fieldName" class="field-name" id="field-name-${field.id}" style="margin-left: 10px; height: 22px;">
            <div class="fieldHeaderName">
              ${field.name}
              <c:if test="${field.mandatory eq true}">
                <span style="color: red">*</span>
              </c:if>
            </div>
            <span class="fieldNotification"></span>
            <div class="fieldHeaderEditButton bootstrap-custom-flat">
              <button id="edit_${field.id}" title="Edit" class="btn btn-primary btn-sm editButton" onclick="editFieldByClassChoice(${field.id})">
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
            <ul id="choiceFieldsFinal" class="hideBulletPoint">
              <c:forEach var="fieldChoice" items="${field.choiceOptionAsList}">
                <c:set var="isSelected" value="false" scope="page" />
                <c:forEach var="fieldSelected" items="${field.choiceOptionSelectedAsList}">
                  <c:if test="${fieldChoice eq fieldSelected}">
                    <c:set var="isSelected" value="true" scope="page" />
                  </c:if>
                </c:forEach>
                <li class='choiceLi ${field.id}'>
                  <input type="checkbox" class="${field.id}" id="${field.id}"
                    name="fieldSelectedChoicesFinal_${field.id}"
                    value="${fieldChoice}" onchange="checkAndMarkField(this)"
                    <c:if test="${isSelected eq 'true'}">checked</c:if>>
                  <input
                    type="hidden" name="fieldChoicesFinal_${field.id}"
                    value="${fieldChoice}">
                  ${fieldChoice}
                </li>
              </c:forEach>
              <input type="hidden" name="wasChanged" value="false" />
              <input type="hidden" name="fieldId" value="${field.id}" />
              <p class="checkboxText singleLineFieldInDocViewMode" id="choiceText_${field.id}">
                ${field.choiceOptionSelectedAsString}
              </p>
            </ul>
            <p class="lastModified">Last modified: <span>${field.modificationDateAsDate}</span></p>
          </td>
        </tr>
      </table>
    </div>
  </td>
</tr>
