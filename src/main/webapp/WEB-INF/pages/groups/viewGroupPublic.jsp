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
              </td>
            </tr>
          </c:if>
        </c:forEach>
        </tbody>
      </table>
    </div>
  </div>
</div>

<br><br>

<script type="text/javascript">
  var groupId = "${group.id}";
  var displayName = "${group.displayName}";
</script>

  <!-- MJJA React Scripts -->
<script src="<c:url value='/ui/dist/myLabGroups.js'/>"></script>
  <!--End MJJA React Scripts -->

<!-- Other React Scripts -->
<script src="<c:url value='/ui/dist/groupUserActivity.js'/>"></script>
<script src="<c:url value='/ui/dist/groupEditBar.js'/>"></script>
<script src="<c:url value='/ui/dist/memberAutoshareStatusWrapper.js'/>"></script>
<!--End React Scripts -->
