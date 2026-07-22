<%@ include file="/common/taglibs.jsp"%>
<input type="hidden" id="noOfRows" value="${fn:length(deleted.results)}">
<div class="panel panel-default">
    <table id="documentHistory" class="table table-striped table-hover mainTable noCheckboxes">
        <thead>
        <tr>
            <th>
                <a href="#" class="orderByLink" data-orderby='name' data-sortorder='ASC'><spring:message code="workspace.list.name.header"/></a>
            </th>
            <th><spring:message code="workspace.list.id.header"/></th>
            <th><spring:message code="deletedItems.lastModifiedByHeader"/></th>
            <th>
                <a href="#" class="orderByLink" data-orderby='creationDate' data-sortorder='DESC'><spring:message code="workspace.list.creationDate.header"/></a>
            </th>
            <th>
                <a href="#" class="orderByLink" data-orderby='modificationDate' data-sortorder='DESC'><spring:message code="workspace.list.date.header"/></a>
            </th>
            <th>
                <a href="#" class="orderByLink" data-orderby='deletedDate' data-sortorder='DESC'><spring:message code="deletedItems.deletedHeader"/></a>
            </th>
            <th><spring:message code="workspace.list.options.header"/></th>
        </tr>
        </thead>

        <tbody>
        <c:forEach var="auditedDoc" items="${deleted.results}">
            <tr>
                <td>${auditedDoc.record.name}</td>
                <td>${auditedDoc.record.globalIdentifier}</td>
                <td>${auditedDoc.record.modifiedBy}</td>
                <td><rst:relDate input="${auditedDoc.record.creationDateAsDate}" relativeForNDays="3"/></td>
                <td><rst:relDate input="${auditedDoc.record.modificationDateAsDate}" relativeForNDays="3"/></td>
                <td><rst:relDate input="${auditedDoc.deletedDate}" relativeForNDays="3"/></td>
                <td>
                    <c:if test="${auditedDoc.record['class'].name eq 'com.researchspace.model.record.StructuredDocument'}">
                        <c:url value="/workspace/editor/structuredDocument/audit/view?recordId=${auditedDoc.record.id}&revision=${auditedDoc.revision}" var="viewDocument"></c:url>
                        <a href="${viewDocument}" style="margin-right:5px;"><spring:message code="workspace.list.view.header"/></a>
                    </c:if>
                    <a href="#" class="restore"><spring:message code="common:actions.restore"/></a>
                    <input type="hidden" name="revision" value="${auditedDoc.revision}"/>
                    <input type="hidden" name="recordId" value="${auditedDoc.record.id}"/>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<div class="tabularViewBottom bootstrap-custom-flat">
    <axt:paginate_new paginationList="${paginationList}" />
    <axt:numRecords />
    <input type="text" name="" id="resultsPerPage" hidden
           value="${numberRecords}">
</div>

