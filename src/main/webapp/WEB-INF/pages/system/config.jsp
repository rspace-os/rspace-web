<%@ include file="/common/taglibs.jsp"%>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title><spring:message code="system.config.button.label" /></title>

    <link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
    <script src="<c:url value='/scripts/pages/system/system.js'/>"></script>
    <script src="<c:url value='/scripts/pages/system/config.js'/>"></script>

    <script src="<c:url value='/scripts/require.js'/>"></script>
    <script type="text/javascript">
        require.config({
            baseUrl: "/"
        });

        // full paths to support MD5 rename
        var nfs_mod_path = '/scripts/pages/system/netfilesystem_mod.js'.replace(/.js$/, '');
        var ldap_mod_path = '/scripts/pages/system/ldap_mod.js'.replace(/.js$/, '');
        var settings_mod_path = '/scripts/pages/system/settings_mod.js'.replace(/.js$/, '');
        let ror_mod_path = '/scripts/pages/system/ror_mod.js'.replace(/.js$/, '');

        require(['.' + nfs_mod_path, '.' + settings_mod_path, '.' + ldap_mod_path, '.' + ror_mod_path],
                 function (filesystems, settings, ldap_mod_path, ror_mod_path) {
            console.info('filesystems, settings, ldap & ror loaded');
        });
    </script>
</head>
    <div id="topSection" class="bootstrap-custom-flat">
        <jsp:include page="topBar.jsp"></jsp:include>
    </div>

<div class="buttonsBelowTopBar">
    <a href="#" class="topSectionTextIconBtn" id="whitelistLink" style="padding-left:46px;background-image:url('/images/icons/whitelistIcon.png');"><spring:message code="system.config.button.whitelist" /></a>
    <a href="#" class="topSectionTextIconBtn" id="netFileSystemLink" style="padding-left:47px; background-image:url('/images/icons/fileSystemIcon.png');"><spring:message code="system.config.button.netfilesystem" /></a>
    <rst:hasDeploymentProperty name="rorEnabled" value="true">
    <a href="#" class="topSectionTextIconBtn" id="rorRegistryLink" style="padding-left:47px;background-size: 25%;background-position: 5px 5px; background-image:url('/images/icons/ROR_logo.svg');"><spring:message code="system.config.button.ror" /></a>
    </rst:hasDeploymentProperty>
    <rst:hasDeploymentProperty name="ldapEnabled" value="true">
        <a href="#" class="topSectionTextIconBtn" id="ldapSettingsLink" style="padding-left:30px; background-image:url('/images/icons/ldapIcon.png');"><spring:message code="system.config.button.ldap" /></a>
    </rst:hasDeploymentProperty>
    <a href="#" class="topSectionTextIconBtn" id="systemSettingsLink" style="padding-left:35px; background-image:url('/images/icons/settings.png');"><spring:message code="system.config.button.systemSettings" /></a>
</div><br/><br/><br/><br/>

<div id="mainArea"></div>

