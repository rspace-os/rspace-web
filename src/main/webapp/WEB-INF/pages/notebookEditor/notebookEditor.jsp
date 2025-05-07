<%@ include file="/common/taglibs.jsp"%>
<c:url var="createFromTemplateURL" value="/workspace/editor/structuredDocument/create/${selectedNotebookId}"></c:url>
<jsp:include page="notebookHeader.jsp" />
  <script src="<c:url value='/scripts/pages/journal.js'/>"></script>
  <script>journal(jQuery)</script>
  <script src="<c:url value='/scripts/bower_components/blueimp-file-upload/js/jquery.fileupload.js'/>"></script>
  <script src="<c:url value='/scripts/pages/gallery/galleryFileUpload.js'/>"></script>
  <script src="<c:url value='/scripts/pages/gallery/galleryFileUpload.js'/>"></script>

  <script src="<c:url value='/scripts/jqueryFileTree/jqueryFileTree.js'/>"></script>

  <script src="<c:url value='/scripts/pages/messaging/notifications.js'/>"></script>
  <script src="<c:url value='/scripts/pages/messaging/messages.js'/>"></script>

<body>
  <div class="mainDocumentView">
    <axt:fileTreeBrowser />
    <div class="documentPanel">
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
      <script>
        const enforceOntologies = "${enforce_ontologies}" === "true";
        const allowBioOntologies = "${allow_bioOntologies}"  === "true";
      </script>
      <!-- Has a hidden input field with template name. '.createDocument' => the action URL will be updated when the displayed folder is refreshed  -->
      <form id='createPopularSD' class="createDocument" method='POST' action="${createFromTemplateURL}"></form>
      <!-- Imports 'send message' functionality (local and integrations) -->
      <%@ include file="../workspace/editor/include/messagingToolbarButtons.jsp"%>

      <div
        id="toolbar2"
        data-workspace-folder-id="${workspaceFolderId}"
        data-can-create-record="${permDTO.createRecord}",
        data-settings-key="${settingsKey}"
        data-pio-enabled="${pioEnabled}"
        data-evernote-enabled="${evernoteEnabled}"
        data-aspose-enabled="${asposeEnabled}"
        data-can-delete="${permDTO.deleteRecord}"
        data-can-share="${canShare}">
      </div>
      <div id="tinymce-ketcher"></div>
      <jsp:include page="notebookMainPanel.jsp" >
        <jsp:param name="publicDocument" value="false"/>
      </jsp:include>
    </div>
  </div>
  <jsp:include page="notebookFooter.jsp" />
  <jsp:include page="../mediaGallery.jsp" />
  <jsp:include page="../workspace/editor/include/messagingDialogs.jsp" />
  <axt:createFromTemplateDlg />
  <!-- Dialog upon Create-Other Documents -->
  <axt:formCreateMenuDialog parentFolderId="${selectedNotebookId}"></axt:formCreateMenuDialog>
  <!-- React Scripts -->
  <script src="<c:url value='/ui/dist/notebookToolbar.js'/>"></script>
  <script src="<c:url value='/ui/dist/fileTreeToolbar.js'/>"></script>
  <script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>
  <script src="<c:url value='/ui/dist/materialsListing.js'/>"></script>
  <!--End React Scripts -->
