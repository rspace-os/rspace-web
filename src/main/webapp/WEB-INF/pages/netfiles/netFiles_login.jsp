<%@ include file="/common/taglibs.jsp"%>

<script type="text/javascript">
    var USER_FILESTORES_JSON_STRING = '${FILE_STORES_JSON}';
    var FILESYSTEMS_JSON_STRING = '${FILE_SYSTEMS_JSON}';
</script>

<div id="nfsUserPasswordLoginPanel" style="display:none" title='<spring:message code="netfilestores.login.dialog.title"/>'>

    <p id="nfsUserPasswordLoginInfo" style="margin: 10px;">
        <spring:message code="netfilestores.login.dialog.intro"/>
        <br/>"<span class="nfsFileSystemName"></span>"
    </p>

    <table style="margin:0px 0px 2px 0px; width: 100%;">
        <tr>
            <td style="padding-left:8px;"><spring:message code="netfilestores.login.username.label"/></td>
            <td><input type='text' id='nfsUsername'></input></td>
        </tr>
        <tr>
            <td style="padding-left:8px;"><spring:message code="netfilestores.login.password.label"/></td>
            <td><input type='password' id='nfsPassword'></input></td>
        </tr>
        <rst:hasDeploymentProperty name="loginDirectoryOption" value="true">
        <tr id="userDirectoriesRequired">
            <td style="padding-left:8px;"><spring:message code="netfilestores.login.user.dir.label"/></td>
            <td><input  id='nfsUserDir'></input></td>
        </tr>
        </rst:hasDeploymentProperty>
    </table>

    <div class="nfsError" style="margin-left:10px; margin-top: 5px; color:red;"></div>
</div>
