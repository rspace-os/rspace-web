<%@ tag description="Includes body content only once per request" pageEncoding="UTF-8" %>
<%@ attribute name="key" required="true" description="Unique key for this block" %>
<%@ taglib uri = "http://java.sun.com/jsp/jstl/core" prefix = "c" %>

<c:if test="${empty requestScope[key]}">
    <jsp:doBody />
    <c:set property="${key}" value="true" target="${requestScope}" />
</c:if>