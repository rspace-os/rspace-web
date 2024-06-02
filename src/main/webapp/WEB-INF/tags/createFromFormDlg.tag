<%-- 
	Dialog that lists templates and forms in separate dialogs
--%>
<%@ attribute name="forms" required="true" type="java.util.List" %>
<%@ attribute name="createFromFormURL" required="true" type="java.lang.String" %>
<%@ attribute name="formsForCreateMenuPagination" required="true" type="java.util.List" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>

<style>
	.pagin a:link
            .pagin a:active {
            color: #1465b7;
            text-decoration: none;
            font-size:1em;
        }
        .pagin a:visited {
            color: #1465b7;
            background-color: transparent;
            font-size:1em;
        }
       .pagin  a:hover {
            color: #cc0000;
            text-decoration: none;
            font-size:1em;
        }
</style>
<span class="pagin">
<axt:paginate paginationList="${formsForCreateMenuPagination}"></axt:paginate></span>
<table>
	<tr>
		<th>Name</th>
	</tr>
	<c:forEach items="${forms}" var="form">
		<tr>
			<td>
			<form method="POST" class="createDocument" action="${createFromFormURL}">
    			<div style="float:left">
    				<img  src="/image/getIconImage/${form.iconId}" alt="Icon Image" height="32" width="32" />
                </div>
    			<div style="float:left;padding-top:12px;padding-left:5px;" >
    				<a href="#" style="color:blue;" class="createSDFromFormLink">${form.name}</a>
    			</div>
    			<input type="hidden" name="template" value="${form.id}">
			</form>
			</td>
		</tr>
	</c:forEach>
</table>