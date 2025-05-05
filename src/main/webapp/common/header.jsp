<%@ include file="/common/taglibs.jsp"%>
<meta name="google-signin-client_id" content="731959816562-p2igqsv375nta4bd0g3c3tkq5r90kpg5.apps.googleusercontent.com">
<c:if test="${isGoogleDriveAppEnabled}">
  <script src="https://accounts.google.com/gsi/client" async defer></script>
  <script src="https://apis.google.com/js/api.js" async defer></script>
</c:if>

<rst:hasDeploymentProperty name="cloud" value="true">
  <script src="<c:url value='/scripts/pages/signup/google-signin.js'/>"></script>
  <script src="https://accounts.google.com/gsi/client?onload=onLoad" async defer></script>
</rst:hasDeploymentProperty>

<script type="text/javascript">
  function removeInventoryAuthOnRunAs() {
    window.sessionStorage.removeItem("id_token");
  }
</script>

<script>
  $(document).on('click', '#logout', function (e) {
    //RSPAC-987 for case when we have google signout
    e.preventDefault();
    if (typeof gapi !== 'undefined' && gapi.auth2) {
      var auth2 = gapi.auth2.getAuthInstance();
      if (auth2) {
        auth2.signOut().then(function () {
          console.log('User signed out.');
        });
      } else {
        console.log('No GAPI authinstance defined');
      }
    } else {
      console.log('No GAPI defined');
    }
    window.location = "/logout";
  });

  function onLoad() {
    gapi.load('auth2', function () {
      gapi.auth2.init();
    });
  }
</script>

<!-- Constructed based on WebConfig.java, dispatcher-servlet.xml and admin.jsp -->
<c:set var="myRSpaceURLs"
  value="${fn:split('/admin/cloud/createCloudGroupSuccess,/import/archiveImport,/audit/auditing,/cloud/group/new,/directory,/groups/view,/groups/viewPIGroup,/record/share/manage,/userform,/workspace/editor/form,/workspace/editor/structuredDocument/audit,/workspace/trash/list',',')}" />
<!-- Detect MyRSpace URL (so the MyRSpace tab can be marked as active) -->
<c:set var="isMyRSpacePage" value="false" />
<c:forEach items="${myRSpaceURLs}" var="myRSpaceURL">
  <c:if test="${fn:startsWith(pageContext.request.servletPath, myRSpaceURL)}">
    <c:set var="isMyRSpacePage" value="true" />
  </c:if>
</c:forEach>


<!-- Detect System URL (so the System tab can be marked as active) -->
<c:set var="systemURLs" value="${fn:split('/community/admin,/groups/admin,/system',',')}" />
<c:set var="isSystemPage" value="false" />
<c:forEach items="${systemURLs}" var="systemURL">
  <c:if test="${fn:startsWith(pageContext.request.servletPath, systemURL)}">
    <c:set var="isSystemPage" value="true" />
  </c:if>
</c:forEach>


<!-- Detect Workspace URL (so the Workspace tab can be marked as active) -->
<c:set var="isWorkspacePage" value="false" />
<c:if test="${(fn:startsWith(pageContext.request.servletPath, '/workspace') && !isMyRSpacePage) ||
            fn:startsWith(pageContext.request.servletPath, '/notebookEditor')}">
  <c:set var="isWorkspacePage" value="true" />
</c:if>

<div class="header-wrapper">
  <!--[if IE]>
    <link rel="stylesheet" type="text/css" href="/styles/ie.css" />
  <![endif]-->
  <div id="browser-warning" class="bootstrap-custom-flat">
    <h4>Browser upgrade recommended to use RSpace</h4>
    <p>It looks like you are using a Microsoft browser - sorry, we don't support either Internet Explorer or Edge. Some
      RSpace features will not work properly with these browsers.</p>
    <p>We <strong>do</strong> fully support Chrome, Firefox and Safari. You can use the links below to install one of
      these browsers if you don't already have it.</p>
    <div class="row">
      <a rel="noreferrer" href="https://www.google.co.uk/chrome/" target="_blank">
        <div class="col-md-4 browser-container">
          <img src="/images/icons/chrome-icon.png" alt="Icon by Martin Leblanc"><br />
          Chrome
        </div>
      </a>
      <a rel="noreferrer" href="https://www.mozilla.org/en-GB/firefox/" target="_blank">
        <div class="col-md-4 browser-container">
          <img src="/images/icons/firefox-icon.png" alt="Icon by Martin Leblanc"><br />
          Firefox
        </div>
      </a>
      <a rel="noreferrer" href="https://www.apple.com/uk/safari/" target="_blank">
        <div class="col-md-4 browser-container">
          <img src="/images/icons/safari-icon.png" alt="Icon by Martin Leblanc"><br />
          Safari
        </div>
      </a>
    </div>
  </div>
  <c:if test="${pageContext.request.locale.language != 'en'}">
    <div id="switchLocale"><a href="<c:url value='/?locale=en'/>">
        <fmt:message key="webapp.name" /> in English</a></div>
  </c:if>

  <rst:hasDeploymentProperty name="standalone" value="false">
    <jsp:include page="/WEB-INF/pages/sso/ssoHeader.jsp" />
  </rst:hasDeploymentProperty>

  <header id="header" class="clearfix <c:if test="${sessionScope['rs.IS_RUN_AS']}">impersonating </c:if>">
    <div>
      <shiro:authenticated>
        <div id="incomingMaintenanceDiv" class="header-info" style="display:none">
          <a id="maintenanceDetailsLink" href="#">More</a>
        </div>
      </shiro:authenticated>

      <rs:isRunAs>
        <span style="background-color:#E4E8EE;" class="header-info header-info--right">
          <spring:message code="header.runAs.msg" />&nbsp;<shiro:principal/>.
          <a id="runAs"
             href="/logout/runAsRelease"
             class="ui-button-fix ui-widget ui-state-default ui-button-text-only ui-corner-left ui-corner-right"
             role="button"
             aria-disabled="false"
             style="padding:0 2px 0 2px;"
             onclick="removeInventoryAuthOnRunAs()"
          >
            Release
          </a>
        </span>
      </rs:isRunAs>
    </div>

    <div id="app-bar"></div>
    <script type="module" src="/../ui/dist/appBar.js"></script>

    <script type='text/javascript'>
      var AppContext_Glbal = new String("${pageContext.request.contextPath}");
    </script>

    <rst:hasDeploymentProperty name="standalone" value="false">
      <c:if test="${empty sessionScope.com_rs_timezone or  sessionScope.firstRequest == true}">
        <script src="<c:url value='/scripts/bower_components/jstz-detect/jstz.min.js'/>"></script>
        <script type="text/javascript">
          $(document).ready(function () {
            // this will be run on 1st pageload in session or
            var tz = jstz.determine(); //Determines the time zone of the browser client
            console.log("Timezone detected: " + tz.name());
            var d = new Date();
            d.setTime(d.getTime() + 365 * 24 * 60 * 60 * 1000); //1 year expiry
            var expires = 'expires=' + d.toUTCString();
            document.cookie = 'com_rs_timezone=' + tz.name() + '; ' + expires + '; /';
            // fallback in case cookies deleted or incognito
            $.post('/session/ajax/timezone', {
              "com_rs_timezone": tz.name()
            });
          });
        </script>
      </c:if>
    </rst:hasDeploymentProperty>

    <!-- initially hidden, this is a jquery-ui dialog that will hold notification content -->
    <div id="notificationsDlg" style="display:none">
      <!--  Holds the dynamic content of notification listings, populated on dialog open -->
      <div class="notificationList"></div>
    </div>
    <div id="messageDlg" style="display:none">
      <!--  initially hidden, this is a jquery-ui dialog and holds the dynamic content of message listings, populated on dialog open -->
      <div class="messageList"></div>
    </div>

    <rst:hasDeploymentProperty name="fileStoreType" value="EGNYTE">
      <jsp:include page="/WEB-INF/pages/connect/egnyte/egnyteHeader.jsp" />
    </rst:hasDeploymentProperty>

    <rst:hasDeploymentProperty name="netFileStoresExportEnabled" value="true">
      <script>
        RS.netFileStoresExportEnabled = true;
      </script>
    </rst:hasDeploymentProperty>

    <script>
      RS.asposeEnabled = ${applicationScope['RS_DEPLOY_PROPS']['asposeEnabled']};
    </script>

    <script>
      RS.chemistryProvider = "${applicationScope['RS_DEPLOY_PROPS']['chemistryProvider']}";
    </script>

    <script>
      $(document).ready(function () {
        var $navbar = $(".rs-navbar");
        var $dropdowns = $navbar.find(".rs-dropdown");

        $dropdowns.click(function () {
          $(this).toggleClass("rs-dropdown--open");
        });

        // closes the dropdown if you click outside.
        // Will not work nice if there are multiple dropdowns in the header! (Kris)
        window.onclick = function (event) {
          if (!event.target.matches('.rs-dropdown') && !event.target.matches('.rs-arrow-down')) {
            $dropdowns.removeClass("rs-dropdown--open");
          }
        }

        $('#top-menu_inventory').click(function() {
            RS.trackEvent('InventoryTabClicked');
        });

      });
    </script>

  </header>
</div>

<!-- Import React and Toast messages on any page that has a  header -->
<script src="<c:url value='/ui/dist/runtime.js'/>"></script>
<script src="<c:url value='/ui/dist/userDetails.js'/>"></script>

<div id="toast-message" data-test-id="toast-messages-wrapper"></div>
<script src="<c:url value='/ui/dist/toastMessage.js'/>"></script>

<div id="confirmation-dialog" data-test-id="confirmation-dialog"></div>
<script src="<c:url value='/ui/dist/confirmationDialog.js'/>"></script>
