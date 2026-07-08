<!DOCTYPE html>
<%@ include file="/common/taglibs.jsp" %>
<html>
	<head>
		<rst:viteClient />
		<title>
			<sitemesh:write property='title'/> |
			<fmt:message key="webapp.name" />
		</title>

		<meta name="viewport" content="width=device-width, initial-scale=1">
		<link href="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/css/bootstrap.min.css'/>" rel="stylesheet" />
		<link href="<rst:assetUrl value='/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css'/>"
			rel="stylesheet" />
		<link media="all" href="<rst:assetUrl value='/styles/simplicity/tools.css'/>" rel="stylesheet" />
		<link media="all" href="<rst:assetUrl value='/styles/simplicity/layout.css'/>" rel="stylesheet" />
		<link media="all" href="<rst:assetUrl value='/styles/public.css'/>" rel="stylesheet" />
		<script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
		<script src="<rst:assetUrl value='/scripts/bower_components/jquery-ui/jquery-ui.min.js'/>"></script>
		<script src="<rst:assetUrl value='/scripts/bower_components/jstz-detect/jstz.min.js'/>"></script>

		<link rel="icon" type="image/png" href="<c:url value="/images/favicon.png"/>"/>

		<script>
			$(document).ready(function () {
				var tz = jstz.determine(); //Determines the time zone of the browser client
				$('#timezone_field').val(tz.name());
			});
		</script>
		<sitemesh:write property='head'/>
	</head>

	<jsp:include page="/scripts/templates/blockUI.html"/>

	<body id="<sitemesh:write property='body.id'/>" class="<sitemesh:write property='body.class'/>">
		<div id="content" class="clearfix">
			<sitemesh:write property='body'/>
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
