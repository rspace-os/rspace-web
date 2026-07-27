<%@ include file="/common/taglibs.jsp"%>

<input type="hidden" id="noOfRows" value="${fn:length(sharedRecords)}">
    <span id="publicRecordsPage"></span>
<div class="panel panel-default">
  <c:if test="${empty sharedRecords}">
    <p style="padding: 10px;"><spring:message code="groups.sharing.noPublishedDocuments"/></p>
  </c:if>

  <c:if test="${not empty sharedRecords}">
    <table id="sharedDataTable" class="table table-striped table-hover mainTable noCheckboxes">
      <thead>
        <tr>
          <th width="20%"><a class="orderBy" id="orderByName"
                             href="${orderByNameLink.link}"><spring:message code="groups.sharing.table.documentName"/></a></th>
          <th width="10%"><spring:message code="groups.sharing.table.link"/></th>
          <th width="10%"><spring:message code="groups.sharing.table.publisher"/></th>
          <th width="40%"><spring:message code="groups.sharing.table.description"/></th>
          <th width="10%"><a class="orderBy" id="orderByCreationDate"
                             href=${orderByCreationDateLink.link}><spring:message code="groups.sharing.table.publishedOn"/></a></th>
        </tr>
      </thead>

      <tbody>
        <c:forEach items="${sharedRecords}" var="groupSharing">
          <tr data-recordId="${groupSharing.shared.id}" data-recordName="${groupSharing.shared.name}" data-shareeName="${groupSharing.sharee.displayName}">
            <td>${groupSharing.shared.name}

              <c:choose>
              <c:when test="${groupSharing.shared.structuredDocument}">
                <td><a id="publicLink_${groupSharing.shared.id}" target='blank' href="/public/publishedView/document/${groupSharing.publicLink}"><spring:message code="groups.sharing.linkText"/></a>
              </td>
              </c:when>
                <c:when test="${groupSharing.shared.notebook}">
                   <td><a id="publicLink_${groupSharing.shared.id}" target='blank' href="/public/publishedView/notebook/${groupSharing.publicLink}"><spring:message code="groups.sharing.linkText"/></a>
                  </td>
                </c:when>
               <c:otherwise>
                  <td/>
               </c:otherwise>
              </c:choose>
            </td>

            <td>${groupSharing.sharedBy.username}</td>
            <td>${groupSharing.publicationSummary}</td>
            <td style="font-size: 12px;">
              <fmt:formatDate pattern="yyyy-MM-dd" value="${groupSharing.creationDate}" />
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
<script>
  $(document).ready(function () {
    makeImageLinksPublic();
  });
</script>