var sysNetFileSysDetUrl;
var sysNetfileSysDetAuthPasswd;

var fileSystemsArray;

function loadNetFileSystemsList() {
    $('#mainArea').empty();

    var jqxhr = $.get('/system/netfilesystem/ajax/view');
    
    jqxhr.done(function (resp) {
        $('#mainArea').html(resp);
        loadNetFileSystems();
    });
    
    jqxhr.fail(function () {
         RS.ajaxFailed("Getting File Systems page", false, jqxhr);
    });
}

function loadNetFileSystems() {
    
    var jqxhr = $.get('/system/netfilesystem/ajax/list');
    
    jqxhr.done(function(result) {
        fileSystemsArray = result;
        setFileSystemClientTypeLabels();
        showAllFileSystemsTable();
    });
    
    jqxhr.fail(function () {
         RS.ajaxFailed("Couldn't retrieve list of File Systems", false, jqxhr);
    });
}

function setFileSystemClientTypeLabels() {

    $.each(fileSystemsArray, function(i, fs) {
        if (fs.clientType === 'SAMBA') {
            fs.clientTypeLabel = 'SMBv1';
        } else if (fs.clientType === 'SMBJ') {
            fs.clientTypeLabel = 'SMBv2/3';
        } else {
            fs.clientTypeLabel = fs.clientType;
        }
    });
}

function showAllFileSystemsTable() {

    var noFileSystems = fileSystemsArray.length === 0;
    $('#noFileSystemsMessage').toggle(noFileSystems);
    $('#allFileSystems').toggle(!noFileSystems);
    $('#allFileSystemsTableBody').empty();
    
    var fileSystemRowTemplate = $('#fileSystemRowTemplate table tbody').html();
    $.each(fileSystemsArray, function(i, fs) {
        var fileSystemRowHtml = Mustache.render(fileSystemRowTemplate, {fileSystem: fs});
        $('#allFileSystemsTableBody').append(fileSystemRowHtml);
    });
}

function showFileSystemDetails() {
    var id = $(this).data('id');
    var fileSystem = getFileSystemFromArray(id);
    displayFileSystemDetailsDiv(fileSystem);
}

function getFileSystemFromArray(fileSystemId) {
    for (var i = 0; i < fileSystemsArray.length; i++) {
        if (fileSystemsArray[i].id === fileSystemId) {
            return fileSystemsArray[i];
        }
    }
}

function deleteFileSystem() {
    var id = $(this).data('id'),
        name = $(this).data('name');
    
    apprise(
        "<div style='line-height:1.3em'>Delete File System '" + name + "'?</div>",
        {   confirm : true, textOk : 'Delete' },
        function(r) {
            RS.blockPage("Deleting...");
            var jqxhr = $.post("/system/netfilesystem/delete", { fileSystemId : id });
            jqxhr.done(function(result) {
                if (result) {
                    $().toastmessage('showSuccessToast', 'File System deleted');
                } else {
                    apprise('The File System \'' + name + '\' couldn\'t be deleted. <br><br>That could happen if users already created some File Stores for this File System.');
                    RS.focusAppriseDialog();
                }
                
                loadNetFileSystemsList();
            });
            jqxhr.fail(function() {
                RS.ajaxFailed("Delete", true, jqxhr);
            });
            jqxhr.always(function () {
                RS.unblockPage();
           });
        }
    );
    RS.focusAppriseDialog();
}

function addNewFileSystem() {
    displayFileSystemDetailsDiv({});
}
function fileSystemRequiresUserDirs(fileSystem) {
    return fileSystem.clientOptions.includes("USER_DIRS_REQUIRED=true");
}
function displayFileSystemDetailsDiv(fileSystem) {
 
    $('#fileSystemDetails').show();
    var isExistingFileSystem = !!fileSystem.id;
    
    $('#fileSystemDetailsHeader').toggle(isExistingFileSystem);
    $('#fileSystemAddingHeader').toggle(!isExistingFileSystem);
    $('.fileSystemIdRow').toggle(isExistingFileSystem);
    
    $('#fileSystemId').empty();
    if (fileSystem.id) {
        $('#fileSystemId').html(fileSystem.id);
    } 

    var isSambaClient = isExistingFileSystem && fileSystem.clientType === 'SAMBA';
    var isSmbjClient = isExistingFileSystem && fileSystem.clientType === 'SMBJ';
    var isSftpClient = isExistingFileSystem && fileSystem.clientType === 'SFTP';
    var isIrodsClient = isExistingFileSystem && fileSystem.clientType === 'IRODS';
    var isS3AWSClient = isExistingFileSystem && fileSystem.clientType === 'S3' && fileSystem.url && fileSystem.url.startsWith('aws::');
    var isS3OtherClient = isExistingFileSystem && fileSystem.clientType === 'S3' && !isS3AWSClient;

    $('#fileSystemClientTypeSamba').prop('checked', isSambaClient || isSmbjClient);
    $('#fileSystemClientTypeSftp').prop('checked', isSftpClient);
    $('#fileSystemClientTypeIrods').prop('checked', isIrodsClient);
    $('#fileSystemClientTypeS3').prop('checked', isS3AWSClient || isS3OtherClient);
    $('#fileSystemClientTypeS3AWS').prop('checked', isS3AWSClient);
    $('#fileSystemClientTypeS3Other').prop('checked', isS3OtherClient);
    $('#fileSystemClientTypeSambaSmbj').prop('checked', !isSambaClient);
    $('#fileSystemClientTypeSambaJcifs').prop('checked', isSambaClient);
    $('#fileSystemDetailsSftpDirChoiceYes').prop('checked', isSftpClient && fileSystemRequiresUserDirs(fileSystem));
    $('#fileSystemDetailsSftpDirChoiceNo').prop('checked', isSftpClient && !fileSystemRequiresUserDirs(fileSystem));

    refreshClientTypeRows();
	
    $('#fileSystemName').val(fileSystem.name || "");
    $('#fileSystemUrl').val(fileSystem.url || "");
    
    $('#fileSystemSambaDomain').val("");
    $('#fileSystemSambaShare').val("");
    $('#fileSystemSftpServerPublicKey').val("");
    $('#fileSystemS3Region').val("");
    $('#fileSystemS3BucketName').val("");

    var clientOptions = parseClientOptions(fileSystem.clientOptions);
    if (isSambaClient) {
        $('#fileSystemSambaDomain').val(clientOptions.SAMBA_DOMAIN);
    } else if (isSmbjClient) {
        $('#fileSystemSambaDomain').val(clientOptions.SAMBA_DOMAIN);
        $('#fileSystemSambaShare').val(clientOptions.SAMBA_SHARE_NAME);
    } else if (isSftpClient) {
        $('#fileSystemSftpServerPublicKey').val(clientOptions.SFTP_SERVER_PUBLIC_KEY);
    } else if(isIrodsClient) {
        $('#fileSystemIrodsZone').val(clientOptions.IRODS_ZONE);
        $('#fileSystemIrodsHomeDir').val(clientOptions.IRODS_HOME_DIR);
        $('#fileSystemIrodsPort').val(clientOptions.IRODS_PORT);
        $('#fileSystemIrodsCsneg').val(clientOptions.IRODS_CSNEG);
    } else if (isS3AWSClient || isS3OtherClient) {
        $('#fileSystemS3Region').val(clientOptions.S3_REGION);
        $('#fileSystemS3BucketName').val(clientOptions.S3_BUCKET_NAME);
        var s3pathStyleEnabled = clientOptions.S3_PATH_STYLE_ACCESS_ENABLED === 'true';
        $('#fileSystemS3PathStyleEnabled').prop('checked', s3pathStyleEnabled);
        $('#fileSystemS3PathStyleDisabled').prop('checked', !s3pathStyleEnabled);
    }
    
    var isPasswordAuth = isExistingFileSystem && fileSystem.authType === 'PASSWORD';
    var isPubKeyAuth = isExistingFileSystem && fileSystem.authType === 'PUBKEY';
    var isNoneAuth = isExistingFileSystem && fileSystem.authType === 'NONE';
    
    $('#fileSystemAuthTypePassword').prop('checked', isPasswordAuth);
    $('#fileSystemAuthTypePubKey').prop('checked', isPubKeyAuth);
    $('#fileSystemAuthTypeNone').prop('checked', isNoneAuth);
    refreshAuthTypeRows();

    $('#fileSystemPubKeyRegistrationUrl').val("");

    if (fileSystem.clientType === 'IRODS'){
      var rows =  fileSystem.clientOptions.split('\n');
      for (var i = 0; i < rows.length; i++) {
        var currRow = rows[i];
        var currRowValue = currRow.substring(currRow.indexOf('=') + 1);
        if (currRow.indexOf('IRODS_AUTH') === 0) {
          $('#iRODSfileSystemAuthTypeNative').prop('checked', currRowValue === 'NATIVE');
          $('#iRODSfileSystemAuthTypePAM').prop('checked', currRowValue === 'PAM');
        }
      }
    }
	
    if (fileSystem.authOptions) {
        if (isPubKeyAuth) {
            var rows =  fileSystem.authOptions.split('\n');
            for (var i = 0; i < rows.length; i++) {
                var currRow = rows[i];
                var currRowValue = currRow.substring(currRow.indexOf('=') + 1);
                if (currRow.indexOf('PUBLIC_KEY_REGISTRATION_DIALOG_URL') === 0) {
                    $('#fileSystemPubKeyRegistrationUrl').val(currRowValue);
                }
            }
        }
    }

    setWhitelistFields('Read', fileSystem.readWhitelist);
    setWhitelistFields('Write', fileSystem.writeWhitelist);
    refreshWhitelistRows();

    var isEnabled = isExistingFileSystem && !fileSystem.disabled;
    var isDisabled = isExistingFileSystem && fileSystem.disabled;
    $('#fileSystemStatusEnabled').prop('checked', isEnabled);
    $('#fileSystemStatusDisabled').prop('checked', isDisabled);

    $('#fileSystemCreateButton').toggle(!isExistingFileSystem);
    $('#fileSystemUpdateButton').toggle(isExistingFileSystem);
}

function parseClientOptions(clientOptionsString) {
    var clientOptions = {};
    if (clientOptionsString) {
        $.each(clientOptionsString.split('\n'), function(i, option) {
            if (option) {
                var val = option.split('=');
                clientOptions[val[0]] = val[1];
            }
        });
    }
    return clientOptions;
}

function saveFileSystem() {

    var clientType = $('input[name=fileSystemClientType]:checked').val();
    if (clientType === 'SAMBA') {
        clientType = $('input[name=fileSystemClientTypeSamba]:checked').val();
    }

    var authOptions = "";
    var authType = $('input[name=fileSystemAuthType]:checked').val();
    if (authType === 'PUBKEY') {
        authOptions = "PUBLIC_KEY_REGISTRATION_DIALOG_URL=" + $('#fileSystemPubKeyRegistrationUrl').val();
    }

    var clientOptions = "";

    if (clientType === 'SAMBA') {
        clientOptions = "SAMBA_DOMAIN=" + $('#fileSystemSambaDomain').val();
    } else if (clientType === 'SMBJ') {
        clientOptions = "SAMBA_DOMAIN=" + $('#fileSystemSambaDomain').val()
                     + "\nSAMBA_SHARE_NAME=" + $('#fileSystemSambaShare').val();
    } else if (clientType === 'SFTP') {
        clientOptions = "SFTP_SERVER_PUBLIC_KEY=" + $('#fileSystemSftpServerPublicKey').val();
        if ( $( "#fileSystemDetailsSftpDirChoiceYes" ).length ) {
            var dirsRequired = $('input[name=fileSystemDirChoice]:checked').val();
            clientOptions +="\nUSER_DIRS_REQUIRED=" + dirsRequired;
        }
    } else if (clientType === 'IRODS') {
        clientOptions = "IRODS_ZONE=" + $('#fileSystemIrodsZone').val()
                    + "\nIRODS_HOME_DIR=" + $('#fileSystemIrodsHomeDir').val()
                    + "\nIRODS_PORT=" + $('#fileSystemIrodsPort').val()
                    + "\nIRODS_CSNEG=" + $('#fileSystemIrodsCsneg').val()
                    + "\nIRODS_AUTH=" + $('input[name="iRODSfileSystemAuthType"]:checked').val()+"\n";
    } else if (clientType === 'S3') {
        var s3Region = $('#fileSystemS3Region').val();
        var s3BucketName = $('#fileSystemS3BucketName').val();
        var s3PathStyleEnabled = $('#fileSystemS3PathStyleEnabled').prop('checked');
        if ($('#fileSystemClientTypeS3AWS').prop('checked')) {
            $('#fileSystemUrl').val("aws::" + s3Region);
        }
        clientOptions = "S3_REGION=" + s3Region
            + "\nS3_BUCKET_NAME=" + s3BucketName
            + "\nS3_PATH_STYLE_ACCESS_ENABLED=" + s3PathStyleEnabled;
    }

    var readWhitelist = collectWhitelistValue('Read');
    var writeWhitelist = collectWhitelistValue('Write');
    var fileSystem = {
            id: $('#fileSystemId').html(),
            name: $('#fileSystemName').val(),
            url: $('#fileSystemUrl').val(),
            disabled: $('input[name=fileSystemStatus]:checked').val() === "false",
            clientType: clientType,
            authType: authType,
            clientOptions: clientOptions,
            authOptions: authOptions,
            readWhitelist: authType === 'NONE' ? readWhitelist : null,
            writeWhitelist: authType === 'NONE' ? writeWhitelist : null
        };
    RS.blockPage("Saving...");
    var jqxhr = RS.sendJsonPostRequestToUrl('/system/netfilesystem/save', fileSystem);
    jqxhr.done(function(result) {
        $().toastmessage('showSuccessToast', 'File System saved');
        showWhitelistWarnings(result);
        loadNetFileSystemsList();
    });
    jqxhr.fail(function () {
         RS.ajaxFailed("Couldn't save File System", false, jqxhr);
    });
    jqxhr.always(function () {
         RS.unblockPage();
    });
    
    /* so html form submit is not called */
    return false;
}
//we currently only save the userDirsChoice for SFTP clients
//therefore we hide the choice from non SFTP clients but we also
//have to give it a value in the UI else the UI framework throws an error on save
function refreshClientTypeRows() {

    // retrieve default label values from system.properties 
    if (sysNetFileSysDetUrl === undefined) {
        sysNetFileSysDetUrl = $("label[for='fileSystemUrl']").text();
    }
    if (sysNetfileSysDetAuthPasswd === undefined) {
        sysNetfileSysDetAuthPasswd = $(
            '#fileSystemAuthTypePasswordSpan').text();
    }

    const isSambaClient = $('#fileSystemClientTypeSamba').prop('checked');
    const isSambaSmbjClient = isSambaClient && $(
        '#fileSystemClientTypeSambaSmbj').prop('checked');
    const isSftpClient = $('#fileSystemClientTypeSftp').prop('checked');
    const isIrodsClient = $('#fileSystemClientTypeIrods').prop('checked');
    const isS3Client = $('#fileSystemClientTypeS3').prop('checked');
    const isS3AWSClient = isS3Client && $('#fileSystemClientTypeS3AWS').prop('checked');
    const existingFileSystem = $('#fileSystemId').html().length;

    $('.fileSystemDetailsUrlRow').toggle(!isS3AWSClient);

    $('#fileSystemDetailsSftpDirChoiceRow').toggle(isSftpClient);
    if ($("#fileSystemDetailsSftpDirChoiceYes").length) {
        if ((existingFileSystem && !isSftpClient) || !existingFileSystem) {
            $('#fileSystemDetailsSftpDirChoiceYes').prop('checked', true);
        }
    }
    $('.fileSystemDetailsSambaRow').toggle(isSambaClient);
    $('.fileSystemDetailsSambaShareRow').toggle(
        isSambaClient && isSambaSmbjClient);
    $('#fileSystemSambaDomain').prop('required', isSambaClient);
    $('.fileSystemDetailsSftpRow').toggle(isSftpClient);
    $('#fileSystemSftpServerPublicKey').prop('required', isSftpClient);
    $('.fileSystemDetailsIrodsZoneRow').toggle(isIrodsClient);
    $('.fileSystemDetailsIrodsHomeDirRow').toggle(isIrodsClient);
    $('.fileSystemDetailsIrodsPortRow').toggle(isIrodsClient);
    $('.fileSystemDetailsIrodsCsnegRow').toggle(isIrodsClient);
    $('.fileSystemDetailsIrodsAuthRow').toggle(isIrodsClient);

    $('#fileSystemIrodsZone').prop('required', isIrodsClient);
    $('#fileSystemIrodsHomeDir').prop('required', isIrodsClient);
    $('#iRODSfileSystemAuthTypeNative').prop('required', isIrodsClient);
    $('#iRODSfileSystemAuthTypePAM').prop('required', isIrodsClient);

    $("label[for='fileSystemAuthTypePassword']").toggle(!isS3Client);
    $("label[for='fileSystemAuthTypePubKey']").toggle(isSftpClient);
    $("label[for='fileSystemAuthTypeNone']").toggle(isS3Client);

    $('.fileSystemDetailsS3Row').toggle(isS3Client);
    $('#fileSystemS3BucketName').prop('required', isS3Client);
    $('#fileSystemS3Region').prop('required', isS3Client);
    $('.fileSystemDetailsS3PathStyleRow').toggle(isS3Client && !isS3AWSClient);

    if (isS3Client) {
        $('#fileSystemAuthTypeNone').click();
    } else if (isSambaClient || isIrodsClient
            || (isSftpClient && $('#fileSystemAuthTypeNone').prop('checked'))) {
        $('#fileSystemAuthTypePassword').click();
    }
    $('#fileSystemAuthTypePasswordSpan').text(sysNetfileSysDetAuthPasswd);
    $("label[for='fileSystemUrl']").text(sysNetFileSysDetUrl);
    if (isIrodsClient) {
        $("label[for='fileSystemUrl']").text('iRODS Host');
    }

    if (isSambaClient || isSambaSmbjClient) {
        $('#fileSystemUrl')
        .attr('title', 'Samba server URL should start with smb://')
        .attr('pattern', '^smb://.*');
    } else if (isIrodsClient) {
        $('#fileSystemUrl')
        .removeAttr('pattern')
        .attr('title', 'iRODS hostname or IP without protocol');
    } else {
        $('#fileSystemUrl').removeAttr('title').removeAttr('pattern');
    }
    $('#fileSystemUrl').prop('required', !isS3AWSClient);

    // Hide whitelist rows when switching away from S3. The NONE auth radio may still
    // be 'checked' after such a switch (its label is hidden by the block above, but
    // the radio state lingers), so refreshAuthTypeRows wouldn't fire on its own.
    refreshWhitelistRows();
}

function refreshAuthTypeRows() {
    var isPubKeyAuth = $('#fileSystemAuthTypePubKey').prop('checked');

    $('.fileSystemDetailsPubKeyRow').toggle(isPubKeyAuth);
    $('#fileSystemPubKeyRegistrationUrl').prop('required', isPubKeyAuth);
    refreshWhitelistRows();
}

function refreshWhitelistRows() {
    // Whitelists only apply when the NONE auth radio is both selected AND available
    // for the current client type (today that's S3 only). Requiring both guards the
    // transient state right after switching client type, when the NONE radio's check
    // state still lingers from the previous S3 selection.
    // The read question is meaningful when writers are restricted ('only listed users'
    // or 'nobody'); when writers are 'anyone', everyone already has read+write so the
    // read question is moot and its whole row stays hidden.
    // The username input next to each 'Only the following users:' radio shows
    // only when that radio is selected.
    // Visibility and 'required' are kept in sync so a hidden field can never block
    // form save: switching away from S3 clears required on the now-invisible radios.
    var isS3Client = $('#fileSystemClientTypeS3').prop('checked');
    var isNoneAuth = $('#fileSystemAuthTypeNone').prop('checked');
    var showWhitelists = isS3Client && isNoneAuth;
    var writeOnly = showWhitelists && $('#fileSystemLimitWriteYes').prop('checked');
    var writeNobody = showWhitelists && $('#fileSystemLimitWriteNobody').prop('checked');
    var showReadQuestion = writeOnly || writeNobody;

    $('.fileSystemDetailsWhitelistsRow').toggle(showWhitelists);
    $('#fileSystemWriteWhitelist').toggle(writeOnly);
    $('.fileSystemDetailsReadWhitelistRow').toggle(showReadQuestion);
    $('#fileSystemReadWhitelist').toggle(
        showReadQuestion && $('#fileSystemLimitReadYes').prop('checked'));

    $('#fileSystemLimitWriteNo').prop('required', showWhitelists);
    $('#fileSystemLimitReadNo').prop('required', showReadQuestion);
}

// Maps a persisted whitelist value to the radio + input pair:
//   '*' (everyone)              -> "Anyone with an RSpace account"; input cleared
//   'alice,bob' (named list)    -> "Only the following users"; input populated
//   '' or null   (write only)   -> "Nobody (read access only)" radio when suffix==='Write'
//                                  (Read has no Nobody option, so falls through to no
//                                  preselection)
//   undefined    (fresh add)    -> no radio preselected; input cleared
function setWhitelistFields(suffix, value) {
    var isEveryone = value === '*';
    var hasNames = typeof value === 'string' && value !== '' && !isEveryone;
    $('#fileSystemLimit' + suffix + 'No').prop('checked', isEveryone);
    $('#fileSystemLimit' + suffix + 'Yes').prop('checked', hasNames);
    if (suffix === 'Write') {
        // persisted '' or null means nobody is on the write list; undefined means
        // a fresh-add filesystem where no choice has been made yet.
        var isNobody = value === '' || value === null;
        $('#fileSystemLimitWriteNobody').prop('checked', isNobody);
    }
    $('#fileSystem' + suffix + 'Whitelist').val(hasNames ? value : '');
}

// Inverse of setWhitelistFields: derive the value to submit from the current radio + input.
// If write access isn't limited (everyone writes), everyone reads too, so the read value
// is forced to '*' regardless of any orphan state in the hidden read radio/input.
// The radio groups are marked required via JS (see refreshWhitelistRows), so HTML5
// form validation prevents save when no choice is made; the sysadmin is forced to
// pick explicitly rather than relying on a silent default.
function collectWhitelistValue(suffix) {
    if (suffix === 'Read' && $('#fileSystemLimitWriteNo').prop('checked')) {
        return '*';
    }
    var limit = $('input[name=fileSystemLimit' + suffix + ']:checked').val();
    if (limit === 'no') {
        return '*';
    }
    if (limit === 'nobody') {
        return '';
    }
    return $('#fileSystem' + suffix + 'Whitelist').val();
}

function showWhitelistWarnings(result) {
    if (!result) {
        return;
    }
    if (result.unknownReadWhitelistUsernames && result.unknownReadWhitelistUsernames.length) {
        $().toastmessage('showWarningToast',
            'Unknown usernames on read whitelist (saved as typed): '
            + result.unknownReadWhitelistUsernames.join(', '));
    }
    if (result.unknownWriteWhitelistUsernames && result.unknownWriteWhitelistUsernames.length) {
        $().toastmessage('showWarningToast',
            'Unknown usernames on write whitelist (saved as typed): '
            + result.unknownWriteWhitelistUsernames.join(', '));
    }
}

$(document).ready(function() {	
    
    $(document).on('click', '#netFileSystemLink', loadNetFileSystemsList);
    $(document).on('click', '.fileSystemDetailsButton', showFileSystemDetails);
    $(document).on('click', '.fileSystemDeleteButton', deleteFileSystem);
    $(document).on('change', 'input[name="fileSystemClientType"]', refreshClientTypeRows);
    $(document).on('change', 'input[name="fileSystemClientTypeSamba"]', refreshClientTypeRows);
    $(document).on('change', 'input[name="fileSystemClientTypeS3"]', refreshClientTypeRows);
    $(document).on('change', 'input[name="fileSystemAuthType"]', refreshAuthTypeRows);
    $(document).on('change', 'input[name="fileSystemLimitRead"]', refreshWhitelistRows);
    $(document).on('change', 'input[name="fileSystemLimitWrite"]', refreshWhitelistRows);
    $(document).on('click','#addNewFileSystem', addNewFileSystem);
    $(document).on('submit', '#fileSystemDetailsForm', saveFileSystem);
});
