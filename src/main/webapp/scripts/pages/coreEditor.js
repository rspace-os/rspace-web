/* jshint maxerr: 100 */

/* coreEditor.js -> Common functions in structuredDocument and notebookEditor */
var fadeTime = 400;
var recordName = '';
var headerInfoFeaturesSetUp = false;
var tagsEditMode = false;
var nameEditMode = false;
var recordTags = "";

var nameFeatureSelectors = {
  editorSelector: "#recordNameInHeaderEditor", // textarea holding name for editing
  fieldSelector: "#recordNameInHeader", // field holding name when not editing
  formSelector: "#inlineRenameRecordForm"
};
var nameForbiddenCharacters = ['/', '\\'];

const getPublicRoute = () => {
  const publicView = $("#public_document_view").length > 0;
  const publicRoute = (publicView ? '/public/publicView' : '');
  return publicRoute;
}

function displayStatus(state) {
  $(".state").hide();
  if (state == "VIEW_MODE") {
    $('#viewGreenStatus').show();
  } else if (state == "EDIT_MODE") {
    $('#editingStatus').show();
  } else if (state == "CANNOT_EDIT_OTHER_EDITING") {
    $('#viewAmberStatus').show();
  } else if (editable == "CANNOT_EDIT_NO_PERMISSION") {
    $('#viewAmberStatusReadPermission').show();
  } else if (state == "CAN_NEVER_EDIT") {
    $('#viewRedStatus').show();
  } else if (state == "SIGNED_AND_LOCKED") {
    $('#signedStatus').show();
  } else if (state == "AWAITING_WITNESS") {
    $('#signedAwaitingWitnessStatus').show();
  } else if (state == "SIGNED_AND_LOCKED_WITNESSES_DECLINED") {
    $('#signedWitnessesDeclinedStatus').show();
  } else if (state == "WITNESSED") {
    $('#witnessedStatus').show();
  }
}

function showCommentDialog(element, isNotebookEditor) {
  var commentId = $(element).attr('id');
  var revisionId = $(element).attr("data-rsrevision");
  var parentId;
  if (isNotebookEditor) {
    parentId = selectedNotebookEntryId;
  } else {
    parentId = $(element).closest(".isResizable").attr("id");
    parentId = parentId.split("_")[2];
  }
  const publicView = $("#public_document_view").length > 0;
  if (!publicView && (editable === 'EDIT_MODE' || editable === 'VIEW_MODE')) {
    $('.commentRow').empty();
    $('#commentText').val('');
    $('#comment-editor').data('id', commentId).data('fieldId', parentId).dialog('open');
    $('#comment-loading').show();
    return false;
  } else {
    $('.commentRow').empty();
    $('#comment-view').data('id', commentId).data('revision', revisionId).dialog('open');
    $('#comment-loading').show();
    return false;
  }
}

function initCommentDialog() {

  $(document).ready(function () {
    RS.switchToBootstrapButton();
    $("#comment-editor").dialog({
      title: "Comments",
      resizable: false,
      autoOpen: false,
      height: 500,
      width: 550,
      modal: true,
      buttons: {
        Cancel: function () {
          apprise("Please confirm that you wish to cancel - the changes you made won't be saved.", { confirm: true, textOk: "Yes, cancel", textCancel: "No, don't" }, function () { $("#comment-editor").dialog('close'); });
        },
        'Save': function () {
          $.ajaxSetup({ async: false });
          var commentId = $(this).data('id');
          var fieldId = $(this).data('fieldId');
          var comment = $('#commentText').val();
          var data = {
            fieldId: fieldId,
            commentId: commentId,
            comment: comment
          };
          var closeDialog = false;
          //Add a new comment content
          var jqxhr = $.post(createURL('/workspace/editor/structuredDocument/addComment'), data,
            function (data) {
              if (data.data !== null) {
                closeDialog = true;
              } else {
                apprise("Errors : " + getValidationErrorString(data.errorMsg));
              }
            });
          jqxhr.fail(function () {
            RS.ajaxFailed("Saving comment", false, jqxhr);
          });

          if (closeDialog) {
            $.ajaxSetup({ async: true });
            $(this).dialog('close');
          }
        }
      },
      open: function () {

        var commentId = $(this).data('id');
        var revision = $(this).data('revision');
        var data = {
          commentId: commentId,
          revision: revision
        };
        //Gets previous comments to the comment.
        var jqxhr = $.get(createURL(getPublicRoute() + '/workspace/editor/structuredDocument/getComments'), data,
          function (result) {
            $.each(result, function (i, val) {
              var commentRow = createCommentRow(val);
              //$('#tablecomments > tbody:last').append(commentRow);
              $('#tablecomments').append(commentRow);
            });
            $('#comment-loading').hide();
            $('#comments').show();
          });
        jqxhr.fail(function () {
          RS.ajaxFailed("Getting comments", false, jqxhr);
        });
      },
      close: function () { }
    });
    RS.switchToJQueryUIButton();
  });
}

function initCommentViewDialog() {

  $("#comment-view").dialog({
    title: "Comments",
    resizable: false,
    autoOpen: false,
    height: 500,
    width: 550,
    modal: true,
    buttons: {
      Close: function () {
        $(this).dialog('close');
      }
    },
    open: function () {

      var commentId = $(this).data('id');
      var revision = $(this).data('revision');
      var data = {
        commentId: commentId,
        revision: revision
      };
      //Gets previous comments to the comment.
      var jqxhr = $.get(createURL(getPublicRoute() + '/workspace/editor/structuredDocument/getComments'), data,
        function (result) {
          $.each(result, function (i, val) {
            var commentRow = createCommentRow(val);
            //$('#tablecomments > tbody:last').append(commentRow);
            $('#tablecommentsView').append(commentRow);
          });
          $('#comment-loadingView').hide();
          $('#commentsView').show();
        });
      jqxhr.fail(function () {
        RS.ajaxFailed("Getting comments", false, jqxhr);
      });
    },
    close: function () { }
  });
}

function createCommentRow(commentData) {
  var result = '<tr class="commentRow"><td style="padding-bottom: 0px; padding-top: 0px;"><table><tr><td style="color:black; font-weight: bold; background-color:#CCC; padding: 12px;">Comment by ' + commentData.lastUpdater + ' at ' + commentData.formatDate + '</td></tr>' +
    '<tr><td style="color:black; background-color:white; width:500px;">' + RS.escapeHtml(commentData.itemContent) + '</td></tr></table></td></tr>';
  return result;
}

/**
 * called after clicking on document 'i' icon, or on attachment links
 *
 * id - record id
 * revision - (optional) revision of the record in question
 * version - (optional) version of the record in question
 * $infoArea - (optional) jquery element containing '.recordInfoPanel' div where get info data is added
 *
 * @returns ajax promise (dialog is only opened after successful getRecordInformation call)
 */
function showRecordInfo(id, revision, version, $infoArea) {
  var url = "/workspace/getRecordInformation?recordId=" + id;
  if (revision) {
    url += "&revision=" + revision;
  } else if (version) {
    url += "&version=" + version;
  }
  var isInfoDialog = !$infoArea;
  if (isInfoDialog) {
    $infoArea = $('#recordInfoDialog');
  }
  var jqxhr = _loadRSpaceElementInfo(id, version, url, $infoArea, isInfoDialog, generate$RecordInfoPanel);
  return jqxhr;
}

function _loadRSpaceElementInfo(id, version, url, $infoArea, isInfoDialog, infoPanelConstructor) {
  if (!id) {
    // RSPAC-1648 don't call server if id not found
    console.warn("skipping showRecordInfo request as no id was passed");
    return $.Deferred().reject().promise();
  }

  var jqxhr = $.get(url, function (result) {
    var info = result.data;
    if (info !== null) {
      var $recordInfoPanel = infoPanelConstructor(info, !!version);
      $infoArea.find('.recordInfoPanel').replaceWith($recordInfoPanel);
      if (isInfoDialog) {
        $infoArea.dialog('open');
      }
    }
  });
  jqxhr.fail(function () {
    if (isInfoDialog) {
      RS.ajaxFailed("Retrieving record information", false, jqxhr);
    } else {
      // RSPAC-1648 show error from attachment loading request as toast
      RS.ajaxFailed("Retrieving attachment information", false, jqxhr, true);
    }
  });
  return jqxhr;
}

function showLinkedRecordInfo($link) {
  // may be an absolute URL that points to record outside current instance
  if (!$link.attr('href').startsWith('/')) {
    showExternalLinkedRecordInfo($link);
    return;
  }
  // otherwise open standard record info dialog for the id
  var versionId = RS.getVersionIdFromGlobalId($link.data('globalid'));
  showRecordInfo($link.attr('id'), null, versionId);
}

function showExternalLinkedRecordInfo($link) {
  initExternalLinkedRecordInfoDialog();
  updateExternalLinkedRecordInfoDialog($link);
  $('#externalLinkedRecordInfoDialog').dialog('open');
}

var externalLinkedRecordInfoDialogInitialised = false;

function initExternalLinkedRecordInfoDialog() {
  if (externalLinkedRecordInfoDialogInitialised) {
    return;
  }
  $(document).ready(function () {
    $('#externalLinkedRecordInfoDialog').dialog({
      title: 'External Link Info',
      autoOpen: false,
      modal: true,
      minWidth: 400,
      open: function () {
        $('.ui-dialog-buttonset button').focus();
      },
      buttons: {
        "OK": function () {
          $(this).dialog("close");
        }
      }
    });
  });
  externalLinkedRecordInfoDialogInitialised = true;
}

function updateExternalLinkedRecordInfoDialog($link) {
  var $infoPanelTable = $('#externalLinkedRecordInfoDialog').find('.infoPanelTable');

  $infoPanelTable.find('.infoPanel-name').text($link.data('name'));
  $infoPanelTable.find('.infoPanel-globalId').text($link.data('globalid'));

  var href = $link.attr('href');
  if (href && href.indexOf('/globalId/') > 0) {
    var serverUrl = href.substring(0, href.indexOf('/globalId/'));
    $infoPanelTable.find('.infoPanel-server').text(serverUrl);
  }
  $infoPanelTable.find('.infoPanel-internalLinkHref').attr('href', href).text(href);
}

function showBoxLinkInfo($boxVersionLinkDiv) {
  var $boxLink = $boxVersionLinkDiv.find('.attachmentLinked');
  var $boxVersion = $boxVersionLinkDiv.find('.boxVersion');
  var $newInfoPanel = $('#boxVersionedLinkInfoPanelTemplate > .recordInfoPanel').clone();

  $newInfoPanel.find('.boxInfoPanel-name').text($boxLink.data('name'));

  var description = $boxLink.data('description');
  $newInfoPanel.find('.boxInfoPanel-description').text($boxLink.data('description'));
  $newInfoPanel.find('.boxInfoPanel-description').parents('tr').toggle(description != '');

  var sharedUrl = $boxLink.attr('href');
  $newInfoPanel.find('.boxInfoPanel-sharedLinkUrl').attr('href', sharedUrl).text(sharedUrl);
  $newInfoPanel.find('.boxInfoPanel-owner').text($boxLink.data('owner'));
  $newInfoPanel.find('.boxInfoPanel-size').text($boxVersion.data('size') + 'B');

  var createdAt = new Date($boxVersion.data('createdat'));
  $newInfoPanel.find('.boxInfoPanel-createdAt').text(createdAt.toLocaleString());
  $newInfoPanel.find('.boxInfoPanel-versionNumber').text("V" + $boxVersion.data('versionnumber'));
  $newInfoPanel.find('.boxFileDownloadBtn').button();

  $newInfoPanel.find('.boxInfoPanel-sha1').text($boxVersion.data('sha1'));
  $newInfoPanel.find('.boxInfoPanel-id').text($boxLink.data('id'));
  $newInfoPanel.find('.boxInfoPanel-versionID').text($boxVersion.data('versionid'));

  var $infoDialog = $('#recordInfoDialog');
  $infoDialog.find('.recordInfoPanel').replaceWith($newInfoPanel);
  var currentWidth = $infoDialog.dialog('option', 'width');
  if (currentWidth < 550) {
    $infoDialog.dialog('option', 'width', 550);
  }
  $infoDialog.dialog('open');
}

var boxClientId = null;
var boxLinkTypePref = null;

function openAuthorizationDialogForBoxAPI(onSuccess, secToken) {
  var url = "https://account.box.com/api/oauth2/authorize?response_type=code&client_id=" + boxClientId + "&state=" + secToken;
  RS.openOauthAuthorizationWindow(url, '/box/redirect_uri', '#boxAPIconnectionSuccess', onSuccess);
}

function checkBoxAPIAvailableForUser(onSuccess) {
  var jqxhr = $.get('/box/boxApiAvailabilityCheck');
  jqxhr.done(function (result) {
    if (result.data === "OK") {
      onSuccess();
    } else if (result.data && result.data.indexOf("USER_NOT_AUTHORIZED:") === 0) {
      var secToken = result.data.substring("USER_NOT_AUTHORIZED:".length);
      openAuthorizationDialogForBoxAPI(onSuccess, secToken);
    } else if (result.error && result.error.errorMessages[0] === "API_CONFIGURATION_INCORRECT") {
      apprise("There is a problem with RSpace Box configuration for Versioned links. Please contact your System Admin.");
    }
  });
}



/**
 * This function modifies attachment area by adding action links panel.
 * If attachment type supports preview, then icon is replaced with thumbnail.
 */
function updateAttachmentDivs($attachmentDivs) {
  $attachmentDivs.each(function () {
    var $thisDiv = $(this);

    const isAudioVideo = $thisDiv.find('.mediaDiv').length > 0;
    if (isAudioVideo) {
      /* for audio/videos we repackage the content into 'attachmentPanel' div,
       * and leave the rest as it was (av have their own nice player) */
      $thisDiv.children().wrapAll('<div class="attachmentPanel"></div>');

      const id = getAttachmentIdFrom$Div($thisDiv);
      const revision = $thisDiv.find('img.attachmentIcon').data('rsrevision');

      let attachmentInfoDiv$ = $thisDiv.find("div.attachmentInfoDiv");
      attachmentInfoDiv$.html("<img class='infoImg' src='/images/info.svg' style='left:2px;top:-1px'/>");
      attachmentInfoDiv$.on('click', function () {
        showRecordInfo(id, revision);
      });

      let $avPanel = $thisDiv.children('.attachmentPanel');
      $avPanel.data('id', id);
      $avPanel.data('rsrevision', revision);
      $avPanel.data('revision', revision);

      _updateAttachedMediaFileWithLastestVersionDetails(id, revision);
      return;
    }

    var id = getAttachmentIdFrom$Div($thisDiv);
    var revision = $thisDiv.find('a.attachmentLinked').data('rsrevision');
    var $attachmentLink = $thisDiv.find('.attachmentLinked');
    var fileName = $attachmentLink.text();
    var extension = fileName.substring(fileName.lastIndexOf('.') + 1);
    var oldIconSrc = $thisDiv.find(".attachmentIcon").attr('src');
    var pdfPreviewSupported = RS.isPdfPreviewSupported(extension);
    var isSnapGeneFormat = RS.isSnapGeneFormat(extension);
    var isSnapGeneAvailable = RS.loadUserSetting('snapgene-available') === "true";
    var $newAttachmentDiv = null;

    if (pdfPreviewSupported) {
      $newAttachmentDiv = _getNewAttachmentDivWithPreview(id, revision, fileName, extension);
    } else if (isSnapGeneFormat) {
      $newAttachmentDiv = _getSnapGeneTemplate(id, oldIconSrc);
    } else if(msOfficePreviewAvailable || collaboraPreviewAvailable){
      $newAttachmentDiv = _getAttachmentDivWithOnlineOfficeWithoutPreview(id, revision, extension);
    } else {
      $newAttachmentDiv = _getNewAttachmentDivWithoutPreview(id, revision, oldIconSrc);
    }
    $thisDiv.empty().append($newAttachmentDiv);
    _updateAttachedMediaFileWithLastestVersionDetails(id, revision);

    // common actions
    $thisDiv.find('.attachmentName').text(fileName);
    var revisionSuffix = revision ? "?revision=" + revision : "";
    $thisDiv.find('.downloadActionLink').attr('href', getPublicRoute() + '/Streamfile/' + id + revisionSuffix);
    $thisDiv.find('.infoActionLink').click(function () {
      showRecordInfo(id, revision);
      return false;
    });
  });
}

/* adding getinfo icon to internal links */
function updateInternalLinks($internalLinks) {

  $internalLinks.each(function () {
    var $thisLink = $(this);
    if ($thisLink.find('button.linkedRecordInfoAction').length) {
      return;
    }

    // if absolute url, then append 'at <server>' span to the link
    var href = $thisLink.attr('href');
    if (href && !href.startsWith('/')) {
      if (href.indexOf('/globalId/') > 0) {
        var externalServerUrl = href.substring(0, href.indexOf('/globalId/'));
        $thisLink.append(" <span style=\"color: red\">at " + externalServerUrl + "</span>");
      }
    }
    // add info button
    $thisLink.append("<img class='linkedRecordInfoAction' src='/images/info.svg'/>");
    $thisLink.find('.linkedRecordInfoAction').click(function () {
      showLinkedRecordInfo($thisLink);
      return false;
    });
  });
}

function updateEmbedIframes($iframeDivs) {
  $iframeDivs.each(function () {
    var $thisIframeDiv = $(this);
    var iframeSrc = $thisIframeDiv.find('iframe').attr('src');
    if (!iframeSrc) {
      return;
    }

    var brandDesc = '';
    if (iframeSrc.includes('jove')) {
      brandDesc = "<img class='joveLogo' src='/images/jove_logo.png'/>";
    } else if (iframeSrc.includes('youtube')) {
      brandDesc = "YouTube";
    }
    if (brandDesc) {
      $thisIframeDiv.append("<div class='iframeDescDiv'>embedded video from " + brandDesc + " </div>");
    }
  });
}

function getAttachmentIdFrom$Div($attachmentDiv) {
  var id = $attachmentDiv.find("div.attachmentInfoDiv").attr('id');
  if (id && (id.indexOf("attachmentInfoDiv_") === 0)) {
    id = id.substring("attachmentInfoDiv_".length);
  } else {
    /* i found some attachments on pangolin that don't have .attachmentInfoDiv.
     * in case this is a wider problem try retrieving id from .attachmentLinked */
    id = $attachmentDiv.find('a.attachmentLinked').attr('id');
    if (id && (id.indexOf("attachOnText_") === 0)) {
      id = id.substring("attachOnText_".length);
    } else {
      // Handle chemistry file ids in img tag
      id = $attachmentDiv.attr('data-chemfileid');
    }
  }
  return id;
}

function _getNewAttachmentDivWithPreview(id, revision, fileName, extension) {
  var $newAttachmentDiv = $('#previewableAttachmentDivTemplate').find('.attachmentPanel').clone();

  $newAttachmentDiv.data('id', id);
  $newAttachmentDiv.data('revision', revision);
  $newAttachmentDiv.find('.viewActionLink').click(function () {
    RS.openWithPdfViewer(id, revision, fileName, extension);
    return false;
  });

  toggleWopiActionElem('GL' + id, extension, revision != null, $newAttachmentDiv.find('.viewInMsOnlineLink'));

  var $attachmentIcon = $newAttachmentDiv.find('.attachmentIcon');
  $attachmentIcon.attr('src', RS.getIconPathForExtension(extension));

  $newAttachmentDiv.find('.previewExpandBtn').on('click', function () {
    _expandAttachmentPreview(id, revision, fileName, extension, $newAttachmentDiv);
    return false;
  });
  $newAttachmentDiv.find('.previewCollapseBtn').on('click', function () {
    _collapseAttachmentPreview($newAttachmentDiv);
    return false;
  });

  return $newAttachmentDiv;
}

function _getAttachmentDivWithOnlineOfficeWithoutPreview(id, revision, extension) {
  let $newAttachmentDiv = $('#onlineOfficeWithoutPreviewAttachmentDivTemplate').find('.attachmentPanel').clone();
  $newAttachmentDiv.data('id', id);
  $newAttachmentDiv.data('revision', revision);

  toggleWopiActionElem('GL' + id, extension, revision != null, $newAttachmentDiv.find('.viewInMsOnlineLink'));
  let $attachmentIcon = $newAttachmentDiv.find('.attachmentIcon');
  $attachmentIcon.attr('src', RS.getIconPathForExtension(extension));
  return $newAttachmentDiv;
}

//array of latest media info objects
var retrievedMediaFileInfos = [];

function _updateAttachedMediaFileWithLastestVersionDetails(id, revision) {
  var mediaBasicInfoPromise = _retrieveMediaFileBasicInfo(id, revision);
  mediaBasicInfoPromise.done(function (info) {
    if (!retrievedMediaFileInfos.includes(info)) {
      retrievedMediaFileInfos.push(info);
    }
    _updateDisplayedMediaFileDetailsWithLatestInfo(info);
  });
};

/**
 * call to retrieve basic details of media attachment. the network request is not
 * send immediately, but rather with 0 timeout, which let to combine multiple
 * requests into one.
 *
 * id - media record id
 * revision - (optional) revision of the record in question
 *
 * @returns ajax promise that'll be resolved retrieved basic attachment details
 */
function _retrieveMediaFileBasicInfo(id, revision) {
  var deferred = $.Deferred();
  if (!id) {
    // RSPAC-1648 don't call server if id not found
    console.warn("skipping retrieveMediaFileInfo request as no id was passed");
    return deferred.reject().promise();
  }

  var alreadyInQueue = false;
  $.each(mediaFilesRequestsQueue, function (i, queuedRequest) {
    if (id === queuedRequest.id && revision == queuedRequest.revision) {
      deferred = queuedRequest.deferred;
      alreadyInQueue = true;
      return false;
    }
  });
  if (!alreadyInQueue) {
    mediaFilesRequestsQueue.push({ id: id, revision: (revision ? revision : ""), deferred: deferred });
    setTimeout(_retrieveQueriedMediaFileBasicInfos, 0);
  }

  return deferred.promise();
}

// array of { id, revision, deferred } objects
var mediaFilesRequestsQueue = [];
// max number of basic infos requested in a single ajax call
var MEDIA_FILES_PER_REQUESTS_LIMIT = 15;

function _retrieveQueriedMediaFileBasicInfos() {
  if (mediaFilesRequestsQueue.length === 0) {
    return;
  }

  var currentRequestsQueue = [];
  var requestsToProcessNo = Math.min(mediaFilesRequestsQueue.length, MEDIA_FILES_PER_REQUESTS_LIMIT);
  var data = { id: [], revision: [] };
  for (var i = 0; i < requestsToProcessNo; i++) {
    var request = mediaFilesRequestsQueue.shift();
    currentRequestsQueue.push(request);
    data.id.push(request.id);
    data.revision.push(request.revision);
  }
  var jqxhr = $.get(getPublicRoute() + "/gallery/getMediaFileSummaryInfo", data);
  jqxhr.done(function (result) {
    var infos = result.data;
    $.each(currentRequestsQueue, function () {
      var revisionOrNull = this.revision ? this.revision : null;
      var data = infos[this.id + '-' + revisionOrNull];
      if (data) {
        data.revision = revisionOrNull;
        this.deferred.resolve(data);
      } else {
        this.deferred.reject(this.id);
      }
    });
    if (result.error) {
      $.each(result.error.errorMessages, function () {
        RS.confirm("There was a problem with loading attachment details: <br/>" + this, "warning", 5000);
      });
    }
  });
  jqxhr.fail(function () {
    RS.ajaxFailed("Retrieving attachments information", false, jqxhr, true);
    $.each(currentRequestsQueue, function () {
      this.deferred.reject(this.id);
    });
  });

  return jqxhr;
}

function updateMediaFileThumbnails() {
  // mark image links for latest version retrieval
  console.log('refreshing image links');

  $('img.imageDropped').each(function () {
    var $imageThumbnail = $(this);
    var imageId = getImageIdFrom$Img($imageThumbnail);
    var revision = $imageThumbnail.data('rsrevision');

    var $thisImg = $(this);
    if (!$thisImg.parents('.imageViewModePanel').length) {
      var $newImageDiv = _get$NewImageDiv(imageId, revision);
      $newImageDiv.find(".imagePanel").append($thisImg.clone());

      var filename = $thisImg.attr('alt');
      if (filename && filename.startsWith("image ")) {
        filename = filename.substr("image ".length)
      } else if ($thisImg.data('type') == 'annotation') {
        filename = '[annotated image]';
      }
      _updateImageFilename($newImageDiv, filename);

      $newImageDiv.find('.imageInfoBtnDiv').click(function () {
        showImageInfoPopup($thisImg)
      });
      $thisImg.replaceWith($newImageDiv);
    }
    if ($thisImg.is('.inlineImageThumbnail')) {
      _updateAttachedMediaFileWithLastestVersionDetails(imageId, revision);
    }
  });
}

function addImagePreviewToChems($chemImgs) {
  $chemImgs.each(function () {
    var $chemImage = $(this);
    if ($chemImage.data('chemfileid')) {

      var $chemPTag = $chemImage.closest('p');
      var chemFileId = $chemImage.data('chemfileid');
      var revision = $chemImage.data('rsrevision');

      var $newImageDiv = _get$NewChemImageDiv(chemFileId, revision);
      $newImageDiv.find(".imagePanel").append($chemPTag.clone());
      $newImageDiv.find('.imagePanel > p').addClass('chem-attachment-css-wrapper');

      var filename = $chemImage.attr('alt');
      if (filename && filename.startsWith("image ")) {
        filename = filename.substr("image ".length)
      } else if ($chemImage.data('type') == 'annotation') {
        filename = '[annotated chemistry image]';
      }
      _updateImageFilename($newImageDiv, filename);

      $newImageDiv.find('.imageInfoBtnDiv').click(function () {
        showRecordInfo(chemFileId, revision);
      });
      if (!$chemPTag.parents('.imageViewModePanel').length) {
        $chemImage.replaceWith($newImageDiv);
      } else if ($chemPTag.parents('.imageViewModePanel').length) {
        var $imageViewModePanel = $chemPTag.parents('.imageViewModePanel');
        $imageViewModePanel.replaceWith($newImageDiv);

      }
    }
    _updateAttachedMediaFileWithLastestVersionDetails(chemFileId, revision);

  });
}

function _get$NewImageDiv(id, revision) {
  var $newImageDiv = $('#imageViewModeDivTemplate').find('.imageViewModePanel').clone();
  $newImageDiv.data('id', id);
  $newImageDiv.data('revision', revision);
  return $newImageDiv;
}

function _get$NewChemImageDiv(id, revision) {
  var $newImageDiv = $('#chemImageViewModeDivTemplate').find('.imageViewModePanel').clone();
  $newImageDiv.data('id', id);
  $newImageDiv.data('revision', revision);
  return $newImageDiv;
}


function _updateDisplayedMediaFileDetailsWithLatestInfo(info) {
  if (!info) {
    return;
  }

  /* find and update view mode panels of this video, update name */
  var isAudioVideo = info.type === 'Audio' || info.type === 'Video';
  if (isAudioVideo) {
    _updateAudioVideoPanelsWithLatestInfo(info);
  }

  /* find and update view mode panels of this document, update name and possibly preview */
  var isDocument = info.type === 'Document' || info.type === 'Miscellaneous';
  if (isDocument) {
    _updateAttachedDocumentPanelsWithLatestInfo(info);
  }

  /* find thumbnails of this image, update modification date to image date */
  var isImage = info.type === 'Image';
  if (isImage) {
    _updateGalleryImagesWithLatestInfo(info);
  }

  var isChem = info.type === 'Chemistry';
  if (isChem) {
    _updateChemImgsWithLastestInfo(info);
  }

  /* update element details in active tinymce editor, if present */
  _updateActiveEditorContentWithLatestMediaFileInfo(info);
}

function updateActiveEditorContentWithRetrievedMediaFileInfos() {
  $.each(retrievedMediaFileInfos, function () {
    _updateActiveEditorContentWithLatestMediaFileInfo(this);
  });
}

function _updateActiveEditorContentWithLatestMediaFileInfo(info) {
  if (typeof tinymce === "undefined" || !tinymce.activeEditor) {
    return;
  }

  runAfterTinymceActiveEditorInitialized(function () {
    var content = tinymce.activeEditor.getContent();
    var $html = $('<div />', { html: content });

    var isImage = info.type === 'Image';
    var isChemistry = info.type === 'Chemistry'
    var contentUpdated = false;

    if (isImage) {
      /* find the image & update thumbnail modification date to image date */
      $html.find('img.inlineImageThumbnail').each(function () {
        var updated = _updateImageThumbnailWithInfo($(this), info);
        if (updated) {
          contentUpdated = true;
        }
      });
    } else if (isChemistry) {
      $html.find('img.chem').each(function () {
        var updated = _updateChemImageThumbnailWithInfo($(this), info);
        if (updated) {
          var event = new CustomEvent('tinymce-chem-updated', { 'detail': $(this).attr('id') });
          window.parent.document.dispatchEvent(event);
          contentUpdated = true;
        }
      });
    } else {
      /* update document name */
      $html.find('#attachOnText_' + info.id).each(function () {
        var $attachmentLink = $(this);
        if ($attachmentLink.data('rsrevision') == info.revision) {
          if ($attachmentLink.text() !== info.name) {
            $attachmentLink.text(info.name);
            contentUpdated = true;
          }
        }
      });
    }

    if (contentUpdated) {
      var updatedContent = $html.html();
      tinymce.activeEditor.setContent(updatedContent);
      tinymce.activeEditor.setDirty(true);
    }
  });
}

function _updateAudioVideoPanelsWithLatestInfo(info) {
  $('.attachmentPanel').each(function () {
    var $viewModeAttachmentDiv = $(this);
    var currentDivId = $viewModeAttachmentDiv.data('id');
    if (currentDivId != info.id) {
      return;
    }
    var $nameDiv = $viewModeAttachmentDiv.find('p.attachmentP a');
    _setViewModeAttachmentNameAndVersionLabel($nameDiv, info.name, info.version);
  });
}

function _updateAttachedDocumentPanelsWithLatestInfo(info) {
  $('.attachmentPanel').each(function () {
    var $viewModeAttachmentDiv = $(this);
    var currentDivId = $viewModeAttachmentDiv.data('id');
    var currentDivRevision = $viewModeAttachmentDiv.data('revision');
    if (currentDivId != info.id ||currentDivRevision != info.revision) {
      return;
    }

    var $nameDiv = $viewModeAttachmentDiv.find('.attachmentName');
    _setViewModeAttachmentNameAndVersionLabel($nameDiv, info.name, info.version);

    if ($viewModeAttachmentDiv.hasClass('previewableAttachmentPanel')) {
      // refresh thumbnail
      _loadAttachmentThumbnail($viewModeAttachmentDiv, info.id, info.revision, info.thumbnailId);
      // refresh pdf previews that were loaded before
      var $attachmentPreviewPanel = $viewModeAttachmentDiv.find(".attachmentPreviewPanel");
      var previewAlreadyLoaded = $attachmentPreviewPanel.find('.pdfPreviewPanel').data('previewLoaded');
      if (previewAlreadyLoaded) {
        $attachmentPreviewPanel.find('.pdfPreviewPanel').data('previewLoaded', false);
        if ($attachmentPreviewPanel.is(':visible')) {
          RS.loadPdfPreviewIntoDiv(id, revision, info.name, info.extension, $attachmentPreviewPanel);
        }
      }
    }
  });
}

function _updateGalleryImagesWithLatestInfo(info) {
  $('img.inlineImageThumbnail').each(function () {
    _updateImageThumbnailWithInfo($(this), info);
  });
}

/*
 * Checks if $imageThumbnail has the matching id, if it does applies filename and checks
 * if src time differs from provided, if it does the src time is updated.
 *
 * @returns true if image src time is updated
 */
function _updateImageThumbnailWithInfo($imageThumbnail, info) {
  var imageId = getImageIdFrom$Img($imageThumbnail);
  if (imageId != info.id) {
    return false;
  }

  $imgDiv = $imageThumbnail.parents('.imageViewModePanel');
  if ($imgDiv.length) {
    _updateImageFilename($imgDiv, info.name)
  }

  var src = $imageThumbnail.attr('src');
  var newSrc = src.replace(/&time=\d+$/, '&time=' + info.modificationDate);
  if (newSrc != src) {
    console.log('found image thumbnail to refresh with new date');
    $imageThumbnail.attr('src', newSrc);
    return true;
  }
  return false;
}

function _updateImageFilename($imgDiv, name) {

  var $img = $imgDiv.find(".imagePanel img");
  var imgWidth = $img.attr('width');
  var filenameWidth = Math.max(imgWidth - 40, 0);

  $imgDiv.find(".imageFileNameDiv")
    .css('max-width', filenameWidth)
    .text(name).attr('title', name);
}

function _updateChemImgsWithLastestInfo(info) {
  $('img.chem').each(function () {
    if ($(this).data('chemfileid') === info.id) {
      _updateChemImageThumbnailWithInfo($(this), info);
      _updateChemImagePanelWithInfo($(this), info);
    }
  });
}

function _updateChemImageThumbnailWithInfo($chemImg, info) {
  let chemFileId = $chemImg.data('chemfileid');
  if (chemFileId === info.id) {
    var src = $chemImg.attr('src');
    var newSrc = src.replace(/&time=\d+$/, '&time=' + info.modificationDate);
    $chemImg.attr('alt', info.name);
    if (newSrc != src) {
      console.log('found chemical image to refresh with new date');
      $chemImg.attr('src', newSrc);
      return true;
    } else {
      return false;
    }
  }
}

function _updateChemInfoPanelWithInfo($chemImg, info) {
  let closestP = $chemImg.closest("p");
  closestP.find("div.MuiPaper-root").remove();
  document.dispatchEvent(new CustomEvent('document-placed'));
}

function _updateChemImagePanelWithInfo($chemImage, info) {
  $imageFileNameDiv = $chemImage.closest('.imageViewModePanel');
  _updateImageFilename($imageFileNameDiv, info.name);
}

function _setViewModeAttachmentNameAndVersionLabel($nameDiv, name, version) {
  var versionText = version > 1 ? " (v" + version + ")" : "";
  $nameDiv.data('originalName', name);
  $nameDiv.text(name + versionText);
}

function _expandAttachmentPreview(id, revision, fileName, extension, $attachmentDiv) {
  $attachmentDiv.find(".previewExpandBtn, .attachmentThumbnailPanel, .inlineActionsPanel").hide();
  $attachmentDiv.find(".previewCollapseBtn, .attachmentPreviewPanel, .attachmentPreviewInfoPanel").show();

  showRecordInfo(id, revision, null, $attachmentDiv);
  var $attachmentPreviewPanel = $attachmentDiv.find(".attachmentPreviewPanel");
  RS.loadPdfPreviewIntoDiv(id, revision, fileName, extension, $attachmentPreviewPanel);
}

function _collapseAttachmentPreview($attachmentDiv) {
  $attachmentDiv.find(".previewCollapseBtn, .attachmentPreviewPanel, .attachmentPreviewInfoPanel").hide();
  $attachmentDiv.find(".previewExpandBtn, .attachmentThumbnailPanel, .inlineActionsPanel").show();
}

function _loadAttachmentThumbnail($attachmentDiv, docId, revision, thumbId) {

  $attachmentDiv.find('img.attachmentIcon')
    .removeAttr("width")
    .css('background-color', 'white');

  var thumbnailId = thumbId || new Date().getTime(); // if no thumbId use current time to force refresh
  var thumbnailSrc = "/image/docThumbnail/" + docId + '/' + thumbnailId;
  if (revision) {
    thumbnailSrc += "?revision=" + revision;
  }
  var $attachmentIcon = $attachmentDiv.find('img.attachmentIcon');
  RS.preloadImage(thumbnailSrc, $attachmentIcon);
}

function _getNewAttachmentDivWithoutPreview(id, revision, oldIconSrc) {
  var $newAttachmentDiv = $('#nonPreviewableAttachmentDivTemplate').find('.attachmentPanel').clone();

  $newAttachmentDiv.data('id', id);
  $newAttachmentDiv.data('rsrevision', revision);
  $newAttachmentDiv.find('.attachmentIcon').attr('src', oldIconSrc);

  return $newAttachmentDiv;
}

function _getSnapGeneTemplate(id, oldIconSrc) {
  var $newAttachmentDiv = $('#snapGeneTemplate').find('.attachmentPanel').clone();

  $newAttachmentDiv.attr('bioid', id);
  $newAttachmentDiv.find('.attachmentIcon').attr('src', oldIconSrc);

  return $newAttachmentDiv;
}

function applyCodeSampleHighlights($node) {
  $node.find('pre[class*="language-"]').each(function () {
    Prism.highlightElement(this);
  });
}

function setUpEditorBreadcrumbs() {
  $('#breadcrumbTag_editorBcrumb').find(".breadcrumbLink")
    .attr('href', function () {
      var folderId = $(this).attr('id').split('_')[1];
      var parentFolderIdAttr = $(this).prev().attr('id');
      var parentFolderUrlParam = parentFolderIdAttr ? '?parentFolderId=' + parentFolderIdAttr.split('_')[1] : '';
      return createURL('/workspace/' + folderId + parentFolderUrlParam);
    });
}

function initInlineRenameRecordForm(formSelector) {
  var editorSelector = nameFeatureSelectors.editorSelector;
  var fieldSelector = nameFeatureSelectors.fieldSelector;
  var form = $(formSelector);
  var nameField = form.find(fieldSelector);
  var nameEditor = form.find(editorSelector);

  nameEditor.attr('placeholder', nameField.text()).addClass("editable").removeAttr("readonly").hide();
  nameField.addClass("editable");
  $(formSelector).find("#renameRecordEdit").show(fadeTime);

  // on Enter pressed submit the form and prevent event from being propagated
  // as this would hide possible apprise windows which show up if e.g. there are invalid
  // characters or saving the new name failed
  $(document).on("keydown", formSelector + " " + editorSelector, function (e) {
    if (e.keyCode == 13) {
      e.preventDefault();
      e.stopPropagation();
      $(formSelector).submit();
    }
  });

  // if Name is editable, then first check new name, and if it's different and valid, try to save it
  $(document).on('submit', formSelector, function (e) {
    if (!isEditable) return false;

    e.preventDefault();
    e.stopPropagation();
    var newName = $(this).find(editorSelector).val();

    var checkName = checkEntryName(newName, formSelector, editorSelector, fieldSelector);
    if (!(checkName.valid && checkName.saveName)) return;

    var id = $('.recordInfoIcon').data('recordid');
    var data = {
      recordId: id,
      newName: newName,
    };

    makeRenameRequest(data, formSelector, editorSelector, fieldSelector);

    $(formSelector).find("#renameRecordEdit").show(fadeTime);
    $(formSelector).find("#renameRecordSubmit").hide(fadeTime);
    nameEditMode = false;
    return false;
  });

  // enter editing mode after clicking the Edit button
  $(document).on('click', formSelector + " #renameRecordEdit", function (e) {
    nameEditMode = true;
    e.preventDefault();
    $(editorSelector).val($('#recordNameInBreadcrumb').text());
    $(this).hide(fadeTime);
    $(this).parent().find("#renameRecordSubmit").show(fadeTime);
    $(formSelector).find(fieldSelector).animate({ opacity: 0 }, fadeTime).hide();
    $(formSelector).find(editorSelector).show().animate({ opacity: 1 }, fadeTime).focus();
    RS.resizeInputFieldToContent($(formSelector).find(editorSelector));
  });

  // enter editing mode after clicking the name field itself
  $(document).on('click', fieldSelector, function (e) {
    nameEditMode = true;
    $(editorSelector).val($('#recordNameInBreadcrumb').text());
    $(this).parent().find("#renameRecordEdit").hide(fadeTime);
    $(this).parent().find("#renameRecordSubmit").show(fadeTime);
    $(this).parent().find(fieldSelector).animate({ opacity: 0 }).hide();
    $(this).parent().find(editorSelector).show().animate({ opacity: 1 }).focus();
    RS.resizeInputFieldToContent($(formSelector).find(editorSelector));
  });
}

function makeRenameRequest(data) {
  var editorSelector = nameFeatureSelectors.editorSelector;
  var fieldSelector = nameFeatureSelectors.fieldSelector;
  var formSelector = nameFeatureSelectors.formSelector;
  var newName = data.newName;
  var jqxhr = $.post("/workspace/editor/structuredDocument/ajax/rename", data, function (data) {
    // if there were errors while saving new name, fall back to original name
    if (data.errorMsg !== null) {
      $(formSelector).find("#renameRecordEdit").show(fadeTime);
      $(formSelector).find("#renameRecordSubmit").hide(fadeTime);
      $(formSelector).find(editorSelector).val("").animate({ opacity: 0 }, fadeTime).hide();
      $(formSelector).find(fieldSelector).text($('#recordNameInBreadcrumb').text()).show().animate({ display: 'inline', opacity: 1 }, fadeTime);
      apprise(getValidationErrorString(data.errorMsg));
    } else {
      // saving new name was successful
      console.log("Entry renamed from '" + recordName + "' to '" + newName + "'");
      $('#recordNameInBreadcrumb').text(newName);
      $("#saveAsTemplateDlg #template_name").val(newName + "_template");
      $(formSelector).find(editorSelector).val("").attr('placeholder', newName).blur().animate({ opacity: 0 }, fadeTime).hide();
      $(formSelector).find(fieldSelector).text(newName).show().animate({ display: 'inline', opacity: 1 }, fadeTime);
      if (isDocumentEditor) {
        var oldTitle = $(document).prop('title');
        var newTitle = oldTitle.replace(/^[^\|]+/, newName + " ");
        $(document).prop('title', newTitle);
      } else {
        if ($("#journalEntriesRibbon").is(":visible")) {
          $("#notebook").journal("loadEntriesRibbon", true);
        }
      }
      reloadFileTreeBrowser();
      recordName = newName;
      $(formSelector).find("#renameRecordEdit").show(fadeTime);
      $(formSelector).find("#renameRecordSubmit").hide(fadeTime);
      nameEditMode = false;
    }
  }).fail(function () {
    RS.ajaxFailed("Renaming record", false, jqxhr);
  });
  return jqxhr;
}

function checkEntryName(newName) {
  var editorSelector = nameFeatureSelectors.editorSelector;
  var fieldSelector = nameFeatureSelectors.fieldSelector;
  var formSelector = nameFeatureSelectors.formSelector;

  // check for invalid characters, warn the user, leave editor field focused without changing the name
  for (i = 0; i < nameForbiddenCharacters.length; i++) {
    if (newName.indexOf(nameForbiddenCharacters[i]) >= 0) {
      apprise("Sorry, '" + nameForbiddenCharacters[i] + "' is a forbidden character that you can't use in document name");
      return { "valid": false, "saveName": false };
    }
  }

  // check if new name is empty, warn the user and fill editor field with original name, staying focused so
  // the user can enter a different name
  if (newName === "") {
    $(formSelector).find(editorSelector).val($('#recordNameInBreadcrumb').text());
    RS.resizeInputFieldToContent($(formSelector).find(editorSelector));
    apprise("Please enter a name");
    return { "valid": false, "saveName": false };
  }

  // check if changes are to be made to the name, if not, pretend to have saved the new name without
  // doing anything, really
  if (newName == recordName) {
    $(formSelector).find(editorSelector).val(newName).attr('placeholder', newName).blur().animate({ opacity: 0 }, fadeTime).hide();
    $(formSelector).find(fieldSelector).show().animate({ display: 'inline', opacity: 1 }, fadeTime);
    $(formSelector).find("#renameRecordEdit").show(fadeTime);
    $(formSelector).find("#renameRecordSubmit").hide(fadeTime);
    nameEditMode = false;
    return { "valid": true, "saveName": false };
  }

  return { "valid": true, "saveName": true };
}

/*
 * Attempt to save new name if it's in edit mode (potentially unsaved).
 * Used before closing document edit/view mode or any saving action.
 */
function renameDocumentBeforeSaving() {
  var renameRequestPromise = $.Deferred().resolve().promise(); // dummy promise

  if (nameEditMode) { // make real promise if name is unsaved
    console.log("Saving new name before saving the document...");
    var newName = $(nameFeatureSelectors.editorSelector).val();
    var id = $('.recordInfoIcon').data('recordid');
    var data = {
      recordId: id,
      newName: newName,
    };

    var checkName = checkEntryName(newName);
    if (!checkName.valid) {
      console.log("New name is invalid, saving stopped.");
      renameRequestPromise = $.Deferred().reject().promise();
    }

    // valid and changed name is to be saved, now create the renaming request promise
    if (checkName.valid && checkName.saveName) {
      renameRequestPromise = makeRenameRequest(data);
      renameRequestPromise.done(function (response) {
        if (response.errorMsg) {
          console.log("Errors occured while saving new name. Saving stopps.");
          renameRequestPromise = $.Deferred().reject().promise();
        } else {
          console.log("New name saved, now saving the document.");
          unchangedContentCounter = 0; // optional and questionable
        }
      });
    }
  }
  return renameRequestPromise;
}
let tagInfoDialog;

//tag values are not globally unique but they are unique on a given document
//and so can be mapped to their meta data
const tagDataValuesToMetaData = new Map();

//When tagit widget destroyed, display tags as comma delimited plain text. However - tag text can also contain commas!
//We are also wrapping tag text with commas in quotes, so its slightly easier to read.
//This holds a map of the display modified values to their actual DB stored values.
const tagTextDisplayValuesToActualValues = new Map();
// For unknown reasons, each 'collapse' of the tags widget (when user clicks on save icon) results in extra clicks
// to openDialog being generated when a user subsequently re-opens the tags widget and clicks on a tag pill.
// See useage in openDialog()
let multipleTagClicks = false;
let openAllowed = true;
const closeHandler = ()=>{
    tagInfoDialog.dialog('close');
};

const openDialog = (selector, widthPX=500) =>{
    if(!multipleTagClicks) {
        multipleTagClicks = true;//block further click events as they are spurious
        setTimeout(() => multipleTagClicks = false, 15);
        // It is essential that manipulation of the dialog be done using the tagInfoDialog reference
        // and not just $("#tag-info-dialog") - there is some issue with closures and variables referencing
        // the wrong instance of the dialog otherwise whenever a user collapses the tagit widget by saving
        // and subsequently attempts to re-use.
        tagInfoDialog =
            $(selector).dialog(
                {
                    close: function (event, ui) {
                        setTimeout(() => {
                            $('#page').unbind('click', closeHandler);
                        }, 5);
                        setTimeout(() => {
                            $('.ui-dialog-titlebar').unbind('click', closeHandler);
                            $(selector).unbind('click', closeHandler);
                        }, 8);
                        openAllowed = false;
                        // Issue - if user clicks on a tag pill when the dialog box is open, the box will lose focus and attempt to close.
                        // However, the click on the tag pill will also fire an openDialog event. User will see dialog close for an instant then re-open.
                        // Solution: using the 'openAllowed' toggle to block the openDialog event, allows click on a tag pill to close an existing dialog box,
                        // - even though click on tag pill will fire an 'openDialog' event.
                        // After 200ms, a user click on a tag pill will be allowed to open the dialog again.
                        setTimeout(() => openAllowed = true, 10);
                    },
                    open: function (event, ui) {
                        if (openAllowed) {
                            $(this).parent().find('.ui-dialog-titlebar-close').hide();
                            setTimeout(() => {
                                $('.ui-dialog-titlebar').bind('click', closeHandler);
                                $(selector).bind('click', closeHandler);
                            }, 5);
                            setTimeout(() => {
                                $('#page').bind('click', closeHandler);
                            }, 8);
                            openAllowed = false;
                        } else {
                            closeHandler();
                        }
                    },
                    width: widthPX
                }
            );
    }
}
const RSPACE_ONTOLOGY_URL_DELIMITER = "__RSP_EXTONT_URL_DELIM__";
const RSPACE_ONTOLOGY_NAME_DELIM = "__RSP_EXTONT_NAME_DELIM__";
const RSPACE_ONTOLOGY_VERSION_DELIM = "__RSP_EXTONT_VERSION_DELIM__";
const RSPACE_EXTONTOLOGY_TAG_DELIMITER = "__RSP_EXTONT_TAG_DELIM__";
const RSPACE_TAGS_FORWARD_SLASH_DELIM = "__rspactags_forsl__";
const RSPACE_TAGS_COMMA_DELIM = "__rspactags_comma__";

//View mode Functions are used when the document is first loaded, invoked by documentView.js and journal.js
const setUpViewModeInfo = (latestTags) => {
    const tagInfoDialogHTMLViewMode = '<div id="tag-info-dialog-viewmode" title="tag info" style="display:none">' +
        '  <p id="tag-info-dialog-content-viewmode"></p></div>'
    if (latestTags.trim().length === 0) {
        $('#notebookTags').html(latestTags);
    } else {
        $('#notebookTags').html(tagInfoDialogHTMLViewMode + formatRecordTagsToTagPillsForViewMode(latestTags));
    }
}
const addOnClickToTagsForViewMode = (tags) => {
    const doStuff = (tag) => {
        const text = tagDataValuesToMetaData.get(tag);
        const width = text ? (text.length < 40 ? 400 : 1000) : 400;
        const dialogContent = extractTagDisplayInfoFromTagMetaData({tagLabel:tag});
        if(dialogContent !== null) {
          $('#tag-info-dialog-content-viewmode').html(dialogContent);
          openDialog('#tag-info-dialog-viewmode', width);
        }
    }
    tags.map((tag,pos)=> {
        const aTagPill = $('#tagpill'+pos)[0];
        aTagPill.onclick = ()=>doStuff(tag);
    });
}
const formatRecordTagsToTagPillsForViewMode = (recTags) => {
    const tagTerms = recTags.split(",")
        .map(tag => replaceCommaDelimiterInTag(tag)).map(tag => tag.trim())
        .map((tag,pos) => {
            const hasMeta = tagDataValuesToMetaData.get(tag)?tagDataValuesToMetaData.get(tag).includes(RSPACE_ONTOLOGY_URL_DELIMITER):false;
            return (hasMeta ?
            '<div id="tagpill'+pos+'" class="tagit-choice"><svg id="' + tag + '" xmlns="http://www.w3.org/2000/svg"  width="1.5em" height="0.8em" viewBox="30 -20 576 512"><!--! Font Awesome Free 6.4.2 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license (Commercial License) Copyright 2023 Fonticons, Inc. --><path d="M208 80c0-26.5 21.5-48 48-48h64c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48h-8v40H464c30.9 0 56 25.1 56 56v32h8c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48H464c-26.5 0-48-21.5-48-48V368c0-26.5 21.5-48 48-48h8V288c0-4.4-3.6-8-8-8H312v40h8c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48H256c-26.5 0-48-21.5-48-48V368c0-26.5 21.5-48 48-48h8V280H112c-4.4 0-8 3.6-8 8v32h8c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48H48c-26.5 0-48-21.5-48-48V368c0-26.5 21.5-48 48-48h8V288c0-30.9 25.1-56 56-56H264V192h-8c-26.5 0-48-21.5-48-48V80z"/></svg>':
                    '<div id="tagpill'+pos+'" class="tagit-choice-inv">' )
                +tag+'</div>'});
    return tagTerms.join(" ");
}
// Change tags back to one comma-delimited string, destroy tag-it feature
function collapseTagForm(selector) {
    tagsEditMode = false;
    $(selector).find(".tagit-new input").attr("value", "");
    var tags = $(selector).tagit("assignedTags");
    //destroy is not working as intended - see the use of 'multipleTagClicks' variable  in openDialog() below
    $(selector).tagit("destroy");
    $("#tag-info-dialog-content-viewmode").remove();
    $("#tag-info-dialog-viewmode").remove();
    if (tagInfoDialog) {
        try {
            tagInfoDialog.dialog("destroy");
        } catch (err) {
            tagInfoDialog = null;
            // console.log(err);//only uncomment for debugging - this issue happens
            // at random but does not seems to be a problem if the error is swallowed
        }
    }
    const dbText =  tags.map(tag=>replaceCommaInTagWithDelimiter(tag)).join(",");
    const displayText = tags.join(" ");
    tagTextDisplayValuesToActualValues.clear();
    tagTextDisplayValuesToActualValues.set(displayText,dbText);
    setUpViewModeInfo(dbText);
    addOnClickToTagsForViewMode(tags);
    $("#editTags").show(fadeTime);
    $("#saveTags").hide(fadeTime);
}
const replaceCommaDelimiterInTag = (tag) => {
    tag = tag.replaceAll("__rspactags_comma__", ",");
    return tag;
};
const replaceCommaInTagWithDelimiter = (tag) => {
    tag = tag.replaceAll(",","__rspactags_comma__");
    return tag;
};
const initTagMetaData = (selector) =>{
    let tags = [];
    const existingTags = $(selector).text().trim();
    if (existingTags) {
        //tags have been saved to DB with latest code
        if (tagMetaData || tagTextDisplayValuesToActualValues.get(existingTags)) {
            const actualValues = tagTextDisplayValuesToActualValues.get(existingTags);
            if (actualValues) {
                tags = actualValues.split(",");
            } else {
                tags = tagMetaData.split(",");
                tags = tags.map(tag=>replaceCommaDelimiterInTag(tag.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0]));
            }
        } else {
            //Tags saved to DB with old code that comma seperated them.
            //We can trust these old values NOT to have any commas in the actual tag values.
            tags =existingTags.split(",");
        }
    }
    if(tagMetaData) {
        const tagsPlusMeta = tagMetaData.split(",");
        tagsPlusMeta.map(tagPlusMeta=> {
            const valueToUseAsTag = tagPlusMeta.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0].trim();
            tagDataValuesToMetaData.set(replaceCommaDelimiterInTag(valueToUseAsTag), tagPlusMeta);
        });
    }
    return tags;
}
const extractTagDisplayInfoFromTagMetaData = (ui) =>{
    const existingMetaData = tagDataValuesToMetaData.get(ui.tagLabel);
    if(existingMetaData){
        return formatTagDisplayString(existingMetaData);
    } else {
        return null;
    }
}
const formatTagDisplayString = (tagWithMeta) => {
    if(tagWithMeta.indexOf(RSPACE_ONTOLOGY_URL_DELIMITER)==-1){
      return null;
    }
    const valToSave = replaceCommaDelimiterInTag(tagWithMeta.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0].trim());
    const nameAndVersion = tagWithMeta.split(RSPACE_ONTOLOGY_NAME_DELIM)[1];
    const uri = tagWithMeta.split(RSPACE_ONTOLOGY_URL_DELIMITER)[1].split(RSPACE_ONTOLOGY_NAME_DELIM)[0];
    const displayString = "<b>"+valToSave+"</b>" + " - ontology: <b>" + nameAndVersion.split(RSPACE_ONTOLOGY_VERSION_DELIM)[0] + "</b>, version: <b>"
        +nameAndVersion.split(RSPACE_ONTOLOGY_VERSION_DELIM)[1] +"</b>" + ", uri:<b>" +uri+"</b>";
    navigator.clipboard.writeText(uri);
    return displayString;
}
const tagInfoDialogHTML =  '<div id="tag-info-dialog" title="tag info" style="display:none">' +
    '  <p id="tag-info-dialog-content"></p></div>'
function initTagForm(selector) {
    let suggestedTags = [];
    const tags = initTagMetaData(selector);
    const CLICK_FOR_NEXT_DATA = "============CLICK_HERE_FOR_NEXT_DATA============"
    tagsEditMode = true;
    let saveIsPossible = true;
    const forbidden = ['<', '>', '\\'];
    $(selector).html("");
    var tagsHtml = "";
    $.each(tags, function (k, tag) {
        tagsHtml += "<li>" + tag.trim() + "</li>";
    });

    tagsHtml = (allowBioOntologies ? '<div class="smallText">BioPortal Ontologies being added to suggestions.Disable on My LabGroups Page.</div>':
            '<div class="smallText">Use My LabGroups page to enable inclusion of BioPortal Ontologies suggestions.</div>') +
        tagInfoDialogHTML+'<img id="ajaxTagsLoadingImg" src="/public/images/ajax-loading.gif" style="display:none;" "/>'+tagsHtml;
    $(selector).html(tagsHtml);


  //results are returned in blocks of 1000
  let requestDataBlockFromPosition = 0;
  let inputText = "";
  let clickForNextData = false;
  const getTagData = (request, response) => {
    var term = request.term;
    if(!clickForNextData){
        requestDataBlockFromPosition = 0;
    }
    clickForNextData = false;
    inputText = term;
      $("#ajaxTagsLoadingImg").show();

    $.getJSON('/workspace/editor/structuredDocument/userTagsAndOntologies', { tagFilter: term, pos: requestDataBlockFromPosition }, function (data, status, xhr) {
        const assignedTags = $(selector).tagit("assignedTags");
        // data formatted for display is key, data to save (the value of the tag minus any metadata) is value

        let allDataValues = data.data.map(tag => {
            if (tag.includes(RSPACE_ONTOLOGY_NAME_DELIM)) {
                const valToSave = replaceCommaDelimiterInTag(tag.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0].trim());
                return assignedTags.includes(valToSave) ? "" : tag;
            } else {
                return assignedTags.includes(tag) ? "" : tag;
            }
        });
        allDataValues = [...new Set(allDataValues)];
        suggestedTags = allDataValues;
      if (suggestedTags.includes("=========SMALL_DATASET_IN_SINGLE_BLOCK=========")) {
        suggestedTags = suggestedTags.filter(tag => tag !== "=========SMALL_DATASET_IN_SINGLE_BLOCK=========");
      }
      else if (suggestedTags.includes("=========TOO_MANY_ONTOLOGY_RESULTS=========")) {
        suggestedTags = [];
        suggestedTags.push("Too many results, please enter a specific search term");
      }
      else if (suggestedTags.includes("=========FINAL_DATA=========")) {
        suggestedTags = suggestedTags.filter(tag => tag !== "=========FINAL_DATA=========");
        suggestedTags.push("================BACK_TO_START================");
        suggestedTags.unshift("================BACK_TO_START================");
      } else {
        suggestedTags.push(CLICK_FOR_NEXT_DATA);
        suggestedTags.unshift(CLICK_FOR_NEXT_DATA);
      }
        response( $.map( suggestedTags, function( item ) {
            return {
                label: item,
                value: item
            }
        }));
        $("#ajaxTagsLoadingImg").hide();
      if (suggestedTags.length === 0 && enforceOntologies) {
        saveIsPossible = false;
      } else {
        saveIsPossible = true;
      }
    });
  }
    const replaceForwardSlashAndCommasInTagPlusMeta = (tagPlusMeta) => {
      if(tagPlusMeta.indexOf(RSPACE_ONTOLOGY_URL_DELIMITER)!==-1) {
          const tagSplit = tagPlusMeta.split(RSPACE_ONTOLOGY_URL_DELIMITER);
          let tagValue = tagSplit[0];
          tagValue = replaceCommaInTag(replaceForwardSlashInTag(tagValue));
          return tagValue + RSPACE_ONTOLOGY_URL_DELIMITER + tagSplit[1];
      } else {
          return tagPlusMeta;//'user free text tags cannot have commas or forward slashes'
      }
    }
    const replaceForwardSlashInTag = (tag) => {
        tag = tag.replaceAll("/", RSPACE_TAGS_FORWARD_SLASH_DELIM);
        return tag;
    }

    const replaceCommaInTag = (tag) => {
        tag = tag.replaceAll(",", RSPACE_TAGS_COMMA_DELIM);
        return tag;
    }

  $(selector).tagit({
      // will modify how tag text looks in the 'pill' in which it is displayed
      preprocessTag: function(val) {
          val = replaceCommaDelimiterInTag(val);
          if(val.indexOf(RSPACE_ONTOLOGY_URL_DELIMITER)!==-1){
              return val.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0];
          }
          return val;
      },
    fieldName: "tags",
      onTagClicked: function (evt, ui) {
          const displayString = extractTagDisplayInfoFromTagMetaData(ui);
          if(displayString !== null) {
            $('#tag-info-dialog-content').html(displayString);
            const width = displayString ? (displayString.length < 40 ? 400 : 1000) : 400;
            openDialog("#tag-info-dialog", width);
          }
      },
      autocomplete: {
          minLength: 0,
          source: getTagData,
          select: function (event, ui) {//called when a user selects a tag from the drop down
              if(ui.item.value.indexOf(RSPACE_ONTOLOGY_URL_DELIMITER)!==-1){
                  tagDataValuesToMetaData.set(replaceCommaDelimiterInTag(ui.item.value.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0]),ui.item.value);
              } else {
                  tagDataValuesToMetaData.set(ui.item.value,ui.item.value);
              }

              $(selector).tagit("createTag", ui.item.value);
              // Preventing the tag input to be updated with the chosen value.
              return false;
          },
          open: function( event, ui ) {//called when tagit displays the tags in the drop down
              $('ul.tagit-autocomplete').find("li").each(function(index){
                  var cur_html = $(this).html();
                  if(cur_html.indexOf(RSPACE_ONTOLOGY_URL_DELIMITER)===-1){
                      return;
                  }
                  $(this).html(formatTagDisplayString(cur_html));
              });
              return true;
          }
      },
    placeholderText: (enforceOntologies ? "Ontologies enforced..." : "Separate tags by comma..."),
    allowSpaces: true,
      allowDuplicates: false,
    readOnly: !isEditable,
      afterTagAdded: function (event, ui) {
          let label = ui.tagLabel;
          const hasMeta = tagDataValuesToMetaData.get(label)?tagDataValuesToMetaData.get(label).includes(RSPACE_ONTOLOGY_URL_DELIMITER):false;
          if(hasMeta) {
              $(ui.tag[0]).prepend('<svg id="' + label + '" xmlns="http://www.w3.org/2000/svg"  width="1.4em" height="0.8em" viewBox="30 -20 576 512"><!--! Font Awesome Free 6.4.2 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license (Commercial License) Copyright 2023 Fonticons, Inc. --><path d="M208 80c0-26.5 21.5-48 48-48h64c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48h-8v40H464c30.9 0 56 25.1 56 56v32h8c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48H464c-26.5 0-48-21.5-48-48V368c0-26.5 21.5-48 48-48h8V288c0-4.4-3.6-8-8-8H312v40h8c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48H256c-26.5 0-48-21.5-48-48V368c0-26.5 21.5-48 48-48h8V280H112c-4.4 0-8 3.6-8 8v32h8c26.5 0 48 21.5 48 48v64c0 26.5-21.5 48-48 48H48c-26.5 0-48-21.5-48-48V368c0-26.5 21.5-48 48-48h8V288c0-30.9 25.1-56 56-56H264V192h-8c-26.5 0-48-21.5-48-48V80z"/></svg>');
          } else {
              ui.tag.css("background-color", "#c0e2fc");
              ui.tag.css("color", "#6389a8");
          }
          if(label.indexOf("'")!==-1){
              label = label.replaceAll("'","\\'");
          }
          $( "svg[id='"+label+"']" ).bind( "click", function() {
              const displayString = extractTagDisplayInfoFromTagMetaData(ui);
              $('#tag-info-dialog-content').html(displayString);
              openDialog("#tag-info-dialog");
          });
          requestDataBlockFromPosition = 0;
      },
    beforeTagAdded: function (event, ui) {
      // don't save tags that are already saved and are just being loaded on tags form init
      if (ui.duringInitialization === true) {return true};
      if (enforceOntologies && !suggestedTags.includes(ui.tagLabel) &&! tagDataValuesToMetaData.get(ui.tagLabel)) {
          $('#tag-info-dialog-content').html("Ontologies are enforced, tag values must come from ontology files shared with a group");
          openDialog("#tag-info-dialog");
        return false;
      }
        if (ui.tagLabel === CLICK_FOR_NEXT_DATA) {
            event.preventDefault();
            requestDataBlockFromPosition++;
            clickForNextData = true;
            setTimeout(() => input.click(), 100);
            return false;
        } else if (ui.tagLabel === "================BACK_TO_START================") {
            event.preventDefault();
            requestDataBlockFromPosition = 0;
            setTimeout(() => input.click(), 100);
            return false;
        } else if (ui.tagLabel === "Too many results, please enter a specific search term") {
            event.preventDefault();
            requestDataBlockFromPosition = 0;
            return false;
        }
        if (!saveIsPossible) {
            return false;
        }
        const errorForbiddenChar = (forbidden) =>{
            $('#tag-info-dialog-content').html("Sorry, '" + forbidden + "' is a forbidden character that you can't use in tags");
            openDialog("#tag-info-dialog");
        }
       const tagPlusMeta =  tagDataValuesToMetaData.get(ui.tagLabel);
        // Ontologies are allowed to contain "/" but user free input is not
        if(!tagPlusMeta || tagPlusMeta.indexOf(RSPACE_ONTOLOGY_URL_DELIMITER)==-1){
            if (ui.tagLabel.indexOf("/") >= 0) {
                event.preventDefault();
                errorForbiddenChar("/");
                return false;
            }
        }
        // validate new tag against forbidden characters
      for (i = 0; i < forbidden.length; i++) {
        if (ui.tagLabel.indexOf(forbidden[i]) >= 0) {
            event.preventDefault();
            errorForbiddenChar(forbidden[i]);
            return false;
        }
      }

      if (ui.tagLabel.length < 2) {
          console.log("length too short ");
          event.preventDefault();
          $('#tag-info-dialog-content').html("Sorry, the tag needs to be at least 2 characters long");
          openDialog("#tag-info-dialog");
        return false;
      }

        let tags = $(selector).tagit("assignedTags");
        tags = tags.map(tag => tagDataValuesToMetaData.get(tag.trim()) ?
            tagDataValuesToMetaData.get(tag.trim()): tag);
        tags.push(tagDataValuesToMetaData.get(ui.tagLabel) ? tagDataValuesToMetaData.get(ui.tagLabel) : ui.tagLabel);
        tags = tags.map(tag => replaceForwardSlashAndCommasInTagPlusMeta(tag));
        for (let i = 0; i < tags.length; i++)
            tags[i] = $.trim(tags[i]);
        tags = tags.filter(tag => tag.length > 0);
        tags = tags.join(",");
        saveRecordTags(tags);
    },
    beforeTagRemoved: function (event, ui) {
      var tags = $(selector).tagit("assignedTags");

      // filter out the removed tag
      tags = $.grep(tags, function (val) {
        return val != ui.tagLabel;
      });
        tags = tags.map(tag => tagDataValuesToMetaData.get(tag.trim()) ? tagDataValuesToMetaData.get(tag.trim()): tag);
        tags = tags.map(tag => replaceCommaInTag(replaceForwardSlashInTag(tag)));
      for (var i = 0; i < tags.length; i++) {
          tags[i] = $.trim(tags[i]);
      }
      tags = tags.join(",");
      saveRecordTags(tags);
    }
  });
  const input = $(selector).find("input");
  input.click(() => input.trigger({
    type: 'keydown', keyCode: "a", which: "a", charCode: "a"
  }));

  $("#editTags").hide(fadeTime);
  $("#saveTags").show(fadeTime);
}

function saveRecordTags(tags) {
  // change delimiters (needed to fit the format that the backend works with)
  var data = {
    recordId: $('.recordInfoIcon').data('recordid'),
    tagtext: tags
  };
  var jqxhr = $.post("/workspace/editor/structuredDocument/tagRecord", data, function (data) {
    if (data.data != null) {
    } else if (data.errorMsg != null) {
        $('#tag-info-dialog-content').html(getValidationErrorString(data.errorMsg));
        openDialog("#tag-info-dialog");
    }
  }).fail(function () {
    RS.ajaxFailed("Tagging", false, jqxhr);
  });
}

function setUpMessagingButtonsAndDialog() {
  if (typeof initialiseRequestDlg === "function") {
    initialiseRequestDlg({
      recordIdGetter: getDocumentOrEntryId,
      availableMessageTypes: 'SIMPLE_MESSAGE,GLOBAL_MESSAGE,REQUEST_RECORD_REVIEW,REQUEST_EXTERNAL_SHARE'
    });

    window.renderToolbar({
      eventHandlers: {
        onCreateRequest: () => {
          $('#createRequestDlg').dialog('open');
        },
      }
    });
  }

  if (typeof initialiseExtMessageChannelListButtonAndDialog === "function") {
    initialiseExtMessageChannelListButtonAndDialog(function () {
      return [getDocumentOrEntryId()];
    }, '.createExtMessage');
  }
}

function getDocumentOrEntryId() {
  return isDocumentEditor ? recordId : getCurrentEntryId();
}

function getSelectedIdsNamesAndTypes() {
  return getDocIdNameAndType();
}

function downloadAsImage($imageDiv) {
  var downloadURL;
  if ($imageDiv.is('img.imageDropped')) {
    var elementId = getImageIdFrom$Img($imageDiv);
    downloadURL = getPublicRoute() + '/Streamfile/' + elementId;
    var revision = $imageDiv.data('rsrevision');
    if (revision) {
      downloadURL += "?revision=" + revision;
    }
  } else if ($imageDiv.is('img.sketch')) {
    downloadURL = $imageDiv.attr('src');
  } else if ($imageDiv.is('img.chem')) {
    downloadURL = getPublicRoute() + '/Streamfile/chemImage/' + $imageDiv.attr('id');
  }

  if (downloadURL) {
    RS.openInNewWindow(downloadURL);
  }
}

function showImageInfoPopup($image) {
  var id = getImageIdFrom$Img($image);
  var revision = $image.data('rsrevision');
  showRecordInfo(id, revision);
}

function getImageIdFrom$Img($image) {
  var compositeId = $image.attr("id");
  if (compositeId.indexOf('-') > 0) {
    var idElements = compositeId.split("-");
    return idElements[1];
  }
  return null;
}

function _getHtmlFormattedFormula(formula) {
  if (!formula) {
    return formula;
  }
  return formula.replace(/(\d+)/g, "<sub>$&</sub>");
}

var rs_tableExport;

function addDownloadImageContextButton($img) {
  var title = 'Download as image';
  if ($img.is('.imageDropped')) {
    title = 'Download full image';
  }
  var cssClass = 'imageContextButtons imageDownloadButton';
  if ($img.attr('width') < 60) {
    cssClass += 'SmallMargin';
  }
  var dowloadImageButton = $('<div></div>', {
    'class': cssClass,
    'title': title,
    on: {
      click: function () {
        downloadAsImage($img);
        removeImageContextButtons();
      }
    }
  });
  $img.after(dowloadImageButton);
}

function addDownloadButtonToTables($tables) {
  $.each($tables, function () {
    var $table = $(this);
    if ($table.parent().is('.tableDownloadWrap')) {
      return; // already wrapped
    }
    var $tableDownloadButton = $('<div></div>', {
      'class': 'tableContextButtons tableDownloadButton',
      'title': 'Download as CSV',
      on: {
        click: function (e) {
          var $table = $(this).siblings('table');
          if ($table.is('table')) {
            var exportOptions = { formats: ['csv'], exportButtons: false };
            if ($table.hasClass('rsCalcTable')) {
              exportOptions.headers = false;
              exportOptions.ignoreCols = 0;
            }
            rs_tableExport = $table.tableExport(exportOptions);
            var tableid = $table.attr('id');
            var exportData = rs_tableExport.getExportData()[tableid].csv;
            rs_tableExport.export2file(exportData.data, exportData.mimeType, exportData.filename, exportData.fileExtension);
          }
        }
      }
    });
    $table.wrap("<div class='tableDownloadWrap' style='display:flex;'></div>");
    var wrap$ = $table.closest('div');
    wrap$.append($tableDownloadButton);
    var leftMargin = $tableDownloadButton.width() + 6;
    if ($table.attr('width') < 60) {
      leftMargin += 10;
    }
    $tableDownloadButton.css('bottom', 13).css('left', leftMargin).css('align-self', 'flex-end');
    $tableDownloadButton.hide();
  });
}

function addImageInfoContextButton($img, onClick) {
  var cssClass = 'imageContextButtons imageInfoButton';
  if ($img.attr('width') < 60) {
    cssClass += 'SmallMargin';
  }
  var imageInfoButton = $('<div></div>', {
    'class': cssClass,
    'title': 'Image details',
    on: {
      click: function () {
        onClick($img);
        removeImageContextButtons();
      }
    }
  });
  $img.after(imageInfoButton);
}

function removeImageContextButtons() {
  $("div.imageContextButtons").remove();
}

function expandPageAndShowFileTreeBrowser() {
  var $page = $('#page');
  if ($page.hasClass('treeBrowserWide')) {
    return; // already expanded
  }
  $page.addClass('treeBrowserWide');
  $page.removeClass('treeBrowserNarrow');

  var currentRecord = isDocumentEditor ? recordId : (getCurrentEntryId() || notebookId);
  setUpFileTreeBrowser({
    initialRecordId: currentRecord,
    showGallery: false,
    navigationHandler: navigateAwayFromCurrentDocument,
    supportTinymceDnd: isDocumentEditor
  });
}

function collapsePageAndHideFileTreeBrowser() {
  var $page = $('#page');
  if ($page.hasClass('treeBrowserNarrow')) {
    return; // already collapsed
  }
  $page.addClass('treeBrowserNarrow');
  $page.removeClass('treeBrowserWide');
}

$(document).ready(function () {
  parseClientUISettingsPref();

  // Ideally, this will be called wherever initTagForm is called (so the custom
  // selector can be passed instead of #notebookTags) BUT only once so we don't
  // assign multiple handlers to the thing.
  RS.addOnKeyboardClickHandlerToDocument('#notebookTags .tagit-choice', function (e) {
    $(this).find('.tagit-close').click();
  });

  $(".state").mouseover(function () {
    if (this.id === "signedStatus" || this.id === "witnessedStatus") {
      $(this).addClass("signPost");
    }
  });
  $(".state").mouseleave(function () {
    if (this.id === "signedStatus" || this.id === "witnessedStatus") {
      $(this).removeClass("signPost");
    }
  });

  $(document).on('click', '#signedStatus, #signedAwaitingWitnessStatus, #signedWitnessesDeclinedStatus, #witnessedStatus', function () {
    signatureShowToastMessage();
    return false;
  });

  var imageContextButtonsSelector = "img.imageDropped, img.sketch, img.chem";
  $(document).on('mouseenter', imageContextButtonsSelector, function (e) {
    var oldTarget = e.relatedTarget;
    if (!$(oldTarget).hasClass("imageContextButtons")) {
      removeImageContextButtons();
      var $img = $(this);
      if ($img.is('img')) {
        addDownloadImageContextButton($img);
      }
    }
  });

  $(document).on('mouseleave', imageContextButtonsSelector, function (e) {
    var newTarget = e.relatedTarget;
    if (!$(newTarget).is("div.imageContextButtons")) {
      removeImageContextButtons();
    }
    // Handle edge case where user quickly mouses from load button to outside image
    if (typeof newTarget === "undefined" || newTarget === null || $(newTarget).parent().attr("id") !== $(this).parent().attr("id")) {
      removeImageContextButtons();
    }
  });

  // table download to csv button RSPAC-1292
  // must work in notebook and document view
  var tableContextButtonsSelector = "div.tableDownloadWrap";
  $(document).on('mouseenter', tableContextButtonsSelector, function () {
    $(this).find("div.tableContextButtons").show();
  });

  // hide table download to csv button RSPAC-1292
  $(document).on('mouseleave', tableContextButtonsSelector, function (e) {
    $(this).find("div.tableContextButtons").hide();
    if (rs_tableExport) {
      rs_tableExport.remove();
    }
  });

  $(document).on('click', ".recordInfoIcon", function () {
    var recordId = $(this).data("recordid");
    var versionId = $(this).data("versionid");
    showRecordInfo(recordId, null, versionId);
  });

  $.get("/deploymentproperties/ajax/properties", function (properties) {
    boxClientId = properties['box.client.id'];

    if (properties['box.api.enabled'] === 'ALLOWED') {
      $.get('/integration/integrationInfo', { name: 'BOX' }, function (integrationResponse) {
        var integration = integrationResponse.data;
        if (integration && integration.available && integration.enabled) {
          boxLinkTypePref = integration.options.BOX_LINK_TYPE;
        }
      });
    }
  });

  $(document).on('click', ".boxVersionLink", function () {
    var $boxLink = $(this);
    showBoxLinkInfo($boxLink);
    return false;
  });

  $(document).on('click', ".boxFileDownloadBtn", function () {
    var id = encodeURIComponent($('.boxInfoPanel-id').text());
    var name = encodeURIComponent($('.boxInfoPanel-name').text());
    var versionID = encodeURIComponent($('.boxInfoPanel-versionID').text());

    checkBoxAPIAvailableForUser(function () {
      RS.openInNewWindow('/box/downloadBoxFile?boxId=' + id + '&boxVersionID=' + versionID + '&boxName=' + name);
    });
  });

  function onExportDocument() {
    RS.getExportSelectionForExportDlg = () =>
      getExportSelectionFromSelectedDocuments(getDocIdNameAndType());
    RS.exportModal.openWithExportSelection(RS.getExportSelectionForExportDlg());
  }
  window.renderToolbar({ eventHandlers: { onExportDocument } });

  $('#close').click(function (e) {
    if (nameEditMode && editable !== 'EDIT_MODE') {
      e.preventDefault();
      var link = $(this).attr("href");
      var renamePromise = renameDocumentBeforeSaving();
      $.when(renamePromise).done(function () {
        window.location = link;
        return;
      });
    }
  });

  RS.addNetFileClickHandler();
});

$(document).ready(function () {
  setUpMessagingButtonsAndDialog();
});


window.addEventListener("ReactToolbarMounted", () => {
  // if share button visible
  if ($('#shareRecord').length) {
    var dialogTitle = isDocumentEditor ? "Share Document" : "Share Entry";
    var idsToShareGetter = function () {
      return [getDocumentOrEntryId()];
    };
    createShareDialog(dialogTitle, idsToShareGetter);
  }
});

/*
 * file tree browser setup for editor/notebook page
 */
function isWindowWideEnoughForFileTreeBrowser() {
  return $(window).width() >= 1290;
}

function toggleFileTreeBrowserDependingOnWidthAndUISettings() {

  // for simplified view don't load nor display tree browser
  if (typeof isSimpleEditorView != "undefined" && isSimpleEditorView) {
    $('#page').removeClass('treeBrowserWide treeBrowserNarrow');
    return;
  }

  var show = clientUISettings.showTreeInEditor === 'a' ||
    (isWindowWideEnoughForFileTreeBrowser() && clientUISettings.showTreeInEditor === 'y');

  if (show) {
    expandPageAndShowFileTreeBrowser();
  } else {
    collapsePageAndHideFileTreeBrowser();
  }
}

function navigateAwayFromCurrentDocument(urlToOpen, modalTargetElem) {
  if (isDocumentEditor && checkForUnsavedChanges()) {
    var onConfirm = function () {
      saveStructuredDocument(true, true, true, urlToOpen);
      return true;
    };
    RS.apprise('You are currently editing the document, please save it before leaving the page.', true,
      modalTargetElem, onConfirm, { title: 'Save current document', textOk: 'Save' });
  } else {
    RS.navigateTo(urlToOpen);
  }
}

//handle file upload via local computer or Dnd into doc editor, or in attachment info panels
function initGalleryFileUpload() {

  setUpFileUpload({
    url: '/gallery/ajax/uploadFile/',
    fileChooserId: '.fromLocalComputer',
    cancelButtonId: "#blockUICancel",
    progressFunction: RS.blockingProgressBar,
    formData: function () {
      // if uploading new version of existing attachment
      if (this.fileInput.hasClass("fileReplaceInput")) {
        return [{ name: 'selectedMediaId', value: this.fileInput.attr('mediaFileId') }];
      }
      // otherwise standard edit mode upload
      var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
      return [{ name: 'fieldId', value: fieldId }];
    },
    postDone: function (data) {
      // uploaded new version of existing attachment file
      if (data.fileInput.hasClass("fileReplaceInput")) {
        // retrieve latest attachment details and update current view
        _updateAttachedMediaFileWithLastestVersionDetails(data.fileInput.attr('mediaFileId'));
        var isGalleryDialog = $("#galleryContentDiv").hasClass('ui-dialog-content') && $("#galleryContentDiv").dialog('isOpen');
        var isRecordInfoDialog = $("#recordInfoDialog").hasClass('ui-dialog-content') && $("#recordInfoDialog").dialog('isOpen');
        if (isGalleryDialog) {
          gallery();
        } else if (isRecordInfoDialog) {
          $("#recordInfoDialog").dialog("close");
        }
        return;
      }

      // insert new file into editor
      var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
      var result = data.result.data;
      insertContentAfterFileUpload(fieldId, result);
    }
  });
}

// //handle chemistry file upload using DnD of a chem file
// // the process is:
// // 1. file uploaded by standard gallery file upload mechanism
// // 2. receive a response with the chemical
// // 3. create an iframe
// // 4. get an image of the chem, crop and scale it
// // 5. save the image
// // 6. insert them chem in tinyMCE
function insertChemElement(ecatChemFileId, fieldId, fileName) {
  var chemDto = {
    ecatChemFileId: ecatChemFileId,
    fieldId: fieldId,
    fullWidth: 1000,
    fullHeight: 1000,
    previewWidth: 300,
    previewHeight: 300
  };
  var result = $.Deferred();
  RS.postData('/chemical/ajax/createChemElement/', chemDto)
    .then((response) => {
      var data = response.data;
      if (data) {
        var milliseconds = new Date().getTime();
        var json = {
          id: data.rsChemElementId,
          ecatChemFileId: data.ecatChemFileId,
          fileName: data.fileName,
          sourceParentId: data.fieldId,
          width: chemDto.previewWidth,
          height: chemDto.previewHeight,
          fullwidth: chemDto.fullWidth,
          fullheight: chemDto.fullHeight,
          fieldId: data.fieldId,
          tstamp: milliseconds
        };
        RS.insertTemplateIntoTinyMCE("chemElementLink", json);
        result.resolve();
      } else if (response.error) {
        handleChemistryError(response, fileName);
        result.reject()
      }
    });
    return result;
}

function handleChemistryError(response, fileName) {
  RS.unblockPage();
  var errorMsg = "There was a problem creating chemical: " + fileName + " <br/>";
  console.error(response.error.errorMessages);
  if (response.error && response.error.errorMessages[0].includes('Index 0 out of bounds for length 0')) {
    errorMsg += "RSpace doesn't currently support the generation of Markush or R-Group structure images, you're file is stored in the gallery but cannot be inserted into a document";
  } else {
    errorMsg += response.error.errorMessages[0];
  }
  // Sticky toasts
  $().toastmessage('showToast', {
    text: errorMsg,
    type: 'error',
    stayTime: 8000
  });
}

//loads a css file into the dom
loadCSS = function (href) {
  var cssLink = $("<link>");
  $("head").append(cssLink); //IE hack: append before setting href

  cssLink.attr({
    rel: "stylesheet",
    type: "text/css",
    href: href
  });
};

$(document).ready(function () {
  function _updateNotebookRibbonVisibility() {
    if (!isDocumentEditor) {
      $("#notebook").journal("toggleEntriesRibbonDependingOnTreeVisibility");
    }
  }

  $(document).on('click', '#showFileTreeSmall', function () {
    expandPageAndShowFileTreeBrowser();
    var showTreeInEditor = isWindowWideEnoughForFileTreeBrowser() ? 'y' : 'a';
    updateClientUISetting('showTreeInEditor', showTreeInEditor);
    _updateNotebookRibbonVisibility();
    RS.trackEvent('ShowTreeInEditor', { showTreeInEditor: showTreeInEditor });
  });
  $(document).on('click', '#hideFileTreeSmall', function (e) {
    e.preventDefault();
    collapsePageAndHideFileTreeBrowser();
    updateClientUISetting('showTreeInEditor', 'n');
    _updateNotebookRibbonVisibility();
    RS.trackEvent('ShowTreeInEditor', { showTreeInEditor: 'n' });
  });

  /* let's start with narrow view */
  $('#page').addClass('treeBrowserNarrow');

  toggleFileTreeBrowserDependingOnWidthAndUISettings();
  $(window).resize(toggleFileTreeBrowserDependingOnWidthAndUISettings);

  RS.checkToolbarDividers(".toolbar-divider");

  initGalleryFileUpload();
});

window.addEventListener("ReactToolbarMounted", () => {
  $('#print').click(function (e) {
    e.preventDefault();
    window.print();
  });
});

