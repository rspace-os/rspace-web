<%@ include file="/common/taglibs.jsp"%>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title><spring:message code="system.createCommunity.button.label" /></title>
    <link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
    <script src="<c:url value='/scripts/pages/system/system.js'/>"></script>
</head>
<body>
  <div id="topSection" class="bootstrap-custom-flat">
    <jsp:include page="topBar.jsp"></jsp:include>
  </div>
  <div id="content">
	<br />
	<h3>Create a Community</h3>
	<p />
	<form:form modelAttribute="community" action="/system/createCommunity">
    	<span style="color:red">
            <form:errors path="*"></form:errors>
        </span>
		<c:choose>
		  <c:when test="${ empty  community.availableAdmins}">
            <spring:message code="system.createCommunity.noAdmins.msg"/>
          </c:when>
		  <c:otherwise>
			<table>
				<tr>
                    <td width="20%"><label for="displayName"><spring:message code="system.communityList.name.label"/></label></td>
				    <td><form:input placeholder="A display name for this community" path="displayName" style="width:240px;" /></td>
				</tr>
				<tr>
                    <td valign="top"><label for="profileText"><spring:message code="system.communityList.profileText.label"/> </label></td>
				    <td><form:textarea placeholder="A short description of this community" maxlength="255" path="profileText" style="width:240px;"></form:textarea></td>
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
					There are no groups yet that can be added to this community
				</c:when>
				<c:otherwise>
				    <label for="groupIds">If you wish to move any groups into this Community, select them below. </label><br><br>
				    <div class="communityViewInnerList">
    					<form:checkboxes delimiter="<br/>"
    						items="${community.availableGroups}" path="groupIds"
    						itemLabel="displayName" itemValue="id" />
					</div>
				</c:otherwise>
			  </c:choose>
			</div>
			<div style="margin-top: 20px; float:left">
			    <input value="Submit" type="submit">
			</div>
          </c:otherwise>
		</c:choose>
    </form:form>
  </div>
</body>