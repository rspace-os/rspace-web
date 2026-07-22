<%@ include file="/common/taglibs.jsp"%>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title><spring:message code="system.config.button.label" /></title>

    <link rel="stylesheet" href="<rst:assetUrl value='/styles/system.css'/>" />
    <script src="<rst:assetUrl value='/scripts/pages/system/system.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/system/config.js'/>"></script>

    <script src="<rst:assetUrl value='/scripts/pages/system/netfilesystem_mod.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/system/settings_mod.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/system/ldap_mod.js'/>"></script>
    <rst:hasDeploymentProperty name="rorEnabled" value="true">
    <rst:bundle bundle="rorIntegration" />
    </rst:hasDeploymentProperty>
</head>
    <div id="topSection" class="bootstrap-custom-flat">
        <jsp:include page="topBar.jsp"></jsp:include>
    </div>

<div class="buttonsBelowTopBar">
    <a href="#" class="topSectionTextIconBtn" id="whitelistLink" style="padding-left:46px;background-image:url('/images/icons/whitelistIcon.png');"><spring:message code="system.config.button.whitelist" /></a>
    <a href="#" class="topSectionTextIconBtn" id="netFileSystemLink" style="padding-left:47px; background-image:url('/images/icons/fileSystemIcon.png');"><spring:message code="system.config.button.netFileSystem" /></a>
    <rst:hasDeploymentProperty name="rorEnabled" value="true">
    <a href="#" class="topSectionTextIconBtn" id="rorRegistryLink" style="padding-left:47px;background-size: 25%;background-position: 5px 5px; background-image:url('/images/icons/ROR_logo.svg');"><spring:message code="system.config.button.ror" /></a>
    </rst:hasDeploymentProperty>
    <rst:hasDeploymentProperty name="ldapEnabled" value="true">
        <a href="#" class="topSectionTextIconBtn" id="ldapSettingsLink" style="padding-left:30px; background-image:url('/images/icons/ldapIcon.png');"><spring:message code="system.config.button.ldap" /></a>
    </rst:hasDeploymentProperty>
    <a href="#" class="topSectionTextIconBtn" id="systemSettingsLink" style="padding-left:35px; background-image:url('/images/icons/settings.png');"><spring:message code="system.config.button.systemSettings" /></a>
</div><br/><br/><br/><br/>

<div id="mainArea">
    <rst:hasDeploymentProperty name="rorEnabled" value="true">
    <div id="rorIntegration"></div>
    </rst:hasDeploymentProperty>
</div>
