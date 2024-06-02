<%@ include file="/common/taglibs.jsp"%>

<%--Given a list of ISearchResults, will display these in the filetree view --%>
<ul class="jqueryFileTree" style="display: none;">
    <c:forEach items="${results}" var="record">
        <li class="directory <c:if test="${record.notebook eq 'true'}">notebook</c:if> collapsed">
            <a href="#" rel="${record.id}/" data-name="${record.name}" data-type="${record.type}" 
                data-creationdate="${record.creationDateMillis}"
                data-modificationdate="${record.modificationDateMillis}">${record.name}</a>
        </li>
    </c:forEach>
</ul>
