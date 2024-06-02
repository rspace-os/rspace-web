<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Unknown RSpace user</title>
</head>

  <div class="container" style="max-width:960px;padding:0 5% 0 5%;">
  	<div class="row">
      	<axt:biggerLogo/>
      	<div style="text-align:center; margin-top:46px;">
      	 <h3 class="form-signup-heading">RSpace doesn't know you...</h3>
          </div>
      </div>
  
      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          Sorry - Your username (<c:out value="${sessionScope.userName}"/>) is not registered with RSpace, so you are not able to use RSpace yet. <br />  
          Please ask your Admin for an account or to enable user self signup.
      </div>
  </div>

