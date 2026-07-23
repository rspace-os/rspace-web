<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="${fn:escapeXml(empty requestScope.rsResolvedLocaleTag ? 'en-US' : requestScope.rsResolvedLocaleTag)}">
<head>
	<title> <spring:message code="create.group.title"/> </title>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
	<style>
		input, textarea, select, button {
		  width : 150px;
		  margin: 0;

		  -webkit-box-sizing: border-box; /* For legacy WebKit based browsers */
		     -moz-box-sizing: border-box; /* For all Gecko based browsers */
		          box-sizing: border-box;
		}
	</style>

	<link href="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/css/jquery.tagit.css'/>" rel="stylesheet" />
    <link href="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css'/>" rel="stylesheet" />
    <script src="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/js/tag-it.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/rspace/cloudGroupCreation.js'/>"></script>
</head>
	<jsp:include page="/WEB-INF/pages/admin/admin.jsp"></jsp:include>
	<div class="container">
		<p style="visibility:hidden;">space holder</p>
        <c:choose>
        <c:when test="${isProjectGroup}">
            <h2><spring:message code="cloud.createGroup.projectGroup.heading"/></h2>
            <ol>

                <p><strong><spring:message code="cloud.createGroup.projectGroup.intro"/></strong></p>
            <li><p><spring:message code="cloud.createGroup.projectGroup.noPi"/></p></li>
                <li><p><spring:message code="cloud.createGroup.projectGroup.ownerInvite"/></p></li>
                <li><p><spring:message code="cloud.createGroup.projectGroup.ownerPromote"/></p></li>
                <li><p><spring:message code="cloud.createGroup.projectGroup.ownerDelete"/></p></li>
            <li><p><spring:message code="cloud.createGroup.projectGroup.dataPrivate"/></p></li>
            <li><p><spring:message code="cloud.createGroup.projectGroup.leave"/></p></li>
           </c:when>
        <c:otherwise>
                <h2> <spring:message code="create.group.heading"/> </h2>
            <p><spring:message code="cloud.createGroup.labGroup.intro"/></p>
            <p><spring:message code="cloud.createGroup.labGroup.sharingOptionsIntro"/></p>
            <ol>
                <li>

                    <spring:message code="cloud.createGroup.labGroup.controlledSharing.label"/>
                    <rst:hasDeploymentProperty name="cloud" value="false">
                    <spring:message code="cloud.createGroup.labGroup.controlledSharing.nonCloud"/>
                    </rst:hasDeploymentProperty>
                    <rst:hasDeploymentProperty name="cloud" value="true">
                    <spring:message code="cloud.createGroup.labGroup.controlledSharing.cloud"/>
                    </rst:hasDeploymentProperty>
                    <spring:message code="cloud.createGroup.labGroup.controlledSharing.shareInstructions"/></p>
                <li><spring:message code="cloud.createGroup.labGroup.openSharing"/>
            </ol>

            <p><spring:message code="cloud.createGroup.labGroup.visibilityNote"/></p>
        </c:otherwise>
        </c:choose>
                <div id="createGroup" data-currentUser="${sessionScope.userInfo.email}"></div>
                <%--Users can only create standard labgroups on community, otherwise its a 'selfservice' labGroup. The presence of the div is used in the CreateGroup React Code--%>
                <rst:hasDeploymentProperty name="cloud" value="false">
                <div id="selfServiceLabGroup"></div>
                <c:if test="${isProjectGroup}">
                    <div id="projectGroup"></div>
                </c:if>
                </rst:hasDeploymentProperty>
	</div>
	<!-- React Scripts -->
		<rst:bundle bundle="createGroup" />
	<!--End React Scripts -->
</html>
