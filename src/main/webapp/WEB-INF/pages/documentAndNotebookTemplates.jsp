<%@ include file="/common/taglibs.jsp" %>

<%--  Various content related to editing, that is shared between notebook
 and  structured documents that is common to both--%>

<!-- Dialog for comments this is here as this file is in common to both
 structuredDocument.jsp and notebookEditor.jsp, and comments are needed in both -->
<div id="comment-editor" style="display: none">
  <div class="divComment" id="" style="margin:4px;">
    <label>Enter a new comment:<br><br>
      <textarea id="commentText" class="newComment" style="width: 500px;"></textarea>
    </label>
  </div>
  <div class="comments" style="height: 300px; overflow:auto;margin:4px;">
    <table id="tablecomments" style="background-color:#EFEDED; margin-top:6px;"></table>
    </table>
  </div>
  <div id="comment-loading">
    <img src='/images/field-editor-load.gif' /><br />
  </div>
</div>

<div id="comment-view" style="display: none">
  <div id="comment-loadingView">
    <img src='/images/field-editor-load.gif' />
  </div>
  <div class="commentsView" style="overflow:auto;">
    <table id=tablecommentsView>
    </table>
  </div>
</div>

<div id="imageViewModeDivTemplate" style="display:none">
  <div class="imageViewModePanel">
    <div class="imagePanel">
    </div>
    <div class="imageData">
      <div class="imageFileNameDiv"></div>
      <div class="imageInfoBtnDiv"
           alt="Image Info"
           title="Image Info">
        <img class="infoImg" src="/images/info.svg" style="top:5px"/>
      </div>
    </div>
  </div>
</div>

<div id="chemImageViewModeDivTemplate" style="display:none">
  <div class="imageViewModePanel">
    <div class="imagePanel">
    </div>
    <div class="imageData">
      <div class="imageFileNameDiv"></div>
      <div class="imageInfoBtnDiv"
           alt="Chem Element Info"
           title="Chem Element Info">
        <img class="infoImg" src="/images/info.svg" style="top:5px"/>
      </div>
      <div class="chemImageNonEditableDiv"
           alt="Chemical Element Non-Editable"
           title="Chemical Element Non-Editable">
        <img class="nonEditableIcon" src="/images/icons/nonEditableIcon.png" style="top:5px"/>
      </div>
    </div>
  </div>
</div>

<div id="previewableAttachmentDivTemplate" style="display:none">
  <div class="attachmentPanel previewableAttachmentPanel">
    <button class='previewToggleBtn previewExpandBtn ignoreDblClick' type='button'>
      <img class="ignoreDblClick" src='/images/icons/mag_glass_plus.png' height="20px">
    </button>
    <button class='previewToggleBtn previewCollapseBtn' type='button' style="display:none">
      <img class="ignoreDblClick" src='/images/icons/mag_glass_minus.png' height="20px">
    </button>

    <div class="attachmentThumbnailPanel">
      <img class="attachmentIcon" width="76" height="76" />
      <div class="attachmentNameDiv">
        <label class="attachmentName">-</label>
        <img class="historicalVersionImg" src="/images/icons/versionSymbol.png"
             title="Historical version" style="display:none"/>
      </div>
    </div>
    <div class="inlineActionsPanel">
      <a href="#" class="inlineActionLink viewInMsOnlineLink" style="display:none">Open in Office</a>
      <a href="#" class="inlineActionLink viewActionLink">View</a>
      <a href="#" class="inlineActionLink downloadActionLink">Download</a>
      <a href="#" class="inlineActionLink infoActionLink">
        Info <img class="infoImg" src="/images/info.svg" style="top:6px"/>
      </a>
    </div>

    <div class="attachmentPreviewPanel"></div>

    <div class="attachmentPreviewInfoPanel">
      <div class="recordInfoPanel"></div>
    </div>
  </div>
</div>

<div id="onlineOfficeWithoutPreviewAttachmentDivTemplate" style="display:none">
  <div class="attachmentPanel">
    <div class="attachmentThumbnailPanel">
      <img class="attachmentIcon" width="76" height="76" />
      <div class="attachmentNameDiv">
        <label class="attachmentName">-</label>
        <img class="historicalVersionImg" src="/images/icons/versionSymbol.png"
             title="Historical version" style="display:none"/>
      </div>
    </div>
    <div class="inlineActionsPanel">
      <a href="#" class="inlineActionLink viewInMsOnlineLink" style="display:none">Open in Office</a>
      <a href="#" class="inlineActionLink downloadActionLink">Download</a>
      <a href="#" class="inlineActionLink infoActionLink">
        Info <img class="infoImg" src="/images/info.svg" style="top:6px"/>
      </a>
    </div>

    <div class="attachmentPreviewPanel"></div>

    <div class="attachmentPreviewInfoPanel">
      <div class="recordInfoPanel"></div>
    </div>
  </div>
</div>

<div id="nonPreviewableAttachmentDivTemplate" style="display:none">
  <div class="attachmentPanel">
    <div class="attachmentThumbnailPanel">
      <img class="attachmentIcon" width="32" height="32" />
      <div class="attachmentNameDiv">
        <label class="attachmentName">-</label>
        <img class="historicalVersionImg" src="/images/icons/versionSymbol.png"
             title="Historical version" style="display:none"/>
      </div>
    </div>
    <div class="inlineActionsPanel" style="margin-top: 3px;">
      <a href="#" class="inlineActionLink downloadActionLink">Download</a>
      <a href="#" class="inlineActionLink infoActionLink">
        Info <img class="infoImg" src="/images/info.svg" style="top:6px"/>
      </a>
    </div>
  </div>
</div>

<div id="snapGeneTemplate" style="display:none">
  <div class="attachmentPanel snapGenePanel">
    <div class="attachmentThumbnailPanel">
      <img class="attachmentIcon" width="32" height="32" />
      <label class="attachmentName">-</label>
    </div>
    <div class="inlineActionsPanel" style="margin-top: 3px;">
      <a href="#" class="inlineActionLink previewActionLink hidden">View</a>
      <a href="#" class="inlineActionLink downloadActionLink">Download</a>
      <a href="#" class="inlineActionLink infoActionLink">
        Info <img class="infoImg" src="/images/info.svg" style="top:6px"/></a>
    </div>
  </div>
</div>

<script id="newRecordHeaderTemplate" type="text/mustache">
  <div class="bootstrap-custom-flat">
    <div class="row">
        <div class="col-md-2">
            <div id="recordNameDiv" class="rs-record-name">
                <form id="inlineRenameRecordForm" class="info-panel">
                    <label for="recordNameInHeaderEditor">Name:</label>
                    <span id="recordNameInHeader" title="Click to edit the name">{{name}}</span>
                    <textarea type="text" name="recordNameInHeader" id="recordNameInHeaderEditor" class="dynamicWidthInputField" placeholder="{{name}}" readonly="true" rows="1" maxlength="255" style="display: none;"></textarea>
                    <button id="renameRecordEdit" alt="Rename" title="Edit name" class="bootstrap-custom-flat" style="display: none;">
                        <span class="glyphicon glyphicon-pencil"></span>
                    </button>
                    <button type="submit" alt="Rename" title="Rename" id="renameRecordSubmit" class="bootstrap-custom-flat" style="display: none;" title="Save name">
                        <span class="glyphicon glyphicon-floppy-disk"></span>
                    </button>
                </form>
            </div>
        </div>
        <%-- Stops chrome inserting usernames into fields of type=text (on this page that is the tagging autocomplete input)       --%>
        <input type="text" id="username" style="width:0;height:0;visibility:hidden;position:absolute;left:0;top:0" />
        <input type="password" style="width:0;height:0;visibility:hidden;position:absolute;left:0;top:0" />
        <div class="col-md-8">
            <div class="rs-record-tags">
                <div>Tags:</div>
                <button id="saveTags" class="bootstrap-custom-flat" style="display: none;" title="Save tags">
                    <span class="glyphicon glyphicon-floppy-disk"></span>
                </button>
                <ul id="notebookTags" title="Click to edit the tags"></ul>
                <button id="editTags" class="bootstrap-custom-flat" style="display: none;" title="Edit tags">
                    <span class="glyphicon glyphicon-pencil"></span>
                </button>
            </div>
        </div>
        <div class="col-md-2">
            <div class="rs-global-id">
                <span>Unique ID:</span>
                <a href="/globalId/{{globalId}}">{{globalId}}</a>
                <a href="#"
                   class="recordInfoIcon"
                   data-recordid="{{recordId}}"
                   data-versionid="{{versionId}}"
                   alt="Record Info"
                   title="Record Info">
                   <img class="infoImg" src="/images/info.svg" style="top:-1px"/>
                </a>
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-md-12">
            <label class="displayRevisionsContainer">
                <input type="checkbox" class="displayRevisions"> Show last modified date
            </label>
        </div>
    </div>

</div>

</script>

<div id="boxVersionedLinkInfoPanelTemplate" style="display: none">
  <div class="recordInfoPanel">
    <table class="boxInfoPanelTable">
      <tr>
        <td class="labelCell">Name: </td>
        <td class="boxInfoPanel-name"></td>
      </tr>
      <tr>
        <td class="labelCell">Description: </td>
        <td class="boxInfoPanel-description"></td>
      </tr>
      <tr>
        <td class="labelCell">Latest Version: </td>
        <td><a class="boxInfoPanel-sharedLinkUrl" href="#" target="_blank"></a></td>
      </tr>
      <tr>
        <td class="labelCell">Owner: </td>
        <td class="boxInfoPanel-owner"></td>
      </tr>
      <tr>
        <td class="labelCell">Created At: </td>
        <td class="boxInfoPanel-createdAt"></td>
      </tr>
      <tr>
        <td class="labelCell">Version: </td>
        <td>
          <span class="boxInfoPanel-versionNumber boxVersion"></span>
          <button type='button' class='boxFileDownloadBtn'>Download</button>
        </td>
      </tr>
      <tr>
        <td>&nbsp;</td>
      </tr>
      <tr>
        <td class="labelCell">File Id: </td>
        <td class="boxInfoPanel-id" href="#"></td>
      </tr>
      <tr>
        <td class="labelCell">Version Id: </td>
        <td class="boxInfoPanel-versionID" href="#"></td>
      </tr>
      <tr>
        <td class="labelCell">Fingerprint: </td>
        <td class="boxInfoPanel-sha1"></td>
      </tr>
      <tr>
        <td class="labelCell">File size: </td>
        <td class="boxInfoPanel-size"></td>
      </tr>
    </table>
  </div>
</div>

<div id="externalLinkedRecordInfoDialog" style="display: none">
  <div class="recordInfoPanel">
    <div class="externalLinkedRecordNoticeDiv">
      <img src="/images/notice.png" style="height: 14px; vertical-align: text-top;" />
      This link points to a different RSpace server</div>
    <table class="infoPanelTable">
      <tr>
        <td class="infoLabelCell">Name: </td>
        <td class="infoPanel-name"></td>
      </tr>
      <tr>
        <td class="infoLabelCell">Global ID: </td>
        <td class="infoPanel-globalId"></td>
      </tr>
      <tr>
        <td class="infoLabelCell">Server: </td>
        <td class="infoPanel-server"></td>
      </tr>
      <tr class="infoPanelObjectIdRow">
        <td class="infoLabelCell">Link: </td>
        <td><a class="infoPanel-internalLinkHref" href="#"></a></td>
      </tr>
    </table>
  </div>
</div>

<div id="chemInfoDialog" style="display: none">
  <div class="recordInfoPanel"></div>
</div>

<div id="chemAttachmentDivTemplate" style="display:none">
  <div class="attachmentPanel" style="display:inline-flex;">
    <div class="attachmentThumbnailPanel" style="align-self: center;"></div>
    <div class="inlineActionsPanel" style="display: flex; align-items: center;">
      <div class="recordInfoPanel"></div>
    </div>
  </div>
</div>

<axt:export />
<axt:shareDlg shareDlgGroups="${groups}" shareDlgUsers="${uniqueUsers}" />

<jsp:include page="recordInfoPanel.jsp" />

<!-- React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<script src="<c:url value='/ui/dist/exportModal.js'/>"></script>
<script src="<c:url value='/ui/dist/previewInfo.js'/>"></script>
<script src="<c:url value='/ui/dist/snapGeneDialog.js'/>"></script>
<script src="<c:url value='/scripts/tinymceDialogUtils.js'/>"></script>
<!--End React Scripts -->
