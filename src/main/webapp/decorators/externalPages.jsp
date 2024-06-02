<!DOCTYPE html>
<%@ include file="/common/taglibs.jsp" %>

<html xmlns="http://www.w3.org/1999/xhtml">

	<head>
		<title>
			<decorator:title /> |
			<fmt:message key="webapp.name" />
		</title>

		<meta name="viewport" content="width=device-width, initial-scale=1">
		<link href="<c:url value='/scripts/bower_components/bootstrap/dist/css/bootstrap.min.css'/>" rel="stylesheet" />
		<link href="<c:url value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>"
			rel="stylesheet" />
		<link media="all" href="<c:url value='/styles/simplicity/tools.css'/>" rel="stylesheet" />
		<link media="all" href="<c:url value='/styles/simplicity/layout.css'/>" rel="stylesheet" />
		<link media="all" href="<c:url value='/styles/public.css'/>" rel="stylesheet" />
		<script src="<c:url value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
		<script src="<c:url value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
		<script src="<c:url value='/scripts/bower_components/jstz-detect/jstz.min.js'/>"></script>

		<rst:hasDeploymentProperty name="cloud" value="true">
			<link rel="icon" href="<c:url value=" /images/faviconCommunity.ico" />"/>
		</rst:hasDeploymentProperty>

		<script>
			$(document).ready(function () {
				var tz = jstz.determine(); //Determines the time zone of the browser client
				$('#timezone_field').val(tz.name());
			});
		</script>
		<decorator:head />
	</head>

	<body>
		<decorator:getProperty property="body.id" writeEntireProperty="true" />
		<decorator:getProperty property="body.class" writeEntireProperty="true" />
		<div id="content" class="clearfix">
			<decorator:body />
			<div class="container" style="max-width:960px;padding:0 5% 0 5%;">
				<div class="row footerVersionRow">
					<div class="col-md-4" style="text-align:center;">
						<fmt:message key="webapp.version" />
					</div>
					<div class="col-md-4" style="text-align:center;">
						<c:forEach items="${applicationScope['RS_DEPLOY_PROPS']['uiFooterUrls']}" var="url" varStatus="loopStatus">
							<a href="${url.value}">${url.key}</a>
							<c:if test="${!loopStatus.last}">|</c:if>
						  </c:forEach>
					</div>
					<div class="col-md-4" style="text-align:center;">
						&copy;&nbsp;
						<fmt:message key="copyright.year" />&nbsp;<a href="<fmt:message key='company.url' />"
						target="_blank">
						<fmt:message key="company.name" /></a>
					</div>
				</div>
			</div>
		</div>
	</body>

</html>