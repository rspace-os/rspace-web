<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Login attempt with username, to account that has an username alias</title>
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
  	<div class="row">
      	<axt:biggerLogo/>
      	<div style="text-align:center; margin-top:46px;">
      	 <h3 class="form-signup-heading">Login attempt with username, rather than username alias</h3>
          </div>
      </div>
  
      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          Sorry - Your username (${remoteUserUsername}) is matching a user who is configured to login with username alias, rather than by main username.
          <br /><br />
          If that's unexpected, please contact your System Admin.
      </div>
</div>

