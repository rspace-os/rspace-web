<%@ include file="/common/taglibs.jsp"%>
<%@ include file="include/templatePublishShareDlg.jsp"%>
<head>
  <title><fmt:message key="forms.manage.title" /></title>
  <script src="<c:url value='/scripts/pages/workspace/editor/formlist.js'/>"></script>
  <!-- <script src="<c:url value='/scripts/pages/admin.js'/>"></script> -->
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />

<div class="tabularViewTop">
  <h2 class="title">Manage Forms</h2>
  <div style="display:flex;align-items: center; padding-right: 15px">
      <label>My forms: <input type="radio" class="userFormsOnly" name="userFormsOnly" value="true"></label>
      <label>All forms: <input type="radio"  class="userFormsOnly" name="userFormsOnly" value="false"></label>
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
  <button id="resetSearch">Clear search</button>
</div>

<div id="formListContainer" class="newTabularView bootstrap-custom-flat">  
  <form id="postable" action="" method=POST>
    <input type="hidden" name="folderId" value="${rootId}">
  </form>
  
  <div id="formActions" class="crudopsTopPanel" style="margin-bottom: 5px !important;">
    <ul>
      <li class="formAction deleteForm deleteFormIcon" tabindex="0">
        Delete
      </li>
      <li class="formAction publish single publishIcon" tabindex="0">
        Publish
      </li>
      <li class="formAction unpublish single unpublishIcon" tabindex="0">  
        Unpublish
      </li>
      <li class="formAction managePermissions single permissionsIcon" tabindex="0">  
        Permissions
      </li>
      <li class="formAction copyForm copyFormIcon" tabindex="0">  
        Duplicate
      </li>
      <li class="formAction removeFromMenu single removeFromMenuIcon" tabindex="0"> 
        Remove from Menu
      </li>
      <li class="formAction addToMenu single addToMenuIcon" tabindex="0">  
        Add to Menu
      </li>
    </ul>
  </div>

  <div class="panel">
    <table id="templateList" class="table table-striped table-hover mainTable" width="100%">
      <thead>
        <tr>
          <th style="max-width: 50px;">
            Options
          </th>
          <th>
            Type
          </th>
          <th style="padding-left:31px">
            <a href="#" class="orderByLink" data-orderby='name' data-sortorder='ASC'>
              Form&nbsp;Name
            </a>
          </th>
          <th>
            <a href="#" class="orderByLink" data-orderby='owner.username' data-sortorder='ASC'>
              Owner
            </a>
          </th>
          <th>
            Version
          </th>
          <th>
            <a href="#" class="orderByLink" data-orderby='publishingState' data-sortorder='DESC'>
              Status
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
<script src="<c:url value='/ui/dist/baseSearch.js'/>"></script>
