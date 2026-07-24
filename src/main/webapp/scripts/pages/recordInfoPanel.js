/**
 * Returns recordInfoPanelTemplate filled with passed record information.
 *
 * @param info record information
 * @returns $recordInfoPanel div filled with record information
 */
function generate$RecordInfoPanel(info) {
  var isStructuredDocument = info.type === "Structured Document" || info.type === 'Template';
  var isNotebook = info.type === "Notebook";
  var isSnippet = info.type === "Snippet";
  var isFolder = info.type === "Folder" || info.type === "System Folder";
  var isForm = info.type === "Form";
  var isPdfExport = info.type === 'PdfDocuments';
  var isDMP = info.type === 'DMPs';
  var isGalleryFile = info.type === 'Image' || info.type === 'Audio' || info.type === 'Video' ||
    info.type === 'Document' || info.type === 'Miscellaneous' || info.type === 'Chemistry'
      || isPdfExport || isDMP;
  var isImageFile = info.type === 'Image';
  var isChemicalFile = info.type === 'Chemistry';
  var isOwnedByCurrentUser = info.ownerUsername === currentUser;
  var isRevisionView = info.revision != null;
  var isVersionView = info.oid.idString.indexOf('v') > 0;
  var isDNAfile = RS.isSnapGeneFormat(info.extension);
  var inGallery = window.location.href.indexOf('/gallery') != -1;
  var isCreatedFromXMLImport = info.fromImport;

  var $newInfoPanel = $('#recordInfoPanelTemplate > .recordInfoPanel').clone();

  $newInfoPanel.find('.infoPanel-name').text(info.name);
  $newInfoPanel.find('.infoPanel-type').text(info.type);
  $newInfoPanel.find('.infoPanel-owner').text(info.ownerFullName);
  if(isForm){
    $newInfoPanel.find('.infoPanelCreatedByRow').show();
    $newInfoPanel.find('.infoPanel-createdBy').text(info.createdBy);
  } else {
    $newInfoPanel.find('.infoPanelCreatedByRow').hide();
  }
  $newInfoPanel.find('tr.infoPanelOriginalSource').toggle(isCreatedFromXMLImport);
  if (isCreatedFromXMLImport) {
	 $newInfoPanel.find('.infoPanelOriginalCreator').text(info.originalOwnerUsernamePreImport);
  }
  $newInfoPanel.find('.infoPanelRevisionInfoDiv').toggle(isVersionView);
  if (isVersionView) {
      var oidNoVersion = info.oid.idString.split('v')[0];
      $newInfoPanel.find('.infoPanel-objectIdLatestLink')
          .attr('href', '/globalId/' + oidNoVersion)
          .text(oidNoVersion);
  }

  $newInfoPanel.find('.infoPanelPathRow').toggle(!!info.path);
  $newInfoPanel.find('.infoPanel-path').text(info.path);
  var timezoneRE = /\s\+\d{4}/;
  var match = info.creationDateWithClientTimezoneOffset.match(timezoneRE);
  if (match) {
    info.creationDateWithClientTimezoneOffset =
      info.creationDateWithClientTimezoneOffset.replace(info.creationDateWithClientTimezoneOffset.match(timezoneRE)[0], "");
  }
  $newInfoPanel.find('.infoPanel-creationDate').text(info.creationDateWithClientTimezoneOffset);
  $newInfoPanel.find('.infoPanel-modificationDate').parent().toggle(!isFolder);
  var match2 = info.modificationDateWithClientTimezoneOffset.match(timezoneRE);
  if (match2) {
    info.modificationDateWithClientTimezoneOffset =
      info.modificationDateWithClientTimezoneOffset.replace(info.modificationDateWithClientTimezoneOffset.match(timezoneRE)[0], "");
  }
  $newInfoPanel.find('.infoPanel-modificationDate').text(info.modificationDateWithClientTimezoneOffset);

  _setInfoPanelGlobalIdRow($newInfoPanel.find('.infoPanelObjectIdRow'), !isSnippet, info.oid);
  _setInfoPanelGlobalIdRow($newInfoPanel.find('.infoPanelOriginalImageIdRow'), isGalleryFile && (!!info.originalImageOid), info.originalImageOid);

  $newInfoPanel.find('tr.infoPanelFileVersionRow').toggle(isGalleryFile && (info.version > 1 || isRevisionView));
  $newInfoPanel.find('tr.infoPanelFileSizeRow').toggle(isGalleryFile);
  $newInfoPanel.find('.infoPanelButtons').toggle(isGalleryFile);
  $newInfoPanel.find('.recordReplaceBtn').toggle(isGalleryFile); // more detailed conditions inside 'isGalleryFile' block

  $newInfoPanel.find('tr.infoPanelDocVersionRow').toggle(isStructuredDocument);
  $newInfoPanel.find('tr.infoPanelStatusRow').toggle(isStructuredDocument);

  var showTags = isStructuredDocument || isFolder || isNotebook;
  $newInfoPanel.find('tr.infoPanelTagsRow').toggle(showTags);
  if (showTags) {
    $newInfoPanel.find('.infoPanel-tags').text(info.tags == null ? "" :
      info.tags
          .replaceAll(",", ", ")
          .replaceAll("__rspactags_forsl__", "/")
          .replaceAll("__rspactags_comma__", ",")
    );
  }
  $newInfoPanel.find('tr.infoPanelSignatureStatusRow').toggle(isStructuredDocument);

  if (isStructuredDocument) {
    $newInfoPanel.find('.infoPanel-docVersion').text(info.version);
    $newInfoPanel.find('.infoPanel-status').text(_getInfoPanelStatus(info));
    $newInfoPanel.find('.infoPanel-signatureStatus').text(_getInfoPanelSignatureStatus(info));
  }

  // show/hide preview panel & resize the dialog accordingly
  var showPreviewPanel = isStructuredDocument && !isVersionView;
  $newInfoPanel.find('div.recordInfoRightPanel').toggle(showPreviewPanel);
  if (showPreviewPanel) {
    $newInfoPanel.find('.strucDocPreview').load("/workspace/editor/structuredDocument/ajax/preview/" + info.id,
      function (response, status, xhr) {
        if (status == "error") {
          var msg = RS.msg("legacyjs.workspace.recordInfoPanel.previewLoadError", xhr.status, xhr.statusText);
          $('.strucDocPreview').html("<p style='font-size:large'>" + msg + "</p>");
        } else {
          // 0.25 scale, so reduce height of parent container
          var $previewContainer = $newInfoPanel.find('.strucDocPreviewContainer');
          var orgHeight = $previewContainer.height();
          $previewContainer.height(orgHeight * 0.25 + 1);
        }
      });
  }
  var dialogWidth = showPreviewPanel ? 600 : 350;
  $('#recordInfoDialog').dialog("option", "width", dialogWidth);

  $newInfoPanel.find('tr.infoPanelTemplateFormName').toggle(isStructuredDocument);
  $newInfoPanel.find('tr.infoPanelTemplateFormId').toggle(isStructuredDocument);
  $newInfoPanel.find('tr.infoPanelTemplateID').toggle(isStructuredDocument);
  if (isStructuredDocument) {
    $newInfoPanel.find('.infoPanel-templateFormName').text(info.templateFormName);
    $newInfoPanel.find('.infoPanel-templateFormID')
      .attr('href', '/globalId/' + info.templateFormId.idString)
      .text(info.templateFormId.idString);
    if (info.templateOid != null) {
      $newInfoPanel.find('.infoPanel-templateID')
        .attr('href', '/globalId/' + info.templateOid)
        .text(info.templateName);
    } else {
      $newInfoPanel.find('.infoPanelTemplateID').hide();
    }
  }

  var $captionViewDiv = $newInfoPanel.find('.infoPanelCaptionViewDiv');
  $captionViewDiv.show();
  $captionViewDiv.attr('data-id', info.id);
  $captionViewDiv.find('.infoPanel-caption').text(info.description);

  var $captionEditDiv = $newInfoPanel.find('.infoPanelCaptionEditDiv');
  $captionEditDiv.hide();

  // only owner can edit the caption (RSPAC-851)
  if (isOwnedByCurrentUser && !isForm && !isRevisionView && !isVersionView) {
    $captionViewDiv.find('.infoLabelCell').wrap('<a></a>');
    $captionViewDiv.css('cursor', 'pointer').click(function () {
      toggleCaptionEditMode();
    });
    $captionEditDiv.find('.infoPanel-captionTextArea').val(info.description);

    var toggleCaptionEditMode = function () {
      $captionViewDiv.toggle();
      $captionEditDiv.toggle();
      $captionEditDiv.find('.infoPanel-captionTextArea').focus();
      return false;
    };

    var lastCaption = info.description;
    $captionEditDiv.find('.infoPanelCancelCaptionBtn').click(function () {
      $captionEditDiv.find('.infoPanel-captionTextArea').val(lastCaption);
      toggleCaptionEditMode();
      return false;
    });
    $captionEditDiv.find('.infoPanelSaveCaptionBtn').click(function () {
      var newCaption = $captionEditDiv.find('.infoPanel-captionTextArea').val();
      var recordSavePromise = _saveRecordDescription(info.id, newCaption);
      recordSavePromise.done(function (result) {
        if (result.data) {
          info.description = lastCaption = newCaption;
          $captionViewDiv.find('.infoPanel-caption').text(newCaption);
          $captionEditDiv.find('.infoPanel-captionTextArea').val(newCaption);
          toggleCaptionEditMode();
          if (typeof updatePhotoswipeImageDescription != undefined) {
              updatePhotoswipeImageDescription(info.id, newCaption);
          }
        }
      });
      return false;
    });
  }

  if (isGalleryFile) {
    $newInfoPanel.find('.infoPanel-fileVersion').text(info.version);
    $newInfoPanel.find('.infoPanel-fileSize').text(RS.humanFileSize(info.size));
    $newInfoPanel.find('.infoPanelHistoricalVersionNotice').toggle(isRevisionView);

    var pdfPreviewSupported = RS.isPdfPreviewSupported(info.extension);
    $newInfoPanel.find('.recordViewBtn').toggle(pdfPreviewSupported);

    if (pdfPreviewSupported) {
      $newInfoPanel.find('.recordViewBtn').click(function () {
        RS.openWithPdfViewer(info.id, info.revision, info.name, info.extension);
      });
    }

    if (info.originalImageOid) {
    	$newInfoPanel.find('.infoPanel-originalImageIdLink')
    		.attr('href', '/globalId/' + info.originalImageOid.idString)
    		.text(info.originalImageOid.idString);
    }

    toggleWopiActionElem(info.oid.idString, info.extension, isRevisionView, $newInfoPanel.find('.recordViewInMsOnlineBtn'));

    $newInfoPanel.find('.recordDownloadBtn').off('click').click(function () {
      var revisionSuffix = isRevisionView ? "?revision=" + info.revision : "";
      window.open('/Streamfile/' + info.id + revisionSuffix);
    });
    $newInfoPanel.find('.linkedRecordsForAttachments').html('');
    $newInfoPanel.find('.recordShowLinkedDocs').toggle(!isRevisionView);
    $newInfoPanel.find('.recordShowLinkedDocs').off('click').click(function () {
      $.get('/gallery/ajax/getLinkedDocuments/' + info.id, function (resp) {
        if (resp.data) {
          var data = resp.data;
          var templateData = { items: data, isEmpty: (data.length == 0 ? true : false) };
          $('.linkedRecordsForAttachments').html('');
          var linkedRecordsTemplate = $('#linkedRecordsTemplate').html();
          var linkedRecordsHtml = Mustache.render(linkedRecordsTemplate, templateData);
          RS.appendMustacheGeneratedHtmlToElement(linkedRecordsHtml, '.linkedRecordsForAttachments');
          $('.linkedRecordsForAttachments').show();
        }
      });
    });

    var canUpload = !isRevisionView && (isOwnedByCurrentUser || (info.status === 'VIEW_MODE'));
    var $inputField = $(".fileReplaceInput, .galleryFileReplace").first();
    var showUploadBtn = canUpload && ($inputField.length > 0) && !isPdfExport && !isDMP;
    $newInfoPanel.find('.recordReplaceBtn').toggle(showUploadBtn);
    $newInfoPanel.find('.recordReplaceBtn').off('click').click(function () {
      // use first matched replace file input field
      $inputField.attr('accept', '.' + info.extension)
        .attr('mediaFileId', info.id)
        .trigger('click');
    });
  }

  var showInternalLinks = isStructuredDocument || isNotebook || isFolder;
  $newInfoPanel.find('.infoPanelInternalLinksDiv').toggle(showInternalLinks);
  if (showInternalLinks) {
    var recordTypeNameToDisplay = isStructuredDocument ? 'document' : (isFolder ? 'folder' : 'notebook');
    _setInfoPanelInternalLinksHtml($newInfoPanel.find('.infoPanelInternalLinksDiv'), info, recordTypeNameToDisplay);
  }

  // Inventory items can link to this ELN document/notebook via an Inventory Link
  // extra-field; surface those back-references in a distinct section.
  var showRelatedInventoryItems = isStructuredDocument || isNotebook;
  $newInfoPanel.find('.infoPanelRelatedInventoryItemsDiv').toggle(showRelatedInventoryItems);
  if (showRelatedInventoryItems) {
    _setInfoPanelRelatedInventoryItemsHtml(
      $newInfoPanel.find('.infoPanelRelatedInventoryItemsDiv'), info,
      isStructuredDocument ? 'document' : 'notebook');
  }

  var showSharingStatus = isStructuredDocument || isNotebook;
  $newInfoPanel.find('.infoPanelSharingDiv').toggle(showSharingStatus);
  if (showSharingStatus) {
    $newInfoPanel.find('.infoPanelSharingDiv').html(_getInfoPanelSharingHtml(info, isNotebook));
    makePublicLink(info, isNotebook,$newInfoPanel);
  }


  if(isDNAfile && RS.loadUserSetting('snapgene-available') === "true") {
    $newInfoPanel.find('.recordViewBtn').toggle(true);

    $newInfoPanel.find('.recordViewBtn').click(function () {
      // tell React to open the dialog
      var event = new CustomEvent('open-dna-info', {'detail': info.id});
      document.dispatchEvent(event);

      // close the info dialog
      $('#recordInfoDialog').dialog('close');
    });
  }

  if(isImageFile && inGallery) {
    $('body').off('click', '.recordEditBtn');
    $newInfoPanel.find('.recordEditBtn').toggle(true);

    $('body').on("click", ".recordEditBtn", function (e) {
      e.preventDefault();
      var event = new CustomEvent('open-image-editor', { 'detail': {
        recordid: info.id
      }});
      document.dispatchEvent(event);
    });
  }

  return $newInfoPanel;
}

function openRecordInfoDialog(recordId) {
  var url = "/workspace/getRecordInformation?recordId=" + recordId;
  $.get(url, function (result) {
    var val = result.data;
    if (val !== null) {
      var $recordInfoPanel = generate$RecordInfoPanel(val);
      $('#recordInfoDialog').find('.recordInfoPanel').replaceWith($recordInfoPanel);
      $('#recordInfoDialog').dialog('open');
      RS.trackEvent("user:open:info_panel:workspace");
    }
  });
}

function _getInfoPanelStatus(info) {
  if (info.status === "VIEW_MODE") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.statusViewableEditable");
  }
  if (info.status === "EDIT_MODE") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.statusCurrentlyEditedByYou");
  }
  if (info.status === "CANNOT_EDIT_OTHER_EDITING") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.statusEditInProgressBy", info.currentEditor);
  }
  if (info.status === "CANNOT_EDIT_NO_PERMISSION") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.statusViewable");
  }
  if (info.status === "CAN_NEVER_EDIT") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.statusReadOnly");
  }
}

function _getInfoPanelSignatureStatus(info) {
  if (info.signatureStatus === "UNSIGNED") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.signatureStatusUnsigned");
  } else if (info.signatureStatus === "SIGNED_AND_LOCKED") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.signatureStatusSigned");
  } else if (info.signatureStatus === "AWAITING_WITNESS") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.signatureStatusAwaitingWitness");
  } else if (info.signatureStatus === "WITNESSED") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.signatureStatusSignedAndWitnessed");
  } else if (info.signatureStatus === "UNSIGNABLE") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.signatureStatusUnsignable");
  } else if (info.signatureStatus === "SIGNED_AND_LOCKED_WITNESSES_DECLINED") {
    return RS.msg("legacyjs.workspace.recordInfoPanel.signatureStatusAllWitnessesDeclined");
  } else {
    console.log("Unexpected signature status");
    return RS.msg("legacyjs.workspace.recordInfoPanel.signatureStatusUnknown");
  }
}

function _setInfoPanelGlobalIdRow($infoPanelGlobalIdRow, showRowFlag, oid) {
  $infoPanelGlobalIdRow.toggle(showRowFlag);
  if (showRowFlag) {
    $infoPanelGlobalIdRow.find('.infoPanel-objectIdLink').attr('href', '/globalId/' + oid.idString);
    $infoPanelGlobalIdRow.find('.infoPanel-objectId').text(oid.idString);
    $infoPanelGlobalIdRow.find('.infoPanel-objectIdDownloadIcon').toggle(oid.idString.startsWith('GL'));
  }
}

function _setInfoPanelInternalLinksHtml($internalLinksDiv, info, recordTypeNameToDisplay) {
  var escapedRecordTypeName = RS.escapeHtml(recordTypeNameToDisplay);
  if (!info.linkedByCount) {
    $internalLinksDiv.empty().append(RS.msg("legacyjs.workspace.recordInfoPanel.noLinksToRecord", escapedRecordTypeName));
    return;
  }

  var internalLinksHtml =
    RS.msg(
      "legacyjs.workspace.recordInfoPanel.linkedByCount",
      escapedRecordTypeName,
      info.linkedByCount
    ) +
    " <br /><a href='#' class='showLinkedDocs'>" +
    RS.msg("legacyjs.workspace.recordInfoPanel.showLinkedDocs", info.linkedByCount) +
    "</a>";

  $internalLinksDiv.empty().append(internalLinksHtml);
  $internalLinksDiv.find('.showLinkedDocs').click(function () {
    var linkedByReq = $.get("/workspace/getLinkedByRecords?targetRecordId=" + info.id);
    linkedByReq.done(function (resp) {
      if (resp.data) {
        var fullLinkedByHtml = RS.msg("legacyjs.workspace.recordInfoPanel.linkedByHeader", escapedRecordTypeName) + " <br/><ul>";
        var privateRecordLinks = Object.create(null);
        $.each(resp.data, function (i, recordInfo) {
          var owner = recordInfo.ownerFullName;
          if (recordInfo.id) {
            var globalId = recordInfo.oid.idString;
            var name = recordInfo.name;
            var encodedGlobalId = encodeURIComponent(globalId).replace(/'/g, "%27");
            fullLinkedByHtml +=
              "<li><a href='/globalId/" +
              encodedGlobalId +
              "'>" +
              RS.escapeHtml(globalId) +
              "</a>: " +
              RS.escapeHtml(name) +
              "</li>";
          } else {
            var count = privateRecordLinks[owner] || 0;
            privateRecordLinks[owner] = ++count;
          }
        });
        $.each(privateRecordLinks, function (owner, count) {
          fullLinkedByHtml += "<li>" + RS.msg("legacyjs.workspace.recordInfoPanel.privateDocsBelongingTo", count, RS.escapeHtml(owner)) + " </li>";
        });
        fullLinkedByHtml += "</ul>";
        $internalLinksDiv.empty().append(fullLinkedByHtml);
        return false;
      }
    });
    return false;
  });
}
/**
 * Renders the "Related inventory items" section: the Inventory items (samples,
 * subsamples, containers, instruments) whose Link extra-field points at this ELN
 * record. Reads the shared, permission-filtered back-reference endpoint, the same
 * one used by the React gallery info panel. The relation is shown with the
 * Inventory item as the subject (e.g. "SA1 IsPartOf this document").
 */
function _setInfoPanelRelatedInventoryItemsHtml($div, info, recordTypeNameToDisplay) {
  $div.empty().append(
    "<strong>" + RS.msg("legacyjs.workspace.recordInfoPanel.relatedInventoryItemsTitle") + "</strong><br/>"
    + "<span class='relatedInventoryItemsContent'>" + RS.msg("legacyjs.workspace.recordInfoPanel.loading") + "</span>");
  var $content = $div.find('.relatedInventoryItemsContent');

  var req = $.get("/workspace/getReferencingInventoryItems/" + encodeURIComponent(info.oid.idString));
  req.done(function (resp) {
    var items = (resp && resp.referencingItems) || [];
    if (!items.length) {
      $content.text(RS.msg("legacyjs.workspace.recordInfoPanel.noInventoryItemsLinkToRecord", recordTypeNameToDisplay));
      return;
    }
    var html = "<ul>";
    $.each(items, function (i, item) {
      var globalId = item.sourceGlobalId;
      var name = item.sourceName || "";
      var relation = item.relationType || "";
      html += "<li><a href='/globalId/" + encodeURIComponent(globalId) + "'>" + RS.escapeHtml(globalId) + "</a>: "
        + RS.escapeHtml(name)
        + (relation ? " <em>(" + RS.escapeHtml(relation) + ")</em>" : "")
        + "</li>";
    });
    html += "</ul>";
    $content.html(html);
  });
  req.fail(function () {
    $content.text(RS.msg("legacyjs.workspace.recordInfoPanel.relatedInventoryItemsLoadFailed"));
  });
}

const makePublicLink = (info, isNotebook, panel) => {
  const publicExistsRequest = $.get('/public/publishedView/publiclink?globalId=' + info.oid.idString);
  publicExistsRequest.done(function (resp) {
    if (resp) {
      let linkToUnpublishedEntryInPublishedNotebook = false;
      //link to an entry in a published notebook, the entry itself was NOT published
      if(resp.indexOf("initialRecordToDisplay")!==-1){
        linkToUnpublishedEntryInPublishedNotebook = true;
      }
      if (linkToUnpublishedEntryInPublishedNotebook) {
        panel.find('.publicLinksDiv').html(RS.msg("legacyjs.workspace.recordInfoPanel.linkedInPublishedNotebook", window.location.origin + '/public/publishedView/notebook/' + resp)).toggle(resp);
      } else if (isNotebook) {
        panel.find('.publicLinksDiv').html(RS.msg("legacyjs.workspace.recordInfoPanel.notebookPublished", window.location.origin + '/public/publishedView/notebook/' + resp)).toggle(resp);
      } else {
        panel.find('.publicLinksDiv').html(RS.msg("legacyjs.workspace.recordInfoPanel.documentPublished", window.location.origin + '/public/publishedView/document/' + resp)).toggle(resp);
      }
    } else {
      panel.find('.publicLinksDiv').html(RS.msg("legacyjs.workspace.recordInfoPanel.recordNotPublished", isNotebook ? 'notebook' : 'document')).toggle(true);
    }
  });
}

function _getInfoPanelSharingHtml(info, isNotebook) {
  var sharingHtml;
  if (!info.shared && !info.implicitlyShared) {
    sharingHtml = RS.msg("legacyjs.workspace.recordInfoPanel.notShared", isNotebook ? "notebook" : "document");
  } else {
    sharingHtml = RS.msg("legacyjs.workspace.recordInfoPanel.sharedHeader", isNotebook ? "notebook" : "document") + " <ul>";
    if (info.sharedGroupsAndAccess) {
      $.each(info.sharedGroupsAndAccess, function (group, access) {
        sharingHtml += "<li>" + RS.msg("legacyjs.workspace.recordInfoPanel.sharedWithGroup", RS.escapeHtml(group), access.toLowerCase()) + "</li>";
      });
    }
    if (info.sharedUsersAndAccess) {
      $.each(info.sharedUsersAndAccess, function (user, access) {
        sharingHtml += "<li>" + RS.msg("legacyjs.workspace.recordInfoPanel.sharedWithUser", RS.escapeHtml(user), access.toLowerCase()) + "</li>";
      });
    }
    if (info.sharedNotebooksAndOwners) {
      $.each(info.sharedNotebooksAndOwners, function (nbGlobalId, owner) {
        sharingHtml += "<li>" + RS.msg("legacyjs.workspace.recordInfoPanel.sharedIntoNotebook", nbGlobalId, RS.escapeHtml(owner)) + "</li>";
      });
    }
    if (info.implicitShares) {
        $.each(info.implicitShares, function (nbGlobalId, owner) {
          sharingHtml += "<li>" + RS.msg("legacyjs.workspace.recordInfoPanel.sharedImplicitly", nbGlobalId, RS.escapeHtml(owner)) + "</li>";
        });
      }
    sharingHtml += "</ul>";
  }
  return sharingHtml;
}

/**
 * Posts new record description (caption) to the server.
 * Returns ajax promise, so extra actions can be attached.
 */
function _saveRecordDescription(recordId, description) {

  var data = {
    recordId: recordId,
    description: description
  };

  RS.blockPage(RS.msg("legacyjs.workspace.recordInfoPanel.updatingRecordInformation"));
  var jqxhr = $.post("/workspace/editor/structuredDocument/ajax/description", data);
  jqxhr.always(function () {
    RS.unblockPage();
  });
  jqxhr.fail(function () {
    RS.ajaxFailed(RS.msg("legacyjs.workspace.recordInfoPanel.updatingInfoAction"), true, jqxhr);
  });

  return jqxhr;
}

var recordInfoDialogInitialised = false;

function initRecordInfoDialog() {
  if (recordInfoDialogInitialised) {
    return;
  }
  RS.switchToBootstrapButton();
  $('#recordInfoDialog').dialog({
    title: RS.msg("legacyjs.workspace.recordInfoPanel.dialogTitle"),
    autoOpen: false,
    modal: true,
    open: function () {
      $('.ui-dialog-buttonset button').focus();
    },
    buttons: {
      [RS.msg("legacyjs.workspace.recordInfoPanel.okButton")]: function () {
        $(this).dialog("close");
      }
    }
  });
  RS.switchToJQueryUIButton();
  recordInfoDialogInitialised = true;
}

var msOfficeSupportedExts = {};

var collaboraSupportedExts = {};

var wopiActionElemsToCheck = [];

function toggleWopiActionElem(globalId, extension, isRevisionView, $actionElem) {
  var lowercaseExt = extension ? extension.toLowerCase() : "";
  var supported = false;
  if (msOfficePreviewAvailable && !isRevisionView) {
    supported = addMsOfficeActionElement(globalId, lowercaseExt, isRevisionView, $actionElem);
  } else if (collaboraPreviewAvailable && !isRevisionView) {
    supported = addCollaboraActionElement(globalId, lowercaseExt, isRevisionView, $actionElem);
  }
  $actionElem.toggle(supported);
}

function addMsOfficeActionElement(globalId, lowercaseExt, isRevisionView, $actionElem) {
  var supported = false;
  if ($.isEmptyObject(msOfficeSupportedExts)) {
    // store for the time when msOfficeSupportedExts is populated
    wopiActionElemsToCheck.push([globalId, lowercaseExt, isRevisionView, $actionElem])
    supported = false;
  }
  var appForExtension = msOfficeSupportedExts[lowercaseExt];
  if (appForExtension) {
    var btnHtml = `<img src="${appForExtension.favIconUrl}" alt="${appForExtension.name}" height="18"> ${RS.msg("legacyjs.workspace.recordInfoPanel.openInApp", appForExtension.name)}`;
    $actionElem.html(btnHtml)
      .off('click').click(function () {
        RS.openInNewWindow('/officeOnline/' + globalId + '/view');
      });
    supported = true;
  }
  return supported;
}

function addCollaboraActionElement(globalId, lowercaseExt, isRevisionView, $actionElem) {
  var supported = false;
  if ($.isEmptyObject(collaboraSupportedExts)) {
    // store for the time when collaboraSupportedExts is populated
    wopiActionElemsToCheck.push([globalId, lowercaseExt, isRevisionView, $actionElem])
    supported = false;
  }
  var appForExtension = collaboraSupportedExts[lowercaseExt];
  if (appForExtension) {
    // Collabora doesnt provide a favicon for some extension types so if this is the case
  // set to default image.
  var iconUrl = appForExtension.favIconUrl
  if(iconUrl === null) {
    iconUrl = RS.getIconPathForExtension(lowercaseExt);
  }
    var btnHtml = `<img src="${iconUrl}" alt="${appForExtension.name}" height="18"> ${RS.msg("legacyjs.workspace.recordInfoPanel.openInApp", "Collabora")}`;
    $actionElem.html(btnHtml)
      .off('click').click(function () {
        RS.openInNewWindow('/collaboraOnline/' + globalId + '/edit');
      });
      supported = true;
  }
  return supported;
}

function initOfficePreviews() {
  var msOfficeExts = RS.loadSessionSetting('msOfficeSupportedExts');
  if (!RS.objIsEmpty(msOfficeExts)) {
    msOfficeSupportedExts = JSON.parse(msOfficeExts);
    _rerunToggleWopiActionElem();
    return;
  }

  var jqxhr = $.get('/officeOnline/supportedExts');
  jqxhr.done(function (data) {
    msOfficeSupportedExts = data;
    RS.saveSessionSetting('msOfficeSupportedExts', JSON.stringify(data));
    console.log('msOfficeSupportedExts added to sessionStorage');
    _rerunToggleWopiActionElem();
  });
  jqxhr.fail(function () {
    RS.ajaxFailed(RS.msg("legacyjs.workspace.recordInfoPanel.retrievingMsOfficeExtsAction"), false, jqxhr);
  });
}

function initCollaboraPreviews() {
  var collaboraExts = RS.loadSessionSetting('collaboraSupportedExts');
  if (!RS.objIsEmpty(collaboraExts)) {
    collaboraSupportedExts = JSON.parse(collaboraExts);
    _rerunToggleWopiActionElem();
    return;
  }

  var jqxhr = $.get('/collaboraOnline/supportedExts');
  jqxhr.done(function (data) {
    collaboraSupportedExts = data;
    RS.saveSessionSetting('collaboraSupportedExts', JSON.stringify(data));
    console.log('collaboraSupportedExts added to sessionStorage');
    _rerunToggleWopiActionElem();
  });
  jqxhr.fail(function () {
    RS.ajaxFailed(RS.msg("legacyjs.workspace.recordInfoPanel.retrievingCollaboraExtsAction"), false, jqxhr);
  });
}

function _rerunToggleWopiActionElem() {
  $.each(wopiActionElemsToCheck, function (i, args) {
    toggleWopiActionElem.apply(null, args);
  });
}

$(document).ready(function () {
  initRecordInfoDialog();
  var requests = {
    snapgene_available: {
      url: '/deploymentproperties/ajax/property',
      type: 'GET',
      data: { name: 'snapgene.available' }
    },
    snapgene_running: {
      url: '/molbiol/dna/serviceStatus',
      type: 'GET'
    }
  };

  var reqPromises = Object.keys(requests).map(function (key) {
    return $.ajax({
      url: requests[key].url,
      type: requests[key].type,
      data: requests[key].data
    });
  });

  Promise.all(reqPromises).then(function (res) {
    var isAvailable = false;
    res.map(function (item) {
      isAvailable = isAvailable || item == "ALLOWED";
    });

    if (isAvailable) {
      RS.saveUserSetting('snapgene-available', "true");
      $('.snapGenePanel .previewActionLink').removeClass('hidden');
    } else {
      RS.saveUserSetting('snapgene-available', "false");
    }
  }).catch(function () {
    RS.saveUserSetting('snapgene-available', "false");
  });

  if (collaboraPreviewAvailable) {
    initCollaboraPreviews();
  } else if (msOfficePreviewAvailable) {
    initOfficePreviews();
  }

});
