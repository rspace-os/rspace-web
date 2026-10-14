<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="breadcrumb" required="true" type="com.researchspace.model.record.Breadcrumb" %>
<%@ attribute name="breadcrumbTagId" required="true" type="java.lang.String" %>

<div id="breadcrumbTag_${breadcrumbTagId}" class="breadcrumbTag">
    <div class="breadcrumbData" style="display: none">
        <c:forEach items="${breadcrumb.elements}" var="bce">
            <span data-id="${bce.id}" data-home="${bce.home}"><c:out value="${bce.displayname}" /></span>
        </c:forEach>
    </div>
    <script>
        $(document).ready(function() {
            var $breadcrumbTag = $("#breadcrumbTag_${breadcrumbTagId}");
            $breadcrumbTag.children(".breadcrumbData").children("span").each(function() {
                var $element = $(this);
                var displayName = $element.data("home")
                    ? RS.msg("legacyjs.core.breadcrumb.home")
                    : $element.text();
                var safeDisplayName = $("<span>").text(displayName).html();
                RS.addBreadcrumbElement("${breadcrumbTagId}", $element.attr("data-id"), safeDisplayName);
            });
            RS.refreshBreadcrumbElems("${breadcrumbTagId}");
        });
    </script>
    <div class="breadcrumbElems"></div>
</div>
