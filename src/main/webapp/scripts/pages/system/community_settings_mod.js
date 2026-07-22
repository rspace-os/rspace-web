var settings;

var textSettings = [];

function loadSettingsFromServer() {
    $('#mainArea').empty();

    var viewRequest = $.get("/community/admin/ajax/systemSettingsView");
    viewRequest.done(function (resp) {
        $('#mainArea').html(resp);
    });
    viewRequest.fail(function () {
         RS.ajaxFailed(RS.msg("legacyjs.system.communitySettings.gettingSettingsPageAction"), false, viewRequest);
    });

    var propertiesRequest = $.get("/community/admin/ajax/editableProperties", { communityId: RS.communityId });
    propertiesRequest.done(function (resp) {
        settings = resp;
    });
    propertiesRequest.fail(function () {
         RS.ajaxFailed(RS.msg("legacyjs.system.communitySettings.gettingSettingsValuesAction"), false, propertiesRequest);
    });

    $.when(viewRequest, propertiesRequest).then(function() {
        $('#settingsLoadingMsg').hide();
        printSettingsList();
    });
}

function printSettingsList() {

    _printCategory(RS.msg("legacyjs.system.settingsCategory.rspaceSettings"));

    _printSubCategory(RS.msg("legacyjs.system.settingsCategory.rspaceApi"));
    _printSettings([ 'api.available' ]);

    _printSubCategory(RS.msg("legacyjs.system.settingsCategory.labGroupSettings"));
    _printSettings([ 'pi_can_edit_all_work_in_labgroup' ]);
    _printSettings(['group_autosharing.available']);
    _printSettings(['self_service_labgroups']);
    _printSettings(['public_sharing']);
    _printSettings(['publicdocs_allow_seo']);
    _printSettings(['allow_project_groups']);

     _printSubCategory(RS.msg("legacyjs.system.settingsCategory.privacy"));
     _printSettings([ 'publicLastLogin.available' ]);

    _printCategory(RS.msg("legacyjs.system.settingsCategory.storageAndDocumentManagement"));
    _printSettings([
            'box.available',
            'box.linking.enabled',
            'box.api.enabled',
            'dropbox.available',
            'dropbox.linking.enabled',
            'egnyte.available',
            'evernote.available',
            'googledrive.available',
            'onedrive.available',
            'onedrive.linking.enabled',
            'nextcloud.available',
            'owncloud.available',
    ]);

    _printCategory(RS.msg("legacyjs.system.settingsCategory.dataRepositoriesAndPublishing"));
    _printSettings([ 'dataverse.available' ]);
    _printSettings([ 'dryad.available' ]);
    _printSettings([ 'figshare.available' ]);
    _printSettings([ 'zenodo.available' ]);

    _printCategory(RS.msg("legacyjs.system.settingsCategory.dataManagementPlans"));
    _printSettings([ 'argos.available' ]);
    _printSettings([ 'dmponline.available' ]);
    _printSettings([ 'dmptool.available' ]);

    _printCategory(RS.msg("legacyjs.system.settingsCategory.collaborationAndCommunication"));
    _printSettings([ 'msteams.available' ]);
    _printSettings([ 'slack.available' ]);
    _printSettings([ 'github.available' ]);

    _printCategory(RS.msg("legacyjs.system.settingsCategory.scientificToolsAndSpecializedData"));
    _printSettings([ 'chemistry.available' ]);
    _printSettings([ 'clustermarket.available' ]);
    _printSettings([ 'omero.available' ]);
    _printSettings([ 'pyrat.available' ]);
    _printSettings([ 'snapgene.available' ]);

    _printCategory(RS.msg("legacyjs.system.settingsCategory.researchMethodsAndProtocols"));
    _printSettings([ 'protocols_io.available' ]);

    _printCategory(RS.msg("legacyjs.system.settingsCategory.identity"));
    _printSettings([ 'orcid.available' ]);

    addSettingRowsHandlers();
}

function addSettingRowsHandlers() {

    $('.settingRow').each(function() {

        var $thisSettingRow = $(this);

        var $thisSettingName = $thisSettingRow.find('.settingName a');
        RS.addOnClickPopoverToElement($thisSettingName);

        $thisSettingRow.find('.settingViewDiv .settingValue').click(function(e) {
            e.preventDefault();
            editSetting($thisSettingRow);
        });
        $thisSettingRow.find('.settingSaveLink').click(function(e) {
            e.preventDefault();
            saveSetting($thisSettingRow);
        });
        $thisSettingRow.find('.settingCancelLink').click(function(e) {
            e.preventDefault();
            cancelSetting($thisSettingRow);
        });
    });
}

function _get$InputFieldForRow($settingRow) {
    var settingName = $settingRow.data('name');
    var isTextSetting = textSettings.indexOf(settingName) >= 0;
    return isTextSetting ? $settingRow.find('.settingTextValue') : $settingRow.find('.settingBooleanSelectValue');
}

function editSetting($settingRow) {
    var settingName = $settingRow.data('name');
    var settingValue = settings[settingName];
    var $inputToShow = _get$InputFieldForRow($settingRow);
    $inputToShow.val(settingValue).show();
    toggleSettingMode($settingRow);
}

function saveSetting($settingRow) {
    var settingName = $settingRow.data('name');
    var settingLabel = $settingRow.data('label') || settingName;
    var newValue = _get$InputFieldForRow($settingRow).val();

    RS.blockPage(RS.msg("legacyjs.system.common.saving"));

    var data = {
        propertyName : settingName,
        newValue: newValue,
        communityId: RS.communityId
    };
    var jqxhr = $.post("/community/admin/ajax/updateProperty", data);
    jqxhr.done(function(result) {
        if (result.errorMsg) {
            apprise(RS.msg("legacyjs.system.communitySettings.updateFailed", settingLabel));
        } else {
            var safeResult = RS.escapeHtml(result.data);
            $().toastmessage('showSuccessToast', RS.msg("legacyjs.system.communitySettings.updated", settingLabel, safeResult));
            settings[settingName] = result.data;
            $settingRow.find('.settingViewDiv .settingValue').html(safeResult || '&nbsp;');
            toggleSettingMode($settingRow);
        }
    });
    jqxhr.fail(function() {
        RS.ajaxFailed(RS.msg("legacyjs.system.communitySettings.updateAction"), true, jqxhr);
    });
    jqxhr.always(function () {
        RS.unblockPage();
   });
}

function cancelSetting($settingRow) {
    toggleSettingMode($settingRow);
}

function toggleSettingMode($settingRow) {
    $settingRow.children('.settingViewDiv').toggle();
    $settingRow.children('.settingEditDiv').toggle();
}

function _printCategory(categoryName) {
    var categoryRowTemplate = $('#systemSettingsCategoryRowTemplate').html();
    var categoryDiv = Mustache.render(categoryRowTemplate, { category: categoryName })
    $('#systemSettingsList').append(categoryDiv);
}

function _printSubCategory(subCategoryName) {
  var subCategoryRowTemplate = $('#systemSettingsSubCategoryRowTemplate').html();
  var subCategoryDiv = Mustache.render(subCategoryRowTemplate, { subcategory: subCategoryName })
  $('#systemSettingsList').append(subCategoryDiv);
}

function _printSettings(settingsToPrint) {
    var settingRowTemplate = $('#systemSettingRowTemplate').html();

    $.each(settingsToPrint, function(i, name) {
        var values = settings[name].split(','), value; // "community setting value, system admin setting value"
        if (values[0] === 'NOT_SET' || values[1] === 'DENIED')
            value = values[1]; // if community setting is not set or 'DENIED', show system admin setting value
        else
            value = values[0]; // show community setting value if it is available

        var labelOverride = $('#systemSettingsLabels').find('div[id="' + name + '.label"]').text();
        var templateData = {
                "name": name,
                "label": labelOverride || name,
                "value": RS.escapeHtml(value) || '&nbsp;',
                "description": $('#systemSettingsDescriptions').find('div[id="' + name + '.description"]').text(),
                "disabled": values[1] === "DENIED"
        };
        var settingDivHtml = Mustache.render(settingRowTemplate, { setting: templateData });
        $('#systemSettingsList').append(settingDivHtml);
    });
}
