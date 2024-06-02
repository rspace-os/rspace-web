<%@ include file="/common/taglibs.jsp"%>

<head>

    <title><spring:message code="public.requestPassword.title"/></title>
    <link href="/styles/pages/public/passwordReset.css" rel="stylesheet">

</head>

<div class="container passwordResetContainer">
    <div class="row">
        <axt:biggerLogo/>
        <div class="passwordResetInstructionDiv">
            <h2 class="form-signup-heading">Forgotten your password?</h2>
            <p><spring:message code="public.requestPassword.help1"/></p>
        </div>
    </div>
    <form class="form-signup" method="POST" action="/signup/passwordResetRequest">
      <fieldset>
      <%--<label for="email">Your email </label> --%>
          <input type="text" name="email" id="email" class="form-control" placeholder="Your Email Address" 
                  required="true" autofocus="true" pattern="[^ @]*@[^ @]*" title="Use a valid email address">
          <button class="btn btn-lg btn-primary btn-block" type="submit" 
                  role="button" aria-disabled="false" name="submit" value="Reset">
              <span class="ui-button-text">Submit</span>
          </button>
      </fieldset>
    </form>
</div>