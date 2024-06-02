<%@ include file="/common/taglibs.jsp"%>

<input type="hidden" id="noOfRows" value="${fn:length(sharedRecords)}">
    <span id="containsPublicLinks"></span>
<div class="panel panel-default">
  <c:if test="${empty sharedRecords}">
    <p style="padding: 10px;">You have no published documents to manage.
  </c:if>

  <c:if test="${not empty sharedRecords}">
    <table id="sharedDataTable" class="table table-striped table-hover mainTable noCheckboxes">
      <thead>
        <tr>
          <th width="20%"><a class="orderBy" id="orderByName"
            href="${orderByNameLink.link}">Document name</a></th>
          <th width="10%">Unique ID</th>
                <th width="15%">Public link</th>
          <th width="15%">Owner</th>
          <th width="15%">Publisher</th>
          <th width="15%"><a class="orderBy" id="orderByCreationDate"
                             href=${orderByCreationDateLink.link}>Published date</a></th>
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
              <a href="${loadDoc}">${groupSharing.shared.name}</a></td>
            <td>
              <a href="${globalURL}">${groupSharing.shared.globalIdentifier}</a>
            </td>

              <c:choose>
              <c:when test="${groupSharing.shared.structuredDocument}">
                <td><a id="publicLink_${groupSharing.shared.id}" target='blank' href="/public/publishedView/document/${groupSharing.publicLink}">public link</a>
              <li alt="copy link" copy-target="${groupSharing.shared.id}" class="linkShare public-tooltip " style="display: contents">
                <img src="/images/icons/copyIcon.png" style="top:-1px;"/>
                <span class="tooltiptext">Copy public link</span>
              </li></td>
              </c:when>
                <c:when test="${groupSharing.shared.notebook}">
                   <td><a id="publicLink_${groupSharing.shared.id}" target='blank' href="/public/publishedView/notebook/${groupSharing.publicLink}">public link</a>
                  <li alt="copy link" copy-target="${groupSharing.shared.id}" class=" linkShare public-tooltip" style="display: contents">
                    <img src="/images/icons/copyIcon.png" style="top:-1px"/>
                    <span class="tooltiptext">Copy public link</span></li></td>
                </c:when>
               <c:otherwise>
                  <td/>
               </c:otherwise>
              </c:choose>
            </td>

            <td>${groupSharing.shared.owner.username}</td>
            <td>${groupSharing.sharedBy.username}</td>
            <td style="font-size: 12px;">
              <fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${groupSharing.creationDate}" />
            </td>
            <td>
              <shiro:hasAnyRoles name="ROLE_ADMIN,ROLE_SYSADMIN">
                <a id="${groupSharing.id}" class="unshare" href="#">Unpublish</a>
              </shiro:hasAnyRoles>
              <shiro:lacksRole name="ROLE_SYSADMIN">
                <shiro:lacksRole name="ROLE_ADMIN">
                  <shiro:hasRole name="ROLE_PI">
                    <c:if test="${!(groupSharing.sharedBy.PI && groupSharing.sharedBy.username != sharee) }">
                      <a id="${groupSharing.id}" class="unshare" href="#">Unpublish</a>
                    </c:if>
                  </shiro:hasRole>
                  <shiro:lacksRole name="ROLE_PI">
                    <c:if test="${groupSharing.sharedBy.username eq sharee}">
                      <a id="${groupSharing.id}" class="unshare" href="#">Unpublish</a>
                    </c:if>
                  </shiro:lacksRole>
                </shiro:lacksRole>
              </shiro:lacksRole>
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
