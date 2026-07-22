<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="admin.title"/></title>
</head>

<%-- Assets for the admin menu below. They live here in the body, NOT in the <head> above,
     because admin.jsp is included as a fragment by ~13 pages that each declare their own <head>
     first, and SiteMesh 3 keeps only the first <head> when heads nest. A second head here is
     dropped and its scripts silently lost (breaking e.g. userform, which calls
     applyAffiliationAutocomplete at init). Body <script>/<link> tags survive fragment inlining and
     run before document.ready, which is when the menu init and those callers execute. The standalone
     /admin page still gets its <title> from the head above. --%>
<link rel="stylesheet" href="<rst:assetUrl value='/styles/admin.css'/>" />
<script src="<rst:assetUrl value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
<script src="<rst:assetUrl value='/scripts/pages/admin.js'/>"></script>

<div class="separator"></div>
<div id="menuScrollContainer">
  <button type="button" class="menuScrollButton leftScroller bootstrap-custom-flat" aria-label="<spring:message code='menu.scrollButtons.previous'/>">
    <span class="glyphicon glyphicon-chevron-left"></span>
  </button>
  <div id="menuFixer">
    <div id="menuMover">

      <shiro:hasAnyRoles name="ROLE_PI">
      <!-- Any group that user is a PI of belongs to My LabGroups and this tab will be active -->
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/viewGroup') &&
            (pi.username eq subject.username)}"> currentPanel</c:if>">

        <a id="myLabGroupLink" href="/groups/viewPIGroup">
          <spring:message code="menu.labGroup"/><br>
          <img src="/images/icons/myLabGroup.png" class="menuInnerPanelIcon">
        </a>
      </li>
      </shiro:hasAnyRoles>

      <!-- Value of 'canEdit' indicates whether or not the profile is my own -->
      <li class="menuInnerPanel
        <c:if test="${pageContext.request.servletPath == '/WEB-INF/pages/userform.jsp' && canEdit}"> currentPanel</c:if>">

        <a id="myProfileLink" href="/userform">
          <spring:message code="menu.profile"/><br>
          <img src="/images/icons/myProfile.png" class="menuInnerPanelIcon">
        </a>
      </li>

      <rst:hasDeploymentProperty name="cloud" value="true">
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/cloud/createCloudGroupForm')}"> currentPanel</c:if>">
        <a id="newLabGroupLink" href='/cloud/group/new'>
          <spring:message code="menu.admin.newGroup"/><br>
          <img src="/images/icons/newGroup.png" class="menuInnerPanelIcon">
        </a>
      </li>
      </rst:hasDeploymentProperty>
        <li id="new_project_group" class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/cloud/createCloudGroupForm') and isProjectGroup}"> currentPanel</c:if>">
            <a id="newProjectGroupLink" href='/projectGroup/newGroupForm'>
                <spring:message code="menu.admin.newProjectGroup"/><br>
                <img src="/images/icons/projectgroup.png" class="menuInnerPanelIcon">
            </a>
        </li>
        <%-- This div is hidden by admin.js code UNLESS the system property self_service_labgroups is ALLOWED.
         The self_service_labgroups property can only be enabled on Enterprise RSpace --%>
      <shiro:hasRole name="ROLE_PI">
        <li id="self_service_labgroups" class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/admin/cloud/createCloudGroupForm') and !isProjectGroup}"> currentPanel</c:if>">
          <a id="newLabGroupLinkSelfService" href='/selfServiceLabGroup/group/new'>
            <spring:message code="menu.admin.newGroup"/><br>
            <img src="/images/icons/newGroup.png" class="menuInnerPanelIcon">
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
        }"> currentPanel</c:if>">

        <a id="directoryLink" href='/directory'>
          <spring:message code="directory.title"/><br>
          <img src="/images/icons/directory.png" class="menuInnerPanelIcon">
        </a>
      </li>

      <shiro:hasPermission name="Form:Create">
      <li class="menuInnerPanel
        <c:if test="${pageContext.request.servletPath == '/WEB-INF/pages/workspace/editor/form.jsp'}"> currentPanel</c:if>">
        <form id="createDocForm" method="POST" action='/workspace/editor/form/'>
          <a id="createFormLink" href="#">
            <spring:message code="menu.templates.create"/><br>
            <img src="/images/icons/newForm.png" class="menuInnerPanelIcon">
          </a>
        </form>
      </li>
      </shiro:hasPermission>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/workspace/editor/formList')}"> currentPanel</c:if>">
        <a id="manageFormsLink" href="/workspace/editor/form/list?orderBy=name&sortOrder=ASC&userFormsOnly=true">
          <spring:message code="menu.templates.list"/><br>
          <img src="/images/icons/listForms.png" class="menuInnerPanelIcon">
        </a>
      </li>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/workspace/workspacedeleted_history')}"> currentPanel</c:if>">
        <a id="deletedItemsLink" href="/workspace/trash/list">
          <spring:message code="menu.admin.trashList"/><br>
          <img src="/images/icons/deletedDocuments.png" class="menuInnerPanelIcon">
        </a>
      </li>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/sharing') && !publishedLinks_for_user_to_see && !publishedLinks_for_sysadmin_to_manage}"> currentPanel</c:if>">
        <a id="sharedDocumentsLink" href="/record/share/manage">
          <spring:message code="menu.admin.listRecordSharing"/><br>
          <img src="/images/icons/manageShared.png" class="menuInnerPanelIcon" style="max-height: 80px">
        </a>
      </li>
      <shiro:hasAnyRoles name="ROLE_PI,ROLE_USER">
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/sharing') && publishedLinks_for_user_to_see && !publishedLinks_for_sysadmin_to_manage}"> currentPanel</c:if>">
        <a id="publishedUserDocumentsLink" href="/record/share/published/manage">
          <shiro:hasRole name="ROLE_PI">
              <spring:message code="menu.publicListRecordSharing.pi"/><br>
          </shiro:hasRole>
          <shiro:hasRole name="ROLE_USER">
            <shiro:lacksRole name="ROLE_PI">
              <spring:message code="menu.publicListRecordSharing.user"/><br>
            </shiro:lacksRole>
          </shiro:hasRole>
          <img width="70" height="70" src="/images/icons/html.png" class="menuInnerPanelIconNew">
        </a>
      </li>
      </shiro:hasAnyRoles>
      <shiro:hasAnyRoles name="ROLE_SYSADMIN,ROLE_ADMIN">
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/groups/sharing') && publishedLinks_for_sysadmin_to_manage}"> currentPanel</c:if>">
        <a id="publishedDocumentsLink" href="/record/share/published/manage">
          <spring:message code="menu.admin.publicListRecordSharing"/><br>
          <img width="70" height="70" src="/images/icons/html.png" class="menuInnerPanelIconNew">
        </a>
      </li>
      </shiro:hasAnyRoles>
      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/audit/auditing')}"> currentPanel</c:if>">
        <a id="auditingLink" href="/audit/auditing">
          <spring:message code="menu.admin.audit"/><br>
          <img src="/images/icons/auditingTrail.png" class="menuInnerPanelIcon">
        </a>
      </li>

      <li class="menuInnerPanel
        <c:if test="${fn:startsWith(pageContext.request.servletPath, '/WEB-INF/pages/import/archiveImport')}"> currentPanel</c:if>">
      	<a id="exportImportLink" href="/import/archiveImport">
          <spring:message code="menu.admin.inOut"/><br>
          <img src="/images/icons/exportImportN2.png" class="menuInnerPanelIcon">
        </a>
      </li>
    </div>
  </div>
  <button type="button" class="menuScrollButton rightScroller bootstrap-custom-flat" aria-label="<spring:message code='menu.scrollButtons.next'/>">
    <span class="glyphicon glyphicon-chevron-right"></span>
  </button>
</div>
