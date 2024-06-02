<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>

<%@ attribute name="isNotebook" required="true" type="java.lang.Boolean"%>
<%@ attribute name="parentId" required="true" type="java.lang.Long"%>

<script src="<c:url value='/scripts/tags/importFromWord.js'/>"></script>

<style>
    #wordDocImportOptions {
        margin: 10px 0px;
    }
</style>

<div id="wordDocChooserDlg" style="display: none">
	<form id="wordImportForm" data-parentid="${parentId}">
		<p>
			<div id="wordImportFormFileLabel">Please choose 1 or more <span class="importfileType">Word/OpenOffice</span> files to import:</div> 
            <span style="display: block;" class="formfield_highlighted"> 
                <input id="wordImportFormFileInput" aria-labelledby="wordImportFormFileLabel" name="wordXfile" type="file" multiple>
                <input id="wordImportFormRecordToReplace" aria-labelledby="wordImportFormFileLabel" name="recordToReplaceId" type="hidden" >
			</span>
        </p>
	</form>
    
    <div id="wordDocImportOptions">
        <label id="wordDocImportSelectLabel">After importing:</label> 
        <select id="wordDocImportRecordSelect" aria-labelledby="wordDocImportSelectLabel" name="wordDocImportRecordSelect" class="wordDocImportSelect">
            <option value="NEW">save as a new document(s)</option>
            <option value="REPLACE">replace selected document</option>
        </select>
        <select id="wordDocImportEntrySelect" aria-labelledby="wordDocImportSelectLabel" name="wordDocImportEntrySelect" class="wordDocImportSelect">
            <option value="NEW">save as a new entry(ies)</option>
            <option value="REPLACE">replace the current entry</option>
        </select>
    </div>

    <c:if test="${isNotebook == false}">
		<axt:folderChooser folderChooserId="-wordimport"></axt:folderChooser>
	</c:if>

</div>