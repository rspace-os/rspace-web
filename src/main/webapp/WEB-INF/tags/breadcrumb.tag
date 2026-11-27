<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="breadcrumb" required="true" type="com.researchspace.model.record.Breadcrumb" %>
<%@ attribute name="breadcrumbTagId" required="true" type="java.lang.String" %>

<div id="breadcrumbTag_${breadcrumbTagId}" class="breadcrumbTag">
    <%-- EscapeXmlELResolver already escapes ${bce.displayname}; c:out here would escape it twice --%>
    <div class="breadcrumbData" style="display: none">
        <c:forEach items="${breadcrumb.elements}" var="bce">
            <span data-id="${bce.id}" data-home="${bce.home}">${bce.displayname}</span>
        </c:forEach>
    </div>
    <div class="breadcrumbElems"></div>
</div>
