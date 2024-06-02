<%@ include file="/common/taglibs.jsp"%>

<fmt:bundle basename="bundles.admin.admin">
<head>
    <title><fmt:message key="admin.title"/></title>
    <meta name="heading" content="<fmt:message key='admin.heading'/>"/>
    <meta name="menu" content="MainMenu"/>

    <!-- moved to default.jsp -->
    <!-- <link rel="stylesheet" href="<c:url value='/styles/bootstrap-custom-flat.css'/>" /> -->

    <link rel="stylesheet" href="<c:url value='/scripts/bower_components/slick-carousel/slick/slick.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/admin.css'/>" />
    <script src="<c:url value='/scripts/bower_components/slick-carousel/slick/slick.min.js'/>"></script>
    <script src="<c:url value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
    <script src="<c:url value='/scripts/pages/admin.js'/>"></script>
</head>

<div class="separator"></div>
<div id="menuScrollContainer">
  <div class="menuScrollButton leftScroller bootstrap-custom-flat">
    <span class="glyphicon glyphicon-chevron-left"></span>
  </div>
  <div id="menuFixer">
    <div id="menuMover">

      <shiro:hasAnyRoles name="ROLE_PI">
      <!-- Any group that user is a PI of belongs to My LabGroups and this tab will be active -->
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/viewGroup') &&
            (pi.username eq subject.username)}"> currentPanel</c:if>" tabindex="0">

        <a id="myLabGroupLink" href="/groups/viewPIGroup">
          <fmt:message key="menu.labGroup"/><br>
          <img src="/images/icons/myLabGroup.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>
      </shiro:hasAnyRoles>

      <!-- Value of 'canEdit' indicates whether or not the profile is my own -->
      <li class="menuInnerPanel
        <c:if test="${pageContext.request.servletPath == '/WEB-INF/pages/userform.jsp' && canEdit}"> currentPanel</c:if>" tabindex="0">

        <a id="myProfileLink" href="/userform">
          <fmt:message key="menu.profile"/><br>
          <img src="/images/icons/myProfile.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>

      <rst:hasDeploymentProperty name="cloud" value="true">
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/cloud/createCloudGroupForm')}"> currentPanel</c:if>" tabindex="0">
        <a id="newLabGroupLink" href='/cloud/group/new'>
          <fmt:message key="menu.admin.newgroup"/><br>
          <img src="/images/icons/newGroup.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>
      </rst:hasDeploymentProperty>
        <li id="new_project_group" class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/cloud/createCloudGroupForm') and isProjectGroup}"> currentPanel</c:if>" tabindex="0">
            <a id="newProjectGroupLink" href='/projectGroup/newGroupForm'>
                <fmt:message key="menu.admin.newProjectGroup"/><br>
                <img src="/images/icons/projectgroup.png" class="menuInnerPanelIcon" tabindex="-1">
            </a>
        </li>
        <%-- This div is hidden by admin.js code UNLESS the system property self_service_labgroups is ALLOWED.
         The self_service_labgroups property can only be enabled on Enterprise RSpace --%>
      <shiro:hasRole name="ROLE_PI">
        <li id="self_service_labgroups" class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/cloud/createCloudGroupForm') and !isProjectGroup}"> currentPanel</c:if>" tabindex="0">
          <a id="newLabGroupLinkSelfService" href='/selfServiceLabGroup/group/new'>
            <fmt:message key="menu.admin.newgroup"/><br>
            <img src="/images/icons/newGroup.png" class="menuInnerPanelIcon" tabindex="-1">
          </a>
        </li>
      </shiro:hasRole>

      <!-- Cases:
        - viewing Directory lists of communities/groups/users,
        - viewing particular community/group/user (in the case of user it can't be my own profile),
        - other (helper) page: group created successfully
      -->
      <li class="menuInnerPanel
        <c:if test="${
        fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/directory/directory') ||
        fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/directory/communityView') ||
        (fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/viewGroup') && !(pi.username eq subject.username)) ||
        (pageContext.request.servletPath == '/WEB-INF/pages/userform.jsp' && !canEdit) ||
        fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/cloud/createCloudGroupSuccess')
        }"> currentPanel</c:if>" tabindex="0">

        <a id="directoryLink" href='/directory'>
          <fmt:message key="directory.title"/><br>
          <img src="/images/icons/directory.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>

      <shiro:hasPermission name="Form:Create">
      <li class="menuInnerPanel
        <c:if test="${pageContext.request.servletPath == '/WEB-INF/pages/workspace/editor/form.jsp'}"> currentPanel</c:if>" tabindex="0">
        <form id="createDocForm" method="POST" action='/workspace/editor/form/'>
          <a id="createFormLink" href="#">
            <fmt:message key="menu.templates.create"/><br>
            <img src="/images/icons/newForm.png" class="menuInnerPanelIcon" tabindex="-1">
          </a>
        </form>
      </li>
      </shiro:hasPermission>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/workspace/editor/formList')}"> currentPanel</c:if>" tabindex="0">
        <a id="manageFormsLink" href="/workspace/editor/form/list?orderBy=name&sortOrder=ASC&userFormsOnly=true">
          <fmt:message key="menu.templates.list"/><br>
          <img src="/images/icons/listForms.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/workspace/workspacedeleted_history')}"> currentPanel</c:if>" tabindex="0">
        <a id="deletedItemsLink" href="/workspace/trash/list">
          <fmt:message key="menu.admin.trashlist"/><br>
          <img src="/images/icons/deletedDocuments.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/sharing') && !publishedLinks_for_user_to_see && !publishedLinks_for_sysadmin_to_manage}"> currentPanel</c:if>" tabindex="0">
        <a id="sharedDocumentsLink" href="/record/share/manage">
          <fmt:message key="menu.admin.listrecordsharing"/><br>
          <img src="/images/icons/manageShared.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>
      <shiro:hasAnyRoles name="ROLE_PI,ROLE_USER">
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/sharing') && publishedLinks_for_user_to_see && !publishedLinks_for_sysadmin_to_manage}"> currentPanel</c:if>" tabindex="0">
        <a id="publishedUserDocumentsLink" href="/record/share/published/manage">
          <shiro:hasRole name="ROLE_PI">
              <fmt:message key="menu.publiclistrecordsharing.pi"/><br>
          </shiro:hasRole>
          <shiro:hasRole name="ROLE_USER">
            <shiro:lacksRole name="ROLE_PI">
              <fmt:message key="menu.publiclistrecordsharing.user"/><br>
            </shiro:lacksRole>
          </shiro:hasRole>
          <img width="70" height="70" src="/images/icons/html.png" class="menuInnerPanelIconNew" tabindex="-1">
        </a>
      </li>
      </shiro:hasAnyRoles>
      <shiro:hasAnyRoles name="ROLE_SYSADMIN,ROLE_ADMIN">
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/sharing') && publishedLinks_for_sysadmin_to_manage}"> currentPanel</c:if>" tabindex="0">
        <a id="publishedDocumentsLink" href="/record/share/published/manage">
          <fmt:message key="menu.admin.publiclistrecordsharing"/><br>
          <img width="70" height="70" src="/images/icons/html.png" class="menuInnerPanelIconNew" tabindex="-1">
        </a>
      </li>
      </shiro:hasAnyRoles>
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/audit/auditing')}"> currentPanel</c:if>" tabindex="0">
        <a id="auditingLink" href="/audit/auditing">
          <fmt:message key="menu.admin.audit"/><br>
          <img src="/images/icons/auditingTrail.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/import/archiveImport')}"> currentPanel</c:if>" tabindex="0">
      	<a id="exportImportLink" href="/import/archiveImport">
          <fmt:message key="menu.admin.inOut"/><br>
          <img src="/images/icons/exportImportN.png" class="menuInnerPanelIcon" tabindex="-1">
        </a>
      </li>
    </div>
  </div>
  <div class="menuScrollButton rightScroller bootstrap-custom-flat">
    <span class="glyphicon glyphicon-chevron-right"></span>
  </div>
</div>
</fmt:bundle>

<script type="text/javascript">
	var currentPosition = 1;
	var currentPanels = $(".menuInnerPanel").length;
</script>

