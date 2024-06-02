<%@ include file="/common/taglibs.jsp" %>
<head>
    <title>Password verification</title>
    <link href="/styles/pages/public/passwordReset.css" rel="stylesheet">
</head>

<div class="container passwordResetContainer">
    <div class="row">
        <axt:biggerLogo/>
        <div class="passwordResetSentDiv">
            <h2 class="form-signup-heading">We need to check it's you...</h2>
            <br/>
            A password request verification email has been sent to ${email}.
            <br/>
            Please check your email and follow the instructions in
            the email to reset your password within 1 hour.
            <br/><br/>
            Thank you.
        </div>
    </div>
</div>