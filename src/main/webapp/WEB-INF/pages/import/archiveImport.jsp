<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="importExport.title"/></title>
    <meta name="import Archive" content="import"/>

    <style>
    	.importForm {
    		margin: 0 auto;
            font-size: 1em;
    	}
    	.formBlock{
    		width:80%;
    	}
    	#importFormError, #importOntologyError{
    		text-align:right;
    		visibility:visible;
    		font-weight:bold;
    		color:red;
    	}
    	#exportBlock{
        	width:450px;
        	float:left;
    	}

    	#importBlock{
        	width:450px;
        	float:left;
        	margin-left:50px;
    	}
    	.exportLink{
        	font-size:1em !important;
        	line-height:1em !important;
        	font-weight:normal !important;
        	padding:4px 5px 3px 50px;
        	border-style:solid;
        	border-width:1px 1px 1px 1px;
        	border-color:#AFAFAF;
        	background:white;
        	background-repeat: no-repeat;
        	background-position: center left;
        	border-radius:0 !important;
        	text-shadow:none !important;
        	cursor: pointer;
        	width:200px;
        	display:inline;
        }
    </style>
    <script>
     var maxFileSize = ${applicationScope['RS_DEPLOY_PROPS']['maxUploadSize']};
     $(document).ready(function (e){
    	 $(document).on('click', '#exportMyWork', function (e){
    		e.preventDefault();
    		RS.getExportSelectionForExportDlg = function() {
					return getExportSelectionFromUsername(currentUser);
    		}
				RS.exportModal.openWithExportSelection(RS.getExportSelectionForExportDlg());
    	 });

    	 $('#importArchiveForm').on('submit',function (e) {
    		var size =  $("input[name='zipfile']")[0].files[0].size;
    		if(size >= maxFileSize) {
    			apprise("Archive [" +RS.humanFileSize(size) + "] is larger than the maximum permitted file upload of "
    			        + RS.humanFileSize(maxFileSize) + ". Please  ask your administrator to enable large files");
    			return false;
    		}

    		RS.blockingProgressBar.show({msg: "Importing archive", checkingInterval:1000, progressType:"rs-importXMLArchive"});
    		return true;
    	 });
		 $('#importOntologyForm').on('submit',function (e) {
			 var size =  $("input[name='csvfile']")[0].files[0].size;
			 if(size >= maxFileSize) {
				 apprise("Archive [" +RS.humanFileSize(size) + "] is larger than the maximum permitted file upload of "
						 + RS.humanFileSize(maxFileSize) + ". Please  ask your administrator to enable large files");
				 return false;
			 }

			 RS.blockingProgressBar.show({msg: "Importing ontology file", checkingInterval:1000, progressType:"rs-importXMLArchive"});
			 return true;
		 });
     });
    </script>
</head>

<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />

<p style="visibility:hidden;">Text</p>

<div id="container">
	<div id="exportBlock">
		<axt:export />
		<h3>
			<spring:message code="action.export" />
		</h3>
		<p>
			<spring:message code="importExport.export.selection.help1" />
		</p>
		<h3>
			<spring:message code="action.exportAll" />
		</h3>
		<p>
			<spring:message code="importExport.export.all.help1" />
		</p>
		<a href="#" id="exportMyWork" class="exportLink"
			style="background-image:url('/images/icons/exportIcon.png');">Export all my work</a>
	</div>

	<div id="importBlock">
		<h3>
			<spring:message code="action.import" />
		</h3>
		<p>
			<spring:message code="importExport.import.help1" />
		</p>
		<p>
			<spring:message code="importExport.import.help2" />
		</p>
		<form id="importArchiveForm" name="importer" method="post" action="/export/importArchive"
			enctype="multipart/form-data">
			<div class="importForm">
				<div class="formBlock">
					<label>
						<spring:message code="importExport.import.findFile" /><br />
						<br />
						<input id="importFileChooser" type="file" name="zipfile"></input><br />
						<br />
					</label>
				</div>
				<div class="formBlock">
					<label>
						<spring:message code="importExport.import.help3" /><br />
						<br />
						<input id="importSubmit" class="exportLink" style="background-image:url('/images/icons/importIcon.png');"
							type="submit" value='<spring:message code="action.import"/>'>
					</label>
				</div>
				<div class="importForm" style="text-align: right; line-height: 32px;">
					<span id="importFormError">
						<c:out value="${importFormError}"></c:out>
					</span>
				</div>

			</div>
		</form>
		<rst:hasDeploymentProperty name="cloud" value="false">
		<h3 style="margin-top:40px">Import an ontology file - csv format</h3>
		<form id="importOntologyForm" name="ontology-importer" method="post" action="/export/importOntology"
			  enctype="multipart/form-data">
			<div class="importForm">
				<div class="formBlock">
					<label>
						<spring:message code="importExport.import.findFile" /><br />
						<br />
						<input id="importOntologyFileChooser" type="file" name="csvfile"></input><br />
						<br />
					</label>
				</div>
                <div class="formBlock">
                    <label> Identify column which holds data
                        <br />
                        <input id="importOntologyDataColumn" type="number" min="1" max = "100" required="required" autofocus name="dataColumn">
                    </label>
                </div>
                <div class="formBlock">
                    <label> Identify column which holds uris for data (if single column data choose '1')
                        <br />
                        <input id="importOntologyDataUrl" type="number"  min="1" max = "100" required="required"name="urlColumn">
                    </label>
                </div>
                <div class="formBlock">
                    <label> Ontology name
                        <br />
                        <input id="importOntologyName" type="text"  required="required"  name="ontologyName">
                    </label>
                </div>
                <div class="formBlock">
                    <label> Ontology version
                        <br />
                        <input id="importOntologyVersion" type="text"  required="required"  name="ontologyVersion">
                    </label>
                </div>
				<div class="formBlock">
					<label>
						<spring:message code="importExport.import.help3" /><br />
						<br />
						<input id="importSubmitOntology" class="exportLink" style="background-image:url('/images/icons/importIcon.png');"
							   type="submit" value='<spring:message code="action.import"/>'>
					</label>
				</div>
				<div class="importForm" style="text-align: right; line-height: 32px;">
					<span id="importOntologyError">
						<c:out value="${importOntologyError}"></c:out>
					</span>
				</div>

			</div>
		</form>
		</rst:hasDeploymentProperty>
		<rst:hasDeploymentProperty name="importArchiveFromServerEnabled" value="true">
			<h3 style="margin-top:40px">Import archive on server</h3>
			If you want to import an archive that is already on the RSpace server, you can set this path instead of uploading
			from your
			computer. Please ask your RSpace administrator on how to get this file path.
			<form id="importArchiveOnServer" name="importOnServer" method="post" action="/export/importServerArchive">
				<div class="importForm">
					<div class="formBlock">
						<label>
							Enter absolute file path on server<br />
							<br />
							<input id="importFileChooser" name="serverFilePath" type="text" /><br />
							<br />
						</label>
					</div>
					<div class="formBlock">
						<label>
							<spring:message code="importExport.import.help3" /><br />
							<br />
							<input id="importServerSubmit" class="exportLink"
								style="background-image:url('/images/icons/importIcon.png');" type="submit"
								value='<spring:message code="action.import"/>'>
						</label>
					</div>
					<div class="importForm" style="text-align: right; line-height: 32px;">
						<span id="importFormError">
							<c:out value="${importFormError}"></c:out>
						</span>
					</div>
				</div>
			</form>
		</rst:hasDeploymentProperty>
	</div>
</div>

<!-- React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<script src="<c:url value='/ui/dist/exportModal.js'/>"></script>
<!--End React Scripts -->
