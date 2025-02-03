<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Conflicting accounts</title>
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
  	<div class="row">
      	<axt:biggerLogo/>
      	<div style="text-align:center; margin-top:46px;">
      	 <h3 class="form-signup-heading">There is a pre-existing RSpace account for your username</h3>
          </div>
      </div>
  
      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          Sorry - Your username (${remoteUserUsername}) is already registered in RSpace as an internal, administrative account.
          Such account can only be accessed through 'Admin&nbsp;Login' screen, not directly. 
          <br /><br />
          If that's unexpected, please contact your System Admin.
      </div>
</div>

