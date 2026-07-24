<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<script src="<rst:assetUrl value='/scripts/tags/useTemplate.js'/>"></script>

<style>
    #useTemplateDlgDesc, #useTemplateDlgName {
        margin: 10px 0px;
    }
</style>

<div id="useTemplateDlg" style="display: none;">

    <div id="useTemplateDlgDesc"><spring:message code="dialogs.useTemplate.description"/></div>
    <axt:folderChooser folderChooserId="-useTemplate"></axt:folderChooser>

    <div id="useTemplateDlgName">
        <label for="useTemplateNameInput"><spring:message code="dialogs.useTemplate.label.name"/></label>
        <input id="useTemplateNameInput" type="text" />
    </div>

    <form id="useTemplateForm" method="POST" action="#">
        <input id="useTemplateNewName" type="hidden" name="newname" />
        <input id="useTemplateFormTemplateId" type="hidden" name="template" />
    </form>

</div>