<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>

<%@ attribute name="isNotebook" required="true" type="java.lang.Boolean"%>
<%@ attribute name="parentId" required="true" type="java.lang.Long"%>

<script src="<rst:assetUrl value='/scripts/tags/importFromWord.js'/>"></script>

<div id="wordDocChooserDlg" style="display: none">
	<form id="wordImportForm" data-parentid="${parentId}">
		<p>
			<div id="wordImportFormFileLabel">Please choose 1 or more <span class="importfileType">Word/OpenOffice</span> files to import:</div> 
            <span style="display: block;" class="formfield_highlighted"> 
                <input id="wordImportFormFileInput" aria-labelledby="wordImportFormFileLabel" name="wordXfile" type="file" multiple>
			</span>
        </p>
	</form>

    <c:if test="${isNotebook == false}">
		<axt:folderChooser folderChooserId="-wordimport"></axt:folderChooser>
	</c:if>

</div>