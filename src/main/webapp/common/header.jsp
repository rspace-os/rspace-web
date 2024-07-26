<%@ include file="/common/taglibs.jsp"%>
<meta name="google-signin-client_id" content="731959816562-p2igqsv375nta4bd0g3c3tkq5r90kpg5.apps.googleusercontent.com">
<script src="<c:url value='/scripts/pages/signup/google-signin.js'/>"></script>
<script src="https://accounts.google.com/gsi/client?onload=onLoad" async defer></script>
<script async defer src="https://apis.google.com/js/api.js"></script>

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

    <nav class="rs-navbar bootstrap-custom-flat">
      <ul class="rs-navbar__list rs-navbar__list--pull-right">
        <li class="rs-navbar__item rs-navbar__item--pull-left" id="branding">
          <a href="${applicationScope['RS_DEPLOY_PROPS']['bannerImageLink']}">
            <rst:hasDeploymentProperty name="cloud" value="true">
              <img src="<c:url value='/images/mainLogoCloudN2.png'/>" alt="RSpace" class="rs-navbar__icon" />
            </rst:hasDeploymentProperty>
            <rst:hasDeploymentProperty name="cloud" value="false">
              <img src="<c:url value='/public/banner'/>" alt="RSpace" class="rs-navbar__icon"
                data-src="${applicationScope['RS_DEPLOY_PROPS']['bannerImageName']}" />
            </rst:hasDeploymentProperty>
          </a>
        </li>
        <li class="rs-navbar__item <c:if test="${isWorkspacePage eq true}"> rs-navbar__item--active </c:if>">
          <a href="<c:url value='/workspace'/>" class="rs-navbar__tab" id="top-menu_workspace">
            <fmt:message key="menu.topleveltabs.workspace" />
          </a>
        </li>

        <li class="rs-navbar__item <c:if test="${pageContext.request.servletPath eq '/gallery'}">
          rs-navbar__item--active </c:if>">
          <a href="<c:url value='/gallery'/>" class="rs-navbar__tab" id="top-menu_gallery">
            <fmt:message key="menu.topleveltabs.gallery" />
          </a>
        </li>
        <li class="rs-navbar__item <c:if test="${pageContext.request.servletPath eq '/dashboard'}">
          rs-navbar__item--active </c:if>">
          <a href="<c:url value='/dashboard'/>" class="rs-navbar__tab">
            <fmt:message key="menu.topleveltabs.dashboard" />
          </a>
        </li>
        <li class="rs-navbar__item <c:if test="${pageContext.request.servletPath eq '/apps' or pageContext.request.servletPath eq '/newApps'}"> rs-navbar__item--active
          </c:if>">
          <a href="<c:url value='/apps'/>" class="rs-navbar__tab" id="top-menu_apps">
            <fmt:message key="menu.topleveltabs.apps" />
          </a>
        </li>
        <li  class="rs-navbar__item <c:if test=" ${pageContext.request.servletPath eq '/inventory' }">
          rs-navbar__item--active </c:if> inventory-tab">
          <a href="<c:url value='/inventory'/>"  class="rs-navbar__tab" id="top-menu_inventory">
            <fmt:message key="menu.topleveltabs.inventory" />
          </a>
        </li>

        <shiro:hasRole name="ROLE_PI">
          <li class="rs-navbar__item <c:if test="${isMyRSpacePage eq true}"> rs-navbar__item--active </c:if>">
            <a href="<c:url value='/groups/viewPIGroup'/>" class="rs-navbar__tab" id="top-menu_myrspace">
              <fmt:message key="menu.topleveltabs.admin" />
            </a>
          </li>
        </shiro:hasRole>
        <shiro:lacksRole name="ROLE_PI">
          <li class="rs-navbar__item <c:if test="${isMyRSpacePage eq true}"> rs-navbar__item--active </c:if>">
            <a href="<c:url value='/userform'/>" class="rs-navbar__tab" id="top-menu_myrspace">
              <fmt:message key="menu.topleveltabs.admin" />
            </a>
          </li>
        </shiro:lacksRole>

        <shiro:hasAnyRoles name="ROLE_SYSADMIN,ROLE_ADMIN">
          <li class="rs-navbar__item <c:if test="${isSystemPage eq true}"> rs-navbar__item--active </c:if>">
            <a href="<c:url value='/system'/>" class="rs-navbar__tab">
              <fmt:message key="menu.topleveltabs.sysadmin" />
            </a>
          </li>
        </shiro:hasAnyRoles>
        <c:if test="${publish_allowed}">
          <li class="rs-navbar__item">
            <a href="<c:url value='/public/publishedView/publishedDocuments' />" class="rs-navbar__tab">Published</a>
          </li>
        </c:if>
        <shiro:authenticated>
          <li class="rs-navbar__item rs-navbar__item--non-activatable">
            <div id="accountTopBarDiv" class="rs-navbar__tab rs-dropdown" tabindex="0">
              <ul class="rs-dropdown__menu">
                <li>
                  <a href="/userform" class="rs-dropdown__item rs-dropdown__option rs-info" id="currentIn">
                    <div id="currentInName" class="rs-info__name">
                      ${sessionScope.userInfo.fullName}
                    </div>
                    <div class="rs-info__email">
                      ${sessionScope.userInfo.email}
                    </div>
                  </a>
                </li>

                <c:if test="${not empty sessionScope.userInfo.previousLastLogin}">
                  <li class="rs-dropdown__divider"></li>
                  <li>
                    <div class="rs-dropdown__item rs-info">
                      <div class="rs-info__name">
                        Last Session:
                      </div>
                      <div class="rs-info__name" id="previousIn">
                        <rst:relDate input="${sessionScope.userInfo.previousLastLogin}" relativeForNDays="3">
                        </rst:relDate>
                      </div>
                    </div>
                  </li>
                </c:if>

                <li class="rs-dropdown__divider"></li>
                <li>
                  <a href="#" id="logout" class="rs-dropdown__item rs-dropdown__option">
                    Sign Out
                  </a>
                </li>

              </ul>
              Account
              <span class="rs-arrow-down"></span>
            </div> <!-- rs-dropdown end -->

          </li>
        </shiro:authenticated>

      </ul> <!-- rs-navbar__list end -->
    </nav>

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
