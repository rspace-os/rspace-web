<%@ include file="/common/taglibs.jsp"%>

<input type="hidden" id="noOfRows" value="${fn:length(sharedRecords)}">
<div class="panel panel-default">
  <c:if test="${empty sharedRecords}">
    <p style="padding: 10px;">You have no shared documents to manage.
  </c:if>

  <c:if test="${not empty sharedRecords}">
    <table id="sharedDataTable" class="table table-striped table-hover mainTable noCheckboxes">
      <thead>
        <tr>
          <th width="20%"><a class="orderBy" id="orderByName"
            href="${orderByNameLink.link}">Document name</a></th>
          <th width="10%">Unique ID</th>
            <th width="20%"><a class="orderBy" id="orderBySharee"
            href="${orderByShareeLink.link}">Shared with</a></th>
          <th width="15%"><a class="orderBy" id="orderByCreationDate"
                             href=${orderByCreationDateLink.link}>Shared date</a></th>
            <th width="15%">Permission</th>
          <th width="10%">Options</th>
        </tr>
      </thead>

      <tbody>
        <c:forEach items="${sharedRecords}" var="groupSharing">
          <c:choose>
            <c:when test="${groupSharing.shared.structuredDocument}">
              <c:url var="loadDoc"
                value="/workspace/editor/structuredDocument/${groupSharing.shared.id}"></c:url>
            </c:when>
            <c:when test="${groupSharing.shared.notebook}">
              <c:url var="loadDoc"
                value="/notebookEditor/${groupSharing.shared.id}"></c:url>
            </c:when>
          </c:choose>
          <c:url value="/globalId/${groupSharing.shared.globalIdentifier}"
            var="globalURL"></c:url>
          <tr data-recordId="${groupSharing.shared.id}" data-recordName="${groupSharing.shared.name}" data-shareeName="${groupSharing.sharee.displayName}">
            <td>
              <a href="#" class="recordInfoIcon" alt="Record Info" title="Record Info">
                <img class="infoImg" src="/images/info.svg" style="top:-1px">
              </a>
                &nbsp; 
              <a href="${loadDoc}">${groupSharing.shared.name}</a>
            </td>
            <td>
              <a href="${globalURL}">${groupSharing.shared.globalIdentifier}</a>
            </td>
                <td>${groupSharing.sharee.displayName}</td>
            <td style="font-size: 12px;">
              <fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${groupSharing.creationDate}" />
            </td>
            <td><c:choose>
                <c:when test="${not empty groupSharing.targetFolder}">
                  <div style="margin: 4px 2px;">
                    Shared for edit into <a href='/globalId/${groupSharing.targetFolder.globalIdentifier}'>${groupSharing.targetFolder.globalIdentifier}</a>
                  </div>
                </c:when>
                <c:otherwise>
                  <select class='update' style="font-size: inherit; padding: 2px 13px 2px 4px;" aria-label="Permission">
                    <option value="" disabled selected>${groupSharing.permType.displayName}</option>
                    <option value='READ'>READ</option>
                    <option value='WRITE'>EDIT</option>
                  </select>
                </c:otherwise>
              </c:choose></td>
            <td>
              <a id="${groupSharing.id}" class="unshare" href="#">Unshare</a>
            </td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
  </c:if>

</div>

<div class="tabularViewBottom bootstrap-custom-flat">
  <axt:paginate_new paginationList="${paginationList}" />
  <axt:numRecords />
  <input type="text" name="" id="resultsPerPage" hidden
    value="${numberRecords}">
</div>
