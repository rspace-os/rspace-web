<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>${docName} (RSpace)</title>

    <script src="<rst:assetUrl value='/scripts/bower_components/jquery/dist/jquery.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
    
    <link rel="stylesheet" type="text/css" href="<rst:assetUrl value='/styles/bootstrap-custom-flat.css'/>" />

</head>

<style>
  body {
    margin: 20px auto;
    width: 1000px;
  }
</style>

<body> 
  <div class="bootstrap-custom-flat">
    <h4>Unsupported action</h4>
    <p>
        RSpace can't determine the way to open file <strong>${docName}</strong> (.${docExtension} type) for <strong>${actionToPerform}</strong> in Office.com.
        <br/>
        That's unexpected. Please try again, or contact your System Admin.
    </p>
  </div>
</body>