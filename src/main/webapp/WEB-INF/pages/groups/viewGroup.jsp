<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="f" uri="http://researchspace.com/functions" %>
<%-- JSP to view the information about a group --%>
<head>
  <title><spring:message code="groups.view.title"/></title>
  <meta name="heading" content="<spring:message code='groups.heading'/>"/>
  <meta name="menu" content="MainMenu"/>
  <meta charset="UTF-8">
  <link rel="stylesheet" href="<rst:assetUrl value='/styles/pages/groups/viewGroup.css'/>" />
  <link href="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/css/jquery.tagit.css'/>" rel="stylesheet" />
  <link href="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/css/tagit.ui-zendesk.css'/>" rel="stylesheet" />
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery-tagit/js/tag-it.min.js'/>"></script>
  <script type="text/javascript">
    $(document).ready(function() {
    <c:if test="${not empty error}">
      RS.confirm("${error}", "warning", 5000, {sticky:true});
    </c:if>
    });
  </script>
  <script src="<rst:assetUrl value='/scripts/pages/viewGroupEditing.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/messaging/messageCreation.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>
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

<spring:message code="groups.view.homeFolder.notAvailable" var="homeFolderNotAvailableTitle"/>
<spring:message code="groups.view.homeFolder.goTo" var="goToHomeFolderTitle"/>
<spring:message code="groups.view.exportWork.label" var="exportWorkLabel"/>

<div id="groupBlocks">
  <div id="groupBlockLeft">
    <h2 style="flex-grow:1;">
      <c:if test="${group.groupType == 'COLLABORATION_GROUP'}"><spring:message code="groups.view.type.collaboration"/> </c:if>
      <c:if test="${group.groupType == 'PROJECT_GROUP'}"><spring:message code="groups.view.type.project"/> </c:if>
      <spring:message code="groups.view.groupLabel"/> <span class="displayname">${group.displayName}</span>
    </h2>

    <div class="groupEditBar">
      <c:if test="${canEdit}">
        <shiro:hasRole name="ROLE_PI">
        <c:if test="${group.isLabGroup() and canEdit == true}">
          <button id="createCollabGroup" type="button" class="createCollabGroup groupEditButton"><img class="groupActionButtonIcon" src="/images/icons/editIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.createCollabGroup"/></button>
         </c:if>
          <c:if test="${group.isSelfService() and canEdit == true and group.owner == subject}">
            <button id="deleteGroup" type="button" class="deleteFromCollbGrpLink groupEditButton"><img class="groupActionButtonIcon" src="/images/icons/editIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.deleteGroup"/></button>
          </c:if>
        </shiro:hasRole>
          <shiro:hasRole name="ROLE_USER">
              <c:if test="${group.isProjectGroup() and canEdit == true and group.owner == subject}">
                  <button id="deleteProjectGroup" type="button" class="deleteFromCollbGrpLink groupEditButton"><img class="groupActionButtonIcon" src="/images/icons/editIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.deleteGroup"/></button>
              </c:if>
          </shiro:hasRole>
        <shiro:hasAnyRoles name="ROLE_ADMIN,ROLE_SYSADMIN">
          <c:if test="${group.groupType != 'PROJECT_GROUP'}">
            <button id="changePiLink" type="button" class="groupEditButton"><img class="groupActionButtonIcon" src="/images/icons/editIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.changePi"/></button>
          </c:if>
        </shiro:hasAnyRoles>
        <button id="renameGrpLink" type="button" class="groupEditButton"><img class="groupActionButtonIcon" src="/images/icons/editIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.rename"/></button>
      </c:if>
      <rst:hasDeploymentProperty name="cloud" value="true">
        <c:forEach items="${group.userGroups}" var="ug">
          <c:if test="${ug.user == subject}">
            <button id="removeMeFromGrpLink" type="button" class="groupEditButton"><img class="groupActionButtonIcon" src="/images/icons/editIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.leaveGroup"/></button>
          </c:if>
        </c:forEach>
      </rst:hasDeploymentProperty>
      <c:if test="${group.isProjectGroup() }">
        <c:forEach items="${group.userGroups}" var="ug">
          <c:if test="${ug.user == subject}">
            <button id="removeMeFromGrpLink" type="button" class="groupEditButton"><img class="groupActionButtonIcon" src="/images/icons/editIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.leaveGroup"/></button>
          </c:if>
        </c:forEach>
      </c:if>
      <c:if test="${not empty userGroups and fn:length(userGroups) gt 1}">
        <div id="groupDrop">
          <dl class="groupDropdown">
            <dt>
              <a class="groupChangeButton" href="#"><spring:message code="groups.view.actions.changeGroup"/></a>
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
          <label><spring:message code="groups.view.rename.prompt" />
            <input class="displayname" id="nameFieldDirect"
              value="${group.displayName}" type="text" width="30">
          </label>
        </p>
      </div>
    </c:if>
    <c:if test="${group.labGroup == true and showExportFunctionality}">
      <button type="button" id="exportGroupRecord" class="groupEditButton">
        <img class="groupActionButtonIcon" src="/images/icons/exportAllGroupIcon.png" alt="" aria-hidden="true"><spring:message code="groups.export.link" />
      </button>
    </c:if>
    <c:if test="${group.groupType == 'COLLABORATION_GROUP' and canEdit == true}">
      <br>
      <div id="piInviteLink" style="text-align: right;">
        <button id="grpid_${group.id}" type="button" class="createRequest groupEditButton" title="<spring:message code='groups.view.newPiInvitation.title'/>">
          <img class="groupActionButtonIcon" src="/images/icons/invitePi.png" alt="" aria-hidden="true"><spring:message code="groups.view.newPiInvitation.label"/></button>
      </div>
      <div id="leaveCollaborationLink" style="text-align: right;">
        <form class="deleteFromCollbGrp"
          action="/groups/admin/removeLabGrpFromCollabGroup/${group.id}"
          method='POST'></form>
        <button type="button" class="deleteFromCollbGrpLink groupEditButton" title="<spring:message code='groups.view.leaveCollaboration.title'/>">
          <img class="groupActionButtonIcon" src="/images/icons/leavecollaboration.png" alt="" aria-hidden="true"><spring:message code="groups.view.leaveCollaboration.label"/></button>
      </div>
    </c:if>
  </div>

  <c:if test="${group.groupType != 'COLLABORATION_GROUP'}">
    <div id="communityDisplay">
      <h3>
        <spring:message code="groups.view.communityLabel"/>
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
      <h3><spring:message code="groups.view.sharedFolder.heading"/></h3>
      <c:if test="${empty folder}">
        <span style="font-size: 1.2em"><spring:message code="groups.view.sharedFolder.missing"/></span>
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
    <h3><spring:message code="groups.view.groupPisHeading"/></h3>
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
                      data-user-id="${groupPI.id}"
                      data-first-name="${groupPI.firstName}"
                      data-last-name="${groupPI.lastName}"
                      data-unique-id="${groupPI.id}"
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
    <spring:message code="groups.profile.edit.help" />
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
    <h3> <spring:message code="groups.view.inviteDialog.existingUsers"/> </h3>
    <div>
      <ul id="existingUsersTag" style="width: 300px; height: 150px;"></ul>
    </div>
    <h3> <spring:message code="groups.view.inviteDialog.newUsers"/> </h3>
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
            <th><spring:message code="system:usersPage.columns.fullName" /></th>
            <th><spring:message code="system:usersPage.columns.username" /></th>
            <c:if test="${showExportFunctionality}">
              <th style="width:10%"><spring:message code="common:sections.documents" /></th>
            </c:if>
            <th><spring:message code="system:usersPage.columns.role" /></th>
            <th><spring:message code="groups.view.table.autosharing"/></th>
            <th style="min-width: 120px"><spring:message code="user.actions.label" /></th>
            <c:if test="${showExportFunctionality}">
              <th><spring:message code="system:usersPage.export.work" /></th>
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
                      data-user-id="${ug.user.id}"
                      data-first-name="${ug.user.firstName}"
                      data-last-name="${ug.user.lastName}"
                      data-unique-id="${ug.user.id}"
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
                      data-user-id="${ug.user.id}"
                      data-username="${ug.user.username}"
                      data-unique-id="${ug.user.username}"
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
                    <c:when test="${ug.user.PI and ug.user ne subject}"><img src="/images/icons/folder-unavailable.png" title="${homeFolderNotAvailableTitle}"></c:when>
                    <c:otherwise><a href="/workspace/${ug.user.rootFolder.id}"><img src="/images/icons/folder-user.png" title="${goToHomeFolderTitle}"></a></c:otherwise>
                    </c:choose>
                  </span>
                </td>
              </c:if>
              <%-- can't remove or edit a PI in a lab group --%>
              <td>
                <span data-test-id="roleInGroup_${ug.user.username}">${ug.roleInGroup.label}</span>
                <c:if test="${ug.adminViewDocsEnabled}"> <img src='/images/icons/viewAllFolderIcon.png' style="vertical-align:middle;padding-bottom:2px;" title="<spring:message code='groups.view.permissions.canView'/>" /></c:if>
                <c:if test="${ug.piCanEditWork}"> <img src='/images/icons/editIcon.png' style="vertical-align:middle;padding-bottom:2px;" title="<spring:message code='groups.view.permissions.canEdit'/>" /></c:if>
              </td>
              <td id="autoshareStatus-${ug.user.id}"></td>
              <td>
                <c:if test="${roleEditable and ug.roleInGroup ne 'PI' and (group.isLabGroup() or canEdit)}">
                  <button type="button" class="changeRole changeRoleButton"
                    data-groupId="${group.id}" data-adminviewall="${ug.adminViewDocsEnabled}" data-userid="${ug.user.id}" data-username="${ug.user.username}" data-role="${ug.roleInGroup}"><img class="groupActionButtonIcon" src="/images/icons/changeRoleIcon.png" alt="" aria-hidden="true"><spring:message code="groups.actions.changeRole" /></button>
                </c:if>
              </td>
              <c:if test="${showExportFunctionality}">
                <td>
                  <c:if test="${!ug.user.PI or ug.user eq subject}">
                    <button type="button" class="exportUsersWorkButton groupEditButton" data-username="${ug.user.username}" aria-label="${exportWorkLabel}" title="${exportWorkLabel}">
                      <img class="exportUsersWorkButtonIcon" src="/images/icons/exportIcon2.png" alt="" aria-hidden="true">
                    </button>
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
                        <button type="button" class="removeLink"><img class="groupActionButtonIcon" src="/images/icons/closeIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.remove"/></button>
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
      <h3><spring:message code="groups.view.disabledAccountsHeading"/></h3>
      <table id="disabledAccounts" class="table" cellspacing="0">
        <tr>
          <th><spring:message code="system:usersPage.columns.fullName" /></th>
          <th><spring:message code="system:usersPage.columns.username" /></th>
          <c:if test="${showExportFunctionality}">
            <th>
              <spring:message code="common:sections.documents" />
            </th>
          </c:if>
          <c:if test="${showExportFunctionality}">
            <th>
              <spring:message code="system:usersPage.export.work" />
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
                      data-user-id="${ug.user.id}"
                      data-first-name="${ug.user.firstName}"
                      data-last-name="${ug.user.lastName}"
                      data-unique-id="${ug.user.id}"
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
                      data-user-id="${ug.user.id}"
                      data-username="${ug.user.username}"
                      data-unique-id="${ug.user.username}"
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
                  <c:when test="${ug.user.PI and ug.user ne subject}"><img src="/images/icons/folder-unavailable.png" title="${homeFolderNotAvailableTitle}"></c:when>
                  <c:otherwise><a href="/workspace/${ug.user.rootFolder.id}"><img src="/images/icons/folder-user.png" title="${goToHomeFolderTitle}"></a></c:otherwise>
                  </c:choose>
                </td>
              </c:if>
              <c:if test="${showExportFunctionality}">
                <td>
                  <c:if test="${!ug.user.PI or ug.user eq subject}">
                    <button type="button" class="exportUsersWorkButton groupEditButton" data-username="${ug.user.username}" aria-label="${exportWorkLabel}" title="${exportWorkLabel}">
                      <img class="exportUsersWorkButtonIcon" src="/images/icons/exportIcon2.png" alt="" aria-hidden="true">
                    </button>
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
                      <button type="button" class="removeLink"><img class="groupActionButtonIcon" src="/images/icons/closeIcon.png" alt="" aria-hidden="true"><spring:message code="groups.view.actions.remove"/></button>
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

<div id="raid-connections" data-group-id="${group.id}"></div>

<div id="setNewPiDialog" style="display:none;">
    <spring:message code="groups.view.setNewPi.label"/>
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
<spring:message code="groups.view.changeRole.helpLinkText" var="changeRoleHelpLinkText"/>
<spring:message code="common:help.labAdminRole" var="labAdminRoleHelpSlug"/>
<spring:message code="groups.view.changeRole.helpText">
  <spring:argument value='<a href="${f:helpDocsUrl(labAdminRoleHelpSlug)}">${changeRoleHelpLinkText}</a>'/>
</spring:message>

  <c:choose>
    <c:when test="${group.isProjectGroup()}">
      <fieldset>
        <legend><spring:message code="groups.view.changeRole.selectRolePrompt"/>  <br><br></legend>
        <label><input type="radio" name="role" id="roleOptionUser" value="DEFAULT"> <spring:message code="common:userDetails.roles.user"/></label><br>
        <label><input type="radio" name="role" id="roleOptionGroupOwner" value="GROUP_OWNER"> <spring:message code="groups.view.role.groupOwner"/></label>
      </fieldset>
    </c:when>
    <c:otherwise>
      <fieldset>
        <legend><spring:message code="groups.view.changeRole.selectRolePrompt"/>  <br><br></legend>
        <label><input type="radio" name="role" id="roleOptionUser" value="DEFAULT"> <spring:message code="common:userDetails.roles.user"/></label><br>
        <label><input type="radio" name="role" id="roleOptionAdmin" value="RS_LAB_ADMIN"> <spring:message code="groups.view.role.labAdmin"/></label>
      </fieldset>
        <p/>
        <div id="adminPermissions" style="display:none">
          <fieldset>
            <legend><spring:message code="groups.view.labAdminPermissions.legend"/></legend>
            <label><input type="radio" name="isAuthorized" id="adminViewOptionPersonal" value="false"> <spring:message code="groups.view.labAdminPermissions.personalOnly"/></label><br/>
            <label><input type="radio" name="isAuthorized" id="adminViewOptionAll" value="true"> <spring:message code="groups.view.labAdminPermissions.viewAll"/></label>
          </fieldset>
        </div>
    </c:otherwise>
  </c:choose>
</div>

<div id="removeMeFromGrp" style="display: none">
    <spring:message code="groups.view.removeMe.confirmText" arguments="${group.displayName}"/>
    <ul>
     <li> <spring:message code="groups.view.removeMe.noSharedContent"/>
     <li> <spring:message code="groups.view.removeMe.workNotVisible"/>
     <c:if test="${group.groupType != 'PROJECT_GROUP'}">
       <li> <spring:message code="groups.view.removeMe.piNoAccess"/>
     </c:if>
      <rst:hasDeploymentProperty name="profileHidingEnabled" value="true">
        <c:if test="${group.privateProfile}">
         <li>  <spring:message code="groups.view.removeMe.privateProfileWarning"/>
        </c:if>
     </rst:hasDeploymentProperty>
    </ul>
</div>

<script type="text/javascript">
  var groupId = "${group.id}";
  var displayName = "${group.displayName}";
</script>

  <!-- MJJA React Scripts -->
<rst:bundle bundle="myLabGroups" />
  <!--End MJJA React Scripts -->


<!-- Other React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<rst:bundle bundle="exportModal" />
<rst:bundle bundle="groupUserActivity" />
<rst:bundle bundle="groupEditBar" />
<rst:bundle bundle="memberAutoshareStatusWrapper" />
<rst:bundle bundle="raidConnections" />
<!--End React Scripts -->
