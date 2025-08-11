<%@ include file="/common/taglibs.jsp"%>
<head>
    <title><fmt:message key="signup.title"/></title>
    <meta name="heading" content="<fmt:message key='signup.heading'/>"/>
    <meta name="google-signin-client_id" content="731959816562-p2igqsv375nta4bd0g3c3tkq5r90kpg5.apps.googleusercontent.com">
    <link href="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.css'/>" rel="stylesheet" />
    <script src="<c:url value='/scripts/bower_components/Apprise-v2/apprise-v2.js'/>"></script>
    <script src="<c:url value='/scripts/global.js'/>"></script>
    <script src="<c:url value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
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
      <script src="<c:url value='/scripts/bower_components/blockui/jquery.blockUI.js'/>"></script>
      <script src="<c:url value='/scripts/pages/signup/signup.js'/>"></script>
      <script src="https://accounts.google.com/gsi/client" async defer></script>
      <script src="<c:url value='/scripts/pages/signup/google-signin.js'/>"></script>
      <script src="<c:url value='/scripts/bower_components/mustache/mustache.min.js'/>"></script>
    </rst:hasDeploymentProperty>

    <link href="/styles/sign-in-up-common.css" rel="stylesheet">
    <link href="/styles/bootstrap-custom-flat.css" rel="stylesheet">
    <link href="/styles/simplicity/header.css" rel="stylesheet">
    <link href="/styles/authentication/login.css" rel="stylesheet">
    <link href="<c:url value='/styles/fontello-key/css/fontello.css'/>" rel="stylesheet">
</head>

<div class="page">
  <!--[if IE]>
    <link rel="stylesheet" type="text/css" href="/styles/ie.css" />
  <![endif]-->
  <div id="browser-warning" class="bootstrap-custom-flat">
      <h4>Browser upgrade recommended to use RSpace</h4>
      <p>It looks like you are using a Microsoft browser - sorry, we don't support either Internet Explorer or Edge. Some RSpace features will not work properly with these browsers.</p>
      <p>We <strong>do</strong> fully support Chrome, Firefox and Safari. You can use the links below to install one of these browsers if you don't already have it.</p>
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
    
  <jsp:include page="/common/externalPagesNavbar.jsp" />

  <!-- BEGIN: sign up form -->
  <div class="rs-sign-in-form bootstrap-custom-flat">
    <div class="rs-sign-in-form__head">
      Please fill in your details
    </div>
    
    <spring:bind path="user.*"></spring:bind>

    <form:form method="post" id="signupForm" 
               action="signup" autocomplete="off" 
               role="form" modelAttribute="user"
               class="form-horizontal rs-sign-in-form__body">
      <fieldset>

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="username" class="sr-only">Create Username</label>
          <rst:hasDeploymentProperty name="standalone" value="true">
            <form:input id="username" type="text" 
                        path="username"
                        placeholder="Create a Username" 
                        required="required" 
                        autofocus="true" 
                        pattern="[A-Za-z0-9@\.]{${applicationScope['RS_DEPLOY_PROPS']['minUsernameLength']},50}"
                        title="Minimum ${applicationScope['RS_DEPLOY_PROPS']['minUsernameLength']}, max 50 alphanumeric characters"
                        class="form-control rs-field__input" />
          </rst:hasDeploymentProperty>
          <rst:hasDeploymentProperty name="standalone" value="false">
            <form:input id="username" type="text" 
                        path="username"
                        readonly="true"
                        class="form-control rs-field__input" />
          </rst:hasDeploymentProperty>
          <div class="rs-field__icon glyphicon glyphicon-user"></div>
          <form:errors class="rs-tooltip error" path="username"></form:errors>
        </div>

        <rst:hasDeploymentProperty name="standalone" value="true">
          <div class="form-group col-lg-12 rs-field rs-field--input">
            <label for="password" class="sr-only">Create a Password</label>
            <form:input id="password" type="password" 
                        path="password"
                        placeholder="Create a Password" 
                        required="required"
                        pattern="[A-Za-z0-9@\.]{8,255}" 
                        title="Minimum 8, max 255 alphanumeric characters"
                        class="form-control rs-field__input"/>
            <div class="rs-field__icon glyphicon fa-key"></div>
            <form:errors class="rs-tooltip error" path="password"></form:errors>
          </div>
  
          <div class="form-group col-lg-12 rs-field rs-field--input">
            <label for="confirmPassword" class="sr-only">Confirm Password</label>
            <form:input id="confirmPassword" type="password"
                        path="confirmPassword" 
                        placeholder="Confirm Password" 
                        required="required"
                        pattern="[A-Za-z0-9@\.]{8,255}" 
                        title="Minimum 8, max 255 alphanumeric characters"
                        class="form-control rs-field__input"/>
            <div class="rs-field__icon glyphicon fa-key"></div>
            <form:errors class="rs-tooltip error" path="confirmPassword"></form:errors>
          </div>
        </rst:hasDeploymentProperty>

        <c:set var="nameEditable" value="${not applicationScope['RS_DEPLOY_PROPS']['profileNameEditable']}"/>
        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="firstName" class="sr-only">First Name</label>
          <form:input id="firstName" type="text"
                      path="firstName" 
                      placeholder="Your First Name" 
                      required="required"
                      readonly="${nameEditable}"
                      autofocus="true" 
                      class="form-control rs-field__input" />
          <div class="rs-field__icon glyphicon glyphicon-pencil"></div>
          <form:errors class="rs-tooltip error" path="firstName"></form:errors>
        </div> 

        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="lastName" class="sr-only">Last Name</label>
          <form:input id="lastName" type="text"
                      path="lastName" 
                      placeholder="Your Last Name" 
                      readonly="${nameEditable}"
                      required="required" 
                      autofocus="true" 
                      class="form-control rs-field__input" />
          <div class="rs-field__icon glyphicon glyphicon-pencil"></div>
          <form:errors class="rs-tooltip error" path="lastName"></form:errors>
        </div>   

        <c:set var="emailEditable" value="${not applicationScope['RS_DEPLOY_PROPS']['profileEmailEditable']}"/>
        <div class="form-group col-lg-12 rs-field rs-field--input">
          <label for="email" class="sr-only">Email address</label>
          <form:input id="email" type="email"
                      path="email" 
                      placeholder="Your Email Address" 
                      required="required" 
                      autofocus="true" 
                      readonly="${emailEditable}"
                      maxlength="255"
                      pattern="[^ @]*@[^ @]*"
                      title="Use a valid email address"
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
              Based on your status at ${applicationScope['RS_DEPLOY_PROPS']['customerNameShort']},
              you cannot become a PI unless a system administrator manually enables this for you.
            </c:if>
          </div>
        </rst:hasDeploymentProperty>
        
        <rst:hasDeploymentProperty name="cloud" value="true">
          <div class="form-group col-lg-12 rs-field rs-field--input">
            <label for="affiliation" class="sr-only">Affiliation</label>
            <form:input id="affiliation" type="text"
                        path="affiliation" 
                        placeholder="Your organisation - type and select" 
                        required="required" 
                        autofocus="true" 
                        maxlength="255"
                        class="form-control rs-field__input" />
            <div class="rs-field__icon glyphicon glyphicon-briefcase"></div>
            <form:errors class="rs-tooltip error" path="affiliation"></form:errors>
          </div>
        </rst:hasDeploymentProperty>
        
         <rst:hasDeploymentProperty name="userSignupCode" testNonBlankOnly="true" value="ignored">
         
             <label for="signupCode" class="sr-only">Signup code</label>
              <form:input id="signupCode" type="text"
                        path="signupCode" 
                        placeholder="Signup code (get this from your administrator)" 
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
              <span class="rs-field__text rs-field__text--normal">I agree to these <a href='<c:url value="http://researchspace.com/terms-conditions/" />' target='_blank'>Terms and Conditions</a></span>
            </label>
          </div>
        </rst:hasDeploymentProperty>

        <div class="form-group col-lg-12 rs-field rs-field--divider">
          <button id="submit" role="button" type="submit" class="btn btn-primary rs-field__button">
            Sign up
          </button>
        </div>

      </fieldset>

      <rst:hasDeploymentProperty name="cloud" value="true"> 
        <fieldset>
          <div class="form-group col-lg-12 rs-field">
            <div class="rs-field__text rs-field__text--normal"><fmt:message key='button.signup.with.google' /></div>
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

<script src="<c:url value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>
<script type="text/javascript">
  $(document).ready(function() {
    $.fn.bootstrapButton = $.fn.button.noConflict();
  });
</script>
