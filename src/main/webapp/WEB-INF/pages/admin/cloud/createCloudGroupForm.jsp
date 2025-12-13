<%@ include file="/common/taglibs.jsp"%>
<!DOCTYPE html>
<html lang="en">
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

	<link href="<c:url value='/scripts/bower_components/jquery-tagit/css/jquery.tagit.css'/>" rel="stylesheet" />
    <link href="<c:url value='/scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css'/>" rel="stylesheet" />
    <script src="<c:url value='/scripts/bower_components/jquery-tagit/js/tag-it.min.js'/>"></script>
    <script src="<c:url value='/scripts/pages/rspace/cloudGroupCreation.js'/>"></script>
</head>
	<jsp:include page="/WEB-INF/pages/admin/admin.jsp"></jsp:include>
	<div class="container">
		<p style="visibility:hidden;">space holder</p>
        <c:choose>
        <c:when test="${isProjectGroup}">
            <h2>Create a Project Group</h2>
            <ol>

                <p><strong>You can organise your colleagues into Project Groups to allow data sharing.</strong></p>
            <li><p>There is no PI in a Project Group. There must be at least one Group Owner, by default the creator of the group.</p></li>
                <li><p>The Group Owner can invite new members.</p></li>
                <li><p>The Group Owner can make other users Group Owners.</p></li>
                <li><p>The Group Owner can delete the group.</p></li>
            <li><p>All data is private, unless explicitly shared among group members.</p></li>
            <li><p>Any group member may leave, except the last Group Owner.</p></li>
           </c:when>
        <c:otherwise>
                <h2> <spring:message code="create.group.heading"/> </h2>
            <p>You can organise your colleagues into LabGroups to make collaboration and oversight easier.</p>
            <%--RA - Openlab text hidden until implemented for 1.57 --%>
            <%-- <p>We offer TWO different types of Lab Group.</p> --%>
            <p>There are two options for sharing data in LabGroups:</p>
            <ol>
                <li>

                    Controlled sharing.
                    <rst:hasDeploymentProperty name="cloud" value="false">
                    As PI, you can see the data of all members of your group. Other group members can only see their own data by default.
                    </rst:hasDeploymentProperty>
                    <rst:hasDeploymentProperty name="cloud" value="true">
                    Only you and your PI can see your data by default.
                    </rst:hasDeploymentProperty>
                    You can let others in your lab see or edit a specific document or notebook by selecting it in the workspace and clicking SHARE.
                    YOU decide when to share your work, who to share it with and what level of access other users have; either 'read only' or 'edit'.</p>
                        <%-- <p>In an <strong>OpenLab</strong> ALL of the work you create will be visible to ALL other members of the lab.
                           You will still need to manually share work with others if you want them to be able to edit your work.</p> --%>
                        <%-- <p>Once you have designated a lab as either <strong>Standard</strong> or <strong>Open</strong>, the lab type cannot be changed,
                            but the group can be disbanded if necessary so that you can recreate a new lab with your choice of sharing strategy.</p> --%>
                <li>Open sharing. It may be that your group is 'open' and you want all data to be shared automatically amongst its members.
                    Your group members can opt-in to this after they join the group, by enabling autosharing.
            </ol>

            <p><%--Note that in either case, --%> Your work is ONLY ever visible to members of groups that you belong to.</p>
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
		<%-- <!--<spring:message code="create.group.help1"/>
	 	<ul>
	 		<li><strong><spring:message code="create.group.bePi.help1"/></strong> <spring:message code="create.group.bePi.help2"/> </li>
	 		<li><strong><spring:message code="create.group.nominatePi.help1"/></strong><spring:message code="create.group.nominatePi.help2"/></li>
	 	</ul>
	 	<br/>
	 	<br/>
	    <form:form id="createCloudGroup" action="/cloud/createCloudGroup" method="post" modelAttribute="createCloudGroupConfig" >
		<div>
			<table width="60%" style="margin-left:120px;">
				<tr><td width="50%"><form:errors class="error" path="groupName" style="color:red;font-weight:bold;"></form:errors></td></tr>
				<tr>
					<td width="50%"><label> <spring:message code="create.group.chooseName.label"/> </label></td>
					<td><form:input type="text" path="groupName" name="groupName" style="width:90%" placeholder="Group Name" required="true" /></td>
				</tr>
			</table>
			<table width="60%" style="margin-left:120px;">
				<form:errors class="error" path="nomination" style="color:red;font-weight:bold;"></form:errors>
				<tr style="margin-bottom: 10px">
					<td><form:radiobutton class="options" path="nomination" style="width:15px;" value="false" required="true" checked="true" /></td>
					<td><label> <spring:message code="create.group.mePi.label"/> </label></td>
				</tr>
				<tr>
					<td><form:radiobutton class="options" path="nomination" style="width:15px;" value="true" required="true" /></td>
					<td><label> <spring:message code="create.group.nominatePi.label"/> </label></td>
				</tr>
			</table>
		</div>
			<table width="60%" style="margin-left:120px;">
				<tr class="nominationOptionError"><td width="50%"><form:errors class="error" path="principalEmail" style="color:red;font-weight:bold;"></form:errors></td></tr>

				<tr id="nominationOption"
					<c:choose>
	  				<c:when test="${createCloudGroupConfig.nomination}">
							style="padding-bottom:10px;"
  					</c:when>
  					<c:otherwise>
  						style="display:none; padding-bottom:10px;"
	  				</c:otherwise>
					</c:choose>
				>
					<td width="50%" style="padding-bottom:10px;">
						<label><spring:message code="create.group.newPi.email.label"/></label>
					</td>
					<td width="50%">
						<ul id="principalEmail" style="width: 300px; height: 25px;"></ul>
					</td>
				</tr>

				<tr><td><form:errors class="error" path="emails" style="color:red;font-weight:bold;"></form:errors></td></tr>
				<tr><td colspan="2" width="100%"><spring:message code="create.group.newPi.inviteByEmail.label"/></td></tr>
				<tr>
					<td width="50%" style="vertical-align:top;">
						<h4> <spring:message code="create.group.rspaceusers.heading"/> </h4>
					</td>
					<td width="50%" style="vertical-align:top;">
						<h4>  <spring:message code="create.group.newusers.heading"/> </h4>
					</td>
				</tr>
				<tr>
					<td width="50%" id="existingUsersCell">
						<ul id="existingUsers" style="width: 300px; height: 125px;"></ul>
					</td>
					<td width="50%" id="nonExistingUsersCell">
						<ul id="nonExistingUsers" style="width: 300px; height: 125px;"></ul>
					</td>
				</tr>
				<tr>
					<td width="50%"></td>
					<td width="50%" class="bootstrap-custom-flat" style="text-align:right">
					   <button class="btn btn-primary" type="submit" role="button" name="createGroup" value="Create" style="width:160px" id="submitCreateCloudGroup">
					       <span>Create LabGroup</span></button>
					</td>
				</tr>
			</table>
	   	</form:form>-->--%>
	</div>
	<!-- React Scripts -->
		<script src="<c:url value='/ui/dist/createGroup.js'/>"></script>
	<!--End React Scripts -->
</html>
