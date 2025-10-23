<%@ include file="/common/taglibs.jsp" %>

<c:set var="colspan" value="2"></c:set>

<tr id="field_${field.id}" name="text" class="field">
  <td class="field-value">
  <div id="theChoice"  class="choiceClass" style="position:relative;">
    <input type="file" multiple class="fromLocalComputer" id="fromLocalComputerToGallery_${field.id}" style="display:none" aria-label="Insert file to Gallery from local computer"/>
    <table>
      <tr>
        <td class="field-name" id="field-name-${field.id}" style="margin-left:10px;height:22px;">
          <c:if test="${structuredDocument.basicDocument ne true and structuredDocument.form.name ne 'Basic Document'}">
            <div class="fieldHeaderName"> 
                ${field.name}
              <c:if test="${field.mandatory eq true}">
                <span style="color: red">*</span>
              </c:if>
            </div>
          </c:if>
          
          <div class="fieldHeaderEditButton bootstrap-custom-flat">
            <button id="edit_${field.id}" title="Edit" class="btn btn-primary btn-sm editButton" onclick="editTextField(${field.id})">
              <span class="glyphicon glyphicon-pencil"></span>
            </button>
            <button id="stopEdit_${field.id}" title="Save and View" class="btn btn-info btn-sm stopEditButton" style="display: none">
              <span class="glyphicon glyphicon-floppy-disk"></span>
            </button>
          </div> 
            <c:if test="${inventoryAvailable eq 'true'}">
              <div class="invMaterialsListing_new" data-field-id="${field.id}" data-document-id=${structuredDocument.id}></div>
            </c:if>
          <div class="bootstrap-custom-flat">
            <button style="display:none;float: right; margin-right: 8px; "    class="btn btn-default" id="jupyter_notebooks_button_${field.id}" onclick="window.dispatchEvent(new CustomEvent('jupyter_viewer_click',{detail:{id: ${field.id}}}))">
              Open Jupyter Notebook
            </button>
          </div>
          <span>
            <div  style="display:none; max-width:950px" class="jupyter_notebooks_contents" data-field-id="${field.id}" data-document-id=${structuredDocument.id}></div>
           </span>
          <div class="fieldNotification"></div>
          <div class="fieldHint"></div>
        </td>
      </tr>
      <tr>
        <div id="mobilePhoto_rtf_${field.id}" class="mobileTakePicture hidden-print">
          <label for="fromLocalComputer_${field.id}">
            <img src="/images/icons/camera.png">
          </label>
          <input 
            type="file" 
            name="image" 
            accept="image/*" 
            capture="environment" 
            class="fromLocalComputer" 
            id="fromLocalComputer_${field.id}" 
            style="display:none"
            aria-label="Insert media from your device"
          >
        </div>
        <td class="field-value-inner">
          <c:if test="${galaxyEnabled eq 'true'}"> <!--TODO add more conditions as and when we integrate with other external workflows -->
            <div class="ext-workflows-textfield" data-field-id="${field.id}" data-document-id=${structuredDocument.id}></div>
          </c:if>
          <textarea
            id="rtf_${field.id}" 
            name="fieldRtfData" 
            aria-label="Document editor" 
            data-isDirty='false' 
            class="tinymce"
          >
            ${field.fieldData}
          </textarea>
          <input type="hidden" name="fieldId" value="${field.id}" aria-label="Hidden input" />
        </td>
      </tr>   
    </table>
    
    <p class="lastModified textFieldLastModified">Last modified: <span>${field.modificationDateAsDate}</span></p>
    <c:if test="${inventoryAvailable eq 'true'}">
         <div class="invMaterialsListing" data-field-id="${field.id}" data-document-id=${structuredDocument.id}></div>
    </c:if>
  </div>
  </td>
</tr>
