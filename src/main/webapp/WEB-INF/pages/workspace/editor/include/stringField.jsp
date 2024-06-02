<%@ include file="/common/taglibs.jsp"%>
<tr id="field_${field.id}" name="string" class="field">
  <td class="field-value">
    <div id="theString" style="position: relative;">
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
            <c:choose>
              <c:when test="${field.ifPassword}">
                <input class="inputField ${field.id}" id="${field.id}" size="80"
                  type="password" name="fieldData" value="${field.fieldData}"
                  oninput="checkAndMarkField(this)" />
              </c:when>
              <c:otherwise>
                <input class="inputField ${field.id}" id="${field.id}" size="80"
                  type="text" name="fieldData" value="${field.fieldData}" 
                  oninput="checkAndMarkField(this)" />
              </c:otherwise>
            </c:choose>
            <p class="plainTextField ${field.id} singleLineFieldInDocViewMode" id="plainText_${field.id}">
              ${field.fieldData}
            </p>
            <p class="lastModified">Last modified: <span>${field.modificationDateAsDate}</span></p>
            <input type="hidden" name="wasChanged" value="false" />
            <input type="hidden" name="defaultValue" value="${field.defaultStringValue}" />
            <input type="hidden" name="isPassword" value="${field.ifPassword}" />
            <input type="hidden" name="fieldId" value="${field.id}" />
          </td>
        </tr>
      </table>
    </div>
  </td>
</tr>
