<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="ssoinfoUsernameNotAlias.title"/></title>
</head>

<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
	<div class="row">
	<axt:biggerLogo/>
	<div style="text-align:center; margin-top:46px;">
	 <h3 class="form-signup-heading"><spring:message code="ssoinfoUsernameNotAlias.heading"/></h3>
          </div>
      </div>

      <div style="max-width:450px;margin: 0 auto;margin-top:30px;text-align:center;">
          <spring:message code="ssoinfoUsernameNotAlias.notice" arguments="${remoteUserUsername}"/>
          <br /><br />
          <spring:message code="contactSystemAdminIfUnexpected"/>
      </div>
</div>
