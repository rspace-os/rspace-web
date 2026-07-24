<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="importExport.export.report.title"/></title>
</head>

<style>
    .archiveExportSummaryMsg {
        margin: 10px 0px;
        font-weight: bold;
    }
    
    .downloadLinkMsg {
        margin: 10px 0px;
    }
    
    .exportedNfsLinksTable {
        margin: 10px 0px;
    }
    
</style>

<script>
   var notificationData = JSON.parse(RS.unescape('${notificationDataJson}'));
   notificationData.creationTime = '${notification.creationTime}';

   $(document).ready(function () {
      processNotificationDataBeforeDisplaying(); 
             
      var templateHTML = $('#archiveExportReportTemplate').html();
      var resultsHTML = Mustache.render(templateHTML, notificationData);
      $('#reportContent').html(resultsHTML);
         
   });
   
   function processNotificationDataBeforeDisplaying() {
       if (!notificationData) {
           return;
       }
       notificationData.creationTimeFormatted = (new Date(notificationData.creationTime)).toUTCString();
       if (notificationData.exportedRecords) {
           // add info about parent notebook to exported notebook entries
           $.each(notificationData.exportedRecords, function() {
              if (this.exportedParentGlobalId && this.exportedParentGlobalId.startsWith('NB')) {
                  this.displayNote = RS.msg("legacyjs.archiveExportReport.exportedAsPartOfNotebook", this.exportedParentGlobalId);
              }
           });
       }
       if (notificationData.nfsLinksIncluded) {
           notificationData.exportedNfsLinksPresent = notificationData.exportedNfsLinks && notificationData.exportedNfsLinks.length > 0;
           
           if (notificationData.maxNfsFileSize) {
               notificationData.maxNfsFileSizeMB = (notificationData.maxNfsFileSize / 1048576).toFixed(0);
           }
           if (notificationData.excludedNfsFileExtensions) {
               notificationData.excludedNfsFileExtensionsList =
                   RS.formatList(notificationData.excludedNfsFileExtensions);
           }
       }
   }
   
</script>

<div id="reportContent">
</div>

<div id="archiveExportReportTemplate" style="display:none">

  <div class="archiveExportSummaryMsg">
      <spring:message code="archiveExportReport.summary"/> <br/>
  </div>
  <div class="downloadLinkMsg">
      <spring:message code="archiveExportReport.downloadLinkLabel"/> <a href="{{downloadLink}}">{{downloadLink}}</a>.
  </div>

  <h2> <spring:message code="archiveExportReport.exportedDocumentsHeading"/></h2>
  <table class="exportedDocumentsTable">
    <tr>
     <th> <spring:message code="archiveExportReport.nameHeader"/> </th>
     <th> <spring:message code="archiveExportReport.globalIdHeader"/> </th>
     <th> <spring:message code="archiveExportReport.notesHeader"/> </th>
    </tr>
    <!-- {{#exportedRecords}}-->
    <tr>
     <td>{{name}}</td>
     <td><a href="/globalId/{{globalId}}">{{globalId}}</a></td>
     <td>{{{displayNote}}}</td>
    </tr>
    <!-- {{/exportedRecords}}-->
  </table>

  {{#nfsLinksIncluded}}
   {{#exportedNfsLinksPresent}}
     <h2> <spring:message code="archiveExportReport.exportedFilestoreLinksHeading"/></h2>
     {{#maxNfsFileSizeMB}}
       <spring:message code="archiveExportReport.fileSizeLimitNotice"/> <br/>
     {{/maxNfsFileSizeMB}}
     {{#excludedNfsFileExtensionsList}}
       <spring:message code="archiveExportReport.excludedFileTypesNotice"/> <br/>
     {{/excludedNfsFileExtensionsList}}
     <table class="exportedNfsLinksTable">
      <tr>
       <th> <spring:message code="archiveExportReport.fileSystemNameHeader"/> </th>
       <th> <spring:message code="archiveExportReport.filestorePathHeader"/> </th>
       <th> <spring:message code="archiveExportReport.resourcePathHeader"/></th>
       <th> <spring:message code="archiveExportReport.addedToArchiveHeader"/> </th>
       <th> <spring:message code="archiveExportReport.errorHeader"/> </th>
       <th> <spring:message code="archiveExportReport.folderHeader"/></th>
       <th> <spring:message code="archiveExportReport.folderExportSummaryHeader"/></th>
      </tr>
      <!-- {{#exportedNfsLinks}}-->
      <tr>
       <td>{{fileSystemName}}</td>
       <td>{{fileStorePath}}</td>
       <td>{{relativePath}}</td>
       <td>{{#addedToArchive}} yes {{/addedToArchive}}
           {{^addedToArchive}} no {{/addedToArchive}}
       </td>
       <td>{{errorMsg}}</td>
       <td>{{#folderLink}} yes {{/folderLink}}
           {{^folderLink}} no {{/folderLink}}
       </td>
       <td>{{folderExportSummaryMsg}}</td>
      </tr>
      <!-- {{/exportedNfsLinks}}-->
     </table>
   {{/exportedNfsLinksPresent}}
   {{^exportedNfsLinksPresent}}
     <spring:message code="archiveExportReport.noFilestoreLinksNotice"/>
   {{/exportedNfsLinksPresent}}
  {{/nfsLinksIncluded}}

</div>
