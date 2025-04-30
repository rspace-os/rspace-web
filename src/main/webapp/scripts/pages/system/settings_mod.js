define(function() {

    var settings;

    function loadSettingsFromServer() {
        $('#mainArea').empty();

        var viewRequest = $.get("/deploymentproperties/ajax/systemSettingsView");
        viewRequest.done(function (resp) {
            $('#mainArea').html(resp);
        });
        viewRequest.fail(function () {
             RS.ajaxFailed("Getting Settings page", false, viewRequest);
        });

        var propertiesRequest = $.get("/deploymentproperties/ajax/editableProperties");
        propertiesRequest.done(function (resp) {
            settings = resp;
        });
        propertiesRequest.fail(function () {
             RS.ajaxFailed("Getting Settings values", false, propertiesRequest);
        });

        $.when(viewRequest, propertiesRequest).then(function() {
            $('#settingsLoadingMsg').hide();
            printSettingsList();
        });
    }

    function printSettingsList() {

        _printCategory('RSpace Settings');

        _printSubCategory('RSpace API');
        _printSettings([ 'api.available' ]);

        _printSubCategory('Lab Group Settings');
        _printSettings([ 'pi_can_edit_all_work_in_labgroup' ]);
        _printSettings(['group_autosharing.available']);
        _printSettings(['self_service_labgroups']);
        _printSettings(['allow_project_groups']);

         _printSubCategory('Privacy');
         _printSettings([ 'publicLastLogin.available' ]);

        _printCategory('Storage and Document Management');
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

        _printCategory('Data Repositories and Publishing');
        _printSettings([ 'public_sharing' ]);
        _printSettings([ 'publicdocs_allow_seo' ]);
        _printSettings([ 'dataverse.available' ]);
        _printSettings([ 'digitalCommonsData.available' ]);
        _printSettings([ 'dryad.available' ]);
        _printSettings([ 'figshare.available' ]);
        _printSettings([ 'zenodo.available' ]);

        _printCategory('Data Management Plans');
        _printSettings([ 'argos.available' ]);
        _printSettings([ 'dmponline.available' ]);
        _printSettings([ 'dmptool.available' ]);

        _printCategory('Collaboration and Communication');
        _printSettings([ 'msteams.available' ]);
        _printSettings([ 'slack.available' ]);
        _printSettings([ 'github.available' ]);

        _printCategory('Scientific Tools and Specialized Data');
        _printSettings([ 'inventory.available' ]);
        _printSettings([ 'chemistry.available' ]);
        _printSettings([ 'clustermarket.available' ]);
        _printSettings([ 'fieldmark.available' ]);
        _printSettings([ 'omero.available' ]);
        _printSettings([ 'pyrat.available' ]);
        _printSettings([ 'snapgene.available' ]);

        _printCategory('Research Methods and Protocols');
        _printSettings([ 'jove.available' ]);
        _printSettings([ 'protocols_io.available' ]);

        _printCategory('Identity');
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
        return $settingRow.find('.settingBooleanSelectValue');
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
        var newValue = _get$InputFieldForRow($settingRow).val();

        RS.blockPage("Saving...");

        var data = {
            propertyName : settingName,
            newValue: newValue
        };
        var jqxhr = $.post("/deploymentproperties/ajax/updateProperty", data);
        jqxhr.done(function(result) {
            if (result.errorMsg) {
                apprise('System Setting \'' + settingName + '\' couldn\'t be updated.');
            } else {
                var safeResult = RS.escapeHtml(result.data);
                $().toastmessage('showSuccessToast', 'System Setting \'' + settingName + '\' updated to \'' + safeResult + '\'');
                settings[settingName] = result.data;
                $settingRow.find('.settingViewDiv .settingValue').html(safeResult || '&nbsp;');
                toggleSettingMode($settingRow);
            }
        });
        jqxhr.fail(function() {
            RS.ajaxFailed("Update", true, jqxhr);
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
            var templateData = {
                    "name": name,
                    "value": RS.escapeHtml(settings[name]) || '&nbsp;',
                    "description": $('#systemSettingsDescriptions').find('div[id="' + name + '.description"]').text()
            };
            var settingDivHtml = Mustache.render(settingRowTemplate, { setting: templateData });
            $('#systemSettingsList').append(settingDivHtml);
        });
    }

    $(document).ready(function() {
        $(document).on('click', '#systemSettingsLink', loadSettingsFromServer);
    });

});
