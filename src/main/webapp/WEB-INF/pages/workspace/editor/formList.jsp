<%@ include file="/common/taglibs.jsp"%>
<%@ include file="include/templatePublishShareDlg.jsp"%>
<head>
  <title><spring:message code="form.manage.title"/></title>
  <script src="<rst:assetUrl value='/scripts/pages/workspace/editor/formlist.js'/>"></script>
  <!-- <script src="<rst:assetUrl value='/scripts/pages/admin.js'/>"></script> -->
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />

<div class="tabularViewTop">
  <h2 class="title"><spring:message code="form.manage.heading"/></h2>
  <div style="display:flex;align-items: center; padding-right: 15px">
      <label><spring:message code="form.manage.myFormsLabel"/> <input type="radio" class="userFormsOnly" name="userFormsOnly" value="true"></label>
      <label><spring:message code="form.manage.allFormsLabel"/> <input type="radio"  class="userFormsOnly" name="userFormsOnly" value="false"></label>
  </div>
  <div 
    id='searchFormListInput'
    class="base-search"
    data-onsubmit="handleSearchForms"
    data-variant="outlined"
    data-elid="search-form-list-input"
  ></div>
</div>

<div id="searchModePanel" style="display: none;">
  <span id="message"></span>
  <button id="resetSearch"><spring:message code="common:search.clearTooltip"/></button>
</div>

<div id="formListContainer" class="newTabularView bootstrap-custom-flat">  
  <form id="postable" action="" method=POST>
    <input type="hidden" name="folderId" value="${rootId}">
  </form>
  
  <div id="formActions" class="crudopsTopPanel" style="margin-bottom: 5px !important;">
    <ul>
      <li class="formAction deleteForm deleteFormIcon" tabindex="0">
        <spring:message code="common:actions.delete"/>
      </li>
      <li class="formAction publish single publishIcon" tabindex="0">
        <spring:message code="common:actions.publish"/>
      </li>
      <li class="formAction unpublish single unpublishIcon" tabindex="0">
        <spring:message code="form.editor.unpublishLabel"/>
      </li>
      <li class="formAction managePermissions single permissionsIcon" tabindex="0">
        <spring:message code="form.manage.permissionsLabel"/>
      </li>
      <li class="formAction copyForm copyFormIcon" tabindex="0">
        <spring:message code="common:actions.duplicate"/>
      </li>
      <li class="formAction removeFromMenu single removeFromMenuIcon" tabindex="0">
        <spring:message code="form.manage.removeFromMenu"/>
      </li>
      <li class="formAction addToMenu single addToMenuIcon" tabindex="0">
        <spring:message code="form.manage.addToMenu"/>
      </li>
    </ul>
  </div>

  <div class="panel">
    <table id="templateList" class="table table-striped table-hover mainTable" width="100%">
      <thead>
        <tr>
          <th style="max-width: 50px;">
            <spring:message code="workspace.list.options.header"/>
          </th>
          <th>
            <spring:message code="workspace.list.type.header"/>
          </th>
          <th style="padding-left:31px">
            <a href="#" class="orderByLink" data-orderby='name' data-sortorder='ASC'>
              <spring:message code="form.manage.formNameHeader"/>
            </a>
          </th>
          <th>
            <a href="#" class="orderByLink" data-orderby='owner.username' data-sortorder='ASC'>
              <spring:message code="workspace.list.owner.header"/>
            </a>
          </th>
          <th>
            <spring:message code="workspace.list.version.header"/>
          </th>
          <th>
            <a href="#" class="orderByLink" data-orderby='publishingState' data-sortorder='DESC'>
              <spring:message code="form.manage.statusHeader"/>
            </a>
          </th>
        </tr>
      </thead>
      <jsp:include page="formList_ajax.jsp"></jsp:include>
    </table>
  </div>
</div>


<jsp:include page="/WEB-INF/pages/recordInfoPanel.jsp"></jsp:include>

<!-- Import React search -->
<rst:bundle bundle="baseSearch" />
