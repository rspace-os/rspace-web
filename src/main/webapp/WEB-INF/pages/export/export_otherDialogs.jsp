<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>

<fmt:bundle basename="bundles.workspace.workspace">

<script src="/scripts/tags/exportOtherDialogs.js"></script>

<!-- Pdf configuration dialog, initially hidden -->
<div id="pdf-config" style="display: none;">
  <table>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.provenance"/></td>
        <td><input id="pdf_a1proven" type="checkbox" name="provenance" checked></input></td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.comments"/></td>
        <td><input id="pdf_a1cmmnt" type="checkbox" name="comments" checked></input></td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.annotation"/></td>
        <td><input id="pdf_a1annotated" type="checkbox" name="annotations" checked></input></td>
    </tr>
      <tr><td><fmt:message key="dialogs.pdfArchiving.label.includeFieldLastModifiedDate"/></td>
        <td><input id="pdf_a1IncludeFieldLastModifiedDate" type="checkbox" name="includeFieldLastModifiedDate" checked></input></td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.numbering"/></td>
        <td><input id="pdf_a1pagenbr" type="checkbox" name="pagename" checked></input></td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.pageFormat"/></td>
        <td><select id="pdf_a1size" name="pagesize">    
            <option value="UNKNOWN"><fmt:message key="dialogs.pdfArchiving.choose.label"/></option>
            <option value="A4"><fmt:message key="dialogs.pdfArchiving.A4.label"/></option>
            <option value="LETTER"><fmt:message key="dialogs.pdfArchiving.Letter.label"/></option>
          </select>
        </td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.dateFormat"/></td>
        <td><select id="pdf_a1date" name="pagedate">     
            <option value="EXP" selected><fmt:message key="dialogs.pdfArchiving.exportDate.label"/></option>
            <option value="NEW"><fmt:message key="dialogs.pdfArchiving.creationDate.label"/></option>
            <option value="UPD"><fmt:message key="dialogs.pdfArchiving.modifiedDate.label"/></option>
          </select>
        </td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.footer"></fmt:message></td>
        <td><input id="pdf_a1footer" type="checkbox" name="footer" checked></input></td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.fileName"></fmt:message></td>
        <td><input id="pdf_name" type="text" ></input></td>
    </tr>
  </table>
</div>

<!-- Word configuration dialog, initially hidden -->
<div id="word-config" style="display: none;">
  <table>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.pageFormat"></fmt:message></td>
        <td><select id="word_a1size" name="pagesize">    
            <option value="UNKNOWN"><fmt:message key="dialogs.pdfArchiving.choose.label"/></option>
            <option value="A4"><fmt:message key="dialogs.pdfArchiving.A4.label"/></option>
            <option value="LETTER"><fmt:message key="dialogs.pdfArchiving.Letter.label"/></option>
          </select>
        </td>
    </tr>
    <tr><td><fmt:message key="dialogs.pdfArchiving.label.fileName"></fmt:message></td>
        <td><input id="word_name" type="text" ></input></td>
    </tr>
  </table>
</div>

<!-- Archive configuration dialog, initially hidden -->
<div id="archive-additional-config" style="display: none;">
	<table style="margin: 0px;">
		<tr>
		    <td colspan="2" style="padding-top: 12px;"><strong><fmt:message key="dialogs.archiving.configure"/></strong></td>
		</tr>
		<tr>
			<td align="center" style="padding-top: 12px;"><fmt:message key="dialogs.archiving.label.linkDepth" /></td>
			<td style="padding-top: 16px; width: 400px;">
                <select id="maxLinkLevel" name="maxLinkLevel" style="width: 100%; box-sizing: border-box;">
					<option value="0"><fmt:message key="dialogs.archiving.label.linkDepthOption0"/></option>
					<option value="1" selected><fmt:message key="dialogs.archiving.label.linkDepthOption"><fmt:param>1</fmt:param></fmt:message></option>
					<option value="2"><fmt:message key="dialogs.archiving.label.linkDepthOption"><fmt:param>2</fmt:param></fmt:message></option>
					<option value="3"><fmt:message key="dialogs.archiving.label.linkDepthOption"><fmt:param>3</fmt:param></fmt:message></option>
					<option value="10000"><fmt:message key="dialogs.archiving.label.linkDepthOption"><fmt:param>Infinity</fmt:param></fmt:message></option>
			    </select>
                <div style="margin: 5px 0px 10px 0px;">
                    <img src="/images/notice.png" style="height: 14px; vertical-align: text-top;">
                    <fmt:message key="dialogs.archiving.label.linked.folders.not.included.msg"/>
                </div>
            </td>
		</tr>
		<rst:hasDeploymentProperty name="netFileStoresExportEnabled" value="true">
          <tr>
            <td align="center"><fmt:message key="dialogs.archiving.label.includeNfsFiles" /></td>
            <td>
                <input id="nfsExportCheckbox" type="checkbox" name="nfsExport"</input>
            </td>
          </tr>
    </rst:hasDeploymentProperty>
		<tr>
		    <td align="center"><fmt:message key="dialogs.archiving.label.desc"/></td>
		    <td style="width: 400px;"><textarea rows="3" cols="35" id="archive-desc" name="description" style="width: 100%; box-sizing: border-box;"></textarea>
		</tr>
        <tr id="archiveVersionsConfigRow">
            <td align="center" style="padding-top: 8px;"><input id="archiveVersionsConfig" type="checkbox"></input></td>
            <td style="padding-top: 8px;"><fmt:message key="dialogs.archiving.instruction.versions"/></td>
        </tr>
	</table>
</div>

<script type="text/x-mustache-template" id="setPageSizeAsDefaultTemplate">
 <tr id='pagesizeDefaultRow'>
  <td>Do you want to save {{currSel}} as a default?</td>
  <td><input type='checkbox' name='setPageSizeAsDefault' id='setPageSizeAsDefault'/>
 </tr>
</script>

<style>
    #nfs-config .filteringOptionsDiv {
        display: inline-block;
        min-width: 150px;
    }
    .filteringOptionsDiv span {
        display: inline-block;
        width: 185px;
        text-align: left;
        padding: 5px 0px;
    }
    .nfsFilesizeLimit {
        width: 60px;
    }
    .foundLinksSummary {
        margin-bottom: 20px;
    }
    .fileSystemToLoginName {
        display: inline-block;
        min-width: 150px;
        max-width: 150px;
    }
    #nfs-config button.nfsShowFileSystemLoginDialogBtn,
    #nfs-config button.nfsShowFiltersDialogBtn {
        width: auto;
        margin-top: 10px;
    }
    #nfs-config a.nfsShowFileSystemLoginDialogBtn {
        display: inline-block;
        margin-top: 10px;
    }
    .nfsScanLinksBtn, .nfsSeeScanReportBtn {
        margin: 10px 0px;
        width: auto;
        padding-left: 10px;
        padding-right: 10px;
    }
    .nfsDialogSectionDiv {
        margin: 0px 5px 7px 5px;
        border: 1px solid;
        padding: 5px 5px 10px 5px;
    }
    .nfsDialogNoLinksFoundDiv {
        margin-top: 10px;
        border: 1px solid;
        padding: 10px;
    }   
    .nfsScanResultTable {
        width: 500px; 
        table-layout: fixed;
    }
    .nfsScanResultTable tr.nfsFileAvailableRow td {
        background: #dff0d8;
    }
    .nfsScanResultTable tr.nfsFileNotAvailableRow td {
        background: #f2dede;
    } 
     
</style>

<div id="nfs-config" style="display: none;">
</div>

<div id="nfsExportConfigDialogTemplate" style="display:none">
  {{^foundNfsLinksCount}}
    <div class="nfsDialogNoLinksFoundDiv">
      No filestore links found in exported content. <br/>
      If that's not expected, please check your export selection.<br/>
      Otherwise please proceed with the export.
    </div>
  {{/foundNfsLinksCount}}
  {{#foundNfsLinksCount}}
    <div class="nfsDialogSectionDiv" style="margin-top: 5px;">
      <h3>Filestore links found in exported content</h3>
      <span class="foundLinksSummary"> {{foundLinksSummary}} </span>
      {{#foundNfsFolderLinksCount}}
      <div style="margin: 5px 0px 10px 0px;">
          <img src="/images/notice.png" style="height: 14px; vertical-align: text-top;">
          At least one of the links points to a filestore folder. Subfolders of linked folders are not included in export.
      </div>
      {{/foundNfsFolderLinksCount}}
      <a class="nfsExportPlanShowLinksBtn" href="#">Show found filestore links</a>
    </div>
    
    <div class="nfsDialogSectionDiv">
      <h3>File Systems that require login</h3>
      <div>
        {{#fileSystemsToLoginCount}}
          {{fileSystemsToLoginSummary}}
          <div class="bootstrap-namespace">
            <button type="button" class='nfsShowFileSystemLoginDialogBtn btn btn-primary'>Login to remaining File Systems</button>
          </div>
        {{/fileSystemsToLoginCount}}
        {{^fileSystemsToLoginCount}}
          You are logged into all File Systems referenced by filestore links.
          <div>
            <a href="#" class='nfsShowFileSystemLoginDialogBtn'>Check File Systems login details</a>
          </div>
        {{/fileSystemsToLoginCount}}
      </div>
    </div>

    <div class="nfsDialogSectionDiv">
      <h3>File filters</h3>
      <span>
        {{^maxFileSizeInMB}}
          Filestore files <strong>of any size</strong> will be included in the export.<br />
        {{/maxFileSizeInMB}}
        {{#maxFileSizeInMB}}
          Filestore files larger than <strong>{{maxFileSizeInMB}} MB </strong> will not be included in the export.<br /> 
        {{/maxFileSizeInMB}}
        {{^excludedFileExtensions}}
          All types of filestore files will be included.<br />
        {{/excludedFileExtensions}}
        {{#excludedFileExtensions}}
          Filestore files of <strong>{{excludedFileExtensions}}</strong> type will be excluded from the export.
        {{/excludedFileExtensions}}
      </span>
      <div class="bootstrap-namespace">
        <button type="button" class='nfsShowFiltersDialogBtn btn btn-default' title="Run the scan again">Change file filtering options</button>
      </div>
    </div>
  
    <div class="nfsDialogSectionDiv" style="margin-bottom: 0px">
      <h3>Link availability scan</h3>
      <div class="nfsScanBtnDiv">
        {{^scanResultsPresent}} 
          <span>Before continuing please run the filestore links availability scan, which will report on any unaccessible or filtered links.</span>
          <div class="bootstrap-namespace" style="margin-top: 10px;">
            <button type="button" class='nfsScanLinksBtn btn btn-primary' title="Run filestore links scan">Scan filestore links</button>
          </div>
        {{/scanResultsPresent}}
        {{#scanResultsPresent}}
          {{^scanResultsAvailableCount}} 
            <span>Export <strong>will not include any filestore files</strong>.
          {{/scanResultsAvailableCount}}
          {{#scanResultsAvailableCount}}
            <span>Export will include <strong>{{scanResultsAvailableCountMsg}}</strong> 
              of total size <strong>{{scanResultsTotalFileSizeToDisplay}}</strong>.</span>
            {{#scanResultsOmittedCount}}
              <span><strong>{{scanResultsOmittedCountMsg}} will be omitted</strong>.</span>
            {{/scanResultsOmittedCount}}
          {{/scanResultsAvailableCount}}
          {{#scanResultsOmittedCount}}
            <span>Please check the scan report for details.</span>
          {{/scanResultsOmittedCount}}
          <div class="bootstrap-namespace" style="margin-top: 10px;">
            <button type="button" title="See the report of scanned files" class='nfsSeeScanReportBtn btn 
                  {{#scanResultsOmittedCount}} btn-danger {{/scanResultsOmittedCount}}
                  {{^scanResultsOmittedCount}} btn-success {{/scanResultsOmittedCount}}
              ' > See scan report</button>
            <button type="button" class='nfsScanLinksBtn btn btn-default' title="Run the scan again">Scan again</button>
          </div>
        {{/scanResultsPresent}}
      </div>
    </div>
    
  {{/foundNfsLinksCount}}

</div>

<div id="nfsExportConfigFoundLinksDialogTemplate" style="display:none">
  <h3>Filestore links found in exported content</h3>
  <div>
    {{#foundNfsLinksForDisplay}}
      <div style="padding: 5px; border: 1px solid grey; margin-bottom: 5px;">
        <h4 style="padding: 5px;">Links from <strong>{{fileSystemName}}</strong> File System</h4>
        <table cellpadding="5" cellspacing="0">
          <tr>
            <th>Full path on the File System</th>
            <th>Link type</th>
            <th>Notes</th>
          </tr>
          <!-- {{#links}}-->
          <tr class="nfsLinkTableRow">
            <td>{{fullPath}}</td>
            <td>{{linkType}}</td>
            <td>
              {{#isFolder}}
                folder link - subfolders won't be included in export
              {{/isFolder}}
            </td>
          </tr>
          <!-- {{/links}}-->
        </table>
      </div>
    {{/foundNfsLinksForDisplay}}
  </div>
</div>

<div id="nfsExportConfigFileSystemsLoginDialogTemplate" style="display:none">
  <h3>File Systems referenced by exported content</h3>
  <div class="bootstrap-custom-flat">
    <!-- {{#foundFileSystems}}-->
    <div style="margin: 5px 10px;">
      <span class='fileSystemToLoginName'>{{name}}</span>
      {{#loggedAs}}
         logged in as '{{loggedAs}}'
      {{/loggedAs}}
      {{^loggedAs}}
        <button class='nfsExportPlanLoginBtn btn btn-primary' href="#" title="Login to the '{{name}}' File System" data-filesystemid='{{id}}'>Login</button>
      {{/loggedAs}}
    </div>
    <!-- {{/foundFileSystems}}-->
  </div>
</div>

<div id="nfsExportConfigFiltersDialogTemplate" style="display:none">
  <h3>Filtering options for filestore files</h3>
  <div class="filteringOptionsDiv">
    <span title="Files larger than the limit will not be added to the archive">Individual file size limit (MB)</span>
    <input class="nfsFilesizeLimit" type="number" name="filesizeLimit"></input> 
    <br />
    <span title="Comma-separated list of file extensions that should not be added to the archive">File types to exclude (comma-separated list)</span>
    <input class="nfsExcludedFileExtensions" type="string" name="excludedFileExtensions" placeholder="e.g. tiff, pdf, zip"></input> 
  </div>
</div>

<div id="nfsExportConfigScanResultsTemplate" style="display:none">
  {{^scanResultsPresent}}
    <span>No results after file scan.</span>
  {{/scanResultsPresent}}
  {{#scanResultsPresent}}
    <h3>Link availability scan results</h3>
    
    <div>
      {{#scanResultsForDisplay}}
      <div style="padding: 5px; border: 1px solid grey; margin-bottom: 5px;">
      <h4 style="padding: 5px;">Links from <strong>{{fileSystemName}}</strong> File System</h4>
      <table cellpadding="5" cellspacing="0" class="nfsScanResultTable">
        <tr>
          <th style="width: 130px;">Full path</th>
          <th style="width: 80px;">Type</th>
          <th style="width: 60px;">Size</th>
          <th style="width: 60px;">Included in export</th>
          <th>Notes</th>
        </tr>
      <!-- {{#results}}-->
        <tr class="nfsFile{{^available}}Not{{/available}}AvailableRow">
          <td style="word-break: break-all;">{{name}}</td>
          <td style="text-align: center;">
              {{type}}</td>
          <td style="text-align: right;">
              {{sizeToDisplay}}</td>
          <td style="text-align: center;">
              {{#available}} yes {{/available}}
              {{^available}} no {{/available}}
          <td>{{msg}}</td>
        </tr>
      <!-- {{/results}}-->
      </table>
      </div>
      {{/scanResultsForDisplay}}
    </div>
  {{/scanResultsPresent}}
</div>

</fmt:bundle>