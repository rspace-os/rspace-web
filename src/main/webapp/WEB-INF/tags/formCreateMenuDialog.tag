<%@ attribute name="parentFolderId" required="true" type="java.lang.Long" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>

<script src="/scripts/tags/formCreateMenuDialog.js"></script>
<div id="formListDlg" style="display: none">
	<p>Click on a form name to create a new Document of that type.</p>
	<!-- Paginates form listing when there are many forms. Clicking the link will  -->
	<input type="hidden" id="parentFolderId" value="${parentFolderId}"/>
    <!-- Popup dialog upon create-from Other Document listing template; gets populated by formCreateMenuDialog.js -->
    <div id="formDetails"></div>
</div>