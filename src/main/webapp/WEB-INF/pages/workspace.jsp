<%@ include file="/common/taglibs.jsp"%>

<script>
    var recordId = "${recordId}"; // the record id of the current workspace view.
    <c:if test="${not empty searchConfig}">
      var isWorkspaceSearch = true;
    </c:if>
    var workspaceSettings = $.parseJSON(RS.unescape('${workspaceConfigJson}'));
    workspaceSettings.resultsPerPage = ${numberRecords};
    workspaceSettings.searchMode = false;
    var settingsKey = '${settingsKey}';
    var isSysAdmin = false;
    <shiro:hasAnyRoles name="ROLE_SYSADMIN">
      isSysAdmin = true;
    </shiro:hasAnyRoles>
</script>

<title>
  <spring:message code="common:appBar.sections.workspace.title" />
</title>

<head>
  <meta name="heading" content="<spring:message code='common:appBar.sections.workspace.title'/>" />
  <axt:stylesheet href="/styles/pages/workspace/workspace.css"/>

  <!-- moved to default.jsp -->
  <!-- <axt:stylesheet href="/styles/bootstrap-custom-flat.css"/> -->

  <axt:stylesheet href="/styles/dropbox.css"/>
  <axt:stylesheet href="/styles/journal.css"/>
  <axt:stylesheet href="/scripts/jqueryFileTree/jqueryFileTree.css"/>
  <axt:stylesheet href="/styles/pages/workspace/workspace-widgets.css"/>
  <axt:stylesheet href="/styles/pages/workspace/workspace-toolbar.css"/>
  <axt:stylesheet href="/styles/fontello.css"/>
  <axt:stylesheet href="/styles/pages/workspace/workspace-extra-icons.css"/>
  <axt:stylesheet href="/styles/pages/workspace/workspace-dialogs.css"/>
  <axt:stylesheet href="/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css"/>
  <axt:stylesheet href="/scripts/bower_components/jquery.fancytree/dist/skin-bootstrap/ui.fancytree.min.css"/>
  <axt:stylesheet href="/styles/tags/fileTreeBrowser.css"/>

  <script src="<rst:assetUrl value='/scripts/pages/workspace/clientUISettings.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery.scrollTo/jquery.scrollTo.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jquery.fancytree/dist/jquery.fancytree-all.min.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/tags/fileTreeBrowser.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/tags/fileTreeBrowserSorter.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/utils/autocomplete_mod.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/workspace/crudops.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/jqueryFileTree/jqueryFileTreeSorter.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jqui-multi-dates-picker/jquery-ui.multidatespicker.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>

  <script src="<rst:assetUrl value='/scripts/pages/workspace.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/workspace/workspaceSearch.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/messaging/notifications.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/messaging/messages.js'/>"></script>

  <script src="<rst:assetUrl value='/scripts/jqueryFileTree/jqueryFileTree.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/messaging/messageCreation.js'/>"></script>
  <script src="<rst:assetUrl value='/scripts/pages/workspace/calendarDialog.js'/>"></script>

  <!-- Moved to default.jsp -->
  <!-- <script src="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script> -->
</head>

<div
  id="toolbar2"
  data-pio-enabled="${pioEnabled}"
  data-aspose-enabled="${asposeEnabled}"
  data-labgroups-folder-id="${labgroupsFolderId}">
</div>

<div id="createDocForm">
  <!-- Importing script for word files manipulation -->
  <axt:importFromWord isNotebook="${isNotebook}" parentId="${recordId}"/>
  <axt:importFromProtocolsIo parentId="${recordId}"/>

  <%--
       And also some strange stuff happens here.
       One of these lines import bootstrap min file (why?!)
       (although we already have our own custom bootstrap on this page).
       Which in its turn causes complaints about path to font icons.
  --%>
  <axt:createFromTemplateDlg />

  <c:url var="createFromTemplateURL" value="/workspace/editor/structuredDocument/create/${recordId}"></c:url>

  <%--
    will have hidden input field with template name put in
    The 'createDocument' class means the action URL will be updated when the displayed
    folder is refreshed
  --%>
  <form id='createPopularSD' class="createDocument" method='POST' action="${createFromTemplateURL}"></form>
</div>

<!-- Dialog upon Create-Other Documents -->
<axt:formCreateMenuDialog parentFolderId="${recordId}">
</axt:formCreateMenuDialog>

<axt:crudops />

<!-- Search/emtpy list messages -->
<!-- <div class="tabularViewTop"> -->
  <div id="searchModePanel" style="display: none;">
    <span id="message"></span>
    <button id="resetSearch"><spring:message code="common:search.clearTooltip"/></button>
    <%--<button class="search-highlight-toggle" data-altLabel="Unighlight matches" data-on="false">Highlight matches</button>--%>
  </div>
<!-- </div> -->

<!-- file structure -->
<div id="record_list_frame" class="newTabularView bootstrap-custom-flat">
  <div id="record_list">
    <%-- this is a jsp:include as the included page does dynamic
      binding of objects to check permissions: c:import only handles strings--%>
      <jsp:include page="workspace_ajax.jsp" />
  </div>
</div>

<div id="fileBrowsing" class="fileTreeBrowserPanel bootstrap-custom-flat" style="display: none;">
    <ul id="fancyTree"></ul>
</div>

<jsp:include page="workspace/editor/include/messagingDialogs.jsp" />
<jsp:include page="workspace/calendarDialog.jsp" />

<div id="chemical-Searcher" style="display: none; text-align: center;">
  <div id="chemical-Searcher-Content"></div>
</div>

<jsp:include page="recordInfoPanel.jsp" />
<form id="createNewFormForm" style="display:none" target='_blank' method='POST' action='/workspace/editor/form/'></form>

<script>
//Hot patch.
//It should be moved into workspace.js during integration process.
$(document).ready(function() {
  //to activate bootstrap.js dropdown component without conflicts with jquery ui.
  //It is located in the end because otherwise jquery ui components overrides it
  $('.dropdown-toggle').dropdown();

});
</script>

<!-- React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<rst:bundle bundle="exportModal" />
<rst:bundle bundle="workspaceToolbar" />
<!--End React Scripts -->
