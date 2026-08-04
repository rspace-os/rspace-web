<%@ include file="/common/taglibs.jsp"%>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title><spring:message code="system.support.button.label" /></title>

    <link rel="stylesheet" href="<rst:assetUrl value='/styles/system.css'/>" />
    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/bootstrap-namespace/bootstrap-namespace.min.css'/>" />

    <script src="<rst:assetUrl value='/scripts/pages/system/support.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/system/system.js'/>"></script>

    <link rel="stylesheet" href="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.css'/>" />
    <script src="<rst:assetUrl value='/scripts/bower_components/jqueryui-timepicker-addon/dist/jquery-ui-timepicker-addon.min.js'/>"></script>

    <script src="<rst:assetUrl value='/scripts/pages/system/maintenance_mod.js'/>"></script>

    <style>
        .ui-timepicker-div .ui-widget-header { margin-bottom: 8px; }
        .ui-timepicker-div dl { text-align: left; }
        .ui-timepicker-div dl dt { height: 25px; margin-bottom: -25px; }
        .ui-timepicker-div dl dd { margin: 0 10px 10px 65px; }
        .ui-timepicker-div td { font-size: 90%; }
        .ui-tpicker-grid-label { background: none; border: none; margin: 0; padding: 0; }
        dt { white-space: normal;}
    </style>
</head>

<div id="topSection" class="bootstrap-custom-flat">
	<jsp:include page="topBar.jsp"></jsp:include>
</div>
<div class="buttonsBelowTopBar">
    <shiro:hasRole name="ROLE_SYSADMIN">
	    <a href="#" class="topSectionTextIconBtn" id="viewServerLogsLink" style="padding-left:68px;background-image:url('/images/icons/logButtonIconView.png');">
	             <spring:message code="system.support.serverLogs.view" /></a>
	    <a href="#" class="topSectionTextIconBtn" id="mailServerLogsLink" style="padding-left:82px;background-image:url('/images/icons/logButtonIconSend.png');">
	            <spring:message code="system.support.serverLogs.mailLabel" /></a>
        <a href="#" class="topSectionTextIconBtn" id="scheduleMaintenanceLink" style="padding-left:65px;background-image:url('/images/icons/scheduleDowntime.png');">
               <spring:message code="system.downtime.button.label" /></a>
         <a href="#" class="topSectionTextIconBtn" id="forceRefreshLicenseLink" style="padding-left:25px;background-image:url('/images/icons/license2.png');">
                   <spring:message code="system.forceRefreshLicense.button.label" /></a>
          <a href="#" class="topSectionTextIconBtn" id="showLicenseLink" style="padding-left:25px;background-image:url('/images/icons/license2.png');">
                   <spring:message code="system.showLicense.button.label" /></a>

    </shiro:hasRole>
</div><br/><br/><br/><br/>

<script type="text/mustache" id="licenseInfoTemplate">
 <div class ="licenseInfo bootstrap-namespace" style="margin-top:50px">
    <h3> <spring:message code="system.support.licenseDetails.title"/> </h3>
     <dl class="dl-horizontal">
      <dt><spring:message code="system.support.licenseDetails.expiryDate"/></dt>
      <dd> {{expiryDateFormatted}}</dd>
      <dt><spring:message code="system.support.licenseDetails.totalUserLicenses"/></dt>
      <dd>{{totalUserSeats}}</dd>

     <dt style="white-space: normal;"><spring:message code="system.support.licenseDetails.totalFreeAdminSeats"/></dt>
      <dd>{{totalFreeRSpaceadmin}}</dd>
      <dt><spring:message code="system.support.licenseDetails.totalFreeSysadminSeats"/> </dt>
      <dd>{{totalFreeSysadmin}}</dd>
    </dl>
 </div>
</script>

<%-- handled by ajax --%>
<div id="postLogsFormContainer" style="display: none">
    <form  id="postLogsForm"
	action="#">
    <br/><spring:message code="system.support.serverLogs.lineCountHelp" arguments="50000"/><br/><br/>
    <input type="number" required min="0" max="50000" name="numLines" value="500" >
    <br/><br/><spring:message code="system.support.serverLogs.problemDescriptionHelp" /><br/><br/>
    <textarea rows="10" cols="57" name="message"></textarea><br/>
    <div class="bootstrap-custom-flat">
      <button class="btn btn-default" id="logSubmit" type="submit" style="margin:10px 0 0 340px;"><span class="ui-button-text"><spring:message code="system.support.serverLogs.sendButton"/></span></button>
    </div>
    </form>
</div>

<div id="mainArea"></div>
