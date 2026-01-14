<%@ include file="/common/taglibs.jsp" %>

<jsp:include page="mediaGalleryTemplates.html" />
<jsp:include page="documentNotebookAndGalleryTemplates.jsp" />

<script>
 var maxFileSize = ${applicationScope['RS_DEPLOY_PROPS']['maxUploadSize']};

 var oneDriveClientId = "${applicationScope['RS_DEPLOY_PROPS']['oneDriveClientId']}";
 var oneDriveRedirect = "${applicationScope['RS_DEPLOY_PROPS']['oneDriveRedirect']}";
 <c:if test="${not empty term}">
     galleryOptionalSearchTermFromUrlParameter = '${term}';
 </c:if>
</script>

<link href="<c:url value='/scripts/jqueryFileTree/jqueryFileTree.css'/>" rel="stylesheet" />
<link href="<c:url value='/styles/rs-widgets.css'/>" rel="stylesheet" />
  
<script src="<c:url value='/scripts/pages/workspace/mediaGalleryManager.js'/>"></script>
<script src="<c:url value='/scripts/pages/workspace/jquery.ui.touch-punch.js'/>"></script>

<script src="<c:url value='/scripts/jqueryFileTree/jqueryFileTree.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/blueimp-file-upload/js/jquery.fileupload.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/blueimp-file-upload/js/jquery.iframe-transport.js'/>"></script>
<script src="<c:url value='/scripts/pages/gallery/galleryFileUpload.js'/>"></script>

<script src="<c:url value='/scripts/bower_components/js-owncloud-client/owncloud.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/js-owncloud-client/nextcloud_owncloud.js'/>"></script>

<script src="<c:url value='/scripts/bower_components/jquery.fancytree/dist/jquery.fancytree-all.js'/>"></script>

<script src="<c:url value='/scripts/pages/ownCloud.js'/>"></script>
<script src="<c:url value='/scripts/pages/nextCloud.js'/>"></script>
<link href="<c:url value='/styles/ownCloud.css'/>" rel="stylesheet" />

<link href="<c:url value='/scripts/bower_components/jquery.fancytree/dist/skin-bootstrap/ui.fancytree.css'/>" rel="stylesheet">

<div id="galleryContentDiv" style="display:none;">
  <rst:hasDeploymentProperty name="netFileStoresEnabled" value="true">
    <c:set var="netFileStoresEnabled" value="true"></c:set>
  </rst:hasDeploymentProperty>


  <div id="toolbar2" data-dmp-enabled="${dmpEnabled}" data-net-filestores-enabled="${netFileStoresEnabled}"></div>
  <div>
    <!--  for import from local file -->
    <input type="file" id="fileBrowserDialog" class="galleryFileInput" style="display:none" multiple/>
    <input type="file" id="fileReplaceDialog" class="galleryFileInput galleryFileReplace" style="display:none"/>
    
    <!-- stores the current  media type selected, e.g, 'Images', 'PDFs' -->
    <input id="mediaTypeSelected" type="hidden" value="Images">
    <!-- id of the folder whose contents are listed in the Gallery display -->
    <input id="currentFolderId" type="hidden" value="0">
    <!--  the page number, numbered from 0 -->
    <input id="pageId" type="hidden" value="0">
    <!-- The id of the parent of the current listed folder -->
    <input id="parentId" type="hidden" value="0">
    <!-- search term passed in the url -->
    <input id="urlSearchTerm" type="hidden" value="">

    <!-- this is the remnants of the old 'From computer pane', this is needed 
      for DnD to work but should be refactored, only dropareaDialog is really needed -->

    <div style="width: 936px; height: 500px; display: none;" class="panel current">
      <fieldset style="width: 95%; height: 30%;">
        <div id="areasDialog">
          <input 
            class="dropareaDialog spotDialog" 
            aria-label="Drop file" 
            type="file" 
            name="xfile" 
            data-post="/gallery/ajax/uploadFile" 
            data-width="500" 
            data-height="200"/>
        </div>
      </fieldset>
    </div>
  </div>

  <!-- Extra header when showing search results after passing search term in url  -->
  <div id="urlTermSearchModeInfoBar">Showing Gallery items matching term: <span id="urlTermSearchModeTerm" /></div>

  <div id="mainGalleryPanel" style="display: none;" class="panel">
    <div id="subBar">
      <div class="gallerySubBarRow crudopsTopPanel" style="display: flex; justify-content: space-between;">
        <div id="topCrudopsLeftButtonsDiv" class="bootstrap-custom-flat">
          <select id="galleryOrderBy" aria-label="Order by" class="galleryOrderSelectBtn galleryTopBarCrudopsBtn" onchange="sortThumbnails()">
              <option selected="selected" value="">Order by</option>
              <option value="nameGallery">Name</option>
              <option value="dateGallery">Date</option>
          </select>
          <button id='orderMediaGallery' class="orderGalleryDesc galleryTopBarCrudopsBtn" title='Order Asc/Desc'>
            Asc/Desc
          </button>
          <button id="gallerySelectAll" class="rs-select-all active galleryTopBarCrudopsBtn" title="Select All/None">
            <span class="rs-select-all-icon" id="selectTogglerGallery"></span>
            <span class="rs-select-all-text">
              <spring:message code="workspace.list.selectAll.header"/>
            </span>
            <span class="rs-select-none-text">
              <spring:message code="workspace.list.selectNone.header"/>
            </span>
          </button>
          <span id="topCrudopsHint"></span>
        </div>
        <div
          style="display: none"
          class="base-search"
          data-placeholder="By name or unique ID..." 
          data-onSubmit="filterGallery" 
          data-elId="gallery-search-input"  
          data-variant="outlined"
        ></div>
      </div>
      <div class="gallerySubBarRow crudopsTopPanel bootstrap-custom-flat">
          <a id='newFolderMediaGallery' tabindex="0" href="#">New Folder</a>
          <axt:galleriesCrudops/>
      </div>
    </div>

    <div id="displayAreaBlock" style="width:100%;" class="bootstrap-custom-flat">

      <!-- Breadcrumbs -->
      <div class="breadcrumb galleryBreadcrumb">
          <axt:breadcrumb breadcrumb="${galleryBcrumb}" breadcrumbTagId="galleryBcrumb"></axt:breadcrumb> 
      </div>

      <div style="display:flex; height: 500px;">
          <!-- Menu block left -->
          <div id="menuBlock">
            <div id="menuPanel">
              <a href="#" class="mediaButtons imageButton "  title="Image Gallery"><img src="/images/icons/leftIconImage.png"></a><br>
              <a href="#" class="mediaButtons audioButton "  title="Audio Gallery"><img src="/images/icons/leftIconAudio.png"></a><br>
              <a href="#" class="mediaButtons videoButton " title="Video Gallery"><img src="/images/icons/leftIconAV.png"></a><br>
              <a href="#" class="mediaButtons documentButton" title="Document Gallery"><img src="/images/icons/leftIconDocument.png"></a><br>
              <a href="#" class="mediaButtons chemistryButton" title="Chemistry Gallery"><img src="/images/icons/leftIconChem.png"></a><br>
              <a href="#" class="mediaButtons miscButton" title="Miscellaneous Gallery"><img src="/images/icons/leftIconMisc.png"></a><br>
              <a href="#" class="mediaButtons snippetButton" title="Snippet Gallery"><img src="/images/icons/leftIconSnippets.png"></a><br>
              <a href="#" class="mediaButtons exportButton" title="Exports Gallery"><img src="/images/icons/leftIconPDF.png"></a><br>
              <rst:hasDeploymentProperty name="netFileStoresEnabled" value="true">
                <a href="#" class="mediaButtons netButton" title="Filestores"><img src="/images/icons/leftIconFilestores.png"></a><br>
              </rst:hasDeploymentProperty>
              <c:if test = "${dmpEnabled eq true}">
               <a href="#" class="mediaButtons dmpButton" title="DMPs"><img width=28 height=31  src="/images/icons/leftIconDMP.png"></a><br>
               </c:if>
            </div>
          </div>

          <!-- Gallery block middle -->
          <div id="galleryBlock">
            <fieldset>
              <legend id="legendGallery">Image Gallery</legend>
              <div id="filesPagination"></div>
              <div class="galleryContent">
                <input type="hidden" id="wasLoaded" value="false"/>
                <div id="folderNameDialog" title="Folder Name">
                  <table>
                    <tbody>
                      <tr>
                        <td>
                          <label for="inputFolderName">
                            Folder name
                          </label>
                        </td>
                        <td>
                          <input id='inputFolderName' class='form-control' />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div id="galleryTable"></div>
              </div>
            </fieldset>
          </div>

          <!-- Info block right -->
          <div id="infoBlock">
            <div id="infoPanel"></div>
          </div>
      </div>
    </div>
  </div>    


  <div id="fromURLPanel" class="panel" style="width: 936px;  display: none; height: 500px;">
    <fieldset style="width: 95%; height: 19%;">
      <legend>From URL</legend>
      <table role="presentation" class="properties">
        <tr>
          <td class="column1"><label id="srclabel" for="src">source link</label></td>
          <td colspan="2"><table role="presentation" border="0" cellspacing="0" cellpadding="0">
            <tr> 
              <td><input name="src" type="text" id="src" value="" onchange="showPreviewImage(this.value);" aria-required="true" /></td> 
              <td id="srcbrowsercontainer">&nbsp; </td>
              <td><input type="button" id="insert" name="insert" class="insertURL" disabled="disabled" value="Save Image" /></td>
            </tr>
          </table></td>
        </tr>
        <tr> 
          <td class="column1"><label id="titlelabel" for="title">title</label></td> 
          <td colspan="2"><input id="title" name="title" type="text" value="" /></td> 
        </tr>
      </table>
    </fieldset>
    <fieldset style="width: 95%; height: 50%;">
      <legend>Preview</legend>
      <div id="prevURL" style="width: 98%; height: 98%; overflow:auto;"></div>
    </fieldset>
    <fieldset style="width: 95%; height: 19%;">
      <legend>List</legend>
      <div id="prev"  style="width: 95%; height: 95%; overflow:auto;">
        <ul id="listThumbnailsURL"  style="list-style-type:none;" ></ul>
      </div>
    </fieldset>
  </div>
</div>


<div class="bootstrap-custom-flat">

  <!-- This shows the 'View or Delete' choice after clicking on an attachment link
  This is known broken as of 0.16 but should be fixed after improving attachment display -->
  <div id="attachmentInformation" style="display: none; z-index: 20001;"></div>

  <!-- panel for previewing images, documents or snippets -->
  <div id="viewerContainer">
      <div id="viewer">
        <div id="viewerNav">
          <div id="nameViewerImage"></div>
          <button id="closeViewer" class="smallBtn closeIcon"> Close </button>
        </div>
        <div id="viewerContent"></div>
      </div>
  </div>


  <div id="galleryInfoPanelTemplate" style="display: none">
      <div class="galleryInfoPanel"> 
          <div id="infoBar">
              <strong>File Info</strong>
          </div>
          <div class="recordInfoPanel"></div>
      </div>
  </div> 
</div>

<div id="owncloudLoginDialog" title="Log in to ownCloud" style="display: none">
   <div class="container">
      <label for="owncloudUsernameField"><b>Username</b></label>
      <input class="owncloudLoginField" id="owncloudUsernameField" type="text" placeholder="Enter Username" name="owncloudUsernameField" required>

      <label for="owncloudPasswordField"><b>Password</b></label>
      <input class="owncloudLoginField" id="owncloudPasswordField" type="password" placeholder="Enter Password" name="owncloudPasswordField" required>       
    </div>
</div>

<div id="owncloudDialog" title="Import From ownCloud"></div>

<!-- React Scripts -->
<script src="<c:url value='/ui/dist/galleryToolbar.js'/>"></script>
<script src="<c:url value='/ui/dist/snapGeneDialog.js'/>"></script>
<script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>
<div id="react-image-editor"></div>
<script src="<c:url value='/ui/dist/imageEditor.js'/>"></script>
<script src="<c:url value='/scripts/tinymceDialogUtils.js'/>"></script>
<!--End React Scripts -->
