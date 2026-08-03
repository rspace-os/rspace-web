<%@ include file="/common/taglibs.jsp"%>

<div class="bootstrap-custom-flat" style="display: inline-block">
	<rst:hasDeploymentProperty name="netFileStoresEnabled" value="true">
		<p class="bg-success" style="padding: 5px 10px">
		    <spring:message code="system.netFileSystem.filestores.enabled" />
		</p>
	</rst:hasDeploymentProperty>
	<rst:hasDeploymentProperty name="netFileStoresEnabled" value="false">
		<p class="bg-warning" style="padding: 5px 10px">
		    <spring:message code="system.netFileSystem.filestores.disabled" />
		</p>
	</rst:hasDeploymentProperty>
    <rst:hasDeploymentProperty name="netFileStoresExportEnabled" value="true">
        <p class="bg-success" style="padding: 5px 10px">
            <spring:message code="system.netFileSystem.filestores.exportEnabled" />
        </p>
    </rst:hasDeploymentProperty>
    <rst:hasDeploymentProperty name="netFileStoresExportEnabled" value="false">
        <p class="bg-warning" style="padding: 5px 10px">
            <spring:message code="system.netFileSystem.filestores.exportDisabled" />
        </p>
    </rst:hasDeploymentProperty>
</div>

<div id="fileSystemsList">
    <div id="noFileSystemsMessage" style="display:none">
        <spring:message code="system.netFileSystem.message.noFilesystem" />
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
                        <span class="ui-button-text"><spring:message code="system.netFileSystem.table.buttonDetails" /></span>
                    </button>
                    <button data-id="{{fileSystem.id}}" data-name="{{fileSystem.name}}" class="fileSystemDeleteButton btn btn-default">
                        <span class="ui-button-text"><spring:message code="common:actions.delete" /></span>
                    </button>
                  </div>
                </td>
            </tr>
          </table>
        </div>

        <table>
            <thead>
                <tr>
                    <th style="width: 12em;"><spring:message code="system.netFileSystem.table.columnName" /></th>
                    <th style="width: 16em;"><spring:message code="system.netFileSystem.table.columnUrl" /></th>
                    <th style="width: 5em;"><spring:message code="system:usersPage.columns.enabled" /></th>
                    <th style="width: 6em;"><spring:message code="system.netFileSystem.table.columnClient" /></th>
                    <th style="width: 10em;"><spring:message code="system.netFileSystem.table.columnAuth" /></th>
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
        <h3 id="fileSystemDetailsHeader"><spring:message code="system.netFileSystem.details.header" /></h3>
        <h3 id="fileSystemAddingHeader"><spring:message code="system.netFileSystem.add.header" /></h3>
        <table>
            <tr class="fileSystemIdRow">
                <td><label for="fileSystemId"><spring:message code="system.netFileSystem.details.id" /></label></td>
                <td><div id="fileSystemId"></span></td>
            </tr>
            <tr>
                <td><label><spring:message code="system.netFileSystem.details.clientLabel" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemClientTypeSamba" name="fileSystemClientType" value="SAMBA" required>
                        <spring:message code="system.netFileSystem.details.clientSambaLabel" /></label>
                    <label><input type="radio" id="fileSystemClientTypeSftp" name="fileSystemClientType" value="SFTP">
                        <spring:message code="system.netFileSystem.details.clientSftpLabel" /></label>
                    <label><input type="radio" id="fileSystemClientTypeIrods" name="fileSystemClientType" value="IRODS">
                        <spring:message code="system.netFileSystem.details.clientIrodsLabel" /></label>
                    <label><input type="radio" id="fileSystemClientTypeS3" name="fileSystemClientType" value="S3">
                        <spring:message code="system.netFileSystem.details.clientS3Label" /></label>
                </td>
            </tr>

            <tr class="fileSystemDetailsSambaRow">
                <td><label><spring:message code="system.netFileSystem.details.clientSambaType" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemClientTypeSambaSmbj" name="fileSystemClientTypeSamba" value="SMBJ" checked>
                        <spring:message code="system.netFileSystem.details.clientSambaSmbj" /></label>
                    <label><input type="radio" id="fileSystemClientTypeSambaJcifs" name="fileSystemClientTypeSamba" value="SAMBA">
                        <spring:message code="system.netFileSystem.details.clientSambaJcifs" /></label>
                </td>
            </tr>
            <tr class="fileSystemDetailsS3Row">
                <td><label><spring:message code="system.netFileSystem.details.clientS3TypeLabel" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemClientTypeS3AWS" name="fileSystemClientTypeS3" value="AWS">
                        <spring:message code="system.netFileSystem.details.clientS3TypeAws" /></label>
                    <label><input type="radio" id="fileSystemClientTypeS3Other" name="fileSystemClientTypeS3" value="OTHER">
                        <spring:message code="system.netFileSystem.details.clientS3TypeOther" /></label>
                </td>
            </tr>

            <tr>
                <td style="width: 120px"><label for="fileSystemName"><spring:message code="system.netFileSystem.details.name" /></label></td>
                <td><input id="fileSystemName" type="text" style="width: 20em" required /></td>
            </tr>
            <tr class="fileSystemDetailsUrlRow">
                <td><label for="fileSystemUrl"><spring:message code="system.netFileSystem.details.url" /></label></td>
                <td><input id="fileSystemUrl" type="text" style="width: 20em" required /></td>
            </tr>
            <tr class="fileSystemDetailsSambaRow">
                <td><label for="fileSystemSambaDomain">
                    <spring:message code="system.netFileSystem.details.clientSambaDomain" /></label></td>
                <td><input id="fileSystemSambaDomain" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsSambaShareRow">
                <td><label for="fileSystemSambaShare">
                    <spring:message code="system.netFileSystem.details.clientSambaShare" /></label></td>
                <td><input id="fileSystemSambaShare" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsIrodsZoneRow">
               <td><label for="fileSystemIrodsZone">
                   <spring:message code="system.netFileSystem.details.clientIrodsZone" /></label></td>
               <td><input id="fileSystemIrodsZone" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsIrodsHomeDirRow">
               <td><label for="fileSystemIrodsHomeDir">
                   <spring:message code="system.netFileSystem.details.clientIrodsHomeDir" /></label></td>
               <td><input id="fileSystemIrodsHomeDir" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsIrodsPortRow">
                <td><label for="fileSystemIrodsPort">
                    <spring:message code="system.netFileSystem.details.clientIrodsPort" /></label></td>
                <td><input id="fileSystemIrodsPort" type="text" style="width: 20em" /></td>
             </tr>

            <tr class="fileSystemDetailsIrodsCsnegRow">
                <td><label for="fileSystemIrodsCsneg">
                    <spring:message code="system.netFileSystem.details.clientIrodsCsNeg" /></label></td>
                <td><input id="fileSystemIrodsCsneg" type="text" style="width: 20em" /></td>
             </tr>


            <tr class="fileSystemDetailsSftpRow">
                <td><label for="fileSystemSftpServerPublicKey">
                    <spring:message code="system.netFileSystem.details.clientSftpServerPublicKey" /></label></td>
                <td><input id="fileSystemSftpServerPublicKey" type="text" style="width: 20em"/></td>
            </tr>
            <rst:hasDeploymentProperty name="loginDirectoryOption" value="true">
                <tr id="fileSystemDetailsSftpDirChoiceRow">
                    <td><label>
                        <spring:message code="system.netFileSystem.details.clientSftpServerDirChoice"/></label></td>
                    <td><label><input type="radio" id="fileSystemDetailsSftpDirChoiceYes" value="true" name="fileSystemDirChoice" required/><spring:message code="common:actions.yes"/></label>
                        <label><input type="radio" id="fileSystemDetailsSftpDirChoiceNo" value="false" name="fileSystemDirChoice" /><spring:message code="common:actions.no"/></label></td>
                </tr>
            </rst:hasDeploymentProperty>

            <tr class="fileSystemDetailsS3Row">
                <td><label for="fileSystemS3BucketName">
                    <spring:message code="system.netFileSystem.details.clientS3BucketName" /></label></td>
                <td><input id="fileSystemS3BucketName" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsS3Row">
                <td><label for="fileSystemS3Region">
                    <spring:message code="system.netFileSystem.details.clientS3Region" /></label></td>
                <td><input id="fileSystemS3Region" type="text" style="width: 20em" /></td>
            </tr>
            <tr class="fileSystemDetailsS3PathStyleRow">
                <td><label for="fileSystemS3PathStyle">
                    <spring:message code="system.netFileSystem.details.clientS3PathStyleLabel" /></label></td>
                <td>
                    <label for="fileSystemS3PathStyleEnabled">
                        <input type="radio" id="fileSystemS3PathStyleEnabled" name="fileSystemS3PathStyle" value="true">
                        <spring:message code="system:usersPage.columns.enabled" /></label>
                    <label for="fileSystemS3PathStyleDisabled">
                        <input type="radio" id="fileSystemS3PathStyleDisabled" name="fileSystemS3PathStyle" value="false">
                        <spring:message code="system:usersPage.columns.disabled" /></label>
                </td>
            </tr>

            <tr class="fileSystemDetailsS3Row">
                <td colspan="2"><hr /></td>
            </tr>
            <tr class="fileSystemDetailsAuthRow">
                <td><label><spring:message code="system.netFileSystem.details.authLabel" /></label></td>
                <td>
                    <label for="fileSystemAuthTypePassword">
                        <input type="radio" id="fileSystemAuthTypePassword" name="fileSystemAuthType" value="PASSWORD" required>
                        <span id="fileSystemAuthTypePasswordSpan"><spring:message code="system.netFileSystem.details.authPassword" /></span></label>
                    <label for="fileSystemAuthTypePubKey">
                        <input type="radio" id="fileSystemAuthTypePubKey" name="fileSystemAuthType" value="PUBKEY">
                        <spring:message code="system.netFileSystem.details.authPubkeyLabel" /></label>
                    <label for="fileSystemAuthTypeNone">
                        <input type="radio" id="fileSystemAuthTypeNone" name="fileSystemAuthType" value="NONE">
                        <spring:message code="system.netFileSystem.details.authNone" /></label>
                </td>
            </tr>
            <tr class="fileSystemDetailsPubKeyRow">
                <td><label for="fileSystemPubKeyRegistrationUrl">
                    <spring:message code="system.netFileSystem.details.authPubkeyRegistrationDialogUrl" /></label></td>
                <td><input id="fileSystemPubKeyRegistrationUrl" type="text" style="width: 20em" /></td>
            </tr>

            <tr class="fileSystemDetailsIrodsAuthRow">
                <td><label><spring:message code="system.netFileSystem.details.clientIrodsAuthLabel" /></label></td>
                <td>
                    <label><input type="radio" id="iRODSfileSystemAuthTypeNative" name="iRODSfileSystemAuthType" value="NATIVE">
                        <span id="iRODSfileSystemAuthTypeNativeSpan"><spring:message code="system.netFileSystem.details.clientIrodsAuthNative" /></span></label>
                    <label><input type="radio" id="iRODSfileSystemAuthTypePAM" name="iRODSfileSystemAuthType" value="PAM">
                        <span id="iRODSfileSystemAuthTypePAMSpan"><spring:message code="system.netFileSystem.details.clientIrodsAuthPam" /></span></label>
                </td>
            </tr>

            <tr class="fileSystemDetailsAllowlistsRow">
                <td><label><spring:message code="system.netFileSystem.details.allowlistsWriteQuestion" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemLimitWriteNo" name="fileSystemLimitWrite" value="no">
                        <spring:message code="system.netFileSystem.details.allowlistsAnyone" /></label><br/>
                    <label><input type="radio" id="fileSystemLimitWriteYes" name="fileSystemLimitWrite" value="yes">
                        <spring:message code="system.netFileSystem.details.allowlistsOnlyThese" /></label>
                    <input id="fileSystemWriteAllowlist" type="text" style="width: 15em" placeholder="<spring:message code='system.netFileSystem.details.allowlistsWritePlaceholder'/>" /><br/>
                    <label><input type="radio" id="fileSystemLimitWriteNobody" name="fileSystemLimitWrite" value="nobody">
                        <spring:message code="system.netFileSystem.details.allowlistsNobody" /></label>
                </td>
            </tr>
            <tr class="fileSystemDetailsAllowlistsRow fileSystemDetailsReadAllowlistRow">
                <td><label><spring:message code="system.netFileSystem.details.allowlistsReadQuestion" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemLimitReadNo" name="fileSystemLimitRead" value="no">
                        <spring:message code="system.netFileSystem.details.allowlistsAnyone" /></label><br/>
                    <label><input type="radio" id="fileSystemLimitReadYes" name="fileSystemLimitRead" value="yes">
                        <spring:message code="system.netFileSystem.details.allowlistsOnlyThese" /></label>
                    <input id="fileSystemReadAllowlist" type="text" style="width: 15em" placeholder="<spring:message code='system.netFileSystem.details.allowlistsReadPlaceholder'/>" /><br/>
                    <label><input type="radio" id="fileSystemLimitReadNobody" name="fileSystemLimitRead" value="nobody">
                        <spring:message code="system.netFileSystem.details.allowlistsReadNobody" /></label>
                </td>
            </tr>

            <tr class="fileSystemDetailsS3Row">
                <td colspan="2"><hr /></td>
            </tr>
            <tr>
                <td><label><spring:message code="system.netFileSystem.details.statusLabel" /></label></td>
                <td>
                    <label><input type="radio" id="fileSystemStatusEnabled" name="fileSystemStatus" value="true" required>
                            <spring:message code="system:usersPage.columns.enabled" /></label>
                    <label><input type="radio" id="fileSystemStatusDisabled" name="fileSystemStatus" value="false">
                            <spring:message code="system:usersPage.columns.disabled" /></label>
                </td>
            </tr>
        </table>

        <div class="bootstrap-custom-flat">
          <button type="submit" class="btn btn-default">
            <div id="fileSystemCreateButton" class="ui-button-text">
                <spring:message code="common:actions.add" /></div>
            <div id="fileSystemUpdateButton" class="ui-button-text">
                <spring:message code="system.netFileSystem.details.buttonUpdate" /></div>
          </button>
        </div>
    </form>

    <br />
    <hr />
    <br />

</div>

<div class="bootstrap-custom-flat">
  <button id="addNewFileSystem" class="btn btn-default" style="width:15em;">
    <span class="ui-button-text"><spring:message code="system.netFileSystem.button.addNewFilesystem" /></span>
  </button>
</div>
