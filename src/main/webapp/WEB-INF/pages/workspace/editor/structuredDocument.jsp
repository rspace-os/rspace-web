<%@ include file="/common/taglibs.jsp"%>

<script>
  const RS_MAX_FILE_SIZE = ${applicationScope['RS_DEPLOY_PROPS']['maxUploadSize']};
</script>

<script src="<rst:assetUrl value='/scripts/tinymceDialogUtils.js'/>"></script>

<!-- Load the legacy TinyMCE 5 runtime first, then preload Vite-managed helper bundles. -->
<script src="<rst:assetUrl value='/scripts/tinymce/tinymce5109/dompurify.min.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/tinymce/tinymce5109/jquery.tinymce.min.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/tinymce/tinymce5109/tinymce.min.js'/>"></script>
<rst:bundle bundle="tinymceGalleryUtils" />
<script>
  window.RSTinyMCEPluginBundleLoaderUrl = "<rst:assetUrl value='/scripts/viteBundleLoader.mjs'/>";
  window.RSTinyMCEPluginBundles = Object.assign(
    {},
    window.RSTinyMCEPluginBundles,
    {
      gallery: "tinymceGallery",
      pyrat: "tinymcePyrat",
      stoichiometry: "tinymceStoichiometry",
      identifiers: "tinymceIdentifiers",
      pubchem: "tinymcePubchem",
    },
  );
</script>
<script src="<rst:assetUrl value='/scripts/pages/workspace/editor/tinymce5_configuration.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/pages/workspace/editor/tinymceRS_pasteHandler.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/pages/workspace/editor/tinymceRS_scrollHandler.js'/>"></script>

<script src="<rst:assetUrl value='/scripts/bower_components/blueimp-file-upload/js/jquery.fileupload.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/bower_components/blueimp-file-upload/js/jquery.iframe-transport.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/pages/gallery/galleryFileUpload.js'/>"></script>

<script src="<rst:assetUrl value='/scripts/pages/workspace/editor/documentEdit.js'/>"></script>

<script src="<rst:assetUrl value='/scripts/pages/messaging/notifications.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/pages/messaging/messages.js'/>"></script>
<script>
  const enforceOntologies = "${enforce_ontologies}" === "true";
  const allowBioOntologies = "${allow_bioOntologies}"  === "true";
</script>
<div class="mainDocumentView">
  <axt:fileTreeBrowser />
  <rs:chkRecordPermssns user="${user}" record="${structuredDocument}" action="COPY">
    <c:set var="canCopy" value="true"></c:set>
  </rs:chkRecordPermssns>

  <c:choose>
  <c:when test="${isDeleted == true}">
    <c:set var="closeHref" value="/workspace/trash/list"></c:set>
  </c:when>
  <c:when test="${not empty docRevision}">
    <c:set var="closeHref" value="/workspace/revisionHistory/list/${structuredDocument.id}?settingsKey=${settingsKey}"></c:set>
  </c:when>
  <c:when test="${not empty fromNotebook}">
    <c:set var="closeHref" value="/notebookEditor/${fromNotebook}?initialRecordToDisplay=${id}&settingsKey=${settingsKey}"></c:set>
  </c:when>
  <c:otherwise>
    <c:set var="closeHref" value="/workspace/${parentId}?settingsKey=${settingsKey}"></c:set>
  </c:otherwise>
  </c:choose>

  <rs:chkRecordPermssns user="${user}" record="${structuredDocument}" action="SHARE">
  <rst:hasDeploymentProperty name="cloud" value="true">
    <c:set var="canShare" value="true"></c:set>
    <c:set var="isCloud" value="true"></c:set>
  </rst:hasDeploymentProperty>
  <rst:hasDeploymentProperty name="cloud" value="false">
  <c:if test="${not empty groups}">
    <c:set var="canShare" value="true"></c:set>
    <c:set var="isCloud" value="false"></c:set>
  </c:if>
  </rst:hasDeploymentProperty>
  </rs:chkRecordPermssns>

  <rs:chkRecordPermssns user="${user}" record="${structuredDocument}" action="DELETE">
  <c:if test="${structuredDocument.owner == user}">
    <c:set var="canDelete" value="true"></c:set>
  </c:if>
  </rs:chkRecordPermssns>

  <rs:chkRecordPermssns user="${user}" record="${structuredDocument}" action="SIGN">
    <c:if test="${not structuredDocument.signed}">
      <c:set var="canSign" value="${structuredDocument.allFieldsValid}"></c:set>
    </c:if>
  </rs:chkRecordPermssns>
    <div class="documentPanel">
      <div
              id="toolbar2"
              data-close-href="${closeHref}"
              data-can-copy="${canCopy}"
              data-can-witness="${canWitness}"
              data-can-sign="${canSign}"
              data-can-delete="${canDelete}"
              data-cloud="${isCloud}"
              data-can-share="${canShare}"
              data-docrevision-empty="${not empty docRevision}"
              data-is-template="${isTemplate}"
              data-version="${structuredDocument.userVersion.version}"
      ></div>
      <div id="tinymce-ketcher"></div>
  <jsp:include page="structuredDocumentMainPanel.jsp" >
    <jsp:param name="publicDocument" value="false"/>
  </jsp:include>
    </div>
  <jsp:include page="include/messagingDialogs.jsp" />

  <%-- Dialogs for the ownCloud / NextCloud TinyMCE import plugins. Both plugins
       share the same dialog ids. --%>
  <div id="owncloudDialog" title="Import From ownCloud / NextCloud"></div>

  <div id="owncloudLoginDialog" title="Log in to ownCloud" style="display: none">
     <div class="container">
        <label for="owncloudUsernameField"><b>Username</b></label>
        <input class="owncloudLoginField" id="owncloudUsernameField" type="text" placeholder="Enter Username" name="owncloudUsernameField" required>

        <label for="owncloudPasswordField"><b>Password</b></label>
        <input class="owncloudLoginField" id="owncloudPasswordField" type="password" placeholder="Enter Password" name="owncloudPasswordField" required>
      </div>
  </div>

  <div id="tempData" style="display: none"></div>

  <!-- React Scripts -->
  <rst:bundle bundle="structuredDocumentToolbar" />
  <rst:bundle bundle="fileTreeToolbar" />
  <rst:bundle bundle="tinymceSidebarInfo" />
  <rst:bundle bundle="internalLink" />
  <rst:bundle bundle="materialsListing" />
  <rst:bundle bundle="externalWorkFlows" />
  <rst:bundle bundle="jupyterNotebooks" />
  <rst:bundle bundle="associatedInventoryRecords" />
  <rst:bundle bundle="tinymceKetcher" />
  <rst:bundle bundle="ketcherViewer" />

  <!--End React Scripts -->
