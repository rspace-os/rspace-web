<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="f" uri="http://researchspace.com/functions" %>

<spring:message code="common:help.additionalServerProperties" var="additionalPropertiesHelpSlug"/>
<div id="systemSettingsView">

    <div style="margin: 5px;">
      <spring:message code="system.settings.header.info" />
      <spring:message code="system.settings.additionalPropertiesLink" var="additionalPropertiesLinkText"/>
      <spring:message code="system.settings.additionalPropertiesInfo">
        <spring:argument value='<a href="${f:helpDocsUrl(additionalPropertiesHelpSlug)}" rel="noreferrer">${additionalPropertiesLinkText}</a>'/>
      </spring:message>
    </div>

    <div id="systemSettingsList">
        <div id="settingsLoadingMsg">
            <spring:message code="system.settings.loading" />
        </div>
    </div>

    <div id="systemSettingsCategoryRowTemplate" style="display:none">
        <div class="settingCategoryRow">
           <h2>{{category}}</h2>
        </div>
    </div>
    <div id="systemSettingsSubCategoryRowTemplate" style="display:none">
        <div class="settingSubCategoryRow">
           <h3>{{subcategory}}</h3>
        </div>
    </div>
    <div id="systemSettingRowTemplate" style="display:none">
        <div class="settingRow" data-name="{{setting.name}}" data-label="{{setting.label}}">
            <div class="settingName bootstrap-custom-flat">
                <a data-content="{{setting.description}}">{{setting.label}}</a>
            </div>
            <div class="settingViewDiv">
                <div class="settingValue">{{{setting.value}}}</div>
            </div>
            <div class="settingEditDiv" style="display:none">
                <div class="settingValue">
                    <select class="settingBooleanSelectValue" style="display:none">
                        <option value="ALLOWED"><spring:message code="system.settingValue.allowed"/></option>
                        <option value="DENIED_BY_DEFAULT"><spring:message code="system.settingValue.deniedByDefault"/></option>
                        <option value="DENIED"><spring:message code="system.settingValue.denied"/></option>
                    </select>
                    <input class="settingTextValue" type="text" style="display:none"></input>
                </div>
                <div class="settingActions">
                    <a class="settingCancelLink" href="#"><spring:message code="common:actions.cancel"/></a>
                    <a class="settingSaveLink" href="#"><spring:message code="common:actions.save"/></a>
                </div>
            </div>
        </div>
    </div>

    <div id="systemSettingsDescriptions" style="display:none">
        <div id="dropbox.available.description"><spring:message code="system.property.description.dropboxAvailable" /></div>
        <div id="dropbox.linking.enabled.description"><spring:message code="system.property.description.dropboxLinkingEnabled" /></div>
        <div id="box.available.description"><spring:message code="system.property.description.boxAvailable" /></div>
        <div id="box.linking.enabled.description"><spring:message code="system.property.description.boxLinkingEnabled" /></div>
        <div id="box.api.enabled.description"><spring:message code="system.property.description.boxApiEnabled" /></div>
        <div id="googledrive.available.description"><spring:message code="system.property.description.googleDriveAvailable" /></div>
        <div id="onedrive.available.description"><spring:message code="system.property.description.oneDriveAvailable" /></div>
        <div id="onedrive.linking.enabled.description"><spring:message code="system.property.description.oneDriveLinkingEnabled" /></div>
        <div id="egnyte.available.description"><spring:message code="system.property.description.egnyteAvailable" /></div>
        <div id="owncloud.available.description"><spring:message code="system.property.description.owncloudAvailable" /></div>
        <div id="nextcloud.available.description"><spring:message code="system.property.description.nextcloudAvailable" /></div>
        <div id="evernote.available.description"><spring:message code="system.property.description.evernoteAvailable" /></div>
        <div id="chemistry.available.description"><spring:message code="system.property.description.chemistryAvailable" /></div>
        <div id="slack.available.description"><spring:message code="system.property.description.slackAvailable" /></div>
        <div id="orcid.available.description"><spring:message code="system.property.description.orcidAvailable" /></div>
        <div id="raid.available.description"><spring:message code="system.property.description.raidAvailable" /></div>
        <div id="dataverse.available.description"><spring:message code="system.property.description.repoAvailable" arguments="Dataverse"/></div>
        <div id="figshare.available.description"><spring:message code="system.property.description.repoAvailable" arguments="Figshare"/></div>
        <div id="pyrat.available.description"><spring:message code="system.property.description.pyratAvailable" /></div>
        <div id="clustermarket.available.description"><spring:message code="system.property.description.clustermarketAvailable" /></div>
        <div id="omero.available.description"><spring:message code="system.property.description.omeroAvailable" /></div>
        <div id="dryad.available.description"><spring:message code="system.property.description.dryadAvailable" /></div>
        <div id="dmpassistant.available.description"><spring:message code="system.property.description.dmpAssistantAvailable" /></div>
        <div id="dmponline.available.description"><spring:message code="system.property.description.dmponlineAvailable" /></div>
        <div id="dmptool.available.description"><spring:message code="system.property.description.dmpToolAvailable" /></div>
        <div id="dsw.available.description"><spring:message code="system.property.description.dswAvailable" /></div>
        <div id="argos.available.description"><spring:message code="system.property.description.argosAvailable" /></div>
        <div id="zenodo.available.description"><spring:message code="system.property.description.zenodoAvailable" /></div>
        <div id="digitalCommonsData.available.description"><spring:message code="system.property.description.digitalCommonsDataAvailable" /></div>
        <div id="fieldmark.available.description"><spring:message code="system.property.description.fieldmarkAvailable" /></div>
        <div id="galaxy.available.description"><spring:message code="system.property.description.galaxyAvailable" /></div>
        <div id="github.available.description"><spring:message code="system.property.description.githubAvailable" /></div>
        <div id="api.available.description"><spring:message code="system.property.description.apiAvailable" /></div>
        <div id="api.oauth.authentication.description"><spring:message code="system.property.description.apiOauthAuthentication" /></div>
        <div id="onboarding.available.description"><spring:message code="system.property.description.onboardingAvailable" /></div>
        <div id="snapgene.available.description"><spring:message code="system.property.description.snapgeneAvailable" /></div>
        <div id="group_autosharing.available.description"><spring:message code="system.property.description.groupAutosharingAvailable" /></div>
        <div id="self_service_labgroups.description"><spring:message code="system.property.description.selfServiceLabgroups" /></div>
        <div id="publicLastLogin.available.description"><spring:message code="system.property.description.publicLastLoginAvailable" /></div>
        <div id="inventory.available.description"><spring:message code="system.property.description.inventoryAvailable" /></div>
        <div id="public_sharing.description"><spring:message code="system.property.description.publicSharing" /></div>
        <div id="publicdocs_allow_seo.description"><spring:message code="system.property.description.publicdocsAllowSeo" /></div>
        <div id="allow_project_groups.description"><spring:message code="system.property.description.allowProjectGroups" /></div>
        <div id="pi_can_edit_all_work_in_labgroup.description"><spring:message code="system.property.description.piCanEditAllWorkInLabgroup" /></div>
        <div id="msteams.available.description"><spring:message code="system.property.description.msTeamsAvailable" /></div>
        <div id="protocols_io.available.description"><spring:message code="system.property.description.protocolsIoAvailable" /></div>
    </div>

    <div id="systemSettingsLabels" style="display:none">
        <div id="clustermarket.available.label"><spring:message code="system.property.label.clustermarketAvailable" /></div>
    </div>

</div>
