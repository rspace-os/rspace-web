<%@ include file="/common/taglibs.jsp"%>
<%-- JSP to view the information about a group --%>
<head>
  <title><fmt:message key="groups.view.title"/></title>
  <meta name="heading" content="<fmt:message key='groups.heading'/>"/>
  <meta name="menu" content="MainMenu"/>
  <meta charset="UTF-8">
  <link rel="stylesheet" href="<c:url value='/styles/pages/groups/viewGroup.css'/>" />
  <link href="<c:url value='/scripts/bower_components/jquery-tagit/css/jquery.tagit.css'/>" rel="stylesheet" />
  <link href="<c:url value='/scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css'/>" rel="stylesheet" />
  <script src="<c:url value='/scripts/bower_components/jquery-tagit/js/tag-it.min.js'/>"></script>
  <script type="text/javascript">
    $(document).ready(function() {
    <c:if test="${not empty error}">
      RS.confirm("${error}", "warning", 5000, {sticky:true});
    </c:if>
    });
  </script>
  <script src="<c:url value='/scripts/pages/viewGroupEditing.js'/>"></script>
  <script src="<c:url value='/scripts/pages/messaging/messageCreation.js'/>"></script>
  <script src="<c:url value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
  <shiro:hasRole name="ROLE_PI">
    <c:set var="userRole" value="pi" />
  </shiro:hasRole>
<shiro:lacksRole name="ROLE_PI">
  <shiro:hasRole name="ROLE_USER">
    <c:set var="userRole" value="user" />
  </shiro:hasRole>
</shiro:lacksRole>
  <shiro:hasAnyRoles name="ROLE_ADMIN,ROLE_SYSADMIN">
    <c:set var="userRole" value="admin" />
  </shiro:hasAnyRoles>
  <rst:hasDeploymentProperty name="profileHidingEnabled" value="true">
    <c:set var="profileHidingEnabled" value="true" />
  </rst:hasDeploymentProperty>
  <rst:hasDeploymentProperty name="cloud" value="true">
    <c:set var="isCloud" value="true" />
  </rst:hasDeploymentProperty>
</head>

<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
<p style="visibility:hidden;">space holder</p>

<div id="groupBlocks">
  <div id="groupBlockLeft">
    <h2 style="flex-grow:1; margin-bottom: 0px;">
      <c:if test="${group.groupType == 'COLLABORATION_GROUP'}">Collaboration </c:if>
      <c:if test="${group.groupType == 'PROJECT_GROUP'}">Project </c:if>
      Group: <span class="displayname">${group.displayName}</span>
    </h2>

    <div class="groupEditBar">
      <c:if test="${canEdit}">
        <shiro:hasRole name="ROLE_PI">
        <c:if test="${group.isLabGroup() and canEdit == true}">
          <a id="createCollabGroup" class="createCollabGroup groupEditButton" href="#">Create Collaboration Group</a>
         </c:if>
          <c:if test="${group.isSelfService() and canEdit == true and group.owner == subject}">
            <a id="deleteGroup" class="deleteFromCollbGrpLink groupEditButton" href="#">Delete Group</a>
          </c:if>
        </shiro:hasRole>
          <shiro:hasRole name="ROLE_USER">
              <c:if test="${group.isProjectGroup() and canEdit == true and group.owner == subject}">
                  <a id="deleteProjectGroup" class="deleteFromCollbGrpLink groupEditButton" href="#">Delete Group</a>
              </c:if>
          </shiro:hasRole>
        <shiro:hasAnyRoles name="ROLE_ADMIN,ROLE_SYSADMIN">
          <c:if test="${group.groupType != 'PROJECT_GROUP'}">
            <a id="changePiLink" class="groupEditButton" href="#">Change PI</a>
          </c:if>
        </shiro:hasAnyRoles>
        <a id="renameGrpLink" class="groupEditButton" href="#">Rename</a>
      </c:if>
      <rst:hasDeploymentProperty name="cloud" value="true">
        <c:forEach items="${group.userGroups}" var="ug">
          <c:if test="${ug.user == subject}">
            <a id="removeMeFromGrpLink" class="groupEditButton" href="#">Leave Group</a>
          </c:if>
        </c:forEach>
      </rst:hasDeploymentProperty>
      <c:if test="${group.isProjectGroup() }">
        <c:forEach items="${group.userGroups}" var="ug">
          <c:if test="${ug.user == subject}">
            <a id="removeMeFromGrpLink" class="groupEditButton" href="#">Leave Group</a>
          </c:if>
        </c:forEach>
      </c:if>
      <c:if test="${not empty userGroups and fn:length(userGroups) gt 1}">
        <div id="groupDrop">
          <dl class="groupDropdown" style="margin-bottom: 0px;white-space: nowrap;">
            <dt style="margin: 0px">
              <a class="groupChangeButton" href="#">Change Group</a>
            </dt>
            <dd>
              <ul id="group_options">
                <c:forEach var="grp" items="${userGroups}">
                  <c:if test="${grp.id ne group.id}">
                    <c:url value="/groups/view/${grp.id}"
                      var="groupURL"></c:url>
                    <li><a href="${groupURL}">
                        ${grp.displayName} (${grp.groupType.label})</a></li>
                  </c:if>
                </c:forEach>
              </ul>
            </dd>
          </dl>
        </div>
      </c:if>
    </div>
    <c:if test="${canEdit}">
      <div id="renameRecordDirect" style="display: none">
        <p>
          <label><spring:message code="group.view.rename.msg" />
            <input class="displayname" id="nameFieldDirect"
              value="${group.displayName}" type="text" width="30">
          </label>
        </p>
      </div>
    </c:if>
    <c:if test="${group.labGroup == true and showExportFunctionality}">
      <a href="#" id="exportGroupRecord" class="groupEditButton" style="background-image:url('/images/icons/exportAllGroupIcon.png');padding:3px 7px 2px 50px;">
        <spring:message code="group.export.link" /> </a>
    </c:if>
    <c:if test="${group.groupType == 'COLLABORATION_GROUP' and canEdit == true}">
      <br>
      <div id="piInviteLink" style="text-align: right;">
        <a id="grpid_${group.id}" href="#" class="createRequest groupEditButton" style="background-image:url('/images/icons/invitePi.png');padding:3px 7px 2px 40px;"
          title="New PI Invitation" alt="New PI Invitation">Invite a New PI</a>
      </div>
      <div id="leaveCollaborationLink" style="text-align: right;">
        <form class="deleteFromCollbGrp"
          action="/groups/admin/removeLabGrpFromCollabGroup/${group.id}"
          method='POST'></form>
        <a class="deleteFromCollbGrpLink groupEditButton" href="#" style="background-image:url('/images/icons/leavecollaboration.png');padding:3px 7px 2px 47px;"
          title="Leave Collaboration" alt="Leave Collaboration">Leave Collaboration</a>
      </div>
    </c:if>
  </div>

  <c:if test="${group.groupType != 'COLLABORATION_GROUP'}">
    <div id="communityDisplay">
      <h3>
        Community:
        <a href="/directory/community/${group.community.id}">${group.community.displayName}</a>
      </h3>
    </div>
  </c:if>

  <div id="groupEditBar"
    data-group-id="${group.id}"
    data-profile-text="${group.profileText}"
    data-can-edit="${canEdit}"
    data-can-editpieditallchioce="${canEditPIEditAllChoice}"
    data-pieditallchoicevalue="${PIEditAllChoiceValue}"
    data-profile-hiding-enabled="${profileHidingEnabled}"
    data-can-hide-group-profile="${canHideGroupProfile}"
    data-group-private-profile="${group.privateProfile}">
  </div>
</div>
<axt:export/>

    <div style="margin-bottom: 2em">
      <h3>Shared Folder</h3>
      <c:if test="${empty folder}">
        <span style="font-size: 1.2em">Folder missing</span>
      </c:if>
      <c:if test="${not empty folder}">
        <span>
          <td class="workspace-record-icon">
              <axt:record_icon record="${folder}" />
          </td>
          <a style="font-size: 1.2em; padding-left: 8px" href="/globalId/FL${folder.id}">${folder.name}</a>
        </span>
      </c:if>
    </div>

<c:if test="${group.groupType == 'COLLABORATION_GROUP'}">
  <div id="memberGroups">
    <h3>Group PIs</h3>
    <table id="memberGroupsTable" class="table">
      <tbody>
      <c:forEach var="groupPI" items="${group.piusers}">
          <tr>

            <td>
              <c:choose>
                <c:when test="${groupPI.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToUser(groupPI)}">
                  <spring:message code="unknown.user.label"/>
                </c:when>
                <c:otherwise>
                    <span
                      data-test-id="mini-profile-activator-${groupPI.id}"
                      class="user-details"
                      data-userid="${groupPI.id}"
                      data-firstName="${groupPI.firstName}"
                      data-lastName="${groupPI.lastName}"
                      data-uniqueid="${groupPI.id}"
                      data-position="bottom_right"
                    >
                      <a href="#" data-test-id="mini-profile-activator-${groupPI.id}">
                        <c:out value="${groupPI.fullName}"/>
                      </a>
                    </span>
                </c:otherwise>
              </c:choose>
            </td>
          </tr>
      </c:forEach>
      </tbody>
    </table>
  </div>
</c:if>

<%-- Dialog for creating request --%>
<div id="editProfileDlg" style="display: none">
  <div style="margin: 10px 0px;">
    <spring:message code="group.profile.edit.help" />
  </div>
  <textarea id="editProfileDlgTextArea" rows="12" cols="40" autofocus="true"></textarea>
</div>

<div id="createRequestDlg" style="display: none">
  <div id="createRequestDlgContent"></div>
</div>

<div id="createCollabGroupDlg" style="display: none">
  <div id="createCollabGroupDlgContent"></div>
</div>

<div id="inviteNewMembersDlg" style="display: none">
  <div id="inviteNewMembersDlgContent">
    <h3> RSpace users </h3>
    <div>
      <ul id="existingUsersTag" style="width: 300px; height: 150px;"></ul>
    </div>
    <h3> New users </h3>
    <div>
      <ul id="nonExistingUsersTag" style="width: 300px; height: 150px;"></ul>
    </div>
  </div>
</div>

<div id="displayAreaBlock">
  <div id="membersBlock">
    <div class="group-members">
      <div id="myLabGroups"
           data-isCloud="${isCloud}"
           data-groupId="${group.id}"
           data-displayName="${group.displayName}"
           data-isLabGroup="${group.labGroup}"
           data-isGroupAutoshareAllowed="${isGroupAutoshareAllowed}"
           data-isGroupPublicationAllowed="${isGroupPublicationAllowed}"
           data-isGroupSeoAllowed="${isGroupSeoAllowed}"
           data-role="${userRole}"
           data-uniqueName="${isAdmin}"
           data-canEdit="${canEdit}"
           data-canManageAutoshare="${canManageAutoshare}"
           data-canManagePublish="${canManagePublish}"
           data-canManageOntologies="${canManageOntologies}"
           data-groupType="${group.groupType}">
      </div>
      <div id="memberAutoshareStatusWrapper"
           data-isCloud="${isCloud}"
           data-groupId="${group.id}"
           data-displayName="${group.displayName}"
           data-isLabGroup="${group.labGroup}"
           data-isGroupAutoshareAllowed="${isGroupAutoshareAllowed}"
           data-canManageAutoshare="${canManageAutoshare}"
           data-subjectId="${subject.id}">
      </div>
      <table id="grpDetails" class="table" cellspacing="0">
        <thead>
          <tr>
            <th><spring:message code="user.fullname.label" /></th>
            <th><spring:message code="user.username.label" /></th>
            <c:if test="${showExportFunctionality}">
              <th style="width:10%"><spring:message code="user.documents.label" /></th>
            </c:if>
            <th><spring:message code="user.role.label" /></th>
            <th>Autosharing</th>
            <th style="min-width: 120px"><spring:message code="user.actions.label" /></th>
            <c:if test="${showExportFunctionality}">
              <th><spring:message code="user.exportWork.label" /></th>
            </c:if>
            <th style="width:15%"><spring:message code="user.remove.label" /></th>
          </tr>
        </thead>
        <tbody>
        <c:forEach items="${group.userGroups}" var="ug">
          <c:if test="${ug.user.enabled}">
            <tr data-username="${ug.user.username}">
              <c:choose>
                <c:when test="${ug.user.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToUser(ug.user)}">
                  <td colspan="2" style='height: 39px;'> <spring:message code="unknown.user.label" /> </td>
                </c:when>
                <c:otherwise>
                  <td style='height: 39px;'>
                    <span
                      data-test-id="mini-profile-activator-${ug.user.id}"
                      class="user-details"
                      data-userid="${ug.user.id}"
                      data-firstName="${ug.user.firstName}"
                      data-lastName="${ug.user.lastName}"
                      data-uniqueid="${ug.user.id}"
                      data-position="bottom_right"
                    >
                      <a href="#" style="font-size: 14px; line-height:30px">
                        <c:out value="${ug.user.fullName}" />
                      </a>
                    </span>
                  </td>
                  <td>
                    <span
                      data-test-id="mini-profile-activator-${ug.user.id}"
                      class="user-details"
                      data-userid="${ug.user.id}"
                      data-username="${ug.user.username}"
                      data-uniqueid="${ug.user.username}"
                      data-display="username"
                      data-position="bottom_right"
                    >
                      <a href="#" style="font-size: 14px; line-height:30px">
                        <c:out value="${ug.user.username}" />
                      </a>
                    </span>
                  </td>
                </c:otherwise>
              </c:choose>
              <c:if test="${showExportFunctionality}">
                <td style="width:10%">
                  <span data-test-id="homeFolderSpan">
                    <c:choose>
                    <c:when test="${ug.user.PI and ug.user ne subject}"><img src="/images/icons/folder-unavailable.png" title="Home Folder Not Available"></c:when>
                    <c:otherwise><a href="/workspace/${ug.user.rootFolder.id}"><img src="/images/icons/folder-user.png" title="Go to User's Home Folder"></a></c:otherwise>
                    </c:choose>
                  </span>
                </td>
              </c:if>
              <%-- can't remove or edit a PI in a lab group --%>
              <td>
                <span data-test-id="roleInGroup_${ug.user.username}">${ug.roleInGroup.label}</span>
                <c:if test="${ug.adminViewDocsEnabled}"> <img src='/images/icons/viewAllFolderIcon.png' style="vertical-align:middle;padding-bottom:2px;" title="Can View Group's Documents" /></c:if>
                <c:if test="${ug.piCanEditWork}"> <img src='/images/icons/editIcon.png' style="vertical-align:middle;padding-bottom:2px;" title="Can Edit Group's Documents" /></c:if>
              </td>
              <td id="autoshareStatus-${ug.user.id}"></td>
              <td>
                <c:if test="${roleEditable and ug.roleInGroup ne 'PI' and (group.isLabGroup() or canEdit)}">
                  <a class="changeRole changeRoleButton" style="position: relative; background-image:url('/images/icons/changeRoleIcon.png');background-repeat: no-repeat; padding:4px 7px 3px 32px;"
                    href="#" data-groupId="${group.id}" data-adminviewall="${ug.adminViewDocsEnabled}" data-userid="${ug.user.id}" data-username="${ug.user.username}" data-role="${ug.roleInGroup}"> <spring:message code="group.actions.changerole" /> </a>
                </c:if>
              </td>
              <c:if test="${showExportFunctionality}">
                <td>
                  <c:if test="${!ug.user.PI or ug.user eq subject}">
                    <a class="exportUsersWorkButton groupEditButton" style="position: relative; background-image:url('/images/icons/exportIcon.png');background-repeat: no-repeat; padding:4px 7px 3px 40px;"
                      href="#" data-username="${ug.user.username}"></a>
                  </c:if>
                </td>
              </c:if>
              <td>
                <c:if test="${ug.roleInGroup ne 'PI' or !group.isLabGroup()}">
                  <c:if test="${canEdit and (roleEditable or ug.roleInGroup eq 'DEFAULT')}">
                    <c:if test="${!group.isProjectGroup() or group.getGroupOwnerUsers().size() gt 1 or ug.roleInGroup ne 'GROUP_OWNER'}">
                      <c:url
                        value="/groups/admin/removeUser/${group.id}/${ug.user.id}"
                        var="removeUserURL">
                      </c:url>
                      <form:form modelAttribute="group" style="display:inline"
                        class="removeUserForm" method="POST" action="${removeUserURL}">
                        <a class="removeLink" style="position: relative; background-image:url('/images/icons/closeIcon.png');background-repeat: no-repeat; padding:4px 7px 3px 25px;" href="#"> Remove </a>
                        <form:input type="hidden" path="uniqueName" />
                        <form:input type="hidden" path="id" />
                      </form:form>
                    </c:if>
                  </c:if>
                </c:if> <%-- Complicated permissions checking done in controller and resolved to boolean PI user can't be changed--%>
              </td>
            </tr>
          </c:if>
        </c:forEach>
        </tbody>
      </table>
      <!-- Display disabled accounts -->
      <h3>Disabled Accounts</h3>
      <table id="disabledAccounts" class="table" cellspacing="0">
        <tr>
          <th><spring:message code="user.fullname.label" /></th>
          <th><spring:message code="user.username.label" /></th>
          <c:if test="${showExportFunctionality}">
            <th>
              <spring:message code="user.documents.label" />
            </th>
          </c:if>
          <c:if test="${showExportFunctionality}">
            <th>
              <spring:message code="user.exportWork.label" />
            </th>
          </c:if>
          <th style="width:15%"><spring:message code="user.remove.label" /></th>
        </tr>
        <c:forEach items="${group.userGroups}" var="ug">
          <c:if test="${!ug.user.enabled}">
            <tr>
              <c:choose>
                <c:when test="${ug.user.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToUser(ug.user)}">
                    <td colspan="2" style='height: 39px;'> <spring:message code="unknown.user.label" /> </td>
                </c:when>
                <c:otherwise>
                  <td style='height: 39px;'>
                    <span
                      data-test-id="mini-profile-activator-${ug.user.id}"
                      class="user-details"
                      data-userid="${ug.user.id}"
                      data-firstName="${ug.user.firstName}"
                      data-lastName="${ug.user.lastName}"
                      data-uniqueid="${ug.user.id}"
                      data-position="bottom_right"
                    >
                      <a href="#" style="font-size: 14px; line-height:30px">
                        <c:out value="${ug.user.fullName}" />
                      </a>
                    </span>
                  </td>
                  <td>
                    <span
                      data-test-id="mini-profile-activator-${ug.user.id}"
                      class="user-details"
                      data-userid="${ug.user.id}"
                      data-username="${ug.user.username}"
                      data-uniqueid="${ug.user.username}"
                      data-display="username"
                      data-position="bottom_right"
                    >
                      <a href="#" style="font-size: 14px; line-height:30px">
                        <c:out value="${ug.user.username}" />
                      </a>
                    </span>
                  </td>
                </c:otherwise>
              </c:choose>
              <c:if test="${showExportFunctionality}">
                <td>
                  <c:choose>
                  <c:when test="${ug.user.PI and ug.user ne subject}"><img src="/images/icons/folder-unavailable.png" title="Home Folder Not Available"></c:when>
                  <c:otherwise><a href="/workspace/${ug.user.rootFolder.id}"><img src="/images/icons/folder-user.png" title="Go to User's Home Folder"></a></c:otherwise>
                  </c:choose>
                </td>
              </c:if>
              <c:if test="${showExportFunctionality}">
                <td>
                  <c:if test="${!ug.user.PI or ug.user eq subject}">
                    <a class="exportUsersWorkButton groupEditButton" style="position: relative; background-image:url('/images/icons/exportIcon.png');background-repeat: no-repeat; padding:4px 7px 3px 40px;"
                      href="#" data-username="${ug.user.username}"></a>
                  </c:if>
                </td>
              </c:if>
              <td>
                <c:if test="${ug.roleInGroup ne 'PI' or !group.isLabGroup()}">
                  <c:if test ="${canEdit}">
                    <c:url
                      value="/groups/admin/removeUser/${group.id}/${ug.user.id}"
                      var="removeUserURL">
                    </c:url>
                    <form:form modelAttribute="group" style="display:inline"
                      class="removeUserForm" method="POST" action="${removeUserURL}">
                      <a class="removeLink" style="position: relative; background-image:url('/images/icons/closeIcon.png');background-repeat: no-repeat; padding:4px 7px 3px 25px;" href="#"> Remove </a>
                      <form:input type="hidden" path="uniqueName" />
                      <form:input type="hidden" path="id" />
                    </form:form>
                  </c:if>
                </c:if> <%-- Complicated permissions checking done in controller and resolved to boolean PI user can't be changed--%>
              </td>
            </tr>
          </c:if>
        </c:forEach>
      </table>
    </div>

    <c:if test="${canEdit}">
      <jsp:include page="/WEB-INF/pages/groups/pendingInvitations.jsp"></jsp:include>
    </c:if>

  </div>
</div>

<div id="groupActivity" data-groupid="${group.id}"></div>

<br><br>

<div id="setNewPiDialog" style="display:none;">
    Select new PI:
    <div class="newPiList">
      <c:forEach items="${group.members}" var="member">
        <c:if test="${member.PI and member.enabled and member ne pi}">
          <div class="setNewPiRadioDiv">
            <input class="setNewPiRadioInput" type="radio" name="setNewPi" value="${member.id}"/> ${member.fullName}
          </div>
        </c:if>
      </c:forEach>
    </div>
</div>

<div id="changeRoleDialog" style="display: none;">
See  <a href="https://researchspace.helpdocs.io/article/8qekgz9y5b-the-lab-admin-role">Help</a> for a full explanation of lab roles.

  <c:choose>
    <c:when test="${group.isProjectGroup()}">
      <fieldset>
        <legend>Select a role for the user.  <br><br></legend>
        <label><input type="radio" name="role" id="roleOptionUser" value="DEFAULT"> User</label><br>
        <label><input type="radio" name="role" id="roleOptionGroupOwner" value="GROUP_OWNER"> Group Owner</label>
      </fieldset>
    </c:when>
    <c:otherwise>
      <fieldset>
        <legend>Select a role for the user.  <br><br></legend>
        <label><input type="radio" name="role" id="roleOptionUser" value="DEFAULT"> User</label><br>
        <label><input type="radio" name="role" id="roleOptionAdmin" value="RS_LAB_ADMIN"> Lab Admin</label>
      </fieldset>
        <p/>
        <div id="adminPermissions" style="display:none">
          <fieldset>
            <legend>Lab Admin permissions</legend>
            <label><input type="radio" name="isAuthorized" id="adminViewOptionPersonal" value="false"> Lab Admin cannot view all group's documents.</label><br/>
            <label><input type="radio" name="isAuthorized" id="adminViewOptionAll" value="true"> Lab Admin <em>can</em> view all group's documents.</label>
          </fieldset>
        </div>
    </c:otherwise>
  </c:choose>
</div>

<div id="removeMeFromGrp" style="display: none">
    By accepting, you will be removed from the group '${group.displayName}':
    <ul>
     <li> You will no longer be able to see shared content within the group.
     <li> Any of your work that you shared will no longer be visible to other group members.
     <c:if test="${group.groupType != 'PROJECT_GROUP'}">
       <li> The PI of the group will no longer be able to see your work.
     </c:if>
      <rst:hasDeploymentProperty name="profileHidingEnabled" value="true">
        <c:if test="${group.privateProfile}">
         <li>  This group is private -  you will no longer be able to view the group's profile
        </c:if>
     </rst:hasDeploymentProperty>
    </ul>
</div>

<script type="text/javascript">
  var groupId = "${group.id}";
  var displayName = "${group.displayName}";
</script>

  <!-- MJJA React Scripts -->
<script src="<c:url value='/ui/dist/myLabGroups.js'/>"></script>
  <!--End MJJA React Scripts -->


<!-- Other React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<script src="<c:url value='/ui/dist/exportModal.js'/>"></script>
<script src="<c:url value='/ui/dist/groupUserActivity.js'/>"></script>
<script src="<c:url value='/ui/dist/groupEditBar.js'/>"></script>
<script src="<c:url value='/ui/dist/memberAutoshareStatusWrapper.js'/>"></script>
<!--End React Scripts -->
