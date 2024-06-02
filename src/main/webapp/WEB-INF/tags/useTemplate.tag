<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>

<script src="<c:url value='/scripts/tags/useTemplate.js'/>"></script>

<style>
    #useTemplateDlgDesc, #useTemplateDlgName {
        margin: 10px 0px;
    }
</style>

<div id="useTemplateDlg" style="display: none;">

    <div id="useTemplateDlgDesc">Document will be created in your <b>Home</b> folder.</div>
    <axt:folderChooser folderChooserId="-useTemplate"></axt:folderChooser>

    <div id="useTemplateDlgName">
        <label for="useTemplateNameInput">Document name: </label>
        <input id="useTemplateNameInput" type="text" />
    </div>

    <form id="useTemplateForm" method="POST" action="#">
        <input id="useTemplateNewName" type="hidden" name="newname" />
        <input id="useTemplateFormTemplateId" type="hidden" name="template" />
    </form>

</div>