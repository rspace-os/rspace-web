<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring"%>

<%@ attribute name="isNotebook" required="true" type="java.lang.Boolean"%>
<%@ attribute name="parentId" required="true" type="java.lang.Long"%>

<script src="<rst:assetUrl value='/scripts/tags/importFromWord.js'/>"></script>

<div id="wordDocChooserDlg" style="display: none">
	<form id="wordImportForm" data-parentid="${parentId}">
		<p>
			<div id="wordImportFormFileLabel"><spring:message code="dialogs.importFromWord.instruction"/></div>
            <span style="display: block;" class="formfield_highlighted">
                <input id="wordImportFormFileInput" aria-labelledby="wordImportFormFileLabel" name="wordXfile" type="file" multiple>
			</span>
        </p>
	</form>

    <div id="wordDocImportOptions">
        <label id="wordDocImportSelectLabel"><spring:message code="dialogs.importFromWord.afterImport"/></label>
        <select id="wordDocImportRecordSelect" aria-labelledby="wordDocImportSelectLabel" class="wordDocImportSelect">
            <option value="NEW"><spring:message code="dialogs.importFromWord.newDocuments"/></option>
            <option value="REPLACE"><spring:message code="dialogs.importFromWord.replaceDocument"/></option>
        </select>
        <select id="wordDocImportEntrySelect" aria-labelledby="wordDocImportSelectLabel" class="wordDocImportSelect">
            <option value="NEW"><spring:message code="dialogs.importFromWord.newEntries"/></option>
            <option value="REPLACE"><spring:message code="dialogs.importFromWord.replaceEntry"/></option>
        </select>
    </div>

    <c:if test="${isNotebook == false}">
		<axt:folderChooser folderChooserId="-wordimport"></axt:folderChooser>
	</c:if>

</div>
