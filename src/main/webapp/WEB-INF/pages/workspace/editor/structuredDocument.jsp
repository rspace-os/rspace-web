<%@ include file="/common/taglibs.jsp"%>

<script src="<c:url value='/scripts/tinymceDialogUtils.js'/>"></script>

<!-- Loading jQuery TinyMCE -->
<script src="<c:url value='/scripts/tinymce/tinymce516/jquery.tinymce.min.js'/>"></script>
<script src="<c:url value='/scripts/tinymce/tinymce516/tinymce.min.js'/>"></script>
<script src="<c:url value='/scripts/pages/workspace/editor/tinymce5_configuration.js'/>"></script>
<script src="<c:url value='/scripts/pages/workspace/editor/tinymceRS_pasteHandler.js'/>"></script>
<script src="<c:url value='/scripts/pages/workspace/editor/tinymceRS_scrollHandler.js'/>"></script>

<script src="<c:url value='/scripts/bower_components/blueimp-file-upload/js/jquery.fileupload.js'/>"></script>
<script src="<c:url value='/scripts/bower_components/blueimp-file-upload/js/jquery.iframe-transport.js'/>"></script>
<script src="<c:url value='/scripts/pages/gallery/galleryFileUpload.js'/>"></script>


<script src="<c:url value='/scripts/pages/workspace/editor/documentEdit.js'/>"></script>

<script src="<c:url value='/scripts/pages/messaging/notifications.js'/>"></script>
<script src="<c:url value='/scripts/pages/messaging/messages.js'/>"></script>
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
  <jsp:include page="../../mediaGallery.jsp" />
  <jsp:include page="include/messagingDialogs.jsp" />

  <div id="tempData" style="display: none"></div>

  <!-- React Scripts -->
  <script src="<c:url value='/ui/dist/structuredDocumentToolbar.js'/>"></script>
  <script src="<c:url value='/ui/dist/fileTreeToolbar.js'/>"></script>
  <script src="<c:url value='/ui/dist/tinymceSidebarInfo.js'/>"></script>
  <script src="<c:url value='/ui/dist/internalLink.js'/>"></script>
  <script src="<c:url value='/ui/dist/materialsListing.js'/>"></script>
    <script src="<c:url value='/ui/dist/externalWorkFlows.js'/>"></script>
  <script src="<c:url value='/ui/dist/associatedInventoryRecords.js'/>"></script>
  <script src="<c:url value='/ui/dist/tinymceKetcher.js'/>"></script>
  <script src="<c:url value='/ui/dist/ketcherViewer.js'/>"></script>

  <!--End React Scripts -->
