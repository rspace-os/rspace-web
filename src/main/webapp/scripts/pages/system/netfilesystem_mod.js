define(function() {

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

        $('#fileSystemClientTypeSamba').prop('checked', isSambaClient || isSmbjClient);
        $('#fileSystemClientTypeSftp').prop('checked', isSftpClient);
        $('#fileSystemClientTypeIrods').prop('checked', isIrodsClient);
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

        var clientOptions = parseClientOptions(fileSystem.clientOptions);
        // $('#fileSystemDetailsSftpDirChoiceRow').hide();
        if (isSambaClient) {
            $('#fileSystemSambaDomain').val(clientOptions.SAMBA_DOMAIN);
        } else if (isSmbjClient) {
            $('#fileSystemSambaDomain').val(clientOptions.SAMBA_DOMAIN);
            $('#fileSystemSambaShare').val(clientOptions.SAMBA_SHARE_NAME);
        } else if (isSftpClient) {
            // $('#fileSystemDetailsSftpDirChoiceRow').show();
            $('#fileSystemSftpServerPublicKey').val(clientOptions.SFTP_SERVER_PUBLIC_KEY);
        } else if(isIrodsClient) {
            $('#fileSystemIrodsZone').val(clientOptions.IRODS_ZONE);
            $('#fileSystemIrodsHomeDir').val(clientOptions.IRODS_HOME_DIR);
            $('#fileSystemIrodsPort').val(clientOptions.IRODS_PORT);
        }
        
        var isPasswordAuth = isExistingFileSystem && fileSystem.authType === 'PASSWORD';
        var isPubKeyAuth = isExistingFileSystem && fileSystem.authType === 'PUBKEY';
        
        $('#fileSystemAuthTypePassword').prop('checked', isPasswordAuth);
        $('#fileSystemAuthTypePubKey').prop('checked', isPubKeyAuth);
        refreshAuthTypeRows();

        $('#fileSystemPubKeyRegistrationUrl').val("");

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

        var authType = $('input[name=fileSystemAuthType]:checked').val();
        if (authType === 'PUBKEY') {
            authOptions = "PUBLIC_KEY_REGISTRATION_DIALOG_URL=" + $('#fileSystemPubKeyRegistrationUrl').val();
        }

        var clientOptions = "";
        var authOptions = "";

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
            + "\nIRODS_PORT=" + $('#fileSystemIrodsPort').val();
        }

        var fileSystem = {
                id: $('#fileSystemId').html(),
                name: $('#fileSystemName').val(),
                url: $('#fileSystemUrl').val(),
                disabled: $('input[name=fileSystemStatus]:checked').val() === "false",
                clientType: clientType,
                authType: authType,
                clientOptions: clientOptions,
                authOptions: authOptions
            };
        console.log("File System:", fileSystem);
        RS.blockPage("Saving...");
        var jqxhr = RS.sendJsonPostRequestToUrl('/system/netfilesystem/save', fileSystem);
        jqxhr.done(function() {
            $().toastmessage('showSuccessToast', 'File System saved');
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
        const isSambaClient = $('#fileSystemClientTypeSamba').prop('checked');
        const isSambaSmbjClient = isSambaClient && $('#fileSystemClientTypeSambaSmbj').prop('checked');
        const isSftpClient = $('#fileSystemClientTypeSftp').prop('checked');
        const isIrodsClient = $('#fileSystemClientTypeIrods').prop('checked');
        const existingFileSystem = $('#fileSystemId').html().length;
        if(!isSftpClient) {
            $('#fileSystemDetailsSftpDirChoiceRow').hide();
        } else {
            $('#fileSystemDetailsSftpDirChoiceRow').show();
        }
        if($("#fileSystemDetailsSftpDirChoiceYes").length) {
            if ((existingFileSystem && !isSftpClient) || !existingFileSystem) {
                $('#fileSystemDetailsSftpDirChoiceYes').prop('checked', true);
            }
        }
        $('.fileSystemDetailsSambaRow').toggle(isSambaClient);
        $('.fileSystemDetailsSambaShareRow').toggle(isSambaClient && isSambaSmbjClient);
        $('#fileSystemSambaDomain').prop('required', isSambaClient);
        $('.fileSystemDetailsSftpRow').toggle(isSftpClient);
        $('#fileSystemSftpServerPublicKey').prop('required', isSftpClient);
        $('.fileSystemDetailsIrodsZoneRow').toggle(isIrodsClient);
        $('.fileSystemDetailsIrodsHomeDirRow').toggle(isIrodsClient);
        $('.fileSystemDetailsIrodsPortRow').toggle(isIrodsClient);

        $('#fileSystemAuthTypePubKey').prop('disabled', isSambaClient);
        if (isSambaClient) {
            $('#fileSystemAuthTypePassword').click();
        }
        
        if (isSambaClient || isSambaSmbjClient) {
            $('#fileSystemUrl')
                .attr('title', 'Samba server URL should start with smb://')
                .attr('pattern', '^smb://.*');
        } else {
            $('#fileSystemUrl').removeAttr('title').removeAttr('pattern')
        }
    }

    function refreshAuthTypeRows() {
        var isPubKeyAuth = $('#fileSystemAuthTypePubKey').prop('checked');
        
        $('.fileSystemDetailsPubKeyRow').toggle(isPubKeyAuth);
        $('#fileSystemPubKeyRegistrationUrl').prop('required', isPubKeyAuth);
    }
    
    $(document).ready(function() {
        $(document).on('click', '#netFileSystemLink', loadNetFileSystemsList);
        $(document).on('click', '.fileSystemDetailsButton', showFileSystemDetails);
        $(document).on('click', '.fileSystemDeleteButton', deleteFileSystem);
        $(document).on('change', 'input[name="fileSystemClientType"]', refreshClientTypeRows);
        $(document).on('change', 'input[name="fileSystemClientTypeSamba"]', refreshClientTypeRows);
        $(document).on('change', 'input[name="fileSystemAuthType"]', refreshAuthTypeRows);
        $(document).on('click','#addNewFileSystem', addNewFileSystem);
        $(document).on('submit', '#fileSystemDetailsForm', saveFileSystem);
    });
    
});