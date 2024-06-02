<%@ include file="/common/taglibs.jsp" %>

<head>
    <title>Request username reminder</title>
    <link href="/styles/pages/public/passwordReset.css" rel="stylesheet">
</head>

<div class="container passwordResetContainer">
    <div class="row">
        <axt:biggerLogo/>
        <div class="passwordResetInstructionDiv">
            <h2 class="form-signup-heading">Forgotten your username?</h2>
            <p>Please enter the email address that you registered with RSpace -
                your username will be sent to that address.</p>
        </div>
    </div>
    <form class="form-signup" method="POST" action="/signup/usernameReminderRequest">
        <fieldset>
            <input type="text" name="email" id="email" class="form-control"
                   placeholder="Your Email Address"
                   required="true" autofocus="true" pattern="[^ @]*@[^ @]*"
                   title="Use a valid email address">
            <button class="btn btn-lg btn-primary btn-block" type="submit"
                    role="button" aria-disabled="false" name="submit" value="Reset">
                <span class="ui-button-text">Submit</span>
            </button>
        </fieldset>
    </form>
</div>