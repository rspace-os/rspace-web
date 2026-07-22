<%@ include file="/common/taglibs.jsp"%>
<head>
    <title><spring:message code="adminLogin.title"/></title>
    <meta name="heading" content="<spring:message code='login.heading'/>"/>

    <script src="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>

    <link href="<rst:assetUrl value='/styles/sign-in-up-common.css'/>" rel="stylesheet">
    <link href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" rel="stylesheet">
    <link href="<rst:assetUrl value='/styles/simplicity/header.css'/>" rel="stylesheet">
    <link href="<rst:assetUrl value='/styles/authentication/login.css'/>" rel="stylesheet">

    <%-- key icon. FontAwesome doesn't work for some reason. Futher investigation is needed. --%>
    <link href="<rst:assetUrl value='/styles/fontello-key/css/fontello.css'/>" rel="stylesheet">
    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
</head>

<div class="page">

  <jsp:include page="/WEB-INF/pages/sso/ssoHeader.jsp" />

  <jsp:include page="/common/externalPagesNavbar.jsp" />

  <%-- BEGIN: login form --%>
  <div class="rs-sign-in-form bootstrap-custom-flat">
    <div class="rs-sign-in-form__head ">
      <span><spring:message code="adminLogin.heading"/></span>
    </div>

    <c:url value='/login' var="loginURL"></c:url>
    <form method="post" id="loginForm" action="${loginURL}" autocomplete="off" class="form-horizontal rs-sign-in-form__body">
      <fieldset>

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <input id="username" type="text" name="username" placeholder="<spring:message code='adminLogin.usernamePlaceholder'/>" required="required" autofocus
                  class="form-control rs-field__input
                        <c:if test='${param.loginException != null}'>
                        rs-field__input--invalid
                        </c:if>
                        " />
          <div class="rs-field__icon glyphicon glyphicon-user"></div>
        </div>

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <input type="hidden" name="adminLogin" />
          <input id="password" type="password" name="password" placeholder="<spring:message code='adminLogin.passwordPlaceholder'/>" required="required"
                 class="form-control rs-field__input
                        <c:if test='${param.loginException != null}'>
                        rs-field__input--invalid
                        </c:if>
                        "/>

          <div class="rs-field__icon glyphicon fa-key"></div>

          <c:if test="${param.loginException != null}">
            <c:choose>
              <c:when test="${param.loginException eq 'IncorrectSignupSourceException'}">
                <div class="rs-tooltip">
                  <spring:message code="errors.login.adminLogin.wrongSignupSource"/>
                </div>
              </c:when>
              <c:otherwise>
                <div class="rs-tooltip">
                  <spring:message code="errors.login.credentials.wrong"/>
                </div>
                <div class="rs-tooltip">
                  <spring:message code="errors.login.lockout.policyDescription"/>
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
            <span><spring:message code='button.login.label'/></span>
          </button>
        </div>

      </fieldset>

    </form>
  </div>
  <%-- END: login form --%>

  <!-- Remove the inventory JWT token -->
  <script type="text/javascript">
    window.sessionStorage.removeItem("id_token");
  </script>


</div>
