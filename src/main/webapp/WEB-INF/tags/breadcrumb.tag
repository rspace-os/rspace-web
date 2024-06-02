<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ attribute name="breadcrumb" required="true" type="com.researchspace.model.record.Breadcrumb" %>
<%@ attribute name="breadcrumbTagId" required="true" type="java.lang.String" %>

<div id="breadcrumbTag_${breadcrumbTagId}" class="breadcrumbTag">
    <script>
    	$(document).ready(function() {
        	<c:forEach items="${breadcrumb.elements}" var="bce">
                  RS.addBreadcrumbElement('${breadcrumbTagId}', '${bce.id}', '${bce.displayname}');
        	</c:forEach>
    		RS.refreshBreadcrumbElems('${breadcrumbTagId}');
    	});
	</script>
    <div class="breadcrumbElems"></div>
</div>