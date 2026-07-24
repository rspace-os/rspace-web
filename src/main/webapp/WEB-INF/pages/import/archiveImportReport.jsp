<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="importExport.import.report.title"/></title>
    <meta name="import Archive" content="import"/>

    <style>
        .archiveImportResultMsg {
            margin: 10px 0px;
            font-weight: bold;
        }
    </style>
    <script>
     $(document).ready(function () {
	 $.get("/export/ajax/latestImportResults", function (data){
		 var templateHTML = $('#archiveImportReportData').html();

	 var resultsHTML = Mustache.render(templateHTML, data);
           $('#reportContent').html(resultsHTML);

	 });
     });
    </script>
</head>

<jsp:include page="archiveImportReportData.html"/>

<h2><spring:message code="importExport.import.report.header"/></h2>
<a href="/workspace"><spring:message code="importExport.import.report.workspace.label"/></a>
<br/>
<a href="/import/archiveImport"><spring:message code="importExport.import.report.another.label"/></a>
<br/>

<div id="reportContent">
</div>
