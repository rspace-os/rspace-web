/**
 * Javascript for the workspace publish dialog, refactored from
 * crudops.js. HTML for the dialog is in publishDlg.tag
 *
 * Sharing itself is handled by the React ShareDialog, which this file only
 * opens via the OPEN_SHARE_DIALOG event.
 *
 * requires global.js
 */

/*
 * arguments
 * - idsToPublishGetter - a function to get all the ids to publish
 * - onpublish - an optional function, can be null
 */
function createPublishDialog(idsToPublishGetter, onpublish = null, tagSelector = '#publish-dialog') {
    onpublish = onpublish || function (publishedIds) {};

    $(tagSelector).dialog({
        modal : true,
        autoOpen : false,
        width : 480,
        height : 630,
        title: 'Publish',
        open : function(event, ui) {
            $(this).find(".accordion").accordion({
                heightStyle: "content", // Another option is heightStyle: "fill".
                active: 0,
                collapsible: false,
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
            disablePublishButton();
        },
        close : function(event, ui) {
            clearPublishOnInternetFields();
            clearPublishLinkFields();
        },
        buttons : {
            Cancel : function() {
                $(this).dialog('close');
            },
            Publish : function() {
                const isCloud = $(this).data("isCloud");
                const publishOnInternet = $('input[id="make_public_confirmation"]').val()?.toLowerCase() === 'confirm';
                const publishLink = $('input[id="make_public_link_confirmation"]').val()?.toLowerCase() === 'confirm';
                // ponytail: the button is disabled until "confirm" is typed, so this is only a backstop
                if (!publishOnInternet && !publishLink) {
                    return;
                }
                const publicationSummary = publishOnInternet ? $("#publicationDescription").val() : $("#publicationLinkDescription").val();
                const displayContactDetails = publishOnInternet ? $("#displayContactDetails").is(":checked") : $("#displayLinkContactDetails").is(":checked");

                $(this).dialog('close');
                var idsToPublish = idsToPublishGetter();
                var dataStr = JSON.stringify({
                    idsToShare : idsToPublish,
                    values : [{
                        publicationSummary: publicationSummary,
                        displayContactDetails: displayContactDetails,
                        publishOnInternet: publishOnInternet
                    }],
                    publish: true
                });
                RS.blockPage("Publishing document(s)");
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
                    const publishedIds = result.data.sharedIds;
                    const numPublicLinks = result.data.publicLinks.length;
                    let linksText = "";
                    result.data.publicLinks.forEach(link => {
                        const nameAndurl = link.split('_&_&_');
                        linksText+=nameAndurl[0] + " "+window.location.origin + nameAndurl[1] + "\n"
                    });
                    const finishPublish = (clipboardButton) =>
                        postPublish(publishedIds, idsToPublish, numPublicLinks, result, clipboardButton);
                    try {
                        if (navigator.clipboard?.writeText) {
                            navigator.clipboard.writeText(linksText)
                                .catch(err => {
                                    console.error(err);
                                    return fallbackCopyTextToClipboard(linksText);
                                })
                                .then(finishPublish);
                        } else {
                            finishPublish(fallbackCopyTextToClipboard(linksText));
                        }
                    } catch (err) {
                        console.error(err);
                        finishPublish(fallbackCopyTextToClipboard(linksText));
                    }
                    RS.trackEvent("user:publish:documents:workspace");
                });
                jqxhr.fail(function(xhr) {
                    RS.ajaxFailed("Publishing", true, xhr);
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
    const postPublish = (publishedIds, idsToPublish, numPublicLinks, result, clipboardButton )=>{
        if (publishedIds) {
            const numPublished = publishedIds.length;
            publishedIds.forEach(function (id) {
                $("#publishedStatusImg_" + id).removeAttr("hidden");
                $("#publish_" + id).val("false");
            });

            const toPublishLength = idsToPublish.length,
                publishedLength = numPublished + numPublicLinks

            onpublish(publishedIds);

            const unpublishedLength = toPublishLength - numPublished;
            // Each branch produces a whole sentence, so no message is assembled from fragments.
            if (numPublished === toPublishLength) {
                const publishedMsg = toPublishLength === 1
                    ? "Document published."
                    : `${toPublishLength} documents published.`;
                const clipboardMsg = clipboardButton
                    ? "There is a copy button at the top left of this screen which will copy document names with their published links to your clipboard."
                    : "Document names with their published links are in your clipboard.";
                RS.confirm(numPublicLinks > 0 ? `${publishedMsg} ${clipboardMsg}` : publishedMsg,
                    "success", clipboardButton ? 5000 : 3000);
            } else {
                if (publishedLength === 0) {
                    RS.confirm("No documents were published.", "warning", 3000);
                } else {
                    RS.confirm("Not all documents were published.", "notice", 3000);
                }

                const outcomeMsg = publishedLength === 0
                    ? "Publication was unsuccessful."
                    : (unpublishedLength === 1
                        ? "Publication was partially unsuccessful, 1 document was skipped."
                        : `Publication was partially unsuccessful, ${unpublishedLength} documents were skipped.`);
                const errorsLength = (result.errorMsg && result.errorMsg.errorMessages) ? result.errorMsg.errorMessages.length : 0;
                const reasonMsg = errorsLength
                    ? `${errorsLength === 1 ? "The following error was reported:" : "The following errors were reported:"}<br/> - ${getValidationErrorString(result.errorMsg, "<br/> - ")}`
                    : (unpublishedLength === 1
                        ? "Maybe the document is already published?"
                        : "Maybe the documents are already published?");
                apprise(`${outcomeMsg} ${reasonMsg}`);

                // TO-DO: RSPAC-1287 Focus the apprise dialog
            }
        } else {
            apprise("Publishing did not complete. " + getValidationErrorString(result.errorMsg));
            // TO-DO: RSPAC-1287 Focus the apprise dialog
        }
    }
}

const clearPublishOnInternetFields = () => {
    $('input[id="make_public_confirmation"]').val('');
    $("#publicationDescription").val('');
    $("#displayContactDetails").prop( "checked", false );
    disablePublishButton();
    setConfirmedLabel(".publishSelected", false);
}
const clearPublishLinkFields = () => {
    $('input[id="make_public_link_confirmation"]').val('');
    $("#publicationLinkDescription").val('');
    $("#displayLinkContactDetails").prop( "checked", false );
    disablePublishButton();
    setConfirmedLabel(".publishLinkSelected", false);
}

$(document).on("click", "#shareRecord", function (e) {
  e.preventDefault();

  // the data-cloud attribute can be in a span or an a tag RSPAC-1629
  // depending if sharing workspace or document
  const isCloud =
    $(this).find("a").data("cloud") ||
    $(this).data("cloud") ||
    $(this).find("span").data("cloud");

  let selected;
  if (typeof getSelectedIdsNamesAndTypes === "function") {
    selected = getSelectedIdsNamesAndTypes();
  }

  let globalIds;
  if (
    /editor\/structuredDocument/.test(window.location.href) ||
    /notebookEditor/.test(window.location.href)
  ) {
    globalIds = [`SD${selected.ids[0]}`];
  } else {
    globalIds =
      typeof getSelectedGlobalIds === "function" ? getSelectedGlobalIds() : [];
  }
  if (globalIds.length === 0) {
    throw new Error("No global IDs found for sharing");
  }

  // Dispatch event for React ShareDialog
  window.dispatchEvent(
    new CustomEvent("OPEN_SHARE_DIALOG", {
      detail: {
        ids: selected.ids,
        names: selected.names,
        globalIds: globalIds,
        isCloud: isCloud,
      },
    }),
  );
});

$(document).on('click', '#publishRecord', function (e) {
    e.preventDefault();

    const isCloud =
        $(this).find("a").data("cloud") ||
        $(this).data("cloud") ||
        $(this).find("span").data("cloud");

    $('#publish-dialog')
        .data("isCloud", isCloud)
        .dialog("open");
});

const disablePublishButton = () => $(":contains('Publish')").closest('button').prop('disabled', true).css('opacity',0.5);
const enablePublishButton = () => $(":contains('Publish')").closest('button').prop('disabled', false).css('opacity',1);

$(document).on('click', '#clearPublish', function() {
    clearPublishOnInternetFields();
});
$(document).on('click', '#clearPublishLink', function() {
    clearPublishLinkFields();
});

$(document).on('input','#make_public_confirmation', function() {
    if($('input[id="make_public_confirmation"]').val().toLowerCase() === 'confirm'){
        setConfirmedLabel(".publishSelected", true);
        enablePublishButton();
    } else {
        setConfirmedLabel(".publishSelected", false);
        disablePublishButton();
    }
});

$(document).on('input','#make_public_link_confirmation', function() {
    if($('input[id="make_public_link_confirmation"]').val().toLowerCase() === 'confirm'){
        setConfirmedLabel(".publishLinkSelected", true);
        enablePublishButton();
    } else {
        setConfirmedLabel(".publishLinkSelected", false);
        disablePublishButton();
    }
});

// Shows or clears the confirmation marker next to a publish section heading.
function setConfirmedLabel(className, confirmed) {
    $(className).text(confirmed ? "(confirmed)" : "");
}
