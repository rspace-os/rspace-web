<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>${docName} (RSpace)</title>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no">
    <link rel="shortcut icon" href="${favIcon}" />

    <script src="/scripts/bower_components/jquery/dist/jquery.min.js"></script>
    <script src="/scripts/bower_components/jquery-ui/jquery-ui.min.js"></script>
    <script src="/scripts/global.js"></script>
    <script src="/scripts/pages/connect/msoffice/officeView.js"></script>
    
    <link rel="stylesheet" href="/scripts/bower_components/jquery-ui/themes/researchspace/jquery-ui.css" />
    <link rel="stylesheet" href="/styles/bootstrap-custom-flat.css" />
    <link rel="stylesheet" href="/styles/pages/connect/msoffice/officeView.css" />

</head>

<div id="msOfficeIframeDiv">

    <form id="office_form" name="office_form" target="office_frame"
        <%--  action="https://FFC-excel.officeapps.live.com/x/_layouts/xlviewerinternal.aspx?wopisrc=https://load-test.researchspace.com/wopi/files/${fileId}"  --%>
          action="${actionUrl}"
          method="post">

        <input name="access_token" value="${msAccessToken}" type="hidden"/>
        <input name="access_token_ttl" value="${msAccessTokenTTL}" type="hidden"/>
    </form>
    
    <span id="frameholder"></span>
    
</div>

<div id="convertDlg" style="display:none">
  <div class="bootstrap-custom-flat">
    <h5>Do you want to convert this RSpace file?</h5>
    <p>
        Office.com cannot edit a file with <strong>.${docExtension}</strong> extension, but it can convert the file
        to <strong>.${convertActionTargetext}</strong>, which will be editable. Conversion shouldn't, but may result 
        in lost content.
    </p>
    <p>
        Do you want to convert the file <strong>${docName}</strong> so you can edit it in Office.com?
    </p>
  </div>
</div>

<script type="text/javascript">
    var editActionAvailable = ${editActionAvailable};
	var editActionUrl = '${editActionUrl}';
	var convertActionAvailable = ${convertActionAvailable};
	var convertActionUrl = '${convertActionUrl}';
</script>
