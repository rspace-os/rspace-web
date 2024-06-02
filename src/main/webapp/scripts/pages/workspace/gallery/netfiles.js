/* globals USER_FILESTORES_JSON_STRING, FILESYSTEMS_JSON_STRING, USER_PUBLIC_KEY:true */

/**
 * JS code for network file stores section of the gallery, with 
 * 'login to filesystem' dialog that may be displayed also on 
 * document/notebook page and 'export with nfs' dialog.
 * 
 * 
 * There are multiple workflows available on a page, so we keep a track 
 * of what to show and what to hide by defining a set of states.
 */
var nfsPageStates = {
    INTRO: "intro",
    BROWSING: "browsing",
    ADDING: "adding",
    SYSTEM_SELECTION: "file_system_selection",
    INIT_ERROR: "initialization_error"
};
var nfsCurrentPageState = nfsPageStates.INTRO;

var userFileStores;
var fileSystems;

var selectedFileSystem = null;
var selectedFileStore = null;

/**
 * method is called from mediaGalleryManager after script and jsp are loaded successfully 
 */ 
function initNetFilesGalleryPage() {

    _initUserFileStoresAndFileSystems();

    $("#addUserFileStoreBtn").on("click", function() {
        _addUserFileStore();
        return false;
    });

    $("#deleteUserFileStoreBtn").on("click", function() {
        _removeUserFileStore();
        return false;
    });

    $("#otherFileSystemsBtn").on("click", function() {
        $("#otherFileSystemsBtn").hide();
        $("#otherFileSystemsList").show();
        return false;
    });
    
    $(document).on("click", "#nfsOpenParentDir", function() {
    	var currentDir = $('#nfsCurrentDirSpan').text();
    	var parentDirPath = _getParentDirForPath(currentDir);
    	_loadNfsFileTree(parentDirPath);
    });
    
    $(document).on("click", "#nfsOpenHomeDir", function() {
    	_loadNfsFileTree();
    });
    
    $('#nfsLogout').on('click', _logoutFromNFS);

    _togglePanelsForCurrentPageState();
}

function initNetFilesLoginDialog() {
    _initUserFileStoresAndFileSystems();
}

function _initUserFileStoresAndFileSystems() {
    try {
        userFileStores = JSON.parse(RS.unescape(USER_FILESTORES_JSON_STRING));
        fileSystems = JSON.parse(RS.unescape(FILESYSTEMS_JSON_STRING));
    } catch (error) {
        nfsCurrentPageState = nfsPageStates.INIT_ERROR;
        console.error('error on parsing Filestores/Filesystem json string: ', error);
        console.info("USER_FILESTORES_JSON_STRING: " + RS.unescape(USER_FILESTORES_JSON_STRING));
        console.info("FILESYSTEMS_JSON_STRING: " + RS.unescape(FILESYSTEMS_JSON_STRING));
    }
}

function _getParentDirForPath(currentDir) {
	/* code is assuming unix paths, as currently only SFTP connector supports parent folders navigation */
	var parentDirPath = "";
	var lastSeparatorIndex = currentDir.lastIndexOf('/');
	if (lastSeparatorIndex > 0) {
		parentDirPath = currentDir.substring(0, lastSeparatorIndex);
	} else if (lastSeparatorIndex === 0) {
		parentDirPath = '/';
	}
	return parentDirPath;
}

function _changePageState(newState) {
    nfsCurrentPageState = newState;
    _togglePanelsForCurrentPageState();
}

function _togglePanelsForCurrentPageState() {


    var fileSystemsConfigured = fileSystems && fileSystems.length > 0;
    if (!fileSystemsConfigured) {
        // initialization error state
        var initErrorState = nfsCurrentPageState = nfsPageStates.INIT_ERROR;
        $('#nfsInitErrorHeader').toggle(initErrorState);

        if (!initErrorState) {
            $('#nfsIntroHeader').show();
            $('#noFileSystemsMsg').show();
            $('#nfsFileStoresPanel').hide();
        }
        return;
    }
    
    var introState = nfsCurrentPageState === nfsPageStates.INTRO;
    var browsingState = nfsCurrentPageState === nfsPageStates.BROWSING;
    var addingState = nfsCurrentPageState === nfsPageStates.ADDING;
    var systemSelectionState = nfsCurrentPageState === nfsPageStates.SYSTEM_SELECTION;
    
    var fileStoresConfigured = userFileStores && userFileStores.length > 0;
    
    _reloadFileStoresPanel();
    
    // intro state
    $('#nfsIntroHeader').toggle(introState);
    $('#fileStoreIntro').toggle(introState && fileStoresConfigured);
    $('#fileStoreIntroNoFilestores').toggle(introState && !fileStoresConfigured);
    
    // browsing state
    $("#fileStoreBrowsingHeader").toggle(browsingState);
    $('#deleteUserFileStoreBtn').prop('disabled', !browsingState);

    $('.userFileStoreRowSelected').removeClass('userFileStoreRowSelected');
    if (browsingState) {
        $('.userFileStoreLink')
            .filter(function() { return $(this).data('filestoreid') == selectedFileStore.id;})
            .closest('tr').addClass('userFileStoreRowSelected');
        $("#activeFileTreeTitle").text('Filestore: ' + selectedFileStore.name);
    }
    
    // browsing/adding state
    $("#nfsFileTreePanel").toggle(browsingState || addingState);

    // adding state
    $("#fileStoreAddHeader").toggle(addingState);

    // systemSelectionState
    $('#fileSystemSelectHeader').toggle(systemSelectionState);
    $('#fileSystemSelectPanel').toggle(systemSelectionState);
    if (systemSelectionState) {
        _loadFileSystemsPanel();
    }
    
    _hideLoginPanel();
    _toggleLoggedUserPanel(false);
}

function _reloadFileStoresPanel() {
    
    $('#fileStoreList .userFileStoreRow').remove();
    
    var template = $('#fileStoreRowTemplate').html();
    $.each(userFileStores, function (i, store) {
        var data = {
            fileStoreId: store.id,
            fileStoreName: store.name
        };
        var newrow = Mustache.render(template, data);
        $('#fileStoreList > tbody:last').append(newrow);
    });
    
    $('.userFileStoreLink').on("click", function() {
        _openFileStore(getFileStoreById($(this).data('filestoreid')));
        return false;
    });
}

function _connectToFileSystemAndLoadFileTreeAtRoot(fileSystem) {
    
    console.log('connecting to file system: ' + fileSystem.name);
    
    selectedFileStore = null;
    selectedFileSystem = fileSystem;

    _changePageState(nfsPageStates.ADDING);
    $('#nfsFileTreePanel').empty();
    
    RS.blockPage("Connecting...");
    var jqxhr = $.post('/netFiles/ajax/tryConnectToFileSystemRoot', { fileSystemId: fileSystem.id, targetDir:_getUserTargetDir(fileSystem) });
    
    jqxhr.done(function(result) {
        if(_problemWithConnection(result)){
            _showNfsActionFailureMsg('Show Filestore', 'please try to log out and log in again to the filesystem');
            _toggleLoggedUserPanel(true);
        }
        _processConnectionTestResult(result, fileSystem);
        RS.unblockPage();
    });
    jqxhr.fail(function() {
        RS.unblockPage();
        _showNfsActionFailureMsg('Connect to Filestores', jqxhr.responseText);
    });
}

function _openFileStore(fileStore) {
    
    console.log('connecting to filestore: ' + fileStore.name);

    selectedFileStore = fileStore;
    selectedFileSystem = null;
    
    _changePageState(nfsPageStates.BROWSING);
    $('#nfsFileTreePanel').empty();
    
    RS.blockPage("Connecting...");
    var jqxhr = $.get('/netFiles/ajax/tryConnectToFileStore', { fileStoreId : fileStore.id });
    
    jqxhr.done(function(result) {
        if(_problemWithConnection(result)){
            _showNfsActionFailureMsg('Show Filestore', 'please try to log out and log in again to the filesystem');
            _toggleLoggedUserPanel(true);
        }
        var fileSystem = getFileSystemById(fileStore.fileSystem.id);
        if (fileSystem) {
            _processConnectionTestResult(result, fileSystem, fileStore.path);
        } else {
            _showNfsActionFailureMsg('Loading connected File System', 'id: ' + fileStore.fileSystem.id);
        }
    });
    // API returns 200 response when attempt to connect to a fileshare fails, so reaching this code indicates a network error between UI and backend
    jqxhr.fail(function() {
        _showNfsActionFailureMsg('Show Filestore', jqxhr.responseText);
        _toggleLoggedUserPanel(true);
    });
    jqxhr.always(function() {
        RS.unblockPage();
    });
}
//attempt to connect to a fileshare returns 200 response when it fails
function _problemWithConnection(result) {
    return result.includes("Connection problem");
}
function _isLoggedIn(result){
    return result && (result.indexOf("logged.as.") === 0);
}
function _processConnectionTestResult(result, fileSystem, nfsuserdir) {
    var loggedIn = false;
    if (_isLoggedIn(result)) {
        _setLoggedUsernameFromResult(result, fileSystem);
        loggedIn = true;
        if (_isNfsGalleryView()) {
            _loadNfsFileTree(nfsuserdir?nfsuserdir:_getUserTargetDir(fileSystem));
        }
    } else if (result === "need.log.in") {
        const unixPath = nfsuserdir ? nfsuserdir.includes("/") : false;
        const windowsPath = nfsuserdir ? nfsuserdir.includes("\\") : false;
        _showLoginPanel(fileSystem, unixPath ? nfsuserdir.split("/")[0] : windowsPath ? nfsuserdir.split("\\")[0] : nfsuserdir);
    }
    return loggedIn;
}

function _isNfsGalleryView() {
    return typeof nfsGalleryView == 'boolean' && nfsGalleryView;
}

function _setLoggedUsernameFromResult(result, fileSystem) {
    fileSystem.loggedUsername = result.substring("logged.as.".length);
}

function getFileStoreById(fileStoreId) {
    return _getStoreOrSystemById(fileStoreId, userFileStores);
}

function getFileSystemById(fileSystemId) {
    return _getStoreOrSystemById(fileSystemId, fileSystems);
}

function _getStoreOrSystemById(id, array) {
    var result;
    $.each(array, function(i, storeOrSystem) {
        if (storeOrSystem.id == id) {
            result = storeOrSystem;
            return false;
        }
    });
    return result;
}

var loadingTopLevelFileTree = false;

function _loadNfsFileTree(targetFolder) {
	
	var rootLocation = targetFolder || "";
    var fileTreeOptions = {
            root: rootLocation,
            script: '/netFiles/ajax/nfsFileTree',
            expandSpeed: 25, 
            collapseSpeed: 25,
            multiFolder: true,
            order: _getNfsOrder()
        };

    if (selectedFileStore) { 
        fileTreeOptions.fileStoreId = selectedFileStore.id;
    } else  if (selectedFileSystem) { 
        fileTreeOptions.fileSystemId = selectedFileSystem.id;
    }
    
    loadingTopLevelFileTree = true;
    $('#nfsFileTreePanel').fileTree(fileTreeOptions, function() {});
    
    if (selectedFileSystem) {
        $('#fileStoreAddHeader').show();
        $("#nfsFileTreePanel").on("click", ".save_userfolder", function() {
            $(this).prop('checked', true);
            var nfsPath = $(this).data('abspath');
            _openSaveUserFileStoreDialog(nfsPath);
            return false;
        });
    } else {
        $("#nfsFileTreePanel").off("click", ".save_userfolder");
    }
    _toggleLoggedUserPanel(true);
}

function _toggleLoggedUserPanel(showPanel) {
    if (!showPanel) {
        $('#nfsLoggedUserPanel').css('visibility', 'hidden');
        return;
    }
    
    $('#nfsLoggedUserPanel').css('visibility', 'visible');
    var fileSystem = selectedFileSystem;
    if (!fileSystem) {
        fileSystem = getFileSystemById(selectedFileStore.fileSystem.id);
    }
    $('#nfsLoggedUserMsg').html('Logged into ' + fileSystem.name + ' as: ');
    $('#nfsLoggedUsername').html(fileSystem.loggedUsername);
}

function _getNfsOrder() {
    var orderBy = $('#galleryOrderBy').val();
    if (orderBy === "dateGallery") {
        return "bydate"; 
    }
    return "byname";
}

function _showNfsActionFailureMsg(action, failureDetails) {
    apprise(action + " action failed ("+ failureDetails +"), if that does not work contact your System Admin");
}

/* 
 * ==========================================
 * file store managing methods below
 * ==========================================
 */

function _addUserFileStore() {
    
    if (fileSystems.length === 1) {
        _connectToFileSystemAndLoadFileTreeAtRoot(fileSystems[0]);
    } else {
        console.log('more than one file system, user should select which to use first');
        _changePageState(nfsPageStates.SYSTEM_SELECTION);
    }
}

function _loadFileSystemsPanel() {
    $('#currentlyUsedFileSystemsList .fileSystemRow').remove();
    $('#otherFileSystemsList .fileSystemRow').remove();

    var showCurrentlyUsedSystems = false;
    var showOtherSystems = false;
    
    var template = $('#fileSystemRowTemplate').html();
    $.each(fileSystems, function (i, system) {
        var data = {
            fileSystemId: system.id,
            fileSystemName: system.name
        };
        var newrow = Mustache.render(template, data);
        if (_isFileSystemUsedBySomeFileStore(system.id)) {
            $('#currentlyUsedFileSystemsList > tbody:last').append(newrow);
            showCurrentlyUsedSystems = true;
        } else {
            $('#otherFileSystemsList > tbody:last').append(newrow);
            showOtherSystems = true;
        }
    });
    
    $('#currentlyUsedFileSystemsList').toggle(showCurrentlyUsedSystems);
    $('#otherFileSystemsBtn').toggle(showCurrentlyUsedSystems && showOtherSystems);
    $('#otherFileSystemsList').toggle(!showCurrentlyUsedSystems && showOtherSystems);
    
    $('.fileSystemLink').on("click", function() {
        _connectToFileSystemAndLoadFileTreeAtRoot(getFileSystemById($(this).data('filesystemid')));
        return false;
    });
}

function _isFileSystemUsedBySomeFileStore(fileSystemId) {
    var isUsed = false;
    $.each(userFileStores, function (i, store) {
        if (store.fileSystem.id === fileSystemId) {
            isUsed = true;
            return false;
        }
    });
    return isUsed;
}

function _openSaveUserFileStoreDialog(nfsPath) {
    $(document).ready(function() {
        RS.switchToBootstrapButton();
        $('#nfsSaveFileStoreDialog').dialog({
        	modal : true,
            autoOpen: false,
            width: 400,
            height: 220,
            buttons: {
                "Cancel": function() {
                    $(this).dialog("close");
                    return false;
                },
                "Save": _saveUserFileStore
            }
        });
        RS.switchToJQueryUIButton();
    });
    
	$('#nfsNewFolderPath').html(nfsPath);
	$('#nfsSaveCurrentDirLabel').toggle(nfsPath === '');
    
    $('#nfsNewFolderName').val('');
    $('#nfsSaveFileStoreDialog').dialog('open');
}

function _saveUserFileStore() {

    var name = $('#nfsNewFolderName').val();
    var path = $('#nfsNewFolderPath').text();

    var saveParams = {
        fileSystemId : selectedFileSystem.id,
        nfsname : name,
        nfspath : path
    };
    var jqxhr = $.post('/netFiles/ajax/saveFileStore', saveParams);
    jqxhr.done(function (result) {
        if (result.data) {
            var newFileStore = $.parseJSON(result.data);
            userFileStores.push(newFileStore);
            _openFileStore(newFileStore);
            $('#nfsSaveFileStoreDialog').dialog("close");
        } else {
            $('#nfsSaveStoreError').html(result.errorMsg.errorMessages);
        }
    });
    jqxhr.fail(function () {
        _showNfsActionFailureMsg('Save Filestore', jqxhr.responseText);
    });
}

function _removeUserFileStore() {
    var params = {
        fileStoreId : selectedFileStore.id 
    };
    
    var jqxhr = $.post('/netFiles/ajax/deleteFileStore', params);
    jqxhr.done(function (result) {
        if (result === "ok") {
            userFileStores = $.grep(userFileStores, function(store) {
                return store.id !== selectedFileStore.id;
            });
            _changePageState(nfsPageStates.INTRO);
        }
    });
    jqxhr.fail(function () {
        _showNfsActionFailureMsg('Delete Filestore', jqxhr.responseText);
    });
}

/* 
 * ==========================================
 * login methods below
 * ==========================================
 */

function _showLoginPanel(fileSystem, targetDir) {
    console.log('logging to ' + fileSystem.name + ' with authType: ' + fileSystem.authType);
    $('#fileStoreAddHeader').hide();
    if (fileSystem.options.USER_DIRS_REQUIRED === "true") {
        $('#userDirectoriesRequired').show();
    } else {
        $('#userDirectoriesRequired').hide();
    }
    if (fileSystem.authType === "PASSWORD") {
        _showUsernamePasswordDialog(fileSystem, targetDir);
    } else if (fileSystem.authType === "PUBKEY") {
        _showPublicKeyPanels(fileSystem);
    }
}
function _showUsernamePasswordDialog(fileSystem, targetDir, afterLoginCallback) {
    $('.nfsFileSystemName').html(fileSystem.name);
    $('.nfsError').html('');

    $('#nfsUsername').val('');
    $('#nfsPassword').val('');
    $('#nfsUserDir').val(targetDir);

    $(document).ready(function() {
        RS.switchToBootstrapButton();
        $("#nfsUserPasswordLoginPanel").dialog({
            resizable: false,
            autoOpen:false,
            width: 400,
            height: 300, // height reset on 'ok' action
            modal: true,
            zIndex: 3002,
            buttons: {
                Cancel: function() {
                    $(this).dialog('close');
                    _changePageState(nfsPageStates.INTRO);
                },
                "OK": _onUsernamePasswordDialogConfirm
            }
        });
        $('#nfsUserPasswordLoginPanel').dialog('open');
        RS.switchToJQueryUIButton();
    });

    function _onUsernamePasswordDialogConfirm() {
        _loginToNFS(fileSystem, afterLoginCallback);
        $("#nfsUserPasswordLoginPanel").dialog("option", "height", 300); // reset to original height
    }

    $('#nfsPassword').unbind();
    $('#nfsPassword').keypress(function (e) {
        if (e.which == 13) {
            _onUsernamePasswordDialogConfirm();
            return false;
        }
    });
}
function showUsernamePasswordDialog(fileSystem, afterLoginCallback) {
    _showUsernamePasswordDialog(fileSystem, null, afterLoginCallback)

}

function _hideLoginPanel() {
    
    if ($('#nfsUserPasswordLoginPanel').dialog('instance')) {
        $('#nfsUserPasswordLoginPanel').dialog('close');
    }
    $('#nfsPublicKeyInfoPanel').hide();
}

function _loginToNFS(fileSystem, afterLoginCallback) {
    
    console.log('logging into ' + fileSystem.name);

    var nfsparams = {
        fileSystemId: fileSystem.id,
        nfsusername: $('#nfsUsername').val(),
        nfspassword: $('#nfsPassword').val(),
        nfsuserdir: $('#nfsUserDir').val()
    };
    $('.nfsError').html('');
    RS.blockPage("Connecting...");
    var jqxhr = $.post('/netFiles/ajax/nfsLogin', nfsparams);

    jqxhr.done(function(result) {
        if (_isLoggedIn(result)) {
            if(nfsparams.nfsuserdir !== undefined) {
                _saveUserTargetDir(fileSystem, nfsparams.nfsuserdir);
            }
        }
        const loggedIn = _processConnectionTestResult(result,fileSystem,selectedFileStore ? selectedFileStore.path:nfsparams.nfsuserdir);
        
        if (loggedIn) {
            _hideLoginPanel();
            if (typeof afterLoginCallback == "function") {
                afterLoginCallback(nfsparams.nfsusername);
            }
        } else {
            var authDialog = $("#nfsUserPasswordLoginPanel");
            if (authDialog.dialog('isOpen')) {
                var currHeight = authDialog.dialog("option", "height");
                authDialog.dialog("option", "height", currHeight + 30);
                $('#nfsPassword').val('');
            }
            $('.nfsError').html(result);
        }
    });
    jqxhr.fail(function() { _showNfsActionFailureMsg('Login', jqxhr.responseText); });
    jqxhr.always(function() { RS.unblockPage(); });
}

function _saveUserTargetDir(fileSystem, targetDir) {
    fileSystem.options.targetDir = targetDir;
    window.sessionStorage.setItem(fileSystem.name + fileSystem.id, targetDir);
}

function _getUserTargetDir(fileSystem) {
    const result = window.sessionStorage.getItem(fileSystem.name + fileSystem.id);
    if(result) {
        return result;
    }
    return "";
}

function _logoutFromNFS() {

    RS.blockPage("Logging out...");
    
    var fileSystemId = null;
    if (selectedFileSystem) {
        fileSystemId = selectedFileSystem.id;
    } else {
        fileSystemId = selectedFileStore.fileSystem.id;
    }
    
    var jqxhr = $.post('/netFiles/ajax/nfsLogout', {fileSystemId: fileSystemId});

    jqxhr.done(function(results) { 
        if (results === "ok") {
            selectedFileStore = null;
            _changePageState(nfsPageStates.INTRO);
        } else {
            _showNfsActionFailureMsg('Logout', "action unsuccessful");
        }
        RS.unblockPage();
    });
    jqxhr.fail(function() {
        RS.unblockPage();
        _showNfsActionFailureMsg('Logout', jqxhr.responseText);
    });
}

/* 
 * ==========================================
 * public key authentication methods below
 * ==========================================
 */

var _publicKeyHandlersInitialised;

function _showPublicKeyPanels(fileSystem) {

    $('.nfsFileSystemName').html(fileSystem.name);
    $('.nfsError').html('');

    if (!_publicKeyHandlersInitialised) {
        _publicKeyHandlersInitialised = true;
    
        $(document).on('click', '#nfsGenerateKey', _generateKeyForNFS);
        $(document).on('click', '#showPublicKey', function() { _showPublicKeyDetails(false); });
        $(document).on('click', '#showPublicKeyInstructions', _showPublicKeyInstructions);
        $(document).on('click', '#dataStoreRegisterButton', function() { _showDataStoreRegisterDialog(fileSystem); });
        
        _loadCopyToClipboardButton();
    }
    
    var publicKeyGenerated = !!USER_PUBLIC_KEY;
    $('#publicKeyLoginIntro').toggle(publicKeyGenerated);
    $('#publicKeyDetailsLink').toggle(publicKeyGenerated);
    
    if (publicKeyGenerated) {
        $('#nfsPubKeyLogin').unbind();
        $('#nfsPubKeyLogin').click(function () {
            _loginToNFS(fileSystem);
            return false;
        });
    }
    
    $('#publicKeyGenerateIntro').toggle(!publicKeyGenerated);
    $('#publicKeyGenerateButton').toggle(!publicKeyGenerated);
    
    $('#publicKeyDetailsIntro, #publicKeyDetails').hide();
    $('#publicKeyRegisterDetailsLink, #publicKeyRegisterDetailsLong').hide();
    
    $('#nfsPublicKeyInfoPanel').show();
}

function _generateKeyForNFS() {
    RS.blockPage("Connecting...");
    var jqxhr = $.post('/netFiles/ajax/nfsRegisterKey');
    jqxhr.done(function(result) { 
        USER_PUBLIC_KEY = result;

        $('#publicKeyString').html(USER_PUBLIC_KEY);
        $('#publicKeyGenerateIntro, #publicKeyGenerateButton').hide();
        _showPublicKeyDetails(true);
        _showPublicKeyInstructions();
    });
    jqxhr.fail(function() {
        _showNfsActionFailureMsg('Generate key', jqxhr.responseText);
    });
    jqxhr.always(function() { RS.unblockPage(); });
}

function _showDataStoreRegisterDialog(fileSystem) {
    
    RS.blockPage("Connecting...");
    var jqxhr = $.get('/netFiles/ajax/nfsPublicKeyRegistrationUrl', { fileSystemId : fileSystem.id });
    
    jqxhr.done(function(result) { 
        
        var iframeWidth = Math.min(1200, window.innerWidth - 100),
        iframeHeight = Math.min(650, window.innerHeight - 110); 

        $('#dataStoreIframe').css('width', iframeWidth);
        $('#dataStoreIframe').css('height', iframeHeight);
        
        $('#dataStoreIframe').attr('src', result);

        var dialogOptions = {
                title: "Register RSpace public key",
                width: iframeWidth,
                height: iframeHeight + 100,
                buttons: [{
                    text: 'Close',
                    click: function() {
                        $(this).dialog('close');
                        $('#publicKeyLoginIntro').hide();
                        $('#publicKeyDetails').hide();
                        $('#publicKeyRegisterDetailsLong').hide();
                      }
                }]
        };
        
        RS.switchToBootstrapButton();
        $('#dataStoreRegistrationDiv').dialog(dialogOptions);
        RS.switchToJQueryUIButton();
    });
    jqxhr.fail(function() {
        _showNfsActionFailureMsg('Retrieving URL for registration dialog', jqxhr.responseText);
    });
    jqxhr.always(function() { RS.unblockPage(); });
}

function _showPublicKeyDetails(afterGenerating) {
    $('#publicKeyDetailsLink').hide();
    $('#publicKeyString').html(USER_PUBLIC_KEY);
    
    $('#publicKeyAfterGenerateInstructions').toggle(afterGenerating);
    $('#publicKeyDetailsIntro').toggle(!afterGenerating);
    $('#publicKeyDetails').show();

    $('#publicKeyRegisterDetailsLink').show();
    _showCopyToClipboardIfAvailable();
}

function _showPublicKeyInstructions() {
    $('#publicKeyRegisterDetailsLink').hide();
    $('#publicKeyRegisterDetailsLong').show();
    $('#publicKeyAfterRegistrationMsg').show();
}

/* 
 * ==========================================
 * copy to clipboard methods below
 * ==========================================
 */

var _copyToClipboardAvailable;
var _copyToClipboardActiveClient;

function _loadCopyToClipboardButton() {
    
    if (_copyToClipboardAvailable) {
        _cleanExistingCopyToClipboardClient();
        return;
    } 

    $.getScript('/scripts/bower_components/zeroclipboard/dist/ZeroClipboard.js').done(function() {
    
        ZeroClipboard.config( { 
            swfPath: '/scripts/bower_components/zeroclipboard/dist/ZeroClipboard.swf',
        });
        
        var testClient = new ZeroClipboard();
        testClient.on("ready", function( readyEvent ) {
            _copyToClipboardAvailable = true;
            testClient.destroy();
        });
        testClient.on("error", function (e) {
            console.log('copy to clipboard not initialised because of: ' + e.name);
        });
    })
    .fail(function( jqxhr, settings, exception ) {
        console.log("Failed " + exception);
    });
}

function _cleanExistingCopyToClipboardClient() {
    if (_copyToClipboardActiveClient) {
        console.log('destroying copy to clipboard active client');
        _copyToClipboardActiveClient.destroy();
    }
}

function _showCopyToClipboardIfAvailable() {

    if (_copyToClipboardAvailable) { 
        $('#copyToClipboardButton').show();
        $('#copyToClipboardInstructions').show();
        $('#copyToClipboardNoButtonInstructions').hide();
        
        _copyToClipboardActiveClient = new ZeroClipboard($('#copyToClipboardButton'));
        _copyToClipboardActiveClient.on("ready", function( readyEvent ) {
            _copyToClipboardActiveClient.on("aftercopy", function( event ) {
                $().toastmessage('showSuccessToast', 'Public key copied to your clipboard');
            });
        });
    }
}
//# sourceURL=netfiles.js