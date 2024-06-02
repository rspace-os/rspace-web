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

        _printCategory('3rd party File Stores');
        _printSettings([
                'dropbox.available',
                'dropbox.linking.enabled',
                'box.available',
                'box.linking.enabled',
                'box.api.enabled',
                'onedrive.available',
                'onedrive.linking.enabled',
                'googledrive.available',
                'egnyte.available',
                'owncloud.available',
                'nextcloud.available',
                'evernote.available'
        ]);

        _printCategory('Publications');
        _printSettings([ 'mendeley.available' ]);

        _printCategory('Protocols');
        _printSettings([ 'protocols_io.available' ]);
        _printSettings([ 'jove.available' ]);

        _printCategory('Chemistry');
        _printSettings([ 'chemistry.available' ]);

        _printCategory('Molecular Biology');
        _printSettings([ 'snapgene.available' ]);

        _printCategory('Inventory');
        _printSettings([ 'inventory.available' ]);

        _printCategory('Communication');
        _printSettings([ 'msteams.available' ]);
        _printSettings([ 'slack.available' ]);
        _printSettings([ 'orcid.available' ]);

        _printCategory('Data Management Plans');
        _printSettings([ 'dmponline.available' ]);
        _printSettings([ 'dmptool.available' ]);
        _printSettings([ 'argos.available' ]);

        _printCategory('Repositories');
        _printSettings([ 'dataverse.available' ]);
        _printSettings([ 'github.available' ]);
        _printSettings([ 'figshare.available' ]);
        _printSettings([ 'pyrat.available' ]);
        _printSettings([ 'clustermarket.available' ]);
        _printSettings([ 'omero.available' ]);
        _printSettings([ 'dryad.available' ]);
        _printSettings([ 'zenodo.available' ]);

        _printCategory('API');
        _printSettings([ 'api.available' ]);

        _printCategory('Onboarding');
        _printSettings([ 'onboarding.available' ]);

        _printCategory('Lab Group Settings');
        _printSettings([ 'pi_can_edit_all_work_in_labgroup' ]);
        _printSettings(['group_autosharing.available']);
        _printSettings(['self_service_labgroups']);
        _printSettings(['public_sharing']);
        _printSettings(['publicdocs_allow_seo']);
        _printSettings(['allow_project_groups']);

         _printCategory('Privacy');
         _printSettings([ 'publicLastLogin.available' ]);

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
