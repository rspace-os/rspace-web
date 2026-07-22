<%@ include file="/common/taglibs.jsp" %>

<script src="<rst:assetUrl value='/scripts/pages/recordInfoPanel.js'/>"></script>
<script id="linkedRecordsTemplate" type="text/x-mustache-template">

  <div class="linkedRecordsForAttachmentsDiv">
 <!--{{^isEmpty}} -->
<spring:message code="recordInfoPanel.referencedByNotice"/>
<table class="linkedRecordsForAttachmentsTable" width="100%">
            <tr class="linkedRecordRow"><th>ID</th><th>Name</th></tr>
            <!--{{#items}} -->
            <tr><td><a href="/globalId/{{oid.idString}}">{{oid.idString}}</a></td><td>{{name}}</td></tr>
           <!--  {{/items}}   -->
  </table>
 <!--{{/isEmpty}} -->
 <!--{{#isEmpty}} -->
<spring:message code="recordInfoPanel.noReferencesNotice"/>
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

  .infoPanelHistoricalVersionNotice {
    margin: 0px auto;
    width: 280px;
  }
  .infoPanel-historicalVersionNoticeIcon {
    height: 18px;
    margin-bottom: -4px;
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
        <spring:message code="recordInfoPanel.revisionNotice.text">
          <spring:argument value='<span class="infoPanel-docVersion"></span>'/>
          <spring:argument value='<a class="infoPanel-objectIdLatestLink" href="#"></a>'/>
        </spring:message>
      </div>

      <table class="infoPanelTable">
        <tr>
          <td class="infoLabelCell"><spring:message code="recordInfo.nameLabel"/> </td>
          <td class="infoPanel-name"></td>
        </tr>
        <tr class="infoPanelObjectIdRow">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.uniqueIdLabel"/> </td>
          <td><a class="infoPanel-objectIdLink" href="#">
                <span class="infoPanel-objectId"></span>
                <img class="infoPanel-objectIdDownloadIcon" src='/images/download.png' />
              </a>
          </td>
        </tr>
        <tr>
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.typeLabel"/> </td>
          <td class="infoPanel-type"></td>
        </tr>
        <tr class="infoPanelPathRow">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.pathLabel"/> </td>
          <td class="infoPanel-path"></td>
        </tr>
        <tr class="infoPanelDocVersionRow">
          <td class="infoLabelCell"><spring:message code="recordInfo.versionLabel"/> </td>
          <td class="infoPanel-docVersion"></td>
        </tr>
        <tr class="infoPanelFileVersionRow">
          <td class="infoLabelCell"><spring:message code="recordInfo.versionLabel"/> </td>
          <td class="infoPanel-fileVersion"></td>
        </tr>
        <tr class="infoPanelFileSizeRow">
          <td class="infoLabelCell"><spring:message code="recordInfo.fileSizeLabel"/> </td>
          <td class="infoPanel-fileSize"></td>
        </tr>
        <tr>
          <td class="infoLabelCell"><spring:message code="recordInfo.ownerLabel"/> </td>
          <td class="infoPanel-owner"></td>
        </tr>
        <tr class="infoPanelCreatedByRow">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.createdByLabel"/> </td>
          <td class="infoPanel-createdBy"></td>
        </tr>
         <tr class="infoPanelOriginalSource">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.sourceLabel"/>  </td>
          <td ><spring:message code="recordInfoPanel.importedFromArchiveNotice"/></td>
        </tr>
        <tr class="infoPanelOriginalSource ">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.originalCreatorLabel"/> </td>
          <td class="infoPanelOriginalCreator"></td>
        </tr>
        <tr>
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.creationDateLabel"/> </td>
          <td class="infoPanel-creationDate"></td>
        </tr>
        <tr>
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.lastModifiedLabel"/> </td>
          <td class="infoPanel-modificationDate"></td>
        </tr>
        <tr class="infoPanelStatusRow">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.statusLabel"/> </td>
          <td class="infoPanel-status"></td>
        </tr>
        <tr class="infoPanelSignatureStatusRow">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.signatureStatusLabel"/> </td>
          <td class="infoPanel-signatureStatus"></td>
        </tr>
        <tr class="infoPanelTemplateFormName">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.createdFromLabel"/> </td>
          <td class="infoPanel-templateFormName"></td>
        </tr>
        <tr class="infoPanelTemplateFormId">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.formIdLabel"/> </td>
          <td><a class="infoPanel-templateFormID" href="#"></a></td>
        </tr>
        <tr class="infoPanelTemplateID">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.templateNameLabel"/> </td>
          <td><a class="infoPanel-templateID" href="#"></a></td>
        </tr>
        <tr class="infoPanelTagsRow">
          <td class="infoLabelCell"><spring:message code="recordInfo.tagsLabel"/> </td>
          <td class="infoPanel-tags"></td>
        </tr>
        <tr class="infoPanelOriginalImageIdRow">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.originalImageLabel"/> </td>
          <td><a class="infoPanel-objectIdLink" href="#">
                <span class="infoPanel-objectId"></span>
                <img class="infoPanel-objectIdDownloadIcon" src='/images/download.png' />
              </a>
          </td>
        </tr>
        <tr class="infoPanelCaptionViewDiv">
          <td class="infoLabelCell"><spring:message code="recordInfoPanel.captionLabel"/> </td>
          <td class="infoPanel-caption"></td>
        </tr>
        <tr class="infoPanelCaptionEditDiv" style="display:none">
          <td class="infoLabelCell" id="infoCaptionLabelCell"><spring:message code="recordInfoPanel.captionLabel"/> </td>
          <td>
            <textarea class="infoPanel-captionTextArea" aria-labelledby="infoCaptionLabelCell" maxlength="250"></textarea>
            <div class="infoPanelCaptionButtons">
              <a class="infoPanelCancelCaptionBtn smallBtn closeIcon" href="#"><spring:message code="common:actions.cancel"/></a>
              <a class="infoPanelSaveCaptionBtn smallBtn createIcon" href="#"><spring:message code="common:actions.save"/></a>
            </div>
          </td>
        </tr>
        <tr>
          <td>&nbsp;</td>
          <td></td>
        </tr>
      </table>

      <div class="infoPanelHistoricalVersionNotice" style="display:none">
        <img class="infoPanel-historicalVersionNoticeIcon" src='/images/warning.png' />
        <spring:message code="recordInfoPanel.historicalVersionNotice"/>
      </div>
      <div class="infoPanelInternalLinksDiv" style="display:none"></div>
      <div class="infoPanelRelatedInventoryItemsDiv" style="display:none"></div>
      <div class="infoPanelSharingDiv" style="display:none"></div>
      <div class="publicLinksDiv" style="display:none"></div>

      <div class="infoPanelButtons bootstrap-custom-flat">
        <button
          type='button'
          title="<spring:message code='recordInfoPanel.openInOfficeComTitle'/>"
          class='infoPanelBtn recordViewInMsOnlineBtn btn btn-default'
          tabindex="0"
        >
          <spring:message code="recordInfoPanel.openInOfficeComButton"/>
        </button>
        <button
          type='button'
          title="<spring:message code='recordInfoPanel.editFileTitle'/>"
          class='infoPanelBtn recordEditBtn btn btn-default'
          style="display:none;"
          tabindex="0"
        >
          <spring:message code="common:actions.edit"/>
        </button>
        <button
          type='button'
          title="<spring:message code='recordInfoPanel.viewFileTitle'/>"
          class='infoPanelBtn recordViewBtn btn btn-default'
          tabindex="0"
        >
          <spring:message code="recordInfo.viewButton"/>
        </button>
        <button
          type='button'
          title="<spring:message code='recordInfoPanel.downloadToDeviceTitle'/>"
          class='infoPanelBtn recordDownloadBtn btn btn-default'
          tabindex="0"
        >
          <spring:message code="common:actions.download"/>
        </button>
        <button
          type='button'
          title="<spring:message code='recordInfoPanel.uploadNewVersionTitle'/>"
          class='infoPanelBtn recordReplaceBtn btn btn-default'
          style="display:none;"
          tabindex="0"
        >
          <spring:message code="recordInfoPanel.uploadNewVersionButton"/>
        </button>
        <button
          type='button'
          title="<spring:message code='recordInfoPanel.linkedDocsTitle'/>"
          class='infoPanelBtn recordShowLinkedDocs btn btn-default'
          style="display:none;"
          tabindex="0"
        >
          <spring:message code="recordInfoPanel.showLinkedDocsButton"/>
        </button>
      </div>
      <div class="linkedRecordsForAttachments" style="display:none;"></div>
    </div>
    <div class="recordInfoRightPanel">
      <div class="strucDocPreviewHeader"><spring:message code="recordInfoPanel.previewLabel"/></div>
      <div class="strucDocPreviewContainer">
        <div class="strucDocPreview"></div>
      </div>
    </div>
  </div>
</div>

<div id="recordInfoDialog" style="display: none">
  <div class="recordInfoPanel"></div>
</div>