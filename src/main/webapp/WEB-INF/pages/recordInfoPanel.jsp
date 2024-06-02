<%@ include file="/common/taglibs.jsp" %>

<script src="<c:url value ='/scripts/pages/recordInfoPanel.js'/>"></script>
<script id="linkedRecordsTemplate" type="text/x-mustache-template">

  <div class="linkedRecordsForAttachmentsDiv">
 <!--{{^isEmpty}} -->
This file is referenced by:
<table class="linkedRecordsForAttachmentsTable" width="100%">
            <tr class="linkedRecordRow"><th>ID</th><th>Name</th></tr>
            <!--{{#items}} -->
            <tr><td><a href="/globalId/{{oid.idString}}">{{oid.idString}}</a></td><td>{{name}}</td></tr>
           <!--  {{/items}}   -->
  </table>
 <!--{{/isEmpty}} -->
 <!--{{#isEmpty}} -->
There are no references to this file.
 <!--{{/isEmpty}} -->
</div>

</script>

<script>
  var msOfficePreviewAvailable = "${applicationScope['RS_DEPLOY_PROPS']['msOfficeEnabled']}" === "true";
  var collaboraPreviewAvailable = "${applicationScope['RS_DEPLOY_PROPS']['collaboraEnabled']}" === "true";
</script>

<style>
  .recordInfoPanel {
    display: flex
  }

  .recordInfoLeftPanel {
    width: 320px;
  }

  .recordInfoRightPanel {
    width: 250px;
    /* 900px of org doc times 0.25 (scale) + a bit more for vertical scrollbar */
    height: 280px;
    overflow-x: hidden;
  }

  div.linkedRecordsForAttachmentsDiv {
    min-height: 30px;
    margin: 5px;
    overflow: auto;
    color: #333;
  }

  .linkedRecordsForAttachmentsTable td,
  .linkedRecordsForAttachmentsTable th {
    padding: 0px 5px;
  }

  .strucDocPreviewContainer {
    width: 900px;
    /* the same as document editor width, so scaling doesn't cut things */
    overflow-x: hidden
  }

  .strucDocPreviewHeader {
    text-align: center;
    margin: 5px 20px 10px 0px;
  }

  .strucDocPreview {
    -webkit-transform: scale(0.25);
    -webkit-transform-origin: 0 0;
    overflow: auto;
    border: 2px solid lightgrey;
    cursor: default;
  }
  
  .infoPanel-objectIdDownloadIcon {
    height: 18px;
    margin-bottom: 2px;
  }
  .ui-dialog .infoPanel-objectIdDownloadIcon {
    margin-bottom: -4px;
  }


</style>

<!-- the panel is used in workspace and document/notebook for document/attachment 'info' dialogs and expanded previews -->

<div id="recordInfoPanelTemplate" style="display: none">
  <div class="recordInfoPanel">
    <div class="recordInfoLeftPanel">
      <div class="infoPanelRevisionInfoDiv" style="display:none">
        The information below describes <strong>version <span class="infoPanel-docVersion"></span></strong>
        of&nbsp;a&nbsp;document <a class="infoPanel-objectIdLatestLink" href="#"></a>, which may not be the latest
        version.
      </div>

      <table class="infoPanelTable">
        <tr>
          <td class="infoLabelCell">Name: </td>
          <td class="infoPanel-name"></td>
        </tr>
        <tr class="infoPanelObjectIdRow">
          <td class="infoLabelCell">Unique Id: </td>
          <td><a class="infoPanel-objectIdLink" href="#">
                <span class="infoPanel-objectId"></span>
                <img class="infoPanel-objectIdDownloadIcon" src='/images/download.png' />
              </a>
          </td>
        </tr>
        <tr>
          <td class="infoLabelCell">Type: </td>
          <td class="infoPanel-type"></td>
        </tr>
        <tr class="infoPanelPathRow">
          <td class="infoLabelCell">Path: </td>
          <td class="infoPanel-path"></td>
        </tr>
        <tr class="infoPanelDocVersionRow">
          <td class="infoLabelCell">Version: </td>
          <td class="infoPanel-docVersion"></td>
        </tr>
        <tr class="infoPanelFileVersionRow">
          <td class="infoLabelCell">Version: </td>
          <td class="infoPanel-fileVersion"></td>
        </tr>
        <tr class="infoPanelFileSizeRow">
          <td class="infoLabelCell">File size: </td>
          <td class="infoPanel-fileSize"></td>
        </tr>
        <tr>
          <td class="infoLabelCell">Owner: </td>
          <td class="infoPanel-owner"></td>
        </tr>
         <tr class="infoPanelOriginalSource">
          <td class="infoLabelCell">Source:  </td>
          <td >Imported from archive</td>
        </tr>
        <tr class="infoPanelOriginalSource ">
          <td class="infoLabelCell">Original creator: </td>
          <td class="infoPanelOriginalCreator"></td>
        </tr>
        <tr>
          <td class="infoLabelCell">Creation Date: </td>
          <td class="infoPanel-creationDate"></td>
        </tr>
        <tr>
          <td class="infoLabelCell">Last Modified: </td>
          <td class="infoPanel-modificationDate"></td>
        </tr>
        <tr class="infoPanelStatusRow">
          <td class="infoLabelCell">Status: </td>
          <td class="infoPanel-status"></td>
        </tr>
        <tr class="infoPanelSignatureStatusRow">
          <td class="infoLabelCell">Signature Status: </td>
          <td class="infoPanel-signatureStatus"></td>
        </tr>
        <tr class="infoPanelTemplateFormName">
          <td class="infoLabelCell">Created from: </td>
          <td class="infoPanel-templateFormName"></td>
        </tr>
        <tr class="infoPanelTemplateFormId">
          <td class="infoLabelCell">Form ID: </td>
          <td><a class="infoPanel-templateFormID" href="#"></a></td>
        </tr>
        <tr class="infoPanelTemplateID">
          <td class="infoLabelCell">Template Name: </td>
          <td><a class="infoPanel-templateID" href="#"></a></td>
        </tr>
        <tr class="infoPanelTagsRow">
          <td class="infoLabelCell">Tags: </td>
          <td class="infoPanel-tags"></td>
        </tr>
        <tr class="infoPanelOriginalImageIdRow">
          <td class="infoLabelCell">Original Image: </td>
          <td><a class="infoPanel-objectIdLink" href="#">
                <span class="infoPanel-objectId"></span>
                <img class="infoPanel-objectIdDownloadIcon" src='/images/download.png' />
              </a>
          </td>
        </tr>
        <tr class="infoPanelCaptionViewDiv">
          <td class="infoLabelCell">Caption: </td>
          <td class="infoPanel-caption"></td>
        </tr>
        <tr class="infoPanelCaptionEditDiv" style="display:none">
          <td class="infoLabelCell" id="infoCaptionLabelCell">Caption: </td>
          <td>
            <textarea class="infoPanel-captionTextArea" aria-labelledby="infoCaptionLabelCell" maxlength="250"></textarea>
            <div class="infoPanelCaptionButtons">
              <a class="infoPanelCancelCaptionBtn smallBtn closeIcon" href="#">Cancel</a>
              <a class="infoPanelSaveCaptionBtn smallBtn createIcon" href="#">Save</a>
            </div>
          </td>
        </tr>
        <tr>
          <td>&nbsp;</td>
          <td></td>
        </tr>
      </table>

      <div class="infoPanelInternalLinksDiv" style="display:none"></div>
      <div class="infoPanelSharingDiv" style="display:none"></div>
      <div class="publicLinksDiv" style="display:none"></div>

      <div class="infoPanelButtons bootstrap-custom-flat">
        <button 
          type='button' 
          title="Open this file in Office.com"
          class='infoPanelBtn recordViewInMsOnlineBtn btn btn-default' 
          tabindex="0"
        >
          Open in Office.com
        </button>
        <button 
          type='button' 
          title="Edit this file" 
          class='infoPanelBtn recordEditBtn btn btn-default'
          style="display:none;" 
          tabindex="0"
        >
          Edit
        </button>
        <button 
          type='button' 
          title="View this file in browser" 
          class='infoPanelBtn recordViewBtn btn btn-default'
          tabindex="0"
        >
          View
        </button>
        <button 
          type='button' 
          title="Download to your device" 
          class='infoPanelBtn recordDownloadBtn btn btn-default'
          tabindex="0"
        >
          Download
        </button>
        <button 
          type='button' 
          title="Upload new version of this file"
          class='infoPanelBtn recordReplaceBtn btn btn-default' 
          style="display:none;" 
          tabindex="0"
        >
          Upload new version
        </button>
        <button 
          type='button' 
          title="Lists documents that link to this file"
          class='infoPanelBtn recordShowLinkedDocs btn btn-default' 
          style="display:none;" 
          tabindex="0"
        >
          Show linked docs
        </button>
      </div>
      <div class="linkedRecordsForAttachments" style="display:none;"></div>
    </div>
    <div class="recordInfoRightPanel">
      <div class="strucDocPreviewHeader">Preview:</div>
      <div class="strucDocPreviewContainer">
        <div class="strucDocPreview"></div>
      </div>
    </div>
  </div>
</div>

<div id="recordInfoDialog" style="display: none">
  <div class="recordInfoPanel"></div>
</div>