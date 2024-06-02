<%@ include file="/common/taglibs.jsp"%>
<head>
    <title>Admin login</title>
    <meta name="heading" content="<fmt:message key='login.heading'/>"/>

    <script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>

    <link href="/styles/sign-in-up-common.css" rel="stylesheet">
    <link href="/styles/bootstrap-custom-flat.css" rel="stylesheet">
    <link href="/styles/simplicity/header.css" rel="stylesheet">
    <link href="/styles/authentication/login.css" rel="stylesheet">
    
    <%-- key icon. FontAwesome doesn't work for some reason. Futher investigation is needed. --%>
    <link href="<c:url value='/styles/fontello-key/css/fontello.css'/>" rel="stylesheet"> 
    <script src="<c:url value='/scripts/global.js'/>"></script>
</head>

<div class="page"> 

  <jsp:include page="/WEB-INF/pages/sso/ssoHeader.jsp" />

  <jsp:include page="/common/externalPagesNavbar.jsp" />

  <%-- BEGIN: login form --%>
  <div class="rs-sign-in-form bootstrap-custom-flat">
    <div class="rs-sign-in-form__head ">
      <span>Welcome to RSpace</span>
    </div>
    
    <c:url value='/login' var="loginURL"></c:url>
    <form method="post" id="loginForm" action="${loginURL}" autocomplete="off" class="form-horizontal rs-sign-in-form__body">
      <fieldset>

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <input id="username" type="text" name="username" placeholder="User" required="required" autofocus 
                  class="form-control rs-field__input
                        <c:if test='${param.loginException != null}'>
                        rs-field__input--invalid
                        </c:if>
                        " />
          <div class="rs-field__icon glyphicon glyphicon-user"></div>
        </div>

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <input type="hidden" name="adminLogin" />
          <input id="password" type="password" name="password" placeholder="Password" required="required" 
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
                  <fmt:message key="errors.login.adminLogin.wrongSignupSource"/>
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

    </form>
  </div>
  <%-- END: login form --%>  

</div>
