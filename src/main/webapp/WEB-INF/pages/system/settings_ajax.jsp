<%@ include file="/common/taglibs.jsp"%>

<div id="systemSettingsView">

    <div style="margin: 5px;"><spring:message code="system.settings.header.info" /></div>

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
        <div class="settingRow" data-name="{{setting.name}}">
            <div class="settingName bootstrap-custom-flat">
                <a data-content="{{setting.description}}">{{setting.name}}</a>
            </div>
            <div class="settingViewDiv">
                <div class="settingValue">{{{setting.value}}}</div>
            </div>
            <div class="settingEditDiv" style="display:none">
                <div class="settingValue">
                    <select class="settingBooleanSelectValue" style="display:none">
                        <option value="ALLOWED">Allowed</option>
                        <option value="DENIED_BY_DEFAULT">Denied By Default</option>
                        <option value="DENIED">Denied</option>
                    </select>
                    <input class="settingTextValue" type="text" style="display:none"></input>
                </div>
                <div class="settingActions">
                    <a class="settingCancelLink" href="#">Cancel</a>
                    <a class="settingSaveLink" href="#">Save</a>
                </div>
            </div>
        </div>
    </div>

    <div id="systemSettingsDescriptions" style="display:none">
        <div id="dropbox.available.description"><spring:message code="system.property.description.dropbox.available" /></div>
        <div id="dropbox.linking.enabled.description"><spring:message code="system.property.description.dropbox.linking.enabled" /></div>
        <div id="box.available.description"><spring:message code="system.property.description.box.available" /></div>
        <div id="box.linking.enabled.description"><spring:message code="system.property.description.box.linking.enabled" /></div>
        <div id="box.api.enabled.description"><spring:message code="system.property.description.box.api.enabled" /></div>
        <div id="googledrive.available.description"><spring:message code="system.property.description.googledrive.available" /></div>
        <div id="onedrive.available.description"><spring:message code="system.property.description.onedrive.available" /></div>
        <div id="onedrive.linking.enabled.description"><spring:message code="system.property.description.onedrive.linking.enabled" /></div>
        <div id="egnyte.available.description"><spring:message code="system.property.description.egnyte.available" /></div>
        <div id="owncloud.available.description"><spring:message code="system.property.description.owncloud.available" /></div>
        <div id="nextcloud.available.description"><spring:message code="system.property.description.nextcloud.available" /></div>
        <div id="evernote.available.description"><spring:message code="system.property.description.evernote.available" /></div>
        <div id="chemistry.available.description"><spring:message code="system.property.description.chemistry.available" /></div>
        <div id="slack.available.description"><spring:message code="system.property.description.slack.available" /></div>
        <div id="orcid.available.description"><spring:message code="system.property.description.orcid.available" /></div>
        <div id="dataverse.available.description"><spring:message code="system.property.description.repo.available" arguments="Dataverse"/></div>
        <div id="figshare.available.description"><spring:message code="system.property.description.repo.available" arguments="Figshare"/></div>
        <div id="pyrat.available.description"><spring:message code="system.property.description.pyrat.available" /></div>
        <div id="clustermarket.available.description"><spring:message code="system.property.description.clustermarket.available" /></div>
        <div id="omero.available.description"><spring:message code="system.property.description.omero.available" /></div>
        <div id="jove.available.description"><spring:message code="system.property.description.jove.available" /></div>
        <div id="dryad.available.description"><spring:message code="system.property.description.dryad.available" /></div>
        <div id="dmponline.available.description"><spring:message code="system.property.description.dmponline.available" /></div>
        <div id="dmptool.available.description"><spring:message code="system.property.description.dmptool.available" /></div>
        <div id="argos.available.description"><spring:message code="system.property.description.argos.available" /></div>
        <div id="zenodo.available.description"><spring:message code="system.property.description.zenodo.available" /></div>
        <div id="digitalCommonsData.available.description"><spring:message code="system.property.description.digitalCommonsData.available" /></div>
        <div id="fieldmark.available.description"><spring:message code="system.property.description.fieldmark.available" /></div>
        <div id="github.available.description"><spring:message code="system.property.description.github.available" /></div>
        <div id="api.available.description"><spring:message code="system.property.description.api.available" /></div>
        <div id="onboarding.available.description"><spring:message code="system.property.description.onboarding.available" /></div>
        <div id="snapgene.available.description"><spring:message code="system.property.description.snapgene.available" /></div>
        <div id="group_autosharing.available.description"><spring:message code="system.property.description.group_autosharing.available" /></div>
        <div id="self_service_labgroups.available.description"><spring:message code="system.property.description.self_service_labgroups" /></div>
        <div id="publicLastLogin.available.description"><spring:message code="system.property.description.publicLastLogin.available" /></div>
        <div id="inventory.available.description"><spring:message code="system.property.description.inventory.available" /></div>
        <div id="public_sharing.description"><spring:message code="system.property.description.public_sharing" /></div>
        <div id="publicdocs_allow_seo.description"><spring:message code="system.property.description.publicdocs_allow_seo" /></div>
        <div id="allow_project_groups.description"><spring:message code="system.property.description.allow_project_groups" /></div>
    </div>

</div>
