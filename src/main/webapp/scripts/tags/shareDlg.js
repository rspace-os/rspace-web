/**
 * Javascript for the workspace sharing dialog, refactored from
 * crudops.js. HTML for the dialog is in shareDlg.tag
 *
 * requires global.js, uses folderChooser.js
 */

var shareDlgShowNotebooks;

function _showInvalidSelection(show){
    $('.shareInvalidSelections')[show ? "show" : "hide"]();
}

function _deselectAllShareOptions(){
    $('.shareSelection:checked').each(function( index ) {
        $(this).attr('checked', false);
    });
}

function _updateShareIntoFolderView() {
    var selected$ = $('.shareSelection:checked');
    var selectedCount = selected$.size();

    var selectedGroups = 0,
        selectedUsers = 0;

    selected$.each(function(s){
        var name = $(this).attr("name");
        selectedGroups = selectedGroups + (name === "grpId" ? 1 : 0);
        selectedUsers = selectedUsers + (name === "userId" ? 1 : 0);
    });

    // switch between all the messages
    if (selectedCount === 0 || selectedCount  > 1) {
        clearFolderChooser('-share');
    } else if (selected$.first().closest('.groupShareSection').length === 1) {
        showFolderChooser('-share', 'to share into. Otherwise, documents will be shared into the top of your LabGroup Shared folder.');
    }

    // update the counters
	var groupSelectCount$ = $("#shareGroupHeader .selectedCount"),
		userSelectCount$ = $("#shareUserHeader .selectedCount");

	if(selectedGroups > 0){
        groupSelectCount$.show().find("span").html(selectedGroups);
    }else{
        groupSelectCount$.hide();
	}

	if(selectedUsers > 0){
        userSelectCount$.show().find("span").html(selectedUsers);
	}else{
        userSelectCount$.hide();
	}
}

function _setShowNotebooksInShareIntoFolderView(selectedTypes) {

    var showNotebooks = onlyNormalDocsOnTypesList(selectedTypes);
    if (showNotebooks !== shareDlgShowNotebooks) {

        var urlParam = 'showNotebooksForShare=' + showNotebooks;
        setFolderChooserDirListingParams('-share', urlParam);

        var linkDesc = 'choose a folder ' + (showNotebooks ? 'or notebook' : '');
        setFolderChooserLinkDesc('-share', linkDesc);

        clearFolderChooser('-share');
        $('#shareIntoNotebookSelectedMsg').hide();
        shareDlgShowNotebooks = showNotebooks;
    }
}

function _getGroupShareChoices() {
    if(!$('input[name="shareGroupSelect"]:checked').length) {
        return $('input[name="grpId"]:checked')
        .map(function() {
            var td$ = $(this).closest('td').next();
            var selectedOp = td$.find('option:selected').val();
            return {
                groupid : $(this).attr('value'),
                operation : selectedOp,
                groupFolderId: $('#folderChooser-id-share').val()
            };
        }).get();
    } else {
        return $('input[name="shareGroupSelect"]:checked')
        .map(function() {
            var td$ = $(this).parent().parent().find("input,select");
            var selectedOp = $(".sharedIntoFolderPermission").val();
            return {
                groupid : $(this).attr('value'),
                operation : selectedOp,
                groupFolderId: $('#folderChooser-id-shareIntoFolder').val()
            };
        }).get();
    }
}
const SHARE_DOCS_TYPE = "sharedocs"
const SHARE_SNIPPETS_TYPE = "sharesnippets"

//set at dialog creation and visible to all onclick functions
const dialogValues = {sharingType:SHARE_DOCS_TYPE};

const getSharedFolderTarget = (type) => {
    if(type === SHARE_DOCS_TYPE){
        return 'data-sharedFolderId';
    } else if (type === SHARE_SNIPPETS_TYPE){
        return 'data-sharedSnippetFolderId';
    }
}
/*
 * arguments
 * - dialogTitle
 * - idsToShareGetter - a function to get all the ids to share
 * - onshare - an optional function, can be null
 */
function createShareDialog(dialogTitle, idsToShareGetter, onshare=null, tagSelector='#share-dialog',
                           type= SHARE_DOCS_TYPE) {
    onshare = onshare || function(sharedIds) {};
    dialogValues.sharingType = type;
    $('body').on('click', '.shareSelection', function (){
        _updateShareIntoFolderView();
    });

    if (dialogTitle!== 'Publish' && typeof initFolderChooser === 'function') {
        initFolderChooser('-share', function () {
            return $('input[name="grpId"]:checked').first().attr(getSharedFolderTarget(type));
        }, function ($selectedRow) {
            var notebookSelected = $selectedRow.data('type') === 'NOTEBOOK';
            var $groupSelectRow = $('input[name="grpId"]:checked').parents('tr');
            $groupSelectRow.find('.shareIntoNotebookInfo').toggle(notebookSelected);
            $groupSelectRow.find('.sharingPermissionSelect').toggle(!notebookSelected);
            $('#shareIntoNotebookSelectedMsg').toggle(notebookSelected);
            $('#folderChooserFolderCreation-share').toggle(!notebookSelected);
        });

        //Add folder tree for sharing into folders/notebooks
        initFolderChooser("-shareIntoFolder", function () {
            return $('input[name="shareGroupSelect"]:checked').first().attr(getSharedFolderTarget(type));
        },function ($selectedRow) {
            var notebookSelected = $selectedRow.data('type') === 'NOTEBOOK';
            var $groupSelectRow = $('input[name="grpId"]:checked').parents('tr');
            $groupSelectRow.find('.shareIntoNotebookInfo').toggle(notebookSelected);
            $groupSelectRow.find('.sharingPermissionSelect').toggle(!notebookSelected);
            $('#shareIntoNotebookSelectedMsg').toggle(notebookSelected);
            $('#folderChooserFolderCreation-share').toggle(!notebookSelected);
        
        });
    }

    $(tagSelector).dialog({
        modal : true,
        autoOpen : false,
        width : 480,
        height : 630,
        title: dialogTitle,
        open : function(event, ui) {
            const publicOnly = $(this).data("publish-only");
            $( ".accordion" ).accordion({
                heightStyle: "content", // Another option is heightStyle: "fill".
                active: publicOnly? 0: false,
                collapsible: !publicOnly,
                activate: function( event, ui ) {
                    if($(ui.oldHeader).length > 0) {
                        if ($(ui.oldHeader)[0].id === 'publishPublicHeader') {
                            clearPublishOnInternetFields();
                        } else if ($(ui.oldHeader)[0].id === 'publishPublicLinkHeader') {
                            clearPublishLinkFields();
                        }
                    }
                }
            });

            if(publicOnly){
                $(":contains('Share')").closest('button').text('Publish').prop('disabled', true).css('opacity',0.5);
            }
            else {
                $(":contains('Publish')").closest('button').text('Share').prop('disabled', false).css('opacity',1);
            }

            var selectedTypes = $(this).data("selectedTypes");
            _setShowNotebooksInShareIntoFolderView(selectedTypes);
            _updateShareIntoFolderView();

            //Reset group share if previously selected
            if($('.shareRadio').is(':checked')) {
                resetGroupShare();
            }
            //Reset user share if previously selected
            if($('.shareSelection').is(':checked')) {
                resetShareCheckBoxes(); 
            }
        },
        close : function(event, ui) {
            // Remove email rows.
            $('.emailRow :first').find('input').val('');
            $('.emailRow').not(':first').remove();

            // Remove group rows.
            $('.groupRow :first').find('input').val('');
            $('.groupRow').not(':first').remove();
            clearPublishOnInternetFields();
            clearPublishLinkFields();
        },
        buttons : {
            Cancel : function() {
                $(this).dialog('close');
            },
            Share : function() {
                const isCloud = $(this).data("isCloud");
                const checkedCount = $('input[name="userId"]:checked').size() + $('input[name="grpId"]:checked').size() + $('input[name="shareGroupSelect"]:checked').size();
                const noEmailInputs = ($("input.email").filter(function () {return $.trim($(this).val()).length > 0;}).length === 0);
                const noExternalGroupInputs = ($("input.externalGroupId").filter(function () {return $.trim($(this).val()).length > 0;}).length === 0);
                const noCheckedEmailCloud = isCloud ? noEmailInputs :  true;
                const noCheckedExternalGroupCloud = isCloud ? noExternalGroupInputs :  true;
                const noPublicShareOnInternetConfirmed = $('input[id="make_public_confirmation"]').val()?.toLowerCase() !== 'confirm';
                const noPublicShareLinkConfirmed = $('input[id="make_public_link_confirmation"]').val()?.toLowerCase() !== 'confirm';
                const publishOnInternet = !noPublicShareOnInternetConfirmed;
                const publishLink = !noPublicShareLinkConfirmed;
                const publish = publishOnInternet || publishLink;
                const publicationSummary = publishOnInternet ? $("#publicationDescription").val() : $("#publicationLinkDescription").val();
                const displayContactDetails =  publishOnInternet ?$("#displayContactDetails").is(":checked") : $("#displayLinkContactDetails").is(":checked");
                // if nothing is checked then remind user.
                if (checkedCount === 0 && noCheckedEmailCloud && noCheckedExternalGroupCloud && noPublicShareOnInternetConfirmed && noPublicShareLinkConfirmed) {
                    let errorMessage = "<p>Please select a sharing option. Either:" +
                    "<ul><li> Share with a group you belong to, or a member of that group" +
                    "<li> Invite by email an individual to share with" +
                    "<li> Invite members of another group to view your document" +
                        " <li> Cancel to abort.</ul></p>"
                    apprise(errorMessage);
                    // TO-DO: RSPAC-1287 Focus the apprise dialog
                    return;
                }

                // Non-empty if sharing with a group
                const shareDestination = $("#folderChooser-type-shareIntoFolder").val();

                if (shareDestination !== "") {
                    const permissionChooser = $("#permissionChooser");
                    const isPermissionStep = permissionChooser.is(":visible");

                    // When sharing into notebooks, share with the permissions
                    // of that notebook. Otherwise, show a permission selector
                    if (!isPermissionStep && shareDestination !== "NOTEBOOK") {
                        $("#folderChooser-shareIntoFolder").hide();
                        permissionChooser.show();

                        // Ensure share with group section is shown
                        $("#accordion").accordion({ active: 0 });

                        return;
                    }
                }

                var selectedUsersAndGroups = _getGroupShareChoices ();
                var userChoices = $('input[name="userId"]:checked')
                    .map(function() {
                        var td$ = $(this).closest('td').next();
                        var selectedOp = td$.find('option:selected').val();
                        return {
                            userId : $(this).attr('value'),
                            operation : selectedOp
                        };
                    }).get();

                var emailChoices = $("input.email").filter(function () {return $.trim($(this).val()).length > 0;})
                    .map(function() {
                        var td$ = $(this).closest('td').next();
                        var selectedOp = td$.find('option:selected').val();
                        return {
                            email : $(this).val(),
                            operation : selectedOp,
                        };
                    }).get();

                var externalGroupChoices = $("input.externalGroupId").filter(function () {return $.trim($(this).val()).length > 0;})
                    .map(function() {
                        var td$ = $(this).closest('td').next();
                        var selectedOp = td$.find('option:selected').val();
                        return {
                            externalGroupId : $(this).data("groupid"),
                            operation : selectedOp,
                        };
                    }).get();

                $(this).dialog('close');
                // Combine arrays
                $.merge(selectedUsersAndGroups, userChoices);
                $.merge(selectedUsersAndGroups, emailChoices);
                $.merge(selectedUsersAndGroups, externalGroupChoices);
                var idsToShare = idsToShareGetter();
                let data = {};
                if(publish){
                    const config = {
                        publicationSummary: publicationSummary,
                        displayContactDetails: displayContactDetails,
                        publishOnInternet: publishOnInternet
                    };
                   data = {
                       idsToShare : idsToShare,
                       values : [config],
                       publish: publish
                   };
                } else {
                    data = {
                        idsToShare : idsToShare,
                        values : selectedUsersAndGroups,
                        publish: publish
                    };
                }
                var dataStr = JSON.stringify(data);
                RS.blockPage("Sharing document(s)");
                var urlString = isCloud ? "/cloud/ajax/shareRecord" :  "/workspace/ajax/shareRecord";
                var jqxhr = $.ajax({
                    url : createURL(urlString),
                    dataType : 'json',
                    data : dataStr,
                    type : "POST",
                    contentType : "application/json;"
                });
                jqxhr.always(function() {
                    RS.unblockPage();
                });
                jqxhr.done(function(result) {
                    const sharedIds = result.data.sharedIds;
                    const numPublicLinks = result.data.publicLinks.length;
                    let linksText = "";
                    if(publish){
                        result.data.publicLinks.forEach(link => {
                            const nameAndurl = link.split('_&_&_');
                            linksText+=nameAndurl[0] + " "+window.location.origin + nameAndurl[1] + "\n"
                        });
                        try {
                            navigator.clipboard.writeText(linksText).catch(err=>{
                                console.error(err);
                                return fallbackCopyTextToClipboard(linksText);
                            })
                                .then((clipboardButton) => postShare(sharedIds, idsToShare, numPublicLinks, publish, null, result, clipboardButton));
                        } catch (err){
                            console.error(err);
                        }
                    } else {
                        postShare(sharedIds,idsToShare,0,false,selectedUsersAndGroups, result, false);
                    }
                });
                jqxhr.fail(function(xhr) {
                    RS.ajaxFailed("Sharing", true, xhr);
                });
            }
        }
    });

    function createTextArea(text) {
        textArea = document.createElement('textArea');
        textArea.value = text;
        document.body.appendChild(textArea);
    }
    function selectText() {
        var range,
            selection;

            range = document.createRange();
            range.selectNodeContents(textArea);
            selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
            textArea.setSelectionRange(0, 999999);
    }

    function copyToClipboard() {
        const successful = document.execCommand('copy');
        document.body.removeChild(textArea);
        console.log(successful);
    }
    const fallbackCopyTextToClipboard2 = (text) => {
        createTextArea(text);
        selectText();
        copyToClipboard();
    }
    const  fallbackCopyTextToClipboard = (text) => {
        if($('#copy_to_clipboard').length > 0){
            document.body.removeChild(document.getElementById('copy_to_clipboard'));
        }
        const btn = document.createElement("button");
        btn.setAttribute("id", 'copy_to_clipboard');
        btn.textContent = 'copy lastest links';
        btn.onclick = ()=>fallbackCopyTextToClipboard2(text);
        // // Avoid scrolling to bottom
        btn.style.top = "0";
        btn.style.left = "10%";
        btn.style.position = "fixed";
        document.body.appendChild(btn);
        return true;
    }
    const postShare = (sharedIds,idsToShare,numPublicLinks,publishAttempted,shareeUsersAndGroups, result, clipboardButton )=>{
        console.log("clipboardButton " + clipboardButton);
        const numShared = sharedIds.length;
        if (sharedIds) {
            if(!publishAttempted) {
                sharedIds.forEach(function (id) {
                    $("#sharedStatusImg_" + id).removeAttr("hidden");
                })
            } else {
                sharedIds.forEach(function (id) {
                    $("#publishedStatusImg_" + id).removeAttr("hidden");
                    $("#publish_" + id).val("false");
                })
            }

            const toShareLength = idsToShare.length,
                sharedLength = numShared + numPublicLinks

            onshare(sharedIds);

            const unsharedLength = toShareLength - numShared;
            const action = (publishAttempted?" published" :" shared");
            if (numShared === toShareLength) {
                var successMsg = (toShareLength == 1 ? "Document" : (toShareLength + " documents")) + action;
                if (numPublicLinks>0) {
                    if(clipboardButton){
                        successMsg += ". There is a copy button at the top left of this screen" +
                            " which will copy document names with their published links to your clipboard."
                    } else {
                        successMsg += ". Document names with their published links are in your clipboard."
                    }
                }
                RS.confirm(successMsg, "success", clipboardButton ? 5000 : 3000);
            } else {
                if (sharedLength === 0) {
                    RS.confirm("Document(s) not "+action, "warning", 3000);
                } else {
                    RS.confirm("Not all documents were "+action, "notice", 3000);
                }

                var errorMsg = (publishAttempted ? "Publication": "Sharing") +  " was " + (sharedLength > 0 ? "partially" : "") + " unsuccessful";
                if (sharedLength > 0) {
                    errorMsg += ", " + unsharedLength + " document" + (unsharedLength === 1 ? " was" : "s were") + " skipped";
                }
                var errorsLength = (result.errorMsg && result.errorMsg.errorMessages) ? result.errorMsg.errorMessages.length : 0;
                if (errorsLength) {
                    errorMsg += " because of the following error" + (errorsLength > 1 ? "s" : "") + ": <br/> - "
                        + getValidationErrorString(result.errorMsg, "<br/> - ");
                } else {
                    errorMsg += ". Maybe the document" + (unsharedLength === 1 ? " is" : "s are") + " already" +  (publishAttempted ? " published?":" shared?");
                }
                apprise(errorMsg);

                // TO-DO: RSPAC-1287 Focus the apprise dialog
            }
        } else {
            apprise("Sharing did not complete: " + getValidationErrorString(result.errorMsg));
            // TO-DO: RSPAC-1287 Focus the apprise dialog
        }
    }
}

function onlyNormalDocsOnTypesList(types) {
    var onlyNormalDocs = true;
    if (types) {
        $.each(types, function(i, type) {
            if (type !== 'NORMAL') {
                onlyNormalDocs = false;
                return false;
            }
        });
    }
    return onlyNormalDocs;
}

const clearPublishOnInternetFields = () => {
    $('input[id="make_public_confirmation"]').val('');
    $("#publicationDescription").val('');
    $("#displayContactDetails").prop( "checked", false );
    disablePublishButton();
    updateSelection(".publishSelected", 'publish', true);
}
const clearPublishLinkFields = () => {
    $('input[id="make_public_link_confirmation"]').val('');
    $("#publicationLinkDescription").val('');
    $("#displayLinkContactDetails").prop( "checked", false );
    disablePublishButton();
    updateSelection(".publishLinkSelected", 'publish', true);
}

$(document).on('click', '#shareRecord', function (e) {
    e.preventDefault();

    // the data-cloud attribute can be in a span or an a tag RSPAC-1629
    // depending if sharing workspace or document
    const isCloud =
      $(this).find("a").data("cloud") ||
      $(this).data("cloud") ||
      $(this).find("span").data("cloud");

    const selected = typeof getSelectedIdsNamesAndTypes === 'function' ? getSelectedIdsNamesAndTypes():galleries_getSelectedIdsNamesAndTypes();
    $('#share-dialog')
      .data("isCloud", isCloud)
      .data("selectedTypes", selected.types).data("publish-only", false)
      .dialog("open");

    applyAutocomplete(".email");
    applyGroupAutocomplete(3, ".externalGroupId");
});

$(document).on('click', '#publishRecord', function (e) {
    e.preventDefault();

    const isCloud =
        $(this).find("a").data("cloud") ||
        $(this).data("cloud") ||
        $(this).find("span").data("cloud");

    const selected = getSelectedIdsNamesAndTypes();
    $('#publish-dialog')
        .data("isCloud", isCloud)
        .data("selectedTypes", selected.types).data("publish-only", true)
        .dialog("open");

    applyAutocomplete(".email");
    applyGroupAutocomplete(3, ".externalGroupId");
});

$(document).on('click', '.add-email', function(e) {
    var row = "<tr class='emailRow row'><td><input type='text' name='email' class='email'></td><td>" +
        "<select><option value='read' selected>Read</option><option value='write'>Edit</option></select></td>" +
        "<td> <button type='button' class='remove-email'>Remove</button></td></tr>";
    $(".share-table").append(row);
    applyAutocomplete(".email");
});

$(document).on('click', '.remove-email', function(e) {
    if($( ".emailRow" ).length > 1) {
        $(this).closest('.emailRow').remove();
    }
});

$(document).on('click', '.add-group', function(e) {
    var row = "<tr class='groupRow row'><td><input type='text' name='externalGroupId' class='externalGroupId' data-groupid></td><td>" +
        "<select><option value='read' selected>Read</option><option value='write'>Edit</option></select></td>" +
        "<td> <button type='button' class='remove-group'>Remove</button></td></tr>";
    $(".group-share-table").append(row);
    applyGroupAutocomplete(3, ".externalGroupId");
});

$(document).on('click', '.remove-group', function(e) {
    if($( ".groupRow" ).length > 1) {
        $(this).closest('.groupRow').remove();
    }
});


$(document).on('click', '.shareInvalidSelections .clear-all', function(e) {
    _deselectAllShareOptions();
    _showInvalidSelection();
    _updateShareIntoFolderView();
});

//Add to shared folder or notebook functionality.
$(document).on('click', '.shareRadio', function(e) {
    $(".groupSelectContainer.active").removeClass("active");
    $(this).parent().parent().addClass("active");
    _updateShareIntoFolderView();
    clearFolderChooser('-shareIntoFolder');
    showFolderChooser('-shareIntoFolder', '');
    setFolderChooserLinkDesc('-shareIntoFolder', '');
    $('#folderChooserLnk-shareIntoFolder').click()
    $('.folderShare').toggle("slow");
    $('.resetContainer').toggle();
    $(".selectedGroupName").text($(this).attr("data-sharedfoldername"));
    updateSelection(".shareGroupSelected", $(this).attr("data-sharedfoldername"), false)
    setTimeout(function(){ $(".directory a").click();}, 1000);
});

const disablePublishButton = () => $(":contains('Publish')").closest('button').prop('disabled', true).css('opacity',0.5);
const enablePublishButton = () => $(":contains('Publish')").closest('button').prop('disabled', false).css('opacity',1);

$(document).on('click', '#clearPublish', function() {
    clearPublishOnInternetFields();
});
$(document).on('click', '#clearPublishLink', function() {
    clearPublishLinkFields();
});

$(document).on('click', '#resetShareIntoFolder', function(e) {
    resetGroupShare();
});

$(document).on('input','#make_public_confirmation', function() {
    if($('input[id="make_public_confirmation"]').val().toLowerCase() === 'confirm'){
        updateSelection(".publishSelected", 'publish', false);
        $(".publishSelected").text("(confirmed)");
        enablePublishButton();
    } else {
        updateSelection(".publishSelected", 'publish', true);
        disablePublishButton();
    }
});

$(document).on('input','#make_public_link_confirmation', function() {
    if($('input[id="make_public_link_confirmation"]').val().toLowerCase() === 'confirm'){
        updateSelection(".publishLinkSelected", 'publish', false);
        $(".publishLinkSelected").text("(confirmed)");
        enablePublishButton();
    } else {
        updateSelection(".publishLinkSelected", 'publish', true);
        disablePublishButton();
    }
});

//Updates the class with a specfic piece of text. If clear is supplied and true then empty text. 
function updateSelection (className, groupName, clear) {
    if(clear) {
        $(className).text(""); 
    } else {
        $(className).text("(" + groupName + " selected)");
    }
}

function resetGroupShare() {
    $("#permissionChooser").hide();
    $("#folderChooser-type-shareIntoFolder").val("");
    $(".shareRadio").attr('checked', false);
    $(".groupSelectContainer.active").removeClass("active");
    _updateShareIntoFolderView();
    clearFolderChooser('-shareIntoFolder');
    $('.folderShare').toggle("slow");
    $('.resetContainer').toggle();
    updateSelection(".shareGroupSelected", "", true)
}

function resetShareCheckBoxes() {
    $('.shareSelection').attr('checked', false);
    _updateShareIntoFolderView()
}
