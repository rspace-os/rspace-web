<%@ include file="/common/taglibs.jsp" %>
<head>
    <title>Password reset link declined</title>
</head>
<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <axt:biggerLogo/>
        <div style="text-align:center; margin-top:46px;">
            <h2 class="form-signup-heading">This link has a problem!</h2>
        </div>
    </div>
    <div style="max-width:450px;margin: 30px auto 0;text-align:center;">
        Sorry, this link could not be accepted - either the token was rejected, or
        it has expired, or it has already been used.
        <p>
            Please try again to <a href="/public/requestPasswordReset">reset your
            password</a>.
        </p>
    </div>
</div>