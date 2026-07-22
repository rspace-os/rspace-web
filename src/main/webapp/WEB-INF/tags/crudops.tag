<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<script src="<rst:assetUrl value='/scripts/tags/shareDlg.js'/>"></script>
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

<spring:message code="dialogs.crudops.msTeamsAbbreviation" var="crudopsMsTeamsAbbreviation"/>
<div id="crudops" class="rs-panel-flat crudopsTopPanel">
  <ul>
    <li class="crudopsAction copyIcon" id="createDocFromTemplate" tabindex="0">
      <spring:message code="dialogs.crudops.createDocument"/>
    </li>
  	<li class="crudopsAction copyIcon" id="copyRecords" tabindex="0">
      <spring:message code="common:actions.duplicate"/>
    </li>
  	<li class="crudopsAction moveIcon" id="moveRecords" tabindex="0">
      <spring:message code="common:actions.move"/>
    </li>
  	<li class="crudopsAction renameIcon" id="renameRecords" tabindex="0">
      <spring:message code="dialogs.renameRecord.action"/>
    </li>
  	<li class="crudopsAction deleteIcon" id="deleteRecords" tabindex="0">
      <spring:message code="common:actions.delete"/>
    </li>
    <li class="crudopsAction exportIcon" id="exportRecords" tabindex="0">
      <spring:message code="common:actions.export"/>
    </li>
    <li class="crudopsAction exportIcon" id="compareRecords" tabindex="0">
      <a href="#"><spring:message code="dialogs.crudops.csv"/></a>
    </li>
  	<li class="crudopsAction revisionsIcon" id="viewRevisions" tabindex="0">
      <spring:message code="dialogs.crudops.revisions"/>
    </li>
    <li class="crudopsAction favoritesIcon" id="addToFavorites" tabindex="0">
      <spring:message code="dialogs.crudops.addToFavorites"/>
    </li>
    <li class="crudopsAction removeFavoritesIcon" id="removeFromFavorites" tabindex="0">
      <spring:message code="dialogs.crudops.removeFromFavorites"/>

  <rst:hasDeploymentProperty name="cloud" value="false">
    <c:if test="${not empty  groups}">
    <li class="crudopsAction shareIcon" id="shareRecord">
      <a href="#" data-cloud="false"><spring:message code="common:actions.share"/></a>
    </li>
    </c:if>
      <c:if test="${publish_own_documents}">
          <li class="crudopsAction publishButton" id="publishRecord">
              <a href="#" data-cloud="false"> <spring:message code="common:actions.publish"/> </a>
          </li>
      </c:if>
  </rst:hasDeploymentProperty>
  <rst:hasDeploymentProperty name="cloud" value="true">
    <li class="crudopsAction shareIcon" id="shareRecord">
      <a href="#" data-cloud="true"><spring:message code="common:actions.share"/></a>
    </li>
  </rst:hasDeploymentProperty>

  <c:if test="${not empty extMessaging}">
      <script src="<rst:assetUrl value='/scripts/pages/messaging/extMessagingCreation.js'/>"></script>
      <c:forEach items="${extMessaging}" var="extMessageTarget">
          <c:if test="${extMessageTarget.available and extMessageTarget.enabled && extMessageTarget.hasOptions()}">
              <li class="crudopsAction sendToIcon" id="sendTo${extMessageTarget.name}"
                data-app="${extMessageTarget.name}" title="<spring:message code='dialogs.crudops.sendToTitle' arguments='${extMessageTarget.displayName}'/>" >
                 <a href="#"><spring:message code="dialogs.crudops.sendToLabel" arguments="${extMessageTarget.displayName eq 'Microsoft Teams' ? crudopsMsTeamsAbbreviation : extMessageTarget.displayName}"/></a>
              </li>
          </c:if>
      </c:forEach>
  </c:if>

    <li class="crudopsAction tagIcon" id="tagRecords" tabindex="0">
      <a href="#"><spring:message code="dialogs.crudops.addRemoveTags"/></a>
    </li>
  </ul>
</div>


    <!-- Move record dialog - initially hidden -->
    <div id='move-dialog' title="<spring:message code='dialogs.moveRecord.dialogTitle'/>" style="display: none;">
        <p><spring:message code="dialogs.moveRecord.instruction"/></p>
        <div class="sortingSettings" style="padding: 5px;">
            <spring:message code="workspace.sort.label"/>
            <select class="orderBy" aria-label="<spring:message code='workspace.sort.ariaLabel'/>">
                <option value="name"><spring:message code="workspace.sort.byName"/></option>
                <option value="creationdate"><spring:message code="workspace.sort.byCreationDate"/></option>
                <option value="modificationdate"><spring:message code="workspace.sort.byLastModified"/></option>
            </select>
            <select class="sortOrder" aria-label="<spring:message code='workspace.sort.sortOrderAriaLabel'/>">
                <option value="ASC"><spring:message code="workspace.sort.ascending"/></option>
                <option value="DESC"><spring:message code="workspace.sort.descending"/></option>
            </select>
        </div>
        <div id="movefolder-tree"
            style="height: 350px; background: white; overflow: scroll; border-style: solid; border-width: 1px; border-color: black; padding: 3px;">
        </div>
        <br />
        <p>
            <spring:message code="dialogs.moveRecord.selection"/><br /> <b><span id="folder-move-path">/</span></b>
        </p>
        <input type="hidden" id="selectedMoveTargetId" name="selectedTargetId"/>
    </div>

    <!-- Rename record dialog, initially hidden on page load -->
    <div id="renameRecord" style="display: none" >
        <p>
          <label for="nameField">
            <spring:message code="dialogs.renameRecord.label.newName"/>
          </label>
          <input id="nameField" type="text" width="30"></p>
    </div>


<axt:export/>

<axt:shareDlg shareDlgGroups="${groups}" shareDlgUsers="${uniqueUsers}"/>
<axt:publishDlg/>
<axt:useTemplate />
