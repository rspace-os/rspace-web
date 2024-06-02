<%@ include file="/common/taglibs.jsp"%>
<head>
    <title><fmt:message key="login.title"/></title>
    <meta name="heading" content="<fmt:message key='login.heading'/>"/>
    <meta name="google-signin-client_id" content="731959816562-p2igqsv375nta4bd0g3c3tkq5r90kpg5.apps.googleusercontent.com">

    <script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
  <script>

    $(document).ready(function () {
      const isPublishAllowed = $.get("/public/publishedView/ajax/publishedDocuments/allowed");
      isPublishAllowed.done(function (resp) {
        if(!resp.data) {
          $('#publishedDocuments').hide();
        }
      });
    });
  </script>
    <rst:hasDeploymentProperty name="cloud" value="true"> 
      <script src="<c:url value='/scripts/pages/signup/google-signin.js'/>"></script>
      <script src="https://accounts.google.com/gsi/client" async defer></script>
      <script src="<c:url value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
      <link href="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
      <script src="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
      <script src="<c:url value='/scripts/bower_components/mustache/mustache.min.js'/>"></script>
    </rst:hasDeploymentProperty> 
    
    <link href="/styles/sign-in-up-common.css" rel="stylesheet">
    <link href="/styles/bootstrap-custom-flat.css" rel="stylesheet">
    <link href="/styles/simplicity/header.css" rel="stylesheet">
    <link href="/styles/authentication/login.css" rel="stylesheet">
    
    <%-- key icon. FontAwesome doesn't work for some reason. Futher investigation is needed. --%>
    <link href="<c:url value='/styles/fontello-key/css/fontello.css'/>" rel="stylesheet"> 
    <script src="<c:url value='/scripts/global.js'/>"></script>
</head>

<!-- Remove the inventory JWT token -->
<script type="text/javascript">
  window.sessionStorage.removeItem("id_token");
</script>

<div class="page"> 
  
  <%-- BEGIN: navigation --%>
  <!--[if IE]>
    <link rel="stylesheet" type="text/css" href="/styles/ie.css" />
  <![endif]-->
  <div id="browser-warning" class="bootstrap-custom-flat">
    <h4>Browser upgrade recommended to use RSpace</h4>
    <p>It looks like you are using a Microsoft browser - sorry, we don't support either Internet Explorer or Edge. Some RSpace features will not work properly with these browsers.</p>
    <p>We <strong>do</strong> fully support Chrome, Firefox and Safari. You can use the links below to install one of these browsers if you don't already have it.</p>
    <div class="row">
      <a href="https://www.google.co.uk/chrome/" target="_blank" rel="noopener">
        <div class="col-md-4 browser-container">
          <img src="/images/icons/chrome-icon.png" alt="Icon by Martin Leblanc"><br />
          Chrome
        </div>
      </a>
      <a href="https://www.mozilla.org/en-GB/firefox/" target="_blank" rel="noopener">
        <div class="col-md-4 browser-container">
          <img src="/images/icons/firefox-icon.png" alt="Icon by Martin Leblanc"><br />
          Firefox
        </div>
      </a>
      <a href="https://www.apple.com/uk/safari/" target="_blank" rel="noopener">
        <div class="col-md-4 browser-container">
          <img src="/images/icons/safari-icon.png" alt="Icon by Martin Leblanc"><br />
          Safari
        </div>
      </a>
    </div>
  </div>

  <jsp:include page="/common/externalPagesNavbar.jsp" />

  <%-- BEGIN: login form --%>
  <div class="rs-sign-in-form bootstrap-custom-flat">
    <div class="rs-sign-in-form__head ">
      <span>Welcome to RSpace</span>
    </div>
     
    
    <c:url value='/login' var="loginURL"></c:url>
    <form method="post" id="loginForm" action="${loginURL}" autocomplete="off" class="form-horizontal rs-sign-in-form__body">
      <c:if test="${fn:length(applicationScope['RS_DEPLOY_PROPS']['customLoginContent']) > 0}">
      <p style="padding: 5px">
      ${applicationScope['RS_DEPLOY_PROPS']['customLoginContent']}
      <p/>
    </c:if>
      <fieldset>

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <input id="username" type="text" name="username" placeholder="User" required="required" autofocus 
                  class="form-control rs-field__input
                        <c:if test='${requestScope.shiroLoginFailure != null}'>
                        rs-field__input--invalid
                        </c:if>
                        " />
          <div class="rs-field__icon glyphicon glyphicon-user"></div>

          <div class="rs-tooltip resetUsernameDiv">
              <a href='<c:url value="public/requestUsernameReminder"/>' tabindex="1"> Forgotten your username?</a>
          </div>
        </div>

          <div class="form-group col-lg-12 rs-field rs-field--input">
          <c:if test="${param.maintenanceLogin != null}">
            <input type="hidden" name="maintenanceLogin" />
          </c:if>

          <input type="hidden" id="timezone_field" name="timezone" />

          <input id="password" type="password" name="password" placeholder="Password" required="required" 
                 class="form-control rs-field__input
                        <c:if test='${requestScope.shiroLoginFailure != null}'>
                        rs-field__input--invalid
                        </c:if>
                        "/>

          <div class="rs-field__icon glyphicon fa-key"></div>

          <%-- BEGIN: tooltips --%>
          <div class="rs-tooltip resetPasswordDiv">
            <a href='<c:url value="public/requestPasswordReset" />'tabindex="2"> Forgotten your password?</a>
          </div>

          <c:if test="${requestScope.shiroLoginFailure != null}">
            <c:choose>
              <c:when test="${requestScope.checkedExceptionMessage != null}">
                <div class="rs-tooltip"> 
                  ${requestScope.checkedExceptionMessage} 
                </div>
              </c:when>
              <c:otherwise>
                <div class="rs-tooltip">
                  <fmt:message key="errors.login.credentials.wrong"/>
                </div>
                <div class="rs-tooltip">
                  <fmt:message key="errors.login.lockout.policy.description"/>
                </div>
              </c:otherwise>
            </c:choose>
          </c:if>
          <%-- END: tooltips --%>
        </div>

        <div class="form-group col-lg-12 rs-field rs-field--input">

        </div>

        <div class="form-group col-lg-12 rs-field">
          <button id="submit" role="button" type="submit" class="btn btn-primary rs-field__button">
            <span><fmt:message key='button.login' /></span>
          </button>
        </div>

      </fieldset>

      <rst:hasDeploymentProperty name="cloud" value="true"> 
        <fieldset id="rs-google-signin-fieldset">
          <div class="form-group col-lg-12 rs-field">
            <div class="rs-field__text rs-field__text--normal"><fmt:message key='button.login.with.google'/></div>
          </div>
          <div class="form-group col-lg-12 rs-field rs-google-signup-menu log-in">
            <%--<div class="g-signin2 rs-google-signin-button" data-onsuccess="onSignIn">  </div>--%>
            <div id="g_id_onload"
                 data-client_id="731959816562-p2igqsv375nta4bd0g3c3tkq5r90kpg5.apps.googleusercontent.com"
                 data-itp_support="true" data-callback="onSignIn">
            </div>
            <div class="g_id_signin" data-type="standard" 
                 data-text="continue_with" data-logo_alignment="center" data-width="340">
            </div>
          </div>
        </fieldset>

      </rst:hasDeploymentProperty>

    </form>
  </div>
  <%-- END: login form --%>  

</div>
