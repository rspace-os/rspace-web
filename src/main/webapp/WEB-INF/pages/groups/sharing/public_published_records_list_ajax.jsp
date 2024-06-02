<%@ include file="/common/taglibs.jsp"%>

<input type="hidden" id="noOfRows" value="${fn:length(sharedRecords)}">
    <span id="publicRecordsPage"></span>
<div class="panel panel-default">
  <c:if test="${empty sharedRecords}">
    <p style="padding: 10px;">You have no published documents to manage.</p>
  </c:if>

  <c:if test="${not empty sharedRecords}">
    <table id="sharedDataTable" class="table table-striped table-hover mainTable noCheckboxes">
      <thead>
        <tr>
          <th width="20%"><a class="orderBy" id="orderByName"
                             href="${orderByNameLink.link}">Document name</a></th>
          <th width="10%">Link</th>
          <th width="10%">Publisher</th>
          <th width="40%">Description</th>
          <th width="10%"><a class="orderBy" id="orderByCreationDate"
                             href=${orderByCreationDateLink.link}>Published on</a></th>
        </tr>
      </thead>

      <tbody>
        <c:forEach items="${sharedRecords}" var="groupSharing">
          <tr data-recordId="${groupSharing.shared.id}" data-recordName="${groupSharing.shared.name}" data-shareeName="${groupSharing.sharee.displayName}">
            <td>${groupSharing.shared.name}

              <c:choose>
              <c:when test="${groupSharing.shared.structuredDocument}">
                <td><a id="publicLink_${groupSharing.shared.id}" target='blank' href="/public/publishedView/document/${groupSharing.publicLink}">link</a>
              </td>
              </c:when>
                <c:when test="${groupSharing.shared.notebook}">
                   <td><a id="publicLink_${groupSharing.shared.id}" target='blank' href="/public/publishedView/notebook/${groupSharing.publicLink}">link</a>
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