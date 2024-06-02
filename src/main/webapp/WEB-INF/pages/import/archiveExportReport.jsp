<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="exportArchive.report.title"/></title>
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
                  this.displayNote = 'exported as a part of <a href="/globalId/' + this.exportedParentGlobalId + '">' + this.exportedParentGlobalId + '</a> notebook';
              } 
           });
       }
       if (notificationData.nfsLinksIncluded) {
           notificationData.exportedNfsLinksPresent = notificationData.exportedNfsLinks && notificationData.exportedNfsLinks.length > 0;
           
           if (notificationData.maxNfsFileSize) {
               notificationData.maxNfsFileSizeMB = (notificationData.maxNfsFileSize / 1048576).toFixed(0);
           }
           if (notificationData.excludedNfsFileExtensions) {
               notificationData.excludedNfsFileExtensionsList = notificationData.excludedNfsFileExtensions.join(", ")
           }
       }
   }
   
</script>

<div id="reportContent">
</div>

<div id="archiveExportReportTemplate" style="display:none">

  <div class="archiveExportSummaryMsg"> 
      Archive export report for {{archiveType}} export of {{exportedUserOrGroupId}} {{exportScope}} generated on {{creationTimeFormatted}}. <br/>
  </div>
  <div class="downloadLinkMsg">
      Download link: <a href="{{downloadLink}}">{{downloadLink}}</a>.
  </div>
  
  <h2> Exported documents</h2>
  <table class="exportedDocumentsTable">
    <tr>
     <th> Name </th>
     <th> Global Id </th>
     <th> Notes </th>
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
     <h2> Exported filestore links</h2>
     {{#maxNfsFileSizeMB}}
       File size limit for individual filestore files was set to <strong>{{maxNfsFileSizeMB}} MB</strong>. <br/>
     {{/maxNfsFileSizeMB}}
     {{#excludedNfsFileExtensionsList}}
       Files of <strong>{{excludedNfsFileExtensionsList}}</strong> type were not included. <br/>
     {{/excludedNfsFileExtensionsList}}
     <table class="exportedNfsLinksTable">
      <tr>
       <th> File System name </th>
       <th> Filestore path </th>
       <th> Resource path</th>
       <th> Added to archive </th>
       <th> Error </th>
       <th> Folder</th>
       <th> Folder export summary</th>
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
     <strong>Export filestore links</strong> option was selected, but exported documents didn't contain any filestore links.
   {{/exportedNfsLinksPresent}}
  {{/nfsLinksIncluded}}

</div>
