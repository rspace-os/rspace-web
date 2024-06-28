<%@ include file="/common/taglibs.jsp"%>

<div class="bootstrap-custom-flat" style="display: inline-block">
	<rst:hasDeploymentProperty name="netFileStoresEnabled" value="true">
		<p class="bg-success" style="padding: 5px 10px">
		    <spring:message code="system.netfilesystem.filestores.enabled.msg" />
		</p>
	</rst:hasDeploymentProperty>
	<rst:hasDeploymentProperty name="netFileStoresEnabled" value="false">
		<p class="bg-warning" style="padding: 5px 10px">
		    <spring:message code="system.netfilesystem.filestores.disabled.msg" />
		</p>
	</rst:hasDeploymentProperty>
	<p class="bg-success" style="padding: 5px 10px">
		<rst:hasDeploymentProperty name="netFileStoresExportEnabled" value="true">
		    <spring:message code="system.netfilesystem.filestores.export.enabled.msg" />
		</rst:hasDeploymentProperty>
		<rst:hasDeploymentProperty name="netFileStoresExportEnabled" value="false">
		    <spring:message code="system.netfilesystem.filestores.export.disabled.msg" />
		</rst:hasDeploymentProperty>
	</p>
</div>

<div id="fileSystemsList">
    <div id="noFileSystemsMessage" style="display:none">
        <spring:message code="system.netfilesystem.message.no.filesystem" />
    </div>
    
    <div id="allFileSystems" style="display:none">
        <br/>
    
        <div id="fileSystemRowTemplate" style="display:none">
          <table>
            <tr>
                <td>{{fileSystem.name}}</td>
                <td>{{fileSystem.url}}</td>
                <td>{{fileSystem.enabled}}</td>
                <td>{{fileSystem.clientTypeLabel}}</td>
                <td>{{fileSystem.authType}}</td>
                <td>
                  <div class="bootstrap-custom-flat">
                    <button data-id="{{fileSystem.id}}" class="fileSystemDetailsButton btn btn-default">
                        <span class="ui-button-text"><spring:message code="system.netfilesystem.table.button.details" /></span>
                    </button>
                    <button data-id="{{fileSystem.id}}" data-name="{{fileSystem.name}}" class="fileSystemDeleteButton btn btn-default">
                        <span class="ui-button-text"><spring:message code="system.netfilesystem.table.button.delete" /></span>
                    </button>
                  </div>
                </td>
            </tr>
          </table>
        </div>
    
        <table>
            <thead> 
                <tr>
                    <th style="width: 12em;"><spring:message code="system.netfilesystem.table.column.name" /></th>
                    <th style="width: 16em;"><spring:message code="system.netfilesystem.table.column.url" /></th>
                    <th style="width: 5em;"><spring:message code="system.netfilesystem.table.column.enabled" /></th>
                    <th style="width: 6em;"><spring:message code="system.netfilesystem.table.column.client" /></th>
                    <th style="width: 10em;"><spring:message code="system.netfilesystem.table.column.auth" /></th>
                    <th style="width: 14em;"></th>
                </tr>
            </thead>
            <tbody id="allFileSystemsTableBody">
            </tbody>
        </table>
    </div>
    
    <br />
    <hr />
    <br />
</div>

<div id="fileSystemDetails" style="display:none">

    <form id="fileSystemDetailsForm">
        <h3 id="fileSystemDetailsHeader"><spring:message code="system.netfilesystem.details.header" /></h3>
        <h3 id="fileSystemAddingHeader"><spring:message code="system.netfilesystem.add.header" /></h3>
        <table>
            <tr class="fileSystemIdRow">
                <td><label for="fileSystemId"><spring:message code="system.netfilesystem.details.id" /></label></td>
                <td><div id="fileSystemId"></span></td>
            </tr>
            <tr>
                <td><label><spring:message code="system.netfilesystem.details.client" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemClientTypeSamba" name="fileSystemClientType" value="SAMBA" required>
                        <spring:message code="system.netfilesystem.details.client.samba" /></label>
                    <label><input type="radio" id="fileSystemClientTypeSftp" name="fileSystemClientType" value="SFTP">
                        <spring:message code="system.netfilesystem.details.client.sftp" /></label>
                     <label><input type="radio" id="fileSystemClientTypeIrods" name="fileSystemClientType" value="IRODS">
                        <spring:message code="system.netfilesystem.details.client.irods" /></label>
                </td>
            </tr>
            <tr class="fileSystemDetailsSambaRow">
                <td><label><spring:message code="system.netfilesystem.details.client.samba.type" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemClientTypeSambaSmbj" name="fileSystemClientTypeSamba" value="SMBJ" checked>
                        <spring:message code="system.netfilesystem.details.client.samba.smbj" /></label>
                    <label><input type="radio" id="fileSystemClientTypeSambaJcifs" name="fileSystemClientTypeSamba" value="SAMBA">
                        <spring:message code="system.netfilesystem.details.client.samba.jcifs" /></label>
                </td>
            </tr>
            <tr>
                <td style="width: 120px"><label for="fileSystemName"><spring:message code="system.netfilesystem.details.name" /></label></td>
                <td><input id="fileSystemName" type="text" style="width: 20em" required /></td>
            </tr>
            <tr>
                <td><label for="fileSystemUrl"><spring:message code="system.netfilesystem.details.url" /></label></td>
                <td><input id="fileSystemUrl" type="text" style="width: 20em" required /></td>
            </tr>
            <tr class="fileSystemDetailsSambaRow">
                <td><label for="fileSystemSambaDomain">
                    <spring:message code="system.netfilesystem.details.client.samba.domain" /></label></td>
                <td><input id="fileSystemSambaDomain" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsSambaShareRow">
                <td><label for="fileSystemSambaShare">
                    <spring:message code="system.netfilesystem.details.client.samba.share" /></label></td>
                <td><input id="fileSystemSambaShare" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsIrodsZoneRow">
               <td><label for="fileSystemIrodsZone">
                   <spring:message code="system.netfilesystem.details.client.irods.zone" /></label></td>
               <td><input id="fileSystemIrodsZone" type="text" style="width: 20em" required/></td>
            </tr>
            <tr class="fileSystemDetailsIrodsHomeDirRow">
               <td><label for="fileSystemIrodsHomeDir">
                   <spring:message code="system.netfilesystem.details.client.irods.homedir" /></label></td>
               <td><input id="fileSystemIrodsHomeDir" type="text" style="width: 20em" required/></td>
            </tr>
            <tr class="fileSystemDetailsIrodsPortRow">
                <td><label for="fileSystemIrodsPort">
                    <spring:message code="system.netfilesystem.details.client.irods.port" /></label></td>
                <td><input id="fileSystemIrodsPort" type="text" style="width: 20em" /></td>
             </tr>
            <tr class="fileSystemDetailsSftpRow">
                <td><label for="fileSystemSftpServerPublicKey">
                    <spring:message code="system.netfilesystem.details.client.sftp.server.public.key" /></label></td>
                <td><input id="fileSystemSftpServerPublicKey" type="text" style="width: 20em"/></td>
            </tr>
            <rst:hasDeploymentProperty name="loginDirectoryOption" value="true">
                <tr id="fileSystemDetailsSftpDirChoiceRow">
                    <td><label>
                        <spring:message code="system.netfilesystem.details.client.sftp.server.dir.choice"/></label></td>
                    <td><label><input type="radio" id="fileSystemDetailsSftpDirChoiceYes" value="true" name="fileSystemDirChoice" required/>Yes</label>
                        <label><input type="radio" id="fileSystemDetailsSftpDirChoiceNo" value="false" name="fileSystemDirChoice" />No</label></td>
                </tr>
            </rst:hasDeploymentProperty>
            <tr>
                <td><label><spring:message code="system.netfilesystem.details.auth" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemAuthTypePassword" name="fileSystemAuthType" value="PASSWORD" required>
                        <span id="fileSystemAuthTypePasswordSpan"><spring:message code="system.netfilesystem.details.auth.password" /></span></label>
                    <label for="fileSystemAuthTypePubKey"><input type="radio" id="fileSystemAuthTypePubKey" name="fileSystemAuthType" value="PUBKEY">
                        <spring:message code="system.netfilesystem.details.auth.pubkey" /></label>
                </td>
            </tr>
            <tr class="fileSystemDetailsPubKeyRow">
                <td><label for="fileSystemPubKeyRegistrationUrl">
                    <spring:message code="system.netfilesystem.details.auth.pubkey.registration.dialog.url" /></label></td>
                <td><input id="fileSystemPubKeyRegistrationUrl" type="text" style="width: 20em" /></td>
            </tr>
            <tr>
                <td><label><spring:message code="system.netfilesystem.details.status" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemStatusEnabled" name="fileSystemStatus" value="true" required>
                            <spring:message code="system.netfilesystem.details.status.enabled" /></label>
                    <label><input type="radio" id="fileSystemStatusDisabled" name="fileSystemStatus" value="false">
                            <spring:message code="system.netfilesystem.details.status.disabled" /></label>
                </td>
            </tr>
        </table>
    
        <div class="bootstrap-custom-flat">
          <button type="submit" class="btn btn-default">
            <div id="fileSystemCreateButton" class="ui-button-text">
                <spring:message code="system.netfilesystem.details.button.add" /></div>
            <div id="fileSystemUpdateButton" class="ui-button-text">
                <spring:message code="system.netfilesystem.details.button.update" /></div>
          </button>
        </div>
    </form>
    
    <br />
    <hr />
    <br />
    
</div>

<div class="bootstrap-custom-flat">
  <button id="addNewFileSystem" class="btn btn-default" style="width:15em;">
    <span class="ui-button-text"><spring:message code="system.netfilesystem.button.add.new.filesystem" /></span>
  </button>
</div>
