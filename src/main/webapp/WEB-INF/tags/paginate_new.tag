<%--
	Builds Bootstrap-compliant pagination from a List<PaginationObject>
--%>
<%@ attribute name="paginationList" required="true" type="java.util.List" %>
<%@ attribute name="isRegularLink" required="false"  type="java.lang.Boolean" %>
<%@ attribute name="omitATagLinkId" required="false"  type="java.lang.Boolean" %>


<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%-- This sets the links to previous /next pages  assuming an Ajax
 based handling to parse the link out of the ID--%>
<ul class="pagination new">
  <c:forEach var="page" items="${paginationList}">
    <c:set var="href" value="${page.link}"/>
    <c:if test="${empty isRegularLink or isRegularLink eq 'false'}">
      <c:set var="href" value="#"/>
    </c:if>

    <c:choose>
      <c:when test="${page.link=='#'}">
      	<li class="active"><a class="${page.className}" href="#">${page.name}</a></li>
      </c:when>
      <c:otherwise>
      	<c:choose>
      		<c:when test="${empty omitATagLinkId or omitATagLinkId eq 'false'}">
      			<li>
              <a class="${page.className}" id="page_${page.link}" href="${href}">${page.name}</a>
            </li>
      		</c:when>
      		<c:otherwise>
            <li>
              <a class="${page.className}" data-pageNumber="${page.pageNumber}" href="${href}">${page.name}</a>
            </li>
      		</c:otherwise>
      	</c:choose>
      </c:otherwise>
  	</c:choose>
  </c:forEach>
</ul>
