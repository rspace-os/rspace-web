<%@ include file="/common/taglibs.jsp"%>

<input type="hidden" id="noOfRows" value="${fn:length(sharedRecords)}">
<div class="panel panel-default">
  <c:if test="${empty sharedRecords}">
    <p style="padding: 10px;"><spring:message code="groups.sharing.noSharedDocuments"/>
  </c:if>

  <c:if test="${not empty sharedRecords}">
    <table id="sharedDataTable" class="table table-striped table-hover mainTable noCheckboxes">
      <thead>
        <tr>
          <th width="20%"><a class="orderBy" id="orderByName"
            href="${orderByNameLink.link}"><spring:message code="groups.sharing.table.documentName"/></a></th>
          <th width="10%"><spring:message code="groups.sharing.table.uniqueId"/></th>
            <th width="20%"><a class="orderBy" id="orderBySharee"
            href="${orderByShareeLink.link}"><spring:message code="groups.sharing.table.sharedWith"/></a></th>
          <th width="15%"><a class="orderBy" id="orderByCreationDate"
                             href=${orderByCreationDateLink.link}><spring:message code="groups.sharing.table.sharedDate"/></a></th>
          <th width="15%"><spring:message code="common:shareDialog.columns.permission"/></th>
          <th width="10%"><spring:message code="groups.sharing.table.options"/></th>
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
          <spring:message code="groups.sharing.recordInfoLabel" var="recordInfoLabel"/>
          <tr data-recordId="${groupSharing.shared.id}" data-recordName="${groupSharing.shared.name}" data-shareeName="${groupSharing.sharee.displayName}">
            <td>
              <a href="#" class="recordInfoIcon" alt="${recordInfoLabel}" title="${recordInfoLabel}">
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
                    <spring:message code="groups.sharing.sharedForEditInto"/> <a href='/globalId/${groupSharing.targetFolder.globalIdentifier}'>${groupSharing.targetFolder.globalIdentifier}</a>
                  </div>
                </c:when>
                <c:otherwise>
                  <select class='update' style="font-size: inherit; padding: 2px 13px 2px 4px;" aria-label="<spring:message code='common:shareDialog.columns.permission'/>">
                    <option value="" disabled selected><spring:message code="${groupSharing.permType == 'WRITE' ? 'groups.sharing.permission.edit' : 'groups.sharing.permission.read'}"/></option>
                    <option value='READ'><spring:message code="groups.sharing.permission.read"/></option>
                    <option value='WRITE'><spring:message code="groups.sharing.permission.edit"/></option>
                  </select>
                </c:otherwise>
              </c:choose></td>
            <td>
              <a id="${groupSharing.id}" class="unshare" href="#"><spring:message code="common:shareDialog.permissions.unshare"/></a>
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
