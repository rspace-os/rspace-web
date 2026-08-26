<%--
	Builds Bootstrap-compliant pagination from a List<PaginationObject>
--%>
<%@ attribute name="paginationList" required="true" type="java.util.List" %>
<%@ attribute name="isRegularLink" required="false"  type="java.lang.Boolean" %>
<%@ attribute name="omitATagLinkId" required="false"  type="java.lang.Boolean" %>


<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<%-- This sets the links to previous /next pages  assuming an Ajax
 based handling to parse the link out of the ID--%>
<ul class="pagination new">
  <c:forEach var="page" items="${paginationList}">
    <c:set var="href" value="${page.link}"/>
    <c:set var="pageLabel" value="${page.name}"/>
    <c:choose>
      <c:when test="${page.name eq 'First'}">
        <spring:message code="pagination.first" var="pageLabel"/>
      </c:when>
      <c:when test="${page.name eq 'Last'}">
        <spring:message code="pagination.last" var="pageLabel"/>
      </c:when>
    </c:choose>
    <c:if test="${empty isRegularLink or isRegularLink eq 'false'}">
      <c:set var="href" value="#"/>
    </c:if>

    <c:choose>
      <c:when test="${page.link=='#'}">
        <li class="active"><a class="${page.className}" data-page-name="${page.name}" href="#">${pageLabel}</a></li>
      </c:when>
      <c:otherwise>
      	<c:choose>
      		<c:when test="${empty omitATagLinkId or omitATagLinkId eq 'false'}">
      			<li>
              <a class="${page.className}" data-page-name="${page.name}" id="page_${page.link}" href="${href}">${pageLabel}</a>
            </li>
      		</c:when>
      		<c:otherwise>
            <li>
              <a class="${page.className}" data-page-name="${page.name}" data-pageNumber="${page.pageNumber}" href="${href}">${pageLabel}</a>
            </li>
      		</c:otherwise>
      	</c:choose>
      </c:otherwise>
  	</c:choose>
  </c:forEach>
</ul>
