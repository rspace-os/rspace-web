<%@ include file="/common/taglibs.jsp"%>

<head>
  <script src="<c:url value='/public/ui/dist/runtime.js'/>"></script>
  <script src="/scripts/global.js"></script>
  <script>
    //if we dont delete id_tokens (Oauth tokens that identify the user) then using the public link can result in actual logged in users being switched
    //for previously logged in users. See JwtService and login.jsp and logout.jsp. The tokens are read in authenticate() method in AbstractApiAuthenticator
    //which is called by the ApiAuthenticationInterceptor preHandle().
    window.sessionStorage.removeItem("id_token");
    const AppContext_Glbal = new String("${pageContext.request.contextPath}");
    window.onload = function () {
      var loadTime = window.performance.timing.domContentLoadedEventEnd-window.performance.timing.navigationStart;
      console.log('Page load time is '+ loadTime);
    }
  </script>
  <%@ include file="/common/meta.jsp"%>
  <jsp:include page="notebookHeader.jsp" />
  <link rel="stylesheet" href="<c:url value='/styles/bootstrap-custom-flat.css'/>" />
  <link media="all" href="<c:url value='/styles/simplicity/theme.css'/>" rel="stylesheet" />
  <link media="print" href="<c:url value='/styles/simplicity/print.css'/>" rel="stylesheet" />
  <link href="<c:url value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>" rel="stylesheet" />
  <link href="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
  <link href="<c:url value='/styles/rs-global.css'/>" rel="stylesheet" />

  <script src="<c:url value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/mustache/mustache.min.js'/>"></script>
  <script src="<c:url value='/scripts/jquery.toastmessage.js'/>"></script>
  <script src="<c:url value='/scripts/global.js'/>"></script>
  <script src="<c:url value='/scripts/global.settingsStorage.js'/>"></script>
  <script src="<c:url value='/scripts/pages/workspace/editor/public_documentView.js'/>"></script>
  <link rel="stylesheet" href="<c:url value='/styles/jquery.toastmessage.css'/>" />

  <script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>

  <script src="<c:url value='/scripts/pages/public_journal.js'/>"></script>
  <script src="<c:url value='/scripts/pages/journal.js'/>"></script>
  <script>
    journal(jQuery, publicJournalExtensions);
  </script>
</head>


<body>
  <div class="mainDocumentView public">
    <div class="documentPanel">
      <jsp:include page="/common/externalPagesNavbar.jsp" />

      <%-- The public view is for a document made accessible to non RSpace users --%>
      <div id="public_document_view"/>
      <jsp:include page="notebookMainPanel.jsp" >
        <jsp:param name="publicDocument" value="true"/>
      </jsp:include>
    </div>
  </div>
  <jsp:include page="notebookFooter.jsp" />

  <!-- React Scripts -->
  <script src="<c:url value='/public/ui/dist/baseSearch.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/tinymceSidebarInfo.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/internalLink.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/exportModal.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/previewInfo.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/snapGeneDialog.js'/>"></script>
  <!--End React Scripts -->

  <script>
    $(window).on('load',function () {
      makeImageLinksPublic();
      disableLinkedDocumentsAndLinkedFiles();
      makeSVGPublic();
      hideInfoPopups();
      hidePreviewButtons();
      hideEditButtons();
    });
  </script>

</body>