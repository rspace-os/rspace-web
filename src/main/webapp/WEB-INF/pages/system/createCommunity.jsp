<%@ include file="/common/taglibs.jsp"%>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title><spring:message code="system.createCommunity.button.label" /></title>
    <link rel="stylesheet" href="<rst:assetUrl value='/styles/system.css'/>" />
    <script src="<rst:assetUrl value='/scripts/pages/system/system.js'/>"></script>
</head>
<body>
  <div id="topSection" class="bootstrap-custom-flat">
    <jsp:include page="topBar.jsp"></jsp:include>
  </div>
  <div id="content">
	<br />
	<h3><spring:message code="system.createCommunity.title" /></h3>
	<p />
	<form:form modelAttribute="community" action="/system/createCommunity">
	<span style="color:red">
            <form:errors path="*"></form:errors>
        </span>
		<c:choose>
		  <c:when test="${ empty  community.availableAdmins}">
            <spring:message code="system.createCommunity.errors.noAdmins"/>
          </c:when>
		  <c:otherwise>
			<spring:message code="system.createCommunity.displayNamePlaceholder" var="displayNamePlaceholder"/>
			<spring:message code="system.createCommunity.profileTextPlaceholder" var="createCommunityProfileTextPlaceholder"/>
			<table>
				<tr>
                    <td width="20%"><label for="displayName"><spring:message code="system.communityList.name.label"/></label></td>
				    <td><form:input placeholder="${displayNamePlaceholder}" path="displayName" style="width:240px;" /></td>
				</tr>
				<tr>
                    <td valign="top"><label for="profileText"><spring:message code="system.communityList.profileText.label"/> </label></td>
				    <td><form:textarea placeholder="${createCommunityProfileTextPlaceholder}" maxlength="255" path="profileText" style="width:240px;"></form:textarea></td>
				</tr>
			</table>
			<div style="width:470px;float:left;">
				<spring:message code="system.createCommunity.chooseAdmins.label"/>
			    <br><br>
                <div class="communityViewInnerList">
				    <form:checkboxes delimiter="<br/>" items="${community.availableAdmins}" path="adminIds"
					   itemLabel="fullNameAndUserNameAndRole" itemValue="id" />
				</div>
			</div>
			<div style="width:470px;float:right;">
			  <c:choose>
				<c:when test="${empty community.availableGroups}">
					<spring:message code="system.createCommunity.noGroupsAvailable"/>
				</c:when>
				<c:otherwise>
				    <label for="groupIds"><spring:message code="system.createCommunity.moveGroupsPrompt"/> </label><br><br>
				    <div class="communityViewInnerList">
					<form:checkboxes delimiter="<br/>"
						items="${community.availableGroups}" path="groupIds"
						itemLabel="displayName" itemValue="id" />
					</div>
				</c:otherwise>
			  </c:choose>
			</div>
			<div style="margin-top: 20px; float:left">
			    <input value="<spring:message code='common:actions.submit'/>" type="submit">
			</div>
          </c:otherwise>
		</c:choose>
    </form:form>
  </div>
</body>