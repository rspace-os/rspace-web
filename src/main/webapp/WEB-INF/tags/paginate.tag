<%-- 
	Builds a set of links for pagination from a List<PaginationObject>
--%>
<%@ attribute name="paginationList" required="true" type="java.util.List" %>
<%@ attribute name="isRegularLink" required="false"  type="java.lang.Boolean" %>
<%@ attribute name="omitATagLinkId" required="false"  type="java.lang.Boolean" %>


<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%-- This sets the links to previous /next pages  assuming an Ajax
 based handling to parse the link out of the ID--%>
<c:forEach var="page" items="${paginationList}"> 
  <c:set var="href" value="${page.link}"/>
  <c:if test="${empty isRegularLink   or isRegularLink eq 'false'}">
   <c:set var="href" value="#"/>
  </c:if>
	<c:choose>
      <c:when test="${page.link=='#'}">
      	<span style="color: #666;">${page.name}</span>
      </c:when>
      <c:otherwise>
      	<c:choose>
      		<c:when test="${empty omitATagLinkId or omitATagLinkId eq 'false'}">
      			<a class="${page.className}" id="page_${page.link}" href="${href}">${page.name}</a>
      		</c:when>
      		<c:otherwise>
      			<a class="${page.className}" data-pageNumber="${page.pageNumber}" href="${href}">${page.name}</a>
      		</c:otherwise>
      	</c:choose>
      </c:otherwise>
	</c:choose>
</c:forEach>