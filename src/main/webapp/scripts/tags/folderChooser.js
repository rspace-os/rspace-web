function initFolderChooser(folderChooserId, initFolderIdGetter, afterDirSelectionCallback) {
    $('body').on('click', '#folderChooser-createNewSubfolder' + folderChooserId, function (e){
        e.preventDefault();
        _doCreateSubfolder (folderChooserId);
    });
    
    $('body').on('click', '#folderChooserLnk' + folderChooserId, function (e){
        e.preventDefault();
        var folderId = '/';
        if (typeof initFolderIdGetter === 'function') {
            folderId = initFolderIdGetter();
        }

        _setUpFolderTree(folderId, folderChooserId, afterDirSelectionCallback);
    });
}

function showFolderChooser(folderChooserId, text) {
    $('#folderChooser' + folderChooserId).show();
    if (text) {
        $('#folderChooserDesc' + folderChooserId).html(text);
    }
}

function clearFolderChooser(folderChooserId) {
    $('#folderChooser' + folderChooserId).hide();
    $('#folderChooserInfo' + folderChooserId).hide();
    $('.folderChooserData' + folderChooserId).val("");
    $('#folderChooser-path' + folderChooserId).html("");
    $('#folderChooserTree' + folderChooserId).html("");
}


function setFolderChooserLinkDesc(folderChooserId, linkDesc) {
    $('#folderChooserLnk' + folderChooserId).html(linkDesc);
}

function setFolderChooserDirListingParams(folderChooserId, params) {
    $('#folderChooserTree' + folderChooserId).data('dirListingUrlParams', params)
}

function _setUpFolderTree(folderId, folderChooserId, afterDirSelectionCallback) {
    var scriptUrl = '/fileTree/ajax/directoriesInModel';
    var dirListingParams = $('#folderChooserTree' + folderChooserId).data('dirListingUrlParams');
    var scriptUrlParams = dirListingParams || 'showNotebooks=true';

    $('#folderChooserTree' + folderChooserId).fileTree({
        // custom argument! This is so we show the root folder of the 
        // tree when it is not the actual root folder, e.g. a group shared folder
        initialLoad : true,
        root : folderId,
        script : createURL(scriptUrl + "?" + scriptUrlParams),
        expandSpeed : 500,
        collapseSpeed : 500,
        multiFolder : false
    },
    function(file, type) {
        if ("directory" == type) {
            var $selectedRow = $('#folderChooserTree' + folderChooserId).find("a[rel='" + file + "']");
            var selectedType = $selectedRow.data('type');
            // get parents, then reverse order to construct the path 
            var $parents = $($selectedRow.parents("li").get().reverse());
            var path = "/";
            $parents.each(function() {
                path = path + RS.escapeHtml($(this).children("a").text()) + "/";
            });
            $("#folderChooser-path" + folderChooserId).html(path);
            // holds the id of the selected target folder
            file = file.replace(/\/$/, "");
            $('#folderChooser-id' + folderChooserId).val(file);
            $('#folderChooser-type' + folderChooserId).val(selectedType);
            
            if (typeof afterDirSelectionCallback != undefined) {
                afterDirSelectionCallback($selectedRow);
            }
        }
    });
    $('#folderChooserInfo'+folderChooserId).show();
}

function _doCreateSubfolder(folderChooserId) {
    var newName = $('#newFolder'+folderChooserId).val().trim();
    var parentFolder = $("#folderChooser-id" + folderChooserId).val().trim();
    var selectionType = $('#folderChooser-type' + folderChooserId).val();
    if (RS.isBlank(newName)) {
        apprise("Please enter a name for the new folder");
        return;
    }
    if (RS.isBlank(parentFolder)) {
        apprise("Please select location for the new folder using the tree view above");
        return;
    }
    if (selectionType === 'NOTEBOOK') {
        apprise("Cannot create a folder inside the Notebook");
        return;
    }
    parentFolder = parentFolder.replace(/\/$/, "");
    _inactivateCreateFolder(folderChooserId);
    var folderClickTimeout = 1200;
    var jqxhr$ = $.post(createURL('/workspace/ajax/create_folder/' + parentFolder),
            {folderNameField:newName}, function (resp) {
        if (resp.data) {
            var createdId = resp.data;

            var parentItem$=$("a[rel='"+parentFolder+"/']");
            if(parentItem$.closest('li').hasClass("collapsed")) {
                setTimeout(function(){parentItem$.click(); }, folderClickTimeout);
                setTimeout(function(){$("a[rel='"+createdId+"/']").click(); _activateCreateFolder (folderChooserId); }, folderClickTimeout * 2);
            } else {
                setTimeout(function(){parentItem$.click(); }, folderClickTimeout);
                setTimeout(function(){parentItem$.click(); }, folderClickTimeout *2);
                setTimeout(function(){$("a[rel='"+createdId+"/']").click(); _activateCreateFolder(folderChooserId); }, folderClickTimeout * 3);
            }
        } else {
            apprise(getValidationErrorString(resp.errorMsg, ';', true));
        }
    });
    jqxhr$.fail(function () {
        RS.ajaxFailed("Folder creation", false, jqxhr$);
    });
    jqxhr$.always(function () {
        // wait for max amount of time for operation to succeed before re-enabling
        setTimeout(function(){ _activateCreateFolder(folderChooserId); }, folderClickTimeout * 3);
    });
}

function _inactivateCreateFolder(folderChooserId) {
    $("#folderChooser-createFolderSpan"+folderChooserId).html(
            "<img width='16' height='16' src='/images/ajax-loading.gif'/> Creating folder...");   
}

function _activateCreateFolder(folderChooserId) {
    $("#folderChooser-createFolderSpan"+folderChooserId).html(
            "<a style='color: #1465b7;' id='folderChooser-createNewSubfolder"+folderChooserId+"' href='#'>Create</a>");
}
