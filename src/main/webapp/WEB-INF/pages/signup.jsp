<%@ include file="/common/taglibs.jsp"%>
<head>
    <title><spring:message code="signup.title"/></title>
    <meta name="heading" content="<spring:message code='signup.heading'/>"/>
    <meta name="google-signin-client_id" content="731959816562-p2igqsv375nta4bd0g3c3tkq5r90kpg5.apps.googleusercontent.com">
    <link href="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
    <script src="<rst:assetUrl value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
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
      <script src="<rst:assetUrl value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
      <script src="<rst:assetUrl value='/scripts/pages/signup/signup.js'/>"></script>
      <script src="https://accounts.google.com/gsi/client" async defer></script>
      <script src="<rst:assetUrl value='/scripts/pages/signup/google-signin.js'/>"></script>
      <script src="<rst:assetUrl value='/scripts/bower_components/mustache/v420/mustache.min.js'/>"></script>
    </rst:hasDeploymentProperty>

    <link href="<rst:assetUrl value='/styles/sign-in-up-common.css'/>" rel="stylesheet">
    <link href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" rel="stylesheet">
    <link href="<rst:assetUrl value='/styles/simplicity/header.css'/>" rel="stylesheet">
    <link href="<rst:assetUrl value='/styles/authentication/login.css'/>" rel="stylesheet">
    <link href="<rst:assetUrl value='/styles/fontello-key/css/fontello.css'/>" rel="stylesheet">
</head>

<div class="page">
  <spring:message code="signup.browserWarning.iconAlt" var="signupBrowserIconAlt"/>
  <div id="browser-warning" class="bootstrap-custom-flat">
      <h4><spring:message code="signup.browserWarning.heading"/></h4>
      <p><spring:message code="signup.browserWarning.notice1"/></p>
      <p><spring:message code="signup.browserWarning.notice2"/></p>
      <div class="row">
        <a rel="noreferrer" href="https://www.google.co.uk/chrome/" target="_blank">
          <div class="col-md-4 browser-container">
            <img src="/images/icons/chrome-icon.png" alt="${signupBrowserIconAlt}"><br />
            <spring:message code="signup.browserWarning.chrome"/>
          </div>
        </a>
        <a rel="noreferrer" href="https://www.mozilla.org/en-GB/firefox/" target="_blank">
          <div class="col-md-4 browser-container">
            <img src="/images/icons/firefox-icon.png" alt="${signupBrowserIconAlt}"><br />
            <spring:message code="signup.browserWarning.firefox"/>
          </div>
        </a>
        <a rel="noreferrer" href="https://www.apple.com/uk/safari/" target="_blank">
          <div class="col-md-4 browser-container">
            <img src="/images/icons/safari-icon.png" alt="${signupBrowserIconAlt}"><br />
            <spring:message code="signup.browserWarning.safari"/>
          </div>
        </a>
      </div>
    </div>
    
  <jsp:include page="/common/externalPagesNavbar.jsp" />

  <!-- BEGIN: sign up form -->
  <div class="rs-sign-in-form bootstrap-custom-flat">
    <div class="rs-sign-in-form__head">
      <spring:message code="signup.form.fillDetailsPrompt"/>
    </div>
    
    <spring:bind path="user.*"></spring:bind>

    <form:form method="post" id="signupForm" 
               action="signup" autocomplete="off" 
               role="form" modelAttribute="user"
               class="form-horizontal rs-sign-in-form__body">
      <fieldset>

        <spring:message code="signup.form.usernamePlaceholder" var="signupUsernamePlaceholder"/>
        <spring:message code="signup.form.usernameLengthTitle" arguments="${applicationScope['RS_DEPLOY_PROPS']['minUsernameLength']}" var="signupUsernameLengthTitle"/>
        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="username" class="sr-only"><spring:message code="signup.form.createUsernameLabel"/></label>
          <rst:hasDeploymentProperty name="standalone" value="true">
            <form:input id="username" type="text"
                        path="username"
                        placeholder="${signupUsernamePlaceholder}"
                        required="required"
                        autofocus="true"
                        pattern="[A-Za-z0-9@\.]{${applicationScope['RS_DEPLOY_PROPS']['minUsernameLength']},50}"
                        title="${signupUsernameLengthTitle}"
                        class="form-control rs-field__input" />
          </rst:hasDeploymentProperty>
          <rst:hasDeploymentProperty name="standalone" value="false">
            <form:input id="username" type="text"
                        path="username"
                        readonly="true"
                        pattern="[a-zA-Z0-9]{6,50}"
                        class="form-control rs-field__input" />
          </rst:hasDeploymentProperty>
          <div class="rs-field__icon glyphicon glyphicon-user"></div>
          <p class="form-text"><spring:message code="signup.form.usernameLengthHint"/></p>
          <form:errors class="rs-tooltip error" path="username"></form:errors>
        </div>

        <rst:hasDeploymentProperty name="standalone" value="true">
          <spring:message code="signup.form.passwordPlaceholder" var="signupPasswordPlaceholder"/>
          <spring:message code="signup.form.passwordCharsTitle" var="signupPasswordCharsTitle" htmlEscape="true"/>
          <div class="form-group col-lg-12 rs-field rs-field--input">
            <label for="password" class="sr-only"><spring:message code="signup.form.createPasswordLabel"/></label>
            <form:input id="password" type="password"
                        path="password"
                        placeholder="${signupPasswordPlaceholder}"
                        required="required"
                        pattern="[ -~]{8,50}"
                        title="${signupPasswordCharsTitle}"
                        class="form-control rs-field__input"/>
            <p class="form-text"><spring:message code="signup.form.passwordCharsHint"/></p>
            <div class="rs-field__icon glyphicon fa-key"></div>
            <form:errors class="rs-tooltip error" path="password"></form:errors>
          </div>

          <spring:message code="signup.form.confirmPasswordPlaceholder" var="signupConfirmPasswordPlaceholder"/>
          <spring:message code="signup.form.confirmPasswordTitle" var="signupConfirmPasswordTitle"/>
          <div class="form-group col-lg-12 rs-field rs-field--input">
            <label for="confirmPassword" class="sr-only"><spring:message code="signup.form.confirmPasswordLabel"/></label>
            <form:input id="confirmPassword" type="password"
                        path="confirmPassword"
                        placeholder="${signupConfirmPasswordPlaceholder}"
                        required="required"
                        pattern="[ -~]{8,50}"
                        title="${signupConfirmPasswordTitle}"
                        class="form-control rs-field__input"/>
            <div class="rs-field__icon glyphicon fa-key"></div>
            <form:errors class="rs-tooltip error" path="confirmPassword"></form:errors>
          </div>
        </rst:hasDeploymentProperty>

        <c:set var="nameEditable" value="${not applicationScope['RS_DEPLOY_PROPS']['profileNameEditable']}"/>
        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="firstName" class="sr-only"><spring:message code="signup.form.firstNameLabel"/></label>
          <spring:message code="signup.form.firstNamePlaceholder" var="signupFirstNamePlaceholder"/>
          <form:input id="firstName" type="text"
                      path="firstName"
                      placeholder="${signupFirstNamePlaceholder}"
                      required="required"
                      readonly="${nameEditable}"
                      autofocus="true"
                      class="form-control rs-field__input" />
          <div class="rs-field__icon glyphicon glyphicon-pencil"></div>
          <form:errors class="rs-tooltip error" path="firstName"></form:errors>
        </div>

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="lastName" class="sr-only"><spring:message code="signup.form.lastNameLabel"/></label>
          <spring:message code="signup.form.lastNamePlaceholder" var="signupLastNamePlaceholder"/>
          <form:input id="lastName" type="text"
                      path="lastName"
                      placeholder="${signupLastNamePlaceholder}"
                      readonly="${nameEditable}"
                      required="required"
                      autofocus="true"
                      class="form-control rs-field__input" />
          <div class="rs-field__icon glyphicon glyphicon-pencil"></div>
          <form:errors class="rs-tooltip error" path="lastName"></form:errors>
        </div>

        <c:set var="emailEditable" value="${not applicationScope['RS_DEPLOY_PROPS']['profileEmailEditable']}"/>
        <spring:message code="form.emailAddressPlaceholder" var="signupEmailPlaceholder"/>
        <spring:message code="form.emailAddressTitle" var="signupEmailTitle"/>
        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="email" class="sr-only"><spring:message code="signup.form.emailLabel"/></label>
          <form:input id="email" type="email"
                      path="email"
                      placeholder="${signupEmailPlaceholder}"
                      required="required"
                      autofocus="true"
                      readonly="${emailEditable}"
                      maxlength="255"
                      pattern="[^ @]*@[^ @]*"
                      title="${signupEmailTitle}"
                      class="form-control rs-field__input" />
          <div class="rs-field__icon glyphicon glyphicon-envelope"></div>
          <form:errors class="rs-tooltip error" path="email"></form:errors>
        </div>
        <c:set var="signup_info">
            <spring:message code="pi.signup.info"/>
        </c:set>

        <rst:hasDeploymentProperty name="picreateGroupOnSignupEnabled" value="true">
          <c:set var="allowPiCreateGroupOnSignup" value="true" />
          <rst:hasDeploymentProperty name="SSOSelfDeclarePiEnabled" value="true">
            <c:if test="${not isAllowedPiRole}">
              <c:set var="allowPiCreateGroupOnSignup" value="false"/>
            </c:if>
          </rst:hasDeploymentProperty>

          <div class="form-group col-lg-12 rs-field rs-field--input pi-create-group-on-signup">
            <c:if test="${allowPiCreateGroupOnSignup}">
                <form:checkbox id="picreateGroupOnSignup"
                            path="picreateGroupOnSignup"
                            class="form-control rs-field__input checkbox" label="${signup_info}"/>
                <form:errors class="rs-tooltip error" path="picreateGroupOnSignup"></form:errors>
            </c:if>
            <c:if test="${not allowPiCreateGroupOnSignup}">
              <spring:message code="signup.form.piStatusNotice" arguments="${applicationScope['RS_DEPLOY_PROPS']['customerNameShort']}"/>
            </c:if>
          </div>
        </rst:hasDeploymentProperty>
        
        <rst:hasDeploymentProperty name="cloud" value="true">
          <spring:message code="signup.form.affiliationPlaceholder" var="signupAffiliationPlaceholder"/>
          <div class="form-group col-lg-12 rs-field rs-field--input">
            <label for="affiliation" class="sr-only"><spring:message code="signup.form.affiliationLabel"/></label>
            <form:input id="affiliation" type="text"
                        path="affiliation"
                        placeholder="${signupAffiliationPlaceholder}"
                        required="required"
                        autofocus="true"
                        maxlength="255"
                        class="form-control rs-field__input" />
            <div class="rs-field__icon glyphicon glyphicon-briefcase"></div>
            <form:errors class="rs-tooltip error" path="affiliation"></form:errors>
          </div>
        </rst:hasDeploymentProperty>

         <rst:hasDeploymentProperty name="userSignupCode" testNonBlankOnly="true" value="ignored">
             <spring:message code="signup.form.signupCodePlaceholder" var="signupCodePlaceholder"/>
             <label for="signupCode" class="sr-only"><spring:message code="signup.form.signupCodeLabel"/></label>
              <form:input id="signupCode" type="text"
                        path="signupCode"
                        placeholder="${signupCodePlaceholder}"
                        required="required"
                        autofocus="true"
                        autocomplete="signupCode"
                        maxlength="255"
                        class="form-control rs-field__input" />
             <form:errors class="rs-tooltip error" path="signupCode"></form:errors>
          </rst:hasDeploymentProperty>

        <rst:hasDeploymentProperty name="signupCaptchaEnabled" value="true">
          <script src="https://www.google.com/recaptcha/api.js" async defer></script>
          <div class="col-lg-12" style="margin-top: 20px;">
            <div class="g-recaptcha" data-sitekey="${applicationScope['RS_DEPLOY_PROPS']['signupCaptchaSiteKey']}"></div>
            <form:errors class="rs-tooltip error" path="captcha"></form:errors>
          </div>
        </rst:hasDeploymentProperty>

        <rst:hasDeploymentProperty name="cloud" value="true">
          <div class="form-group col-lg-12 rs-field rs-field--divider" style="margin-bottom: 0px;">
            <form:hidden path="token"/>
            <label>
              <input type="checkbox" value="remember-me" required/>
              <spring:message code="signup.form.termsAgreementLinkText" var="termsAgreementLinkText"/>
              <span class="rs-field__text rs-field__text--normal"><spring:message code="signup.form.termsAgreement">
                <spring:argument value='<a href="https://researchspace.com/terms-conditions/" target="_blank">${termsAgreementLinkText}</a>'/>
              </spring:message></span>
            </label>
          </div>
        </rst:hasDeploymentProperty>

        <div class="form-group col-lg-12 rs-field rs-field--divider">
          <button id="submit" role="button" type="submit" class="btn btn-primary rs-field__button">
            <spring:message code="signup.form.submitButton"/>
          </button>
        </div>

      </fieldset>

      <rst:hasDeploymentProperty name="cloud" value="true"> 
        <fieldset>
          <div class="form-group col-lg-12 rs-field">
            <div class="rs-field__text rs-field__text--normal"><spring:message code='button.signup.with.google'/></div>
          </div>
          <div class="form-group col-lg-12 rs-field rs-google-signup-menu sign-up">
            <div id="g_id_onload"
                 data-client_id="731959816562-p2igqsv375nta4bd0g3c3tkq5r90kpg5.apps.googleusercontent.com"
                 data-callback="onSignUp">
            </div>
            <div class="g_id_signin" data-type="standard" 
                 data-text="signup_with" data-logo_alignment="center" data-width="340">
          </div>
        </fieldset>
      </rst:hasDeploymentProperty>
     <input type="hidden" id="timezone_field" name="timezone" />
    </form:form>
  </div>  
  <!-- END: sign up form -->

</div>

<script src="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
<script type="text/javascript">
  $(document).ready(function() {
    $.fn.bootstrapButton = $.fn.button.noConflict();
  });
</script>
