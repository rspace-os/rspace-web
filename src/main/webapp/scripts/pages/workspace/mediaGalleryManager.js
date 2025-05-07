/* jshint maxerr: 200 */

/**
 * Constants for media types
 */
var MEDIA_TYPE_IMAGES = "Images";
var MEDIA_TYPE_VIDEOS = "Videos";
var MEDIA_TYPE_AUDIOS = "Audios";
var MEDIA_TYPE_DOCS = "Documents";
var MEDIA_TYPE_CHEMISTRY = "Chemistry";
var MEDIA_TYPE_MISCDOCS = "Miscellaneous";
var MEDIA_TYPE_EXPORTED = "PdfDocuments";
var MEDIA_TYPE_SNIPPETS = "Snippets";
var MEDIA_TYPE_FILESTORES = "NetworkFiles";
var MEDIA_TYPE_DMPS = "DMPs";

var uploadedMediaInfo;

// var to signal if we have a search string applied.
var isInSearch = false; //This is used to keep track and reapply filters when sorting

/*
 * Google Drive variables
 */
// The Browser API key obtained from the Google Developers Console.
var gdDeveloperKey = '';
// The Client ID obtained from the Google Developers Console. Replace with your own Client ID.
var gdClientId = "";
// Scope to use to access user's files.
var gdScope = 'https://www.googleapis.com/auth/drive.file'
var gdPickerApiLoaded = false;
var gdOauthToken;

var chemistryAvailable = false;

function insertURL() {
  var url = $('#src').attr('value');
  var filename = $('#title').val();

  insertURLWithFilename(url, filename);
}

function insertURLWithFilename(url, filename) {
  if (filename === '') {
    var fileNameIndex = url.lastIndexOf("/") + 1;
    filename = url.substr(fileNameIndex);
  }
  var mediaType = $('#mediaTypeSelected').val();

  data = {
    url: url,
    filename: filename,
    mediatype: mediaType
  };

  var jqxhr = $.post(createURL("/gallery/ajax/uploadFileFromURL"), data,
    function (result) {
      if (result.data != null) {
        var milliseconds = new Date().getTime();
        var html = '<li class="fromURL" id="' + result.data.id +
          '"><img class="imageThumbnailURL" data=' +
          result.data.widthResized + '-' + result.data.heightResized +
          ' id="' + result.data.id + '" src="/gallery/getThumbnail/' +
          result.data.id + '/' + milliseconds + '" alt="image" />' + RS.escapeHtml(result.data.name) + '</li>';
        $('#listThumbnailsURL').append(html);
        $('#title').attr('value', '');
        $('#src').attr('value', '');
        $('#src').val('');
        $('#prevURL').html('');
        $('.insertURL').attr('disabled', 'disabled');
        $('.insertURL').button("disable");
      }
    });
  jqxhr.fail(function () {
    RS.ajaxFailed("Uploading image from URL", false, jqxhr);
  });
}

function _getCurrentFolderId() {
  return $('#currentFolderId').val();
}
// helper function for Box/Dropbox upload
function importURL(url, filename, source) {
  data = {
    url: url,
    filename: filename,
    targetFolderId: _getCurrentFolderId()
  };
  var jqxhr = $.post(createURL("/gallery/ajax/importFromURL"), data,
    function (result) {
      if (result.data != null) {
        RS.confirm('Import of ' + result.data.name + ' from ' + source + ' succeeded.', "success", 3000);
      }
    });
  jqxhr.fail(function () {
    RS.ajaxFailed("Importing file from " + source, false, jqxhr);
  });
}

//helper function for Box/Dropbox/1drive upload, does not popup messagees
function importURLQuiet(url, filename, source) {
  data = {
    url: url,
    filename: filename,
    targetFolderId: _getCurrentFolderId()
  };
  var jqxhr = $.post(createURL("/gallery/ajax/importFromURL"), data,
    function (result) {
      if (result.data != null) {
        console.log('Import of ' + result.data.name + ' from ' + source + ' succeeded.', "success", 3000);
      }
    });
  jqxhr.fail(function () {
    console.log("Importing file from " + source, false, jqxhr);
  });
  return jqxhr;
}

/**
 * Retrieve document content from nextcloud and import it into the Gallery
 * @param selectedNode Selected node object in FancyTree
 * @param nextcloudURL Base URL of nextcloud instance, e.g. https://my-nextcloud:8083
 * @param nextcloudUsername User's nextcloud username
 * @param nextcloudPassword User's nextcloud password
 * @returns
 */
function importFromNextcloudToGallery(selectedNodes, nextcloudURL, nextcloudServerName, nextcloudUsername, nextcloudPassword, nextcloudAccessToken) {
  RS.blockPage("Importing files from " + nextcloudServerName);

  // Assemble data needed for importFromURL call for each selected node
  var dataArray = selectedNodes.map(selectedNode => {
    var data = {
      url: nextcloudURL + "/remote.php/webdav" + encodeURI(selectedNode.data.path),
      filename: selectedNode.title,
      targetFolderId: _getCurrentFolderId(),
      username: nextcloudUsername,
    }

    if (nextcloudPassword != null) {
      data["password"] = nextcloudPassword;
    }
    if (nextcloudAccessToken != null) {
      data["accessToken"] = nextcloudAccessToken;
    }

    return data;
  });

  // Turn our array of data into an array of promises
  var postArray = dataArray.map(data => $.post(createURL("/gallery/ajax/importFromURL"), data));

  // Do all our imports and wait for them to complete before unblocking
  $.when.apply($, postArray).then(function (result) {
    RS.unblockPage();
    RS.confirm('Import from ' + nextcloudServerName + ' succeeded.', "success", 3000);
    gallery();
  }).fail(function () {
    RS.unblockPage();
    RS.ajaxFailed("Importing file from " + nextcloudServerName, false, jqxhr);
  });
}

/**
 * Retrieve document content from ownCloud and import it into the Gallery
 * @param selectedNode Selected node object in FancyTree
 * @param ownCloudURL Base URL of ownCloud instance, e.g. https://my-owncloud:8083
 * @param ownCloudUsername User's ownCloud username
 * @param ownCloudPassword User's ownCloud password
 * @returns
 */
function importFromOwnCloudToGallery(selectedNodes, ownCloudURL, ownCloudServerName, ownCloudUsername, ownCloudPassword, ownCloudAccessToken) {
  RS.blockPage("Importing files from " + ownCloudServerName);

  // Assemble data needed for importFromURL call for each selected node
  var dataArray = selectedNodes.map(selectedNode => {
    var data = {
      url: ownCloudURL + "/remote.php/webdav" + encodeURI(selectedNode.data.path),
      filename: selectedNode.title,
      targetFolderId: _getCurrentFolderId(),
      username: ownCloudUsername,
    }

    if (ownCloudPassword != null) {
      data["password"] = ownCloudPassword;
    }
    if (ownCloudAccessToken != null) {
      data["accessToken"] = ownCloudAccessToken;
    }

    return data;
  });

  // Turn our array of data into an array of promises
  var postArray = dataArray.map(data => $.post(createURL("/gallery/ajax/importFromURL"), data));

  // Do all our imports and wait for them to complete before unblocking
  $.when.apply($, postArray).then(function (result) {
    RS.unblockPage();
    RS.confirm('Import from ' + ownCloudServerName + ' succeeded.', "success", 3000);
    gallery();
  }).fail(function () {
    RS.unblockPage();
    RS.ajaxFailed("Importing file from " + ownCloudServerName, false, jqxhr);
  });
}

function importFromDropbox() {
  var options = {

    // Required. Called when a user selects an item in the Chooser.
    success: function (files) {
      var name = files[0].name;
      var url = files[0].link;
      importURL(url, name, 'Dropbox');
    },

    // Optional. Called when the user closes the dialog without selecting a
    // file
    // and does not include any parameters.
    cancel: function () { },

    // Optional. "preview" (default) is a preview link to the document for
    // sharing,
    // "direct" is an expiring link to download the contents of the file.
    // For more
    // information about link types, see Link types below.
    // linkType: "preview", // or "direct"
    linkType: "direct",

    // Optional. A value of false (default) limits selection to a single
    // file, while
    // true enables multiple file selection.
    multiselect: false, // or true

    // Optional. This is a list of file extensions. If specified, the user
    // will
    // only be able to select files with these extensions. You may also
    // specify
    // file types, such as "video" or "images" in the list. For more
    // information,
    // see File types below. By default, all extensions are allowed.
    // extensions: ['.pdf', '.doc', '.docx'],
  };
  Dropbox.appKey = 'fe4ktl7kwhho28d';
  Dropbox.choose(options);
}

function importFromBox() {
  var options = {
    clientId: 'ma1sc6u3qi51nhldr8dia7eljsil0ov4',
    linkType: 'direct',
    multiselect: false
  };
  var boxSelect = new BoxSelect(options);
  // Register a success callback handler
  boxSelect.success(function (response) {
    var url = response[0].url;
    var name = response[0].name;
    importURL(url, name, 'Box');
  });
  // Register a cancel callback handler
  boxSelect.cancel(function () { });
  boxSelect.launchPopup();
}

function importFromOneDrive() {
  var pickerOptions = {
    action: "download",
    success: function (files) {
      if (files.value.length > 0) {
        RS.blockPage("Importing files from OneDrive");
      }
      var fileCount = files.value.length;
      var import_requests = [];
      for (var i = 0; i < files.value.length; i++) {
        var fileName = files.value[i].name;
        /* @content.downloadUrl is described in online docs, but it seems like @microsoft.graph.downloadUrl is used instead */
        var link = files.value[i]["@content.downloadUrl"] || files.value[i]["@microsoft.graph.downloadUrl"];
        import_requests.push(importURLQuiet(link, fileName, 'OneDrive'));
      }
      $.when.apply($, import_requests).done(function (resp) {
        RS.confirm('Imported ' + fileCount + 'item(s)', "success", 3000);
        gallery(); // reload folder in case has new contents
      })
        .always(RS.unblockPage());
    },
    multiSelect: true //RSPAC-1743
  };

  openOneDriveFilePicker(pickerOptions);
}

function openOneDriveFilePicker(pickerOptions) {

  pickerOptions.clientId = oneDriveClientId;
  pickerOptions.advanced = { redirectUri: oneDriveRedirect };

  if (!pickerOptions.error) {
    pickerOptions.error = function () {
      apprise('An error occured when opening OneDrive file picker. Please try again or contact your System Admin.');
    }
  }

  try {
    OneDrive.open(pickerOptions);
  } catch (e) {
    apprise('There is a problem with RSpace OneDrive configuration. Please contact your System Admin.');
    console.log("onedrive.open() exception", e);
  }
}

function showPreviewImage(url) {
  // document.ready wrapper added to be sure that the .button() call happens
  // only after jQuery UI and Bootstrap are sorted out
  $(document).ready(function () {
    if (url == "") {
      $('#prevURL').html('');
      $('.insertURL').attr('disabled', 'disabled');
      $('#title').attr('value', '');
      return;
    }

    var img = document.createElement("img");

    $(img).error(function () {
      apprise('Image could not be retrieved from ' + url);
      return;
    });
    $(img).on("load", function () {
      if (img.height > 0) {
        $('#prevURL').html('<img id="previewImg" src="' + url + '" border="0"/>');
        $('.insertURL').removeAttr("disabled");
        $('.insertURL').button("enable");
        var fileNameIndex = url.lastIndexOf("/") + 1;
        var filename = url.substr(fileNameIndex);
        $('#title').attr('value', filename);
        $('#src').attr('value', url);
      } else {
        apprise("Image  from " + url + " has height of 0 - are you sure it's an image");
        return;
      }
    });

    $(img).attr("src", url);


  });
}

function sortThumbnails() {
  var mediaType = $("#mediaTypeSelected").val();
  if (mediaType != MEDIA_TYPE_FILESTORES) {
    gallery(isInSearch);
  } else {
     $('#topCrudopsHint').text("Hint: to apply new ordering reload the filestore by clicking on its name again.").show();
  }
}

function filterGallery() {
  var mediaType = $("#mediaTypeSelected").val();
  if (mediaType != MEDIA_TYPE_FILESTORES) {
    $('#galleryContentDiv').data('isFilter', 'true');
    gallery(true);
  }
}

function swapOrder() {
  if ($('#orderMediaGallery').hasClass('orderGalleryAsc')) {
    $("#orderMediaGallery").removeClass("orderGalleryAsc");
    $("#orderMediaGallery").addClass("orderGalleryDesc");
  } else {
    $("#orderMediaGallery").removeClass("orderGalleryDesc");
    $("#orderMediaGallery").addClass("orderGalleryAsc");
  }

  $("#orderMediaGallery").blur();
  sortThumbnails();
}

function truncateGalleryNames() {
  $.each($('#galleryTable .nameGallery.gallery'), function () {
    var $nameDiv = $(this);
    var $nameSpan = $nameDiv.find('.mediaFileName');
    var targetHeight = this.clientHeight || this.offsetHeight;
    var text = $nameSpan.text();
    var truncated = false;
    while (text.length > 8 && (this.scrollHeight > targetHeight)) {
      if (text.length > 40) {
        text = text.substring(0, 37); // quick truncate for very long names
      }
      text = text.substr(0, text.length - 1);
      $nameSpan.text(text + '...');
      truncated = true;
    }
    if (truncated) {
      /* sometimes scrollHeight calculations are off by a small bit, 
       * let's truncate one extra letter to be sure */
      text = text.substr(0, text.length - 1);
      $nameSpan.text(text + '...');
    }
  });
}

function getGalleryLegend(mediaType) {
  switch (mediaType) {
    case MEDIA_TYPE_MISCDOCS:
      return "Miscellaneous Gallery";
    case MEDIA_TYPE_EXPORTED:
      return "Exports Gallery";
    case MEDIA_TYPE_CHEMISTRY:
      return "Chemistry Gallery";
    default:
      // mediatype is in plural we want on singular
      return mediaType.substring(0, mediaType.length - 1) + " Gallery";
  }
}

/**
 * Loads gallery content. Following config options are read from $('#galleryContentDiv').data():
 * - workspaceParentFolderId
 * - currentFolderName
 * - isFilter if this is a new search - this will force page to be 0 for search results
 *
 * applySearch is an optional parameter that if true, takes the search filter's settings
 */
function gallery(applySearch) {
  var configSource = $('#galleryContentDiv');
  var config = {};
  config.workspaceFolderId = configSource.data('workspaceParentFolderId') || -1;
  config.currentFolderName = configSource.data('currentFolderName') || '';
  config.isFilter = configSource.data('isFilter') || '';

  $('#listThumbnails').html('');
  $('#listThumbnailsURL').html('');
  $('#galleryTable').html('');
  $('#filesPagination').html('');
  $('#fromURLPanel').hide();
  $('#mainGalleryPanel').show();
  $('.mediaSourceMenu').last().hide(100);

  // Show search in dialog only
  $(".ui-dialog .gallerySubBarRow .base-search").css('display', 'block');

  var mediaType = $('#mediaTypeSelected').val();
  $("#legendGallery").html(getGalleryLegend(mediaType));

  var currentFolderId = _getCurrentFolderId();
  var pageId = 0;
  if (!config.isFilter) {
    pageId = $('#pageId').val();
  }

  var sortOrder = "";
  if ($('#orderMediaGallery').hasClass('orderGalleryDesc')) {
    sortOrder = "DESC";
  } else {
    sortOrder = "ASC";
  }

  var orderByVal = $('#galleryOrderBy').val();
  var orderBy = "";
  if (orderByVal == "nameGallery") {
    orderBy = "name";
  } else if (orderByVal == "dateGallery") {
    orderBy = "modificationDate";
  }

  var searchTerm = "";
  var urlSearchTermMode = false;
  if (applySearch === true) {
    // check for gallery search field
    searchTerm = $('#gallery-search-input').val();
    if (!searchTerm) {
      // check for search term in url 
      searchTerm = $('#urlSearchTerm').val();
      if (searchTerm) {
        urlSearchTermMode = true;
      }
    }
    if (searchTerm) {
      searchTerm = searchTerm.trim();
    }
  } else {
    document.dispatchEvent(new Event('reset-search-input'));
  }
  $('#urlTermSearchModeInfoBar').toggle(urlSearchTermMode);
  $('#urlTermSearchModeTerm').text(searchTerm);
  $('#subBar').toggle(!urlSearchTermMode);
  isInSearch = applySearch === true;

  updateGalleryCrudMenu(); // reset select all button is this was previously 'none' in previous tab
  emptyInfo();

  data = {
    mediatype: mediaType,
    currentFolderId: currentFolderId,
    pageNumber: pageId,
    sortOrder: sortOrder,
    orderBy: orderBy,
    name: searchTerm
  };

  var url = createURL("/gallery/getUploadedFiles");
  var jqxhr = $.get(url, data, function (result) {
    if (result.data != null) {
      var html = "";
      var printParentFolder = !result.data.onRoot;
      var parentId = result.data.parentId;
      var grandparentId = result.data.itemGrandparentId;
      $('#currentFolderId').val(parentId);
      uploadedMediaInfo = result.data.items.results;
      $('#galleryContentDiv').data('isFilter', '');

      if (printParentFolder) {
        var parentFolderTemplate = $('#parentFolderTemplate').html();
        var parentFolderHtml = Mustache.render(parentFolderTemplate, { grandparentId: grandparentId });
        RS.appendMustacheGeneratedHtmlToElement(parentFolderHtml, '#galleryTable');
      }

      addToGalleryBreadcrumbs(parentId, config.currentFolderName);

      if (result.data.items.results.length > 0) {
        // print gallery elements
        $.each(result.data.items.results, function (i, val) {

          var elementTemplate, elementData;

          // common for all templates
          elementData = {
            valueId: val.id,
            valueName: val.name,
            valueType: val.type,
            valueShared: val.shared,
            shortFilename: val.name,
          };
          if (val.version > 1) {
            elementData.fileVersion = val.version;
          }

          var iconSrc = generateIconSrc(val.type, val.extension, val.thumbnailId, val.id);

          if (val.type.match(/Folder/)) {
            elementTemplate = $('#folderTemplate').html();
            elementData.isMediaFile = false;
            if (val.type.match(/System/)) {
              if(val.name.toLowerCase().indexOf("snippets")!=-1){
                elementData.apiFolder = false;
                elementData.valueShared =true;
              }
              else {
                elementData.apiFolder = true;
              }
              elementData.system = true;
            }
          } else if (val.type == 'Image') {
            elementTemplate = $('#ecatImageTemplate').html();
            elementData.isMediaFile = true;
            elementData.imgSrc = '/gallery/getThumbnail/' + val.id + '/' + val.modificationDate;
            elementData.modificationDate = val.modificationDate;
            elementData.widthResized = val.widthResized;
            elementData.heightResized = val.heightResized;
            elementData.rotation = val.rotation;
          } else if (val.type == 'Video' || val.type == 'Audio') {
            elementTemplate = $('#ecatAudioOrVideoTemplate').html();
            elementData.isMediaFile = true;
            elementData.valueExtension = val.extension;
            elementData.imgSrc = iconSrc;
            elementData.widthResized = val.widthResized;
            elementData.heightResized = val.heightResized;
            elementData.shortFilename = val.name;
          } else if (val.type == 'Documents' || val.type == 'Miscellaneous' || val.type == 'DMPs') {
            elementTemplate = $('#ecatDocumentOrMiscTemplate').html();
            elementData.isMediaFile = true;
            elementData.imgSrc = iconSrc;
            elementData.escapedName = RS.escapeHtml(val.name);
            elementData.extension = val.extension;

            //if (RS.isPdfPreviewSupported(val.extension)) {
            //    elementData.tryImgSrc = "/image/docThumbnail/" + val.id + '/' + (val.thumbnailId || 0);
            //}
          } else if (val.type == 'Snippet') {
            elementTemplate = $('#ecatSnippetTemplate').html();
            elementData.isMediaFile = false;
          } else if (val.type == MEDIA_TYPE_EXPORTED) {
            elementTemplate = $('#ecatPdfTemplate').html();
            elementData.isMediaFile = true;
            elementData.originalFileName = val.originalFileName;
            elementData.escapedName = RS.escapeHtml(val.name);
            elementData.imgSrc = iconSrc;
            elementData.extension = val.extension;
          } else if (val.type = MEDIA_TYPE_CHEMISTRY) {
            elementTemplate = $('#ecatChemistryTemplate').html();
            elementData.isMediaFile = true;
            elementData.imgSrc = '/gallery/getChemThumbnail/' + val.id + '/' + val.modificationDate;
            elementData.escapedName = RS.escapeHtml(val.name);
            elementData.extension = val.extension;
          }

          if (elementTemplate) {
            html += Mustache.render(elementTemplate, elementData);
          }
        }); // end of printing gallery elements loop

        RS.appendMustacheGeneratedHtmlToElement(html, '#galleryTable');

        // print pagination
        if (result.data.items.linkPages.length > 1) {
          var pagesHTML = "Pages: ";
          $.each(result.data.items.linkPages, function (i, val) {
            if (val.link != '#') {
              pagesHTML += " <span class='pagesSpan' onclick='goToPage(" + val.pageNumber + ")'>" + val.name + "</span> ";
            } else {
              pagesHTML += " <span class='currentPagespan'>" + val.name + "</span> ";
            }
          });
          $('#filesPagination').append(pagesHTML);
        }

        truncateGalleryNames();
        setDraggableProperty();
      } // end of printing elements for non-empty gallery

      // make sure these are always displayed
      $('#newFolderMediaGallery').show();
      $('#orderMediaGallery').show();
      $('#gallerySelectAll').show();
      $('#topCrudopsHint').hide();
      $('#topBar').show();

      if (mediaType === MEDIA_TYPE_IMAGES | mediaType === MEDIA_TYPE_CHEMISTRY) {
        populatePhotoswipeImageArray($("#galleryTable img.imageThumbnail"));
      }

      RS.emulateKeyboardDoubleClick('.galleryFolder');
      $('.galleryFolder').on('click tap', function () {
        var $thisFolder = $(this);
        openFolder($thisFolder.attr('id'), $thisFolder.attr('title'));
      });

      RS.emulateKeyboardClick('.imageThumbnail');
    }
  });

  jqxhr.fail(function () {
    RS.ajaxFailed("Getting files", false, jqxhr);
  });
}

function addToGalleryBreadcrumbs(folderId, folderName) {
  var displayName = folderName || $('#mediaTypeSelected').val();
  RS.addBreadcrumbAndRefresh("galleryBcrumb", "" + folderId, RS.escapeHtml(displayName));

  $('#breadcrumbTag_galleryBcrumb').find(".breadcrumbLink")
    .attr('href', function () {
      var folderId = $(this).attr('id').split('_')[1];
      return createURL('/gallery/' + folderId);
    })
    .on('click tap', function (e) {
      e.preventDefault();
      var folderId = $(this).attr('id').split('_')[1];
      openFolder(folderId);
    });
}

function url() {
  $('#mainGalleryPanel').hide();
  $('#fromURLPanel').show();
  $('.mediaSourceMenu').last().hide(100);
  $('#src').val('');
}

function moveElements(folder, elements) {
  var success;
  var folderId = $(folder).attr('id');
  var values = [];
  $.each(elements, function (index, value) {
    values.push($(value).attr('id'));
  });

  var data = {
    folderId: folderId,
    filesId: values,
    mediaType: $('#mediaTypeSelected').val()
  };

  $.ajaxSetup({ async: false });
  var jqxhr = $.post(createURL('/gallery/ajax/moveFiles'), data,
    function (result) {
      if (result.data == true) {
        $('#draggingContainer').remove();
        success = true;
      } else if (result.errorMsg != null) {
        apprise(getValidationErrorString(result.errorMsg));
        success = false;
      }
      $.ajaxSetup({ async: true });
    }).fail(function () {
      RS.ajaxFailed("Moving files", false, jqxhr);
    });
  return success;
}

function openFolder(id, name) {
  $('#parentId').val(_getCurrentFolderId());
  $('#currentFolderId').val(id);
  $('#pageId').val(0);
  $('#galleryContentDiv').data('currentFolderName', name);
  gallery();
}

function goToPage(page) {
  $('#pageId').val(page - 1);
  gallery();
}

function initMediaGalleryValues() {
  $('.mediaButtons').removeClass('ui-state-active');
  $("#newFolderMediaGallery").attr("disabled", false);
  $('#pageId').val(0);
  $('#currentFolderId').val(0);
  $('#parentId').val(0);
  $('#galleryOrderBy').val("");
  $('#listThumbnailsURL').val("");
  $('#gallery-search-input').val("");
  $('#urlSearchTerm').val("");
  $('#filesPagination').html('');
  initGalleriesCrudopsActions();
}

function setDraggableProperty() {
  $('.selectable').draggable({
    helper: function () {
      var selected = $('.selectable input:checked').parents('div.selectable');
      if (selected.length === 0) {
        selected = $(this);
        $('input.inputCheckbox', this).attr('checked', true);
      }
      var container = $('<div/>').attr('id', 'draggingContainer');
      container.append(selected.clone());
      return container;
    },
    stop: function () {
      $('.selectable input:checked').attr('checked', false);
    }
  });

  $('.dropTarget').droppable({
    tolerance: 'pointer',
    drop: function (event, ui) {
      RS.blockPage("Moving files");
      if (moveElements(this, ui.helper.children()) == true) {
        gallery();
      }
      RS.unblockPage();
    }
  });
}

function generateIconSrc(type, extension, thumbnailId, id) {
  if (type == 'Video' || type == 'Videos' || type == 'Audios' || type == 'Audio') {
    var iconSrc = RS.getIconPathForExtension(extension);
    if (iconSrc == "" && type == 'Video') {
      iconSrc = "/images/icons/video.png";
    }
    if (iconSrc == "" && type == 'Audio') {
      iconSrc = "/images/icons/audioIcon.png";
    }
  } else if (type == 'Documents' || type == 'Miscellaneous' || type == 'DMPs') {
    var iconSrc = "";
    if(type == 'Documents' & id != null) {
	   let suffix = (thumbnailId!=null)?thumbnailId:"none";
        iconSrc = createURL("/image/docThumbnail/" + id + "/" + suffix);
     } else {
      iconSrc = RS.getIconPathForExtension(extension) || "/images/icons/textBig.png";
    }
  } else if (type == MEDIA_TYPE_EXPORTED) {
    var iconSrc = "";
    // use generated thumbnail if possible, else use default image
    if (id != null) {
	  let suffix = (thumbnailId!=null)?thumbnailId:"none";
      iconSrc = createURL("/image/docThumbnail/" + id + "/" + suffix);
    } else {
      iconSrc = RS.getIconPathForExtension(extension);
    }
  } else if (type == MEDIA_TYPE_CHEMISTRY) {
    if (chemistryAvailable) {
      iconSrc = createURL("/gallery/getChemThumbnail/" + id);
    } else {
      iconSrc = RS.getIconPathForExtension(extension);
    }
  }
  return iconSrc;
}

function openNewFolderDialog() {
  $('#folderNameDialog').dialog('open');
}

var mediaGalleryInitialised = false;

function openMediaGalleryDialog(fieldId) {
  if (!mediaGalleryInitialised) {
    initMediaGallery();
    mediaGalleryInitialised = true;
  }
  $('#galleryContentDiv').dialog('open');
}

function initMediaGallery() {
  initMediaGalleryButtons();
  initMediaGalleryDialog();
  initFolderNameDialogFromMediaGallery();
  initVideoPreview();
}

/**
 * Creates 'insert from gallery' dialog in document editor
 */
function initMediaGalleryDialog() {
  $(document).ready(function () {
    RS.switchToBootstrapButton();
    $('#galleryContentDiv').dialog({
      modal: true,
      draggable: false,
      autoOpen: false,
      resizable: false,
      zIndex: 3001,
      width: 958,
      title: "Gallery",
      buttons: {
        Cancel: function () {
          $(this).dialog('close');
        },
        Insert: function () {
          addFromGallery();
          $('#galleryContentDiv').dialog('close');
          isInsertGalleryItemClickHandlerInitialized = true;
        }
      },
      open: function () {
        var tabSelected = $(this).data('tabSelected');

        $("#mediaTypeSelected").val(MEDIA_TYPE_IMAGES);
        initMediaGalleryValues();
        $('#listThumbnails').html('');
        $('#listThumbnailsURL').html('');
        $('#sourceSelector').hide();
        $('#prevURL').html('');
        $('#galleryTable').html('');
        $('#src').attr('value', '');

        if (tabSelected == "images") {
          selectImagesTab();
        } else if (tabSelected == "audios") {
          selectAudiosTab();
        } else if (tabSelected == "videos") {
          selectVideosTab();
        } else if (tabSelected == "docs") {
          selectDocsTab();
        } else if (tabSelected == "exports") {
          selectExportsTab();
        } else if (tabSelected == "chemistry") {
          selectChemistryTab();
        } else if (tabSelected == "miscellaneous") {
          selectMiscTab();
        } else if (tabSelected == "snippets") {
          selectSnippetsTab();
        }else if (tabSelected == "dmps") {
           selectDMPsTab();
        }
        gallery();

        initGalleriesCrudopsDialogs();

        // add overlay
        $('.ui-dialog-buttonpane').find('button:contains("Cancel")').css({ 'background-color': 'transparent' }).addClass('override-button-text-color');
        
        // make body unscrollable & center dialog
        $('body').css('overflow', 'hidden');
        $(this).css("max-height", "calc(100vh - 100px");
        $(this).parent().css('position', 'fixed');
        $(this).parent().css('left', '50%');
        $(this).parent().css('top', '50%');
        $(this).parent().css('transform', 'translate(-50%, -50%)');
      },
      close: function () {
        var parent = $('#areasDialog').parent();
        $('#areasDialog').remove();
        parent.append("<div id='areasDialog'><input class='dropareaDialog spotDialog' type='file' name='xfile' data-post='/gallery/ajax/uploadFile' data-width='500' data-height='200'/></div>");

        if (typeof isDocumentEditor !== 'undefined' && isDocumentEditor) {
          reloadPhotoswipeImageArray();
        }
        $('body').css('overflow', 'visible');
      }
    });
    RS.switchToJQueryUIButton();
  });
}

function selectImagesTab() {
  $('.imagelabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_IMAGES);
}

function selectAudiosTab() {
  $('.audiolabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_AUDIOS);
}

function selectVideosTab() {
  $('.videolabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_VIDEOS);
}

function selectDocsTab() {
  $('.documentlabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_DOCS);
}

function selectExportsTab() {
  $('.exportlabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_EXPORTED);
}

function selectChemistryTab() {
  $('.chemistryLabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_CHEMISTRY);
}

function selectDMPsTab() {
  $('.dmpLabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_DMPS);
}

function selectMiscTab() {
  $('.misclabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_MISCDOCS);
}

function selectSnippetsTab() {
  $('.snippetslabel').addClass('ui-state-active');
  $("#mediaTypeSelected").val(MEDIA_TYPE_SNIPPETS);
}

function initVideoPreview() {
  $(document).ready(function () {
    $('#light').dialog({
      modal: true,
      autoOpen: false,
      resizable: true,
      zIndex: 3001,
      height: "auto",
      width: 900,
      title: "Media Preview",
      buttons: {
        Close: function () {
          $(this).dialog('close');
        },
      },
      open: function () { },
      close: function () {
        var videocontainer = $("#videoContainer");
        videocontainer.remove();
      }
    });
  });
}

function initFolderNameDialogFromMediaGallery() {
  $(document).ready(function () {
    RS.switchToBootstrapButton();
    $("#folderNameDialog").dialog({
      resizable: false,
      autoOpen: false,
      modal: true,
      zIndex: 3002,
      create: function () {
        $(this).parent().addClass('bootstrap-custom-flat');
      },
      buttons: {
        Cancel: function () {
          $(this).dialog("close");
        },
        "OK": function () {
          var name = $('#inputFolderName').val();
          if (name != "") {
            createFolder(name);
            $('#inputFolderName').val('');
          }
          $(this).dialog("close");
        }
      }
    });
    RS.switchToJQueryUIButton();
    $('#folderNameDialog').keypress(function (e) {
      if (e.keyCode === 13) {
        $(this).parent().find(".ui-dialog-buttonset button:eq(1)").click();
      }
    });
  });
}

function initMediaGalleryButtons() {
  $(document).ready(function () {
    $('#gallerySelector').button();
    $('#sourceSelector').button();
    $('#typeMedia').buttonset().find('label').css('width', '100px');
    $('#sourceOptions').buttonset();
    $('.insertURL').button();


    document.getElementById('moveToIrods')?.addEventListener('click', () => {
      window.dispatchEvent(new CustomEvent("OPEN_IRODS_DIALOG", {
        detail: {
          ids: [...document.querySelectorAll('.inputCheckbox:checked')]
                           .map(n => n.dataset.recordid)
        }
      }));
    });

    initGalleriesCrudopsActions();
  });
}

function createFolder(name) {
  var data = {
    parentId: _getCurrentFolderId(),
    folderName: name,
    isMedia: "true",
  }
  $.ajaxSetup({ async: false });
  var jqxhr = $.post(createURL('/gallery/ajax/createFolder'), data,
    function (result) {
      if (result.data == true) {
        gallery();
      }
      $.ajaxSetup({ async: true });
    });

  jqxhr.fail(function () {
    RS.ajaxFailed("Creating folder", false, jqxhr);
  })
}

/*
 * Top level method to handle the insertion of a link to a Gallery item
 * into the document editor.
 * @param data - (optional) the record information for an item (returned by getRecordInformation)
 */
function addFromGallery(data) {
  // Insert any selected folders first
  insertFolders(data);

  var mediaTypeSelectedVal = (data != null) ? data.type + 's' : $("#mediaTypeSelected").val();

  //Values are hardcoded in different styles, so it has to be hacky
  if (mediaTypeSelectedVal == MEDIA_TYPE_MISCDOCS + "s") {
    mediaTypeSelectedVal = MEDIA_TYPE_MISCDOCS;
  }

  switch (mediaTypeSelectedVal) {
    case MEDIA_TYPE_IMAGES:
      insertImagesFromGallery(data);
      break;
    case MEDIA_TYPE_VIDEOS:
    case MEDIA_TYPE_AUDIOS:
      insertAVFromGallery(mediaTypeSelectedVal, data);
      break;
    case MEDIA_TYPE_DOCS:
    case MEDIA_TYPE_EXPORTED:
    case MEDIA_TYPE_MISCDOCS:
    case MEDIA_TYPE_DMPS:
      insertGenericDoc(mediaTypeSelectedVal, data);
      break;
    case MEDIA_TYPE_CHEMISTRY:
      if (chemistryAvailable) {
        insertChemistryFileFromGallery(mediaTypeSelectedVal, data);
      } else {
        insertGenericDoc(mediaTypeSelectedVal, data);
      }
      break;
    case MEDIA_TYPE_SNIPPETS:
      insertSnippetFromGallery(data);
      break;
    case MEDIA_TYPE_FILESTORES:
      insertNetFileFromGallery();
      break;
  }
}

/*
 * Import folders from gallery to tinymce, irrelevent to their gallery type
 */
function insertFolders(data) {
  var ed = tinymce.activeEditor;

  if (data && (data.type === 'Folder')) {
    RS.tinymceInsertInternalLink(data.id, data.oid.idString, data.name, ed);
    return;
  }

  // no specific folder provided, iterate over selected checkboxes
  $('.selectable .folderCheckbox:checked').each(function () {
    var id = $(this).data('recordid');
    var globalId = 'GF' + id;
    var recordName = $(this).data('recordname');
    $(this).prop('checked', false); // set to unchecked so that other functions are not confused
    RS.tinymceInsertInternalLink(id, globalId, recordName, ed);
  });
}

/*
 * Import image(s) from gallery to tinymce
 * @param data - the record information for an item(returned by getRecordInformation)
 */
function insertImagesFromGallery(data) {
  var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
  var selected = (data != null) ? data : $('.selectable input:checked').parents('div.selectable');
  var fromPaste = (data != null);

  $(selected).each(function (index, val) {
    var rotation = 0;
    if (!fromPaste) {
      var $img = $('.imageThumbnail', val);
      var data = $img.attr("data");
      var rotation = $img.attr("data-rotation") || 0;
    }

    var json = {};
    var id = fromPaste ? val.id : $img.attr("id");
    var name = fromPaste ? val.name : $img.attr("title");
    var height = fromPaste ? val.heightResized : data.split("-")[1];
    var width = fromPaste ? val.widthResized : data.split("-")[0];
    var modificationDate = fromPaste ? val.modificationDate : $img.data("modificationdate");

    var json = {
      milliseconds: modificationDate,
      itemId: id,
      name: name,
      fieldId: fieldId,
      width: width,
      height: height,
      rotation: rotation
    }
    
    RS.insertTemplateIntoTinyMCE('#insertedImageTemplate', json);
  });
}

/*
 * @param mediaType - either MEDIA_TYPE_AUDIOS or MEDIA_TYPE_VIDEOS
 * @param data - the record information for an item(returned by getRecordInformation)
 */
function insertAVFromGallery(mediaType, data) {
  var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
  var selected = (data != null) ? data : $('.selectable input:checked').parents('div.selectable');
  var fromPaste = (data != null);

  $(selected).each(function (index, val) {
    if (!fromPaste) {
      var f = $('.imageThumbnail', val);
      var dataVideo = $(f).attr("data-video");
      var iconSrc = $(f).attr("src");
      var data = dataVideo.split(",");
    }

    var id = fromPaste ? val.id : data[0];
    var filename = fromPaste ? val.name : data[1];
    var extension = fromPaste ? val.extension : data[2];
    var iconSrc = fromPaste ? generateIconSrc(mediaType, extension, val.thumbnailId) : $(f).attr("src");
    var videoHTML = isPlayableOnJWPlayer(extension) ? setUpJWMediaPlayer(id, filename, extension) : "";
    var imgClass = (mediaType == MEDIA_TYPE_VIDEOS) ? "videoDropped" : "audioDropped";

    var json = {
      compositeId: fieldId + "-" + id,
      imgClass: imgClass,
      videoHTML: videoHTML,
      iconSrc: iconSrc,
      filename: filename,
      extension: extension,
      id: id
    };

    RS.insertTemplateIntoTinyMCE('#avTableForTinymceTemplate', json);
  });
}

/*
 * @param mediaType - either MEDIA_TYPE_DOCS, MEDIA_TYPE_EXPORTED, or MEDIA_TYPE_MISCDOCS
 * @param data - the record information for an item(returned by getRecordInformation)
 */
function insertGenericDoc(mediaType, data) {
  var templateId = (mediaType == MEDIA_TYPE_DOCS) ?
      "#insertedDocumentTemplate" : "#insertedMiscdocTemplate";
  var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
  var selected = (data != null) ? data : $('.selectable input:checked').parents('div.selectable');
  var fromPaste = (data != null);

  $(selected).each(function (index, val) {
    var f = $('.imageThumbnail', val);
    var docId = fromPaste ? val.id : $(f).attr("id");
    var docName = fromPaste ? val.name : $(f).attr("data-name");
    var docNameParts = docName.split(".");
    var extension = fromPaste ? val.extension : docNameParts[docNameParts.length - 1];
    var iconPath = generateIconSrc(mediaType, extension, val.thumbnailId, val.id);
    var json = { id: docId, name: docName, iconPath: iconPath };
    RS.insertTemplateIntoTinyMCE(templateId, json);
  });
}

function insertChemistryFileFromGallery(mediaType, data) {
  var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
  var selected = (data != null) ? data : $('.selectable input:checked').parents('div.selectable');
  RS.blockPage("Inserting Chemical...");
  var promises = [];
  $(selected).each(function (index, val) {
    var fileName = val.children.item(1).children.item(0).getAttribute("data-recordname");
    promises.push(insertChemElement(val.id, fieldId, fileName));
  });
  Promise.all(promises)
  .then(() => RS.trackEvent("user:add:chemistry_object:document", { from: "gallery" }))
  .catch((error) => {
    console.error("Error while inserting chemicals from Gallery:", error);
  })
  .finally(() => {
    RS.unblockPage();
  })
}

function insertSnippetFromGallery(data) {
  var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
  var selected = $('.selectable input:checked').parents('div.selectable');
  var fromPaste = (data != null);

  // asynchronous, so when adding more than one snippet they might be inserted
  // in random order
  $(selected).each(function (index, val) {
    var id = fromPaste ? val.id : $('.imageThumbnail', val).attr('id');
    var jqxhr = $.post("/snippet/insertIntoField", {
      snippetId: id,
      fieldId: fieldId
    });
    jqxhr.done(function (result) {
      if (result) {
        RS.tinymceInsertContent(result);
        RS.clearBreadcrumb("galleryBcrumb");
      }
    });
    jqxhr.fail(function (result) {
      alert("An error occured on inserting the snippet " + id);
    });
  });
}

function insertNetFileFromGallery() {
  var selected = $('.nfsCheckbox:checked');
  $(selected).each(function (index, val) {
    var path = $(this).data('path');
    if (!path || path.indexOf(':') < 0) {
      alert('unparseable path attribute: ' + path);
      return;
    }
    var sepId = path.indexOf(':');
    var fileStoreId = path.substr(0, sepId);
    var relFilePath = path.substr(sepId + 1);

    var json = {
      name: $(this).data('name'),
      linktype: $(this).data('linktype'),
      fileStoreId: fileStoreId,
      relFilePath: relFilePath,
      nfsId: $(this).data('nfsid'),
      nfsType: fileSystemType
    };
    RS.insertTemplateIntoTinyMCE('netFilestoreLink', json);
  });
}

function openMedia(id, name, extension) {

  var filepath = "/Streamfile/" + id;

  var init = '<div id="container"><span class="ui-icon ui-icon-info infoVideo zoomClassPercent" style="float: left; margin-right: .3em;"></span>' +
    '<div class="toggler">  <div id="effect" class="ui-widget-content ui-corner-all"><h3 class="ui-widget-header ui-corner-all">Information</h3> <p>' +
    'Your browser cannot play this video format, but your computer might be able to.  To try this, download the original file from the link above and open it.' +
    '</p></div></div><br><br>'

  // maybe it could play wma too
  if (extension == 'wmv') {
    html = init + '<object width="100%" height="100%" type="video/x-ms-asf" url="' + filepath + '" data="' + filepath + '"classid="CLSID:6BF52A52-394A-11d3-B153-00C04F79FAA6">' +
      '<param name="url" value="' + filepath + '"><param name="filename" value="' + filepath + '"><param name="autostart" value="0"><param name="uiMode" value="full"><param name="autosize" value="1"><param name="playcount" value="1">' +
      '<embed type="application/x-mplayer2" src="' + filepath + '"" width="100%" height="100%" autostart="false" showcontrols="true" pluginspage="http://www.microsoft.com/Windows/MediaPlayer/"></embed></div>';

  } else if (extension == 'mov' || extension == 'avi') {
    html = init + '<object id="videoContainer" classid="clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B"> <param name="src" value="' + filepath + '"> <param name="autoplay" value="true"> <param name="controller" value="true">' +
      '<embed src="' + filepath + '" autoplay="false" controller="true"></embed> </object></div>';

  } else if (extension == 'mp4' || extension == 'flv') {
    html = init + '<div id="videoContainer">Loading the player ...</div></div>';
  } else if (extension == 'wma' || extension == 'wav') {
    // html = init +'<div id="videoContainer">Loading the player
    // ...</div></div>';
    html = init + '<EMBED id="videoContainer" CONTROLS=CONSOLE SRC="' + filepath + '" AUTOSTART=false WIDTH=344 HEIGHT=160 LOOP=1>';
  } else if (extension == 'acc' || extension == 'mp3' || extension == 'ogg') {
    html = init + '<div id="videoContainer">Loading the player ...</div></div>';
  } else {
    html = '<div id="text_content"> <br /> <div class="ui-state-error ui-corner-all messages" id="errorMessages"> <p> <span class="ui-icon ui-icon-info zoomClassPercent"' +
      'style="float: left; margin-right: .3em;"></span> Your browser might not play this video format, but your computer might be able to.  To try this, download the original file from the link above and open it.</p></div></div>';
  }

  $("#light").html(html);
  var milliseconds = new Date().getTime();
  $("#light").append('<p><pre>' + name + '</p>');
  $("#light").append('<p><pre><a id="lightbox_download" href="' + filepath + '" target="_blank" download="' + milliseconds + '_' + name + '" >Download</a></pre></p>');

  if (isPlayableOnJWPlayer(extension)) {

    // Video player just need a name with a extension to know how play it.
    // On the server /StreamFile/Id/Name it does not use name anymore.
    // I did not change it because in previous version of RS could be videos
    // or audios already linked
    var file = createURL("/Streamfile/" + id + "/" + name);

    jwplayer("videoContainer").setup({
      flashplayer: "/scripts/player.swf",
      file: file,
      height: 270,
      width: 480
    });
    $("#videoContainer").find('[name="allowscriptaccess"]').attr('value', 'never');
  }

  $('#lightbox_download').button();
  $('#lightbox_close').button();
  $(".infoVideo").click(function () {
    $("#effect").show('size', 500);
    return false;
  });

  $("#effect").hide();
  $('#light').dialog('open');
}

function getMediaPlayerHTML(id, name, extension) {
  var html = ""
  var milliseconds = new Date().getTime();
  if (isPlayableOnJWPlayer(extension)) {
    html = '<div class="mediaDiv" style="border:1px solid; padding:3px; margin: 3px"><div class="videoTemp" id="videoContainer_' +
      id + '_' + milliseconds + '">Loading the player ...</div></div>';
  }
  return html;
}

function clickImageGallery() {
  _changeGallery(MEDIA_TYPE_IMAGES);
}

function clickAudioGallery() {
  _changeGallery(MEDIA_TYPE_AUDIOS);
}

function clickVideoGallery() {
  _changeGallery(MEDIA_TYPE_VIDEOS);
}

function clickDocsGallery() {
  _changeGallery(MEDIA_TYPE_DOCS);
}

function clickMiscGallery() {
  _changeGallery(MEDIA_TYPE_MISCDOCS);
}

function clickChemistryGallery() {
  _changeGallery(MEDIA_TYPE_CHEMISTRY);
}
function clickDMPGallery() {
  _changeGallery(MEDIA_TYPE_DMPS);
}

function clickPdfGallery() {
  _changeGallery(MEDIA_TYPE_EXPORTED);
}

function clickSnippetsGallery() {
  _changeGallery(MEDIA_TYPE_SNIPPETS);
}

function _changeGallery(mediaTypeSelected) {
  _resetElementsAfterChangingGallery(mediaTypeSelected);
  gallery();
}

function _resetElementsAfterChangingGallery(mediaTypeSelected) {

  initMediaGalleryValues();
  $("#mediaTypeSelected").val(mediaTypeSelected);

  $('#listThumbnails').html('');
  $('.mediaMenu').last().hide(100);
  emptyInfo();

  hideGalleryViewer();
  RS.clearBreadcrumb("galleryBcrumb");
}

/*
 * the method loads net file stores panel into gallery, it's a bit more complex
 * as we download additional js and jsp view
 */
function clickNetFileStoresGallery() {

  RS.blockPage("");

  var jqxhrScript = $.getScript('/scripts/pages/workspace/gallery/netfiles.js');
  var jqxhrPage = $.get('/netFiles/ajax/netFilesGalleryView');

  $.when(jqxhrScript, jqxhrPage).done(function (script, page) {

    _resetElementsAfterChangingGallery(MEDIA_TYPE_FILESTORES);
    $('#newFolderMediaGallery').hide();
    $('#orderMediaGallery').hide();
    $('#gallerySelectAll').hide();
    $(".gallerySubBarRow .base-search").hide();


    $('#legendGallery').html("Filestores");
    $('#galleryTable').html(page[0]);
    $("#nfsInfoPanel").appendTo("#infoPanel");

    /* init method defined in downloaded netfiles.js script that is loaded at this point  */
    initNetFilesGalleryPage();

  }).fail(function (jqxhr, settings, exception) {
    apprise('An error occured on loading net files gallery');
  }).always(function () { 
    RS.unblockPage(); 
  });
}

var isInsertGalleryItemClickHandlerInitialized = false;

$(document).ready(function () {
  console.log("in mgm.js doc ready..");

  // Hides a visible menu when mouse clicked outside the menu area.
  $(document).on("click tap", "body", function () {
    $(".visibleMenu").fadeOut(200, function () {
      $(this).removeClass("visible");
    });
  });

  // Show and enable hiding the gallery menu on the media gallery
  $(document).on("click tap", "#gallerySelector", function (event) {
    console.log("Handling gallery selector event");
    event.stopPropagation();
    $(".visibleMenu").fadeOut(200, function () {
      $(this).removeClass("visible");
    });
    $('.mediaMenu').last().toggle(100).addClass("visibleMenu");
  });

  // Show and enable hiding the source menu on media gallery.
  $(document).on("click tap", "#sourceSelector", function (event) {
    event.stopPropagation();
    $(".visibleMenu").fadeOut(200, function () {
      $(this).removeClass("visible");
    });
    $('.mediaSourceMenu').last().toggle(100).addClass("visibleMenu");
  });

  // Insert the file if using the Insert option button
  if (!isInsertGalleryItemClickHandlerInitialized) {
    $('#galleryContentDiv').on("click tap", "#galleriesCrudInsert", function (event) {
      event.preventDefault();
      event.stopPropagation();
      addFromGallery();
      $('#galleryContentDiv').dialog('close');
    });
    isInsertGalleryItemClickHandlerInitialized = true;
  }

  $(document).on("click tap", "#fromLocalComputer", function (e) {
    e.preventDefault();
    $("#fileBrowserDialog").trigger('click');
  });

  /*$(document).on("click tap", "#fromLinkURL", function (e) {
    e.preventDefault();
    url();
  });*/
  $(document).on('click tap', ".insertURL", function (e) {
    e.preventDefault();
    insertURL();
  });

  var propertiesRequest = $.get("/deploymentproperties/ajax/properties");
  var integrationsRequest = $.get("/integration/allIntegrations");
  //    integrationsRequest.done(function(allIntegrations) {

  let requestsPromise = $.when(propertiesRequest, integrationsRequest);3
  requestsPromise.done(function (propertiesResponse, allIntegrations) {

    if (!allIntegrations[0].data) {
      console.warn('no data from allIntegrations request');
      return;
    }

    var properties = propertiesResponse[0];

    var integrations = allIntegrations[0].data;
    var dropboxEnabled = integrations.DROPBOX.enabled && integrations.DROPBOX.available;
    var boxEnabled = integrations.BOX.enabled && integrations.BOX.available;
    var oneDriveEnabled = integrations.ONEDRIVE.enabled && integrations.ONEDRIVE.available;
    var googleDriveEnabled = integrations.GOOGLEDRIVE.enabled && integrations.GOOGLEDRIVE.available;
    var nextcloudEnabled = integrations.NEXTCLOUD.enabled && integrations.NEXTCLOUD.available && properties["nextcloud.url"] !== '';
    var ownCloudEnabled = integrations.OWNCLOUD.enabled && integrations.OWNCLOUD.available && properties["ownCloud.url"] !== '';
    chemistryAvailable = integrations.CHEMISTRY.available;

    if (dropboxEnabled) {
      $('#fromDropbox').show();
      $(document).on("click tap", "#fromDropbox", function () {
        appriseNoConnection("Dropbox"); // temporary until script is loaded
      });
      $.getScript("https://www.dropbox.com/static/api/2/dropins.js", function () {
        $(document).off("click", "#fromDropbox");
        $(document).on("click tap", "#fromDropbox", function (e) {
          e.preventDefault();
          importFromDropbox();
        });
      });
    }

    if (boxEnabled) {
      $('#fromBox').show();
      $(document).on("click tap", "#fromBox", function () {
        appriseNoConnection("Box");
      });
      $.getScript("https://app.box.com/js/static/select.js", function () {
        $(document).off("click", "#fromBox");
        $(document).on("click tap", "#fromBox", function (e) {
          e.preventDefault();
          importFromBox();
        });
      });
    }

    if (oneDriveEnabled) {
      $('#fromOneDrive').show();
      $(document).on("click tap", "#fromOneDrive", function () {
        appriseNoConnection("OneDrive");
      });
      $.getScript("//js.live.net/v7.2/OneDrive.js", function () {
        $(document).off("click", "#fromOneDrive");
        $(document).on("click tap", "#fromOneDrive", function (e) {
          e.preventDefault();
          importFromOneDrive();
        });
      });
    }

    if (googleDriveEnabled) {
      $.getScript("https://accounts.google.com/gsi/client?onload=onGoogleDriveAPILoad");
      gdDeveloperKey = properties["googledrive.developer.key"];
      gdClientId = properties["googledrive.client.id"];     
    }

    if (nextcloudEnabled) {
      $('#fromnextcloud').html('From ' + properties["nextcloud.server.name"]);
      $('#fromnextcloud').show();
      $(document).on("click tap", "#fromnextcloud", function (e) {
        e.preventDefault();
        authenticateOwnCloud(importFromNextcloudToGallery, "Import", false, properties["nextcloud.url"], properties["nextcloud.server.name"], properties["owncloud.auth.type"]);
      });
    }

    if (ownCloudEnabled) {
      $('#fromOwnCloud').html('From ' + properties["owncloud.server.name"]);
      $('#fromOwnCloud').show();
      $(document).on("click tap", "#fromOwnCloud", function (e) {
        e.preventDefault();
        authenticateOwnCloud(importFromOwnCloudToGallery, "Import", false, properties["owncloud.url"], properties["owncloud.server.name"], properties["owncloud.auth.type"]);
      });
    }

  });

  $(document).on('click tap', "#newFolderMediaGallery", function (e) {
    e.preventDefault();
    openNewFolderDialog();
  });

  $(document).on('click tap', "#orderMediaGallery", function (e) {
    e.preventDefault();
    swapOrder();
  });

  $(document).on("click tap", ".documentButton", function (e) {
    e.preventDefault();
    clickDocsGallery();
  });
  $(document).on("click tap", ".exportButton", function (e) {
    e.preventDefault();
    clickPdfGallery();
  });
  $(document).on("click tap", ".snippetButton", function (e) {
    e.preventDefault();
    clickSnippetsGallery();
  });
  $(document).on("click tap", ".videoButton", function (e) {
    e.preventDefault();
    clickVideoGallery();
  });
  $(document).on("click tap", ".audioButton", function (e) {
    e.preventDefault();
    clickAudioGallery();
  });
  $(document).on("click tap", ".imageButton", function (e) {
    e.preventDefault();
    clickImageGallery();
  });
  $(document).on("click tap", ".miscButton", function (e) {
    e.preventDefault();
    clickMiscGallery();
  });
  $(document).on("click tap", ".chemistryButton", function (e) {
    e.preventDefault();
    clickChemistryGallery();
  });
  $(document).on("click tap", ".netButton", function (e) {
    e.preventDefault();
    clickNetFileStoresGallery();
  });
  $(document).on("click tap", ".dmpButton", function (e) {
    e.preventDefault();
    clickDMPGallery();
  });

  $(document).on("click tap", ".imagePreview", function (e) {
    e.preventDefault();
    var id = $(this).attr("id");

    showInfoInGallery(id);
    //showImagePreview(id, name);
  });

  $(document).on("click tap", ".snippetPreview", function () {
    var id = $(this).attr("id");
    var name = $(this).attr("name");

    showInfoInGallery(id);
    showSnippetPreview(id, name);
  });

  $(document).on("click tap", ".pdfPreview, .documentOrMisc", function (e) {
    e.preventDefault();
    var id = $(this).data("id");
    var name = $(this).data("name");
    var extension = $(this).data("extension");

    showInfoInGallery(id);

    if (RS.isPdfPreviewSupported(extension)) {
      showPdfPreview(id, name, extension);
    }
  });

  $(document).on("click tap", "#closeViewer", function (e) {
    e.preventDefault();
    hideGalleryViewer();
  });

  var orderMediaGallryBtn = $('#orderMediaGallery').prop("disabled", true);
  $('#galleryOrderBy').on('change', function () {
    orderMediaGallryBtn.prop("disabled", this.value.length === 0);
  });

  // Toggle item's checkbox on click/tap outside of icon/checkbox/info
  $(document).on('click tap', '.selectable', function (e) {
    var target = $(e.target);

    if (target.is('img') || target.hasClass('infoImg')) return;

    if (!target.hasClass('inputCheckbox')) {
      var checkBox = $(this).find(".inputCheckbox");
      checkBox.prop("checked", !checkBox.prop("checked"));
    }
    showInfoInGallery($(this).prop('id'));
    updateGalleryCrudMenu();
  });

  /**
   * Handler for Get Info to display file information in the media gallery
   */
  $(document).on("click tap", ".infoImg", function () {
    var selectedId = $(this).attr('data-recordId');
    showInfoInGallery(selectedId);
  });

});

function appriseNoConnection(provider) {
  apprise("Unable to contact" + provider + ".  Please try again later or ensure that your network connection is active and reload the page.");
}

function onGoogleDriveAPILoad() {
  gapi.load('auth2', {});
  gapi.load('picker', { 'callback': onGoogleDrivePickerApiLoad });
}

function onGoogleDrivePickerApiLoad() {
  gdPickerApiLoaded = true;
}

function showGalleryViewer() {
  $("#viewerContainer").show();
  $(".galleryContent").append($("#viewerContainer"));
  $(".galleryContent").scrollTop(0); // FF needs it
  $("#galleryTable").hide();
}

function hideGalleryViewer() {
  $("#viewerContainer").hide();
  $("#galleryTable").show();
}

function showSnippetPreview(id, name) {
  var url = "/snippet/content/" + id;
  var jqxhr = $.get(url);

  jqxhr.done(function (result) {
    if (result) {
      $("#nameViewerImage").text(name);
      $("#viewerContent").html(result);
      $("#viewerContent").css("text-align", "left");
      showGalleryViewer();
    }
  });
  jqxhr.fail(function (result) {
    apprise("An error occured on retrieving snippet " + id);
  });
}

function showPdfPreview(id, name, extension) {
  $("#nameViewerImage").text(name);
  $("#viewerContent").html('');
  showGalleryViewer();

  RS.loadPdfPreviewIntoDiv(id, null, name, extension, $("#viewerContent"));
}

/*
 * This is show info for Gallery
 */
function showInfoInGallery(selectedId) {
  var $infoPanel = $("#infoPanel").empty();
  var info = null;

  if (uploadedMediaInfo) {
    $.each(uploadedMediaInfo, function (i, val) {
      if (val.id == selectedId) {
        info = val;
        return;
      }
    });
  }
  if (info === null) {
    return;
  }
  var $recordInfoPanelDiv = generate$RecordInfoPanel(info);
  var $infoPanelContent = $('#galleryInfoPanelTemplate > .galleryInfoPanel').clone();
  $infoPanelContent.find('.recordInfoPanel').replaceWith($recordInfoPanelDiv);

  $infoPanel.append($infoPanelContent);
}

function emptyInfo() {
  $("#infoPanel").empty();
}

function isGalleryOpenedFromEditorDialog() {
  return $('#galleryContentDiv').hasClass('ui-dialog-content');
}
