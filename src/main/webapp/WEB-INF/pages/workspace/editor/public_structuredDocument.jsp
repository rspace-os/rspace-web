<%@ include file="/common/taglibs.jsp"%>
<%@ include file="/common/meta.jsp"%>

<head> 
<!-- Import React and Toast messages on any page that has a  header -->
  <script src="<c:url value='/public/ui/dist/runtime.js'/>"></script>

  <script>
    //if we dont delete id_tokens (Oauth tokens that identify the user) then using the public link can result in actual logged in users being switched
    //for previously logged in users. See JwtService and login.jsp and logout.jsp. The tokens are read in authenticate() method in AbstractApiAuthenticator
    //which is called by the ApiAuthenticationInterceptor preHandle().
    window.sessionStorage.removeItem("id_token");
    const AppContext_Glbal = new String("${pageContext.request.contextPath}");
  
  </script>
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
  <link rel="stylesheet" href="<c:url value='/styles/jquery.toastmessage.css'/>" />
  <script defer src="<c:url value='/scripts/segment.js'/>"></script>
  
  <script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>

</head>

<body>

  <script>
    var workspaceSettings = {};
    workspaceSettings.currentUser = "${sessionScope.userInfo.username}";
    const sharedFolderID = "${sharedFolderID}";
    const sharingUser = "${sharingUser}";
    const documentName = "${documentName}"
    const contactDetails = "${contactDetails}"
    const publicationSummary = "${publicationSummary}"
  </script>
  <script src="<c:url value='/scripts/pages/workspace/editor/public_documentView.js'/>"></script>

  <div class="mainDocumentView public">

    <jsp:include page="/common/externalPagesNavbar.jsp" />

    <h1 class="publicTitle">${documentName}</h1>
    <h3 class="publicSummary">${publicationSummary}</h3>
    <c:if test="${not empty contactDetails}">
      <h3 class="publicSummary">contact: ${contactDetails}</h3>
    </c:if>
  
    <div class="documentPanel">
    <jsp:include page="structuredDocumentMainPanel.jsp" >
      <jsp:param name="publicDocument" value="true"/>
    </jsp:include>
    </div>
  <div id="tempData" style="display: none"></div>
  <script>
    $(window).on('load',function() {
      const publicationSummary = "${publicationSummary}";
      const publishOnInternet = "${publishOnInternet}";
        makeImageLinksPublic();
        disableLinkedDocumentsAndLinkedFiles();
        makeSVGPublic();
        hideInfoPopups();
        hidePreviewButtons();
        if(publishOnInternet === "true") {
          addMetaToHeader(publicationSummary);
        } else {
          forbidBots();
        }
    });
  </script>

  <!-- React Scripts -->
  <script src="<c:url value='/public/ui/dist/tinymceSidebarInfo.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/internalLink.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/exportModal.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/previewInfo.js'/>"></script>
  <script src="<c:url value='/public/ui/dist/snapGeneDialog.js'/>"></script>
  <!--End React Scripts -->

</body>
