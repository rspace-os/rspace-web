<%@ include file="/common/taglibs.jsp"%>
<tr id="field_${field.id}" name="choice" class="field" data-multiple-choice="${field.multipleChoice}">
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
              <button id="edit_${field.id}" title="<spring:message code='common:actions.edit'/>" class="btn btn-primary btn-sm editButton" onclick="editFieldByClassChoice(${field.id})">
                <span class="glyphicon glyphicon-pencil"></span>
              </button>
              <button id="stopEdit_${field.id}" title="<spring:message code='form.field.saveAndViewTitle'/>" class="btn btn-info btn-sm stopEditButton" style="display: none">
                <span class="glyphicon glyphicon-floppy-disk"></span>
              </button>
            </div>
          </td>
        </tr>
        <tr>
          <td class="field-value-inner">
            <%-- Single fields that already store several values keep checkboxes so no value is lost --%>
            <c:set var="choiceInputType"
              value="${field.multipleChoice or fn:length(field.choiceOptionSelectedAsList) gt 1 ? 'checkbox' : 'radio'}" />
            <ul id="choiceFieldsFinal" class="hideBulletPoint">
              <c:forEach var="fieldChoice" items="${field.choiceOptionAsList}" varStatus="choiceStatus">
                <c:set var="isSelected" value="false" scope="page" />
                <c:forEach var="fieldSelected" items="${field.choiceOptionSelectedAsList}">
                  <c:if test="${fieldChoice eq fieldSelected}">
                    <c:set var="isSelected" value="true" scope="page" />
                  </c:if>
                </c:forEach>
                <li class='choiceLi ${field.id}'>
                  <label for="choice_${field.id}_${choiceStatus.index}" style="font-weight: normal;">
                  <input type="${choiceInputType}" class="${field.id}" id="choice_${field.id}_${choiceStatus.index}"
                    name="fieldSelectedChoicesFinal_${field.id}"
                    value="${fieldChoice}" onchange="checkAndMarkField(this, ${field.id})"
                    <c:if test="${isSelected eq 'true'}">checked</c:if>>
                  <input
                    type="hidden" name="fieldChoicesFinal_${field.id}"
                    value="${fieldChoice}">
                  ${fieldChoice}
                  </label>
                </li>
              </c:forEach>
              <input type="hidden" name="wasChanged" value="false" />
              <input type="hidden" name="fieldId" value="${field.id}" />
              <p class="checkboxText singleLineFieldInDocViewMode" id="choiceText_${field.id}">
                ${field.choiceOptionSelectedAsString}
              </p>
            </ul>
            <p class="lastModified"><spring:message code="form.field.lastModifiedLabel"/> <span>${field.modificationDateAsDate}</span></p>
          </td>
        </tr>
      </table>
    </div>
  </td>
</tr>
