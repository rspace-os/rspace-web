<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<script src="<c:url value='/scripts/tags/shareDlg.js'/>"></script>
<%--
This is a reusable menu bar for CRUD type operations on resources.
It defines operations for move, copy, delete, revision and rename.

Its default operation is to work for a workspace page, however it is now parameterised to work
with child records from within a Document editor page as well.

It isn't yet fully reusable: We should continue with  ways to 

a) Add optional UI and operations
b) Disable operations when not needed
c) supply a URL for Ajax
d) Provide callbacks for Ajax replies for other operations when needed

This page makes certain assumptions about the UI 
a) Records are displayed in a table, 1 per row
b) Functionality is accessed through checkbox with class 'record_checkbox'
c) For renaming, existing names are held in <a id="_>name</a? type format for updating a name
d) A function 'idsToNames' gets ids and names of records based on a clicked checkbox. This depends on a layout 
 similar to the workspace page.
 --%>

<div id="crudops" class="rs-panel-flat crudopsTopPanel"> 
  <ul>
    <li class="crudopsAction copyIcon" id="createDocFromTemplate" tabindex="0">
      Create Document
    </li>
  	<li class="crudopsAction copyIcon" id="copyRecords" tabindex="0">
      Duplicate
    </li>
  	<li class="crudopsAction moveIcon" id="moveRecords" tabindex="0">
      Move
    </li>
  	<li class="crudopsAction renameIcon" id="renameRecords" tabindex="0">
      Rename
    </li>
  	<li class="crudopsAction deleteIcon" id="deleteRecords" tabindex="0">
      Delete
    </li>
    <li class="crudopsAction exportIcon newExport" tabindex="0">
      Export
    </li>
  	<li class="crudopsAction revisionsIcon" id="viewRevisions" tabindex="0">
      Revisions
    </li>
    <li class="crudopsAction favoritesIcon" id="addToFavorites" tabindex="0">
      Add to Favorites
    </li>
    <li class="crudopsAction removeFavoritesIcon" id="removeFromFavorites" tabindex="0">
      Remove from Favorites

  <rst:hasDeploymentProperty name="cloud" value="false">
    <c:if test="${not empty  groups}">
    <li class="crudopsAction shareIcon" id="shareRecord">
      <a href="#" data-cloud="false">Share</a>
    </li>
    </c:if>
      <c:if test="${publish_own_documents}">
          <li class="crudopsAction publishButton" id="publishRecord">
              <a href="#" data-cloud="false"> Publish </a>
          </li>
      </c:if>
  </rst:hasDeploymentProperty>
  <rst:hasDeploymentProperty name="cloud" value="true">
    <li class="crudopsAction shareIcon" id="shareRecord">
      <a href="#" data-cloud="true">Share</a>
    </li>
  </rst:hasDeploymentProperty>
  
  <rst:hasDeploymentProperty name="offlineButtonVisible" value="true">
    <li class="crudopsAction copyIcon" id="startOfflineWork">
      <a href="#">Offline</a>
    </li>
    <li class="crudopsAction copyIcon" id="endOfflineWork">
      <a href="#">End Offline</a>
    </li>
  </rst:hasDeploymentProperty>

  <c:if test="${not empty extMessaging}">
      <script src="<c:url value='/scripts/pages/messaging/extMessagingCreation.js'/>"></script>
      <c:forEach items="${extMessaging}" var="extMessageTarget">
          <c:if test="${extMessageTarget.available and extMessageTarget.enabled && extMessageTarget.hasOptions()}">
              <li class="crudopsAction sendToIcon" id="sendTo${extMessageTarget.name}"
                data-app="${extMessageTarget.name}" title="Send Message to ${extMessageTarget.displayName}" >
                 <a href="#">Send to ${extMessageTarget.displayName eq 'Microsoft Teams' ? 'MS Teams' : extMessageTarget.displayName}</a>
              </li>
          </c:if>
      </c:forEach>
  </c:if>
  
    <li class="crudopsAction tagIcon" id="tagRecords" tabindex="0">
      <a href="#">Add/Remove Tags</a>
    </li>

    <li class="crudopsAction tagIcon" id="compareRecords" tabindex="0">
      <a href="#">Compare</a>
    </li>
  </ul>
</div>

<fmt:bundle basename="bundles.workspace.workspace">

    <!-- Move record dialog - initially hidden -->
    <div id='move-dialog' title="Select move target" style="display: none;">
        <p><fmt:message key="dialogs.moveRecord.instruction"></fmt:message></p>
        <div class="sortingSettings" style="padding: 5px;">
            Order by:
            <select class="orderBy" aria-label="Order by">
                <option value="name">Name</option>
                <option value="creationdate">Creation Date</option>
                <option value="modificationdate">Last Modified</option>
            </select>
            <select class="sortOrder" aria-label="Sort order">
                <option value="ASC">Ascending</option>
                <option value="DESC">Descending</option>
            </select>
        </div>
        <div id="movefolder-tree"
            style="height: 350px; background: white; overflow: scroll; border-style: solid; border-width: 1px; border-color: black; padding: 3px;">
        </div>
        <br />
        <p>
            <fmt:message key="dialogs.moveRecord.selection"></fmt:message><br /> <b><span id="folder-move-path">/</span></b>
        </p>
        <input type="hidden" id="selectedMoveTargetId" name="selectedTargetId"/>
    </div>

    <!-- Rename record dialog, initially hidden on page load -->
    <div id="renameRecord" style="display: none" >
        <p>
          <label for="nameField">
            <fmt:message key="dialogs.renameRecord.label.newName"></fmt:message>
          </label>
          <input id="nameField" type="text" width="30"></p>
    </div>
    
</fmt:bundle>

<axt:export/>

<axt:shareDlg shareDlgGroups="${groups}" shareDlgUsers="${uniqueUsers}"/>
<axt:publishDlg/>
<axt:useTemplate />
