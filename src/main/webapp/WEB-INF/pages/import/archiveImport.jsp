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
			apprise(RS.msg("legacyjs.archiveImport.archiveTooLarge", RS.humanFileSize(size), RS.humanFileSize(maxFileSize)));
			return false;
		}

		RS.blockingProgressBar.show({msg: RS.msg("legacyjs.archiveImport.importingArchive"), checkingInterval:1000, progressType:"rs-importXMLArchive"});
		return true;
	 });
		 $('#importOntologyForm').on('submit',function (e) {
			 var size =  $("input[name='csvfile']")[0].files[0].size;
			 if(size >= maxFileSize) {
				 apprise(RS.msg("legacyjs.archiveImport.archiveTooLarge", RS.humanFileSize(size), RS.humanFileSize(maxFileSize)));
				 return false;
			 }

			 RS.blockingProgressBar.show({msg: RS.msg("legacyjs.archiveImport.importingOntologyFile"), checkingInterval:1000, progressType:"rs-importXMLArchive"});
			 return true;
		 });
     });
    </script>
</head>

<jsp:include page="/WEB-INF/pages/admin/admin.jsp" />

<p style="visibility:hidden;"></p>

<div id="container">
	<div id="exportBlock">
		<axt:export />
		<h3>
			<spring:message code="common:actions.export" />
		</h3>
		<p>
			<spring:message code="importExport.export.selection.instructions" />
		</p>
		<h3>
			<spring:message code="importExport.export.all.heading" />
		</h3>
		<p>
			<spring:message code="importExport.export.all.instructions" />
		</p>
		<a href="#" id="exportMyWork" class="exportLink"
			style="background-image:url('/images/icons/exportIcon2.png');"><spring:message code="archiveImport.exportAllLink"/></a>
	</div>

	<div id="importBlock">
		<h3>
			<spring:message code="common:actions.import" />
		</h3>
		<p>
			<spring:message code="importExport.import.archiveIntro" />
		</p>
		<p>
			<spring:message code="importExport.import.archiveFormatExplanation" />
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
						<spring:message code="importExport.import.submitHelp" /><br />
						<br />
						<input id="importSubmit" class="exportLink" style="background-image:url('/images/icons/importIcon2.png');"
							type="submit" value='<spring:message code="common:actions.import"/>'>
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
		<h3 style="margin-top:40px"><spring:message code="archiveImport.importOntologyHeading"/></h3>
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
                    <label> <spring:message code="archiveImport.importOntologyDataColumnLabel"/>
                        <br />
                        <input id="importOntologyDataColumn" type="number" min="1" max = "100" required="required" autofocus name="dataColumn">
                    </label>
                </div>
                <div class="formBlock">
                    <label> <spring:message code="archiveImport.importOntologyUriColumnLabel"/>
                        <br />
                        <input id="importOntologyDataUrl" type="number"  min="1" max = "100" required="required"name="urlColumn">
                    </label>
                </div>
                <div class="formBlock">
                    <label> <spring:message code="archiveImport.importOntologyNameLabel"/>
                        <br />
                        <input id="importOntologyName" type="text"  required="required"  name="ontologyName">
                    </label>
                </div>
                <div class="formBlock">
                    <label> <spring:message code="archiveImport.importOntologyVersionLabel"/>
                        <br />
                        <input id="importOntologyVersion" type="text"  required="required"  name="ontologyVersion">
                    </label>
                </div>
				<div class="formBlock">
					<label>
						<spring:message code="importExport.import.submitHelp" /><br />
						<br />
						<input id="importSubmitOntology" class="exportLink" style="background-image:url('/images/icons/importIcon2.png');"
							   type="submit" value='<spring:message code="common:actions.import"/>'>
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
			<h3 style="margin-top:40px"><spring:message code="archiveImport.importArchiveOnServerHeading"/></h3>
			<spring:message code="archiveImport.importArchiveOnServerInstructions"/>
			<form id="importArchiveOnServer" name="importOnServer" method="post" action="/export/importServerArchive">
				<div class="importForm">
					<div class="formBlock">
						<label>
							<spring:message code="archiveImport.serverFilePathLabel"/><br />
							<br />
							<input id="importFileChooser" name="serverFilePath" type="text" /><br />
							<br />
						</label>
					</div>
					<div class="formBlock">
						<label>
							<spring:message code="importExport.import.submitHelp" /><br />
							<br />
							<input id="importServerSubmit" class="exportLink"
								style="background-image:url('/images/icons/importIcon2.png');" type="submit"
								value='<spring:message code="common:actions.import"/>'>
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
<rst:bundle bundle="exportModal" />
<!--End React Scripts -->
