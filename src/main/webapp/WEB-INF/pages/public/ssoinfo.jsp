<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Unknown RSpace user</title>
</head>

<c:set var="showDefaultSSOInfo" value="true"/>

<rst:hasDeploymentProperty name="SSOInfoVariant" value="liege">
  <div class="container" style="max-width:960px;padding:0 5% 0 5%;">
    <div class="row">
        <axt:biggerLogo/>
        <div style="text-align:center; margin-top:46px;">
         <h3 class="form-signup-heading">Request access to RSpace</h3>
          </div>
      </div>
  
      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          Sorry - you are unable to use RSpace just yet - an administrator has to create an RSpace account for you first. <br />
          Please contact labis@uliege.be to ask for an RSpace account.
      </div>
  </div>
  <c:set var="showDefaultSSOInfo" value="false"/>
</rst:hasDeploymentProperty>

<c:if test="${showDefaultSSOInfo}">
  <div class="container" style="max-width:960px;padding:0 5% 0 5%;">
  	<div class="row">
      	<axt:biggerLogo/>
      	<div style="text-align:center; margin-top:46px;">
      	 <h3 class="form-signup-heading">RSpace doesn't know you...</h3>
          </div>
      </div>
  
      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          Sorry - Your username (<c:out value="${sessionScope.userName}"/>) is not registered with RSpace, so you are not able to use RSpace yet. <br />  
          Please contact RSpace Support (${applicationScope['RS_DEPLOY_PROPS']['SSOAdminEmail']}) to ask for an account.
      </div>
  </div>
</c:if>

