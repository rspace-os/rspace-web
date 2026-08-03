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
    <h4><spring:message code="connect.msoffice.unsupportedView.heading"/></h4>
    <p>
        <spring:message code="connect.msoffice.unsupportedView.notice"><spring:argument value="${docName}"/><spring:argument value="${docExtension}"/><spring:argument value="${actionToPerform}"/></spring:message>
    </p>
  </div>
</body>