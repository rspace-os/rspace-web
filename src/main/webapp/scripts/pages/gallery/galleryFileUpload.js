var cancelRequested = false;
var currFileJxQr = null;
var fileCount = 0;
var toCancel = [];
var totalFiles = 0;
var errorCount = 0;

/*
 * Sets up file uploader using jQuery file upload plugin.
 * @param config a hash of key:value configurations
 *
 */
function setUpFileUpload(config) {
  var url = config.url;
  var fileUploadId = config.fileChooserId;
  var cancelButtonId = config.cancelButtonId;
  var progressBar = config.progressFunction;
  var postDone = config.postDone || function (data) {};
  var postStop = config.postStop || function (event) {};
  var formData = config.formData || [];

  $(document).on("click", cancelButtonId, function (e) {
    RS.confirm("File upload cancellation attempted...", "success", 3000);
    cancelRequested = true;
    console.log("attempting to cancel  " + toCancel.length + " uploads.");
    for (var i = 0; i < toCancel.length; i++) {
      toCancel[i].abort();
    }
    progressBar.hide();
    _resetFileUploader();
  });

  $(fileUploadId).fileupload({
    dataType: "json",
    url: url,
    add: function (e, data) {
      var uploadErrors = [];
      totalFiles = data.originalFiles.length;
      if (data.files[0]["size"] && data.files[0]["size"] > maxFileSize) {
        uploadErrors.push(
          "File " + data.files[0].name + " is too big (" + RS.humanFileSize(data.files[0]["size"]) + "), maximum individual size is " + RS.humanFileSize(maxFileSize) + "."
          );
      }
      if (uploadErrors.length > 0) {
        _hideProgressIfAllError(progressBar);
        RS.confirm(uploadErrors.join("\n"), "error", 5000);
      } else if (!cancelRequested) {
        console.log("Submitting file " + data.files[0].name);
        progressBar.show({
          msg: "Uploading...",
          showCancel: "true",
          progressType: "any",
        }); // just show if at least 1 file can be submitted
        currFileJxQr = data.submit();
        toCancel.push(currFileJxQr);
      } else {
        console.log(data.files[0].name + " is cancelled");
      }
    },
    paramName: "xfile",
    sequentialUploads: true,
    formData: formData,
    // at end of all uploads
    stop: function (e) {
      progressBar.hide();
      if (e.target.className == "chemFromLocalComputer") {
        RS.blockPage("Inserting chem files...");
      } else {
        if (fileCount === 1) {
          RS.confirm(fileCount + " file uploaded", "success", 4000);
        } else {
          RS.confirm(fileCount + " files uploaded", "success", 4000);
        }
      }
      postStop(e);
      _resetFileUploader();
    },
    // after each upload
    done: function (e, data) {
      var result = data.result.data;
      if (result) {
        progressBar.message(
          `Uploaded ${
            result.hasOwnProperty("name") ? result.name : result.fileName
          }`
        );
        fileCount++;
      } else {
        RS.confirm(
          getValidationErrorString(data.result.errorMsg),
          "error",
          6000
        );
        _hideProgressIfAllError(progressBar);
      }
      postDone(data);
    },
    // on each fail
    fail: function (e, data) {
      _hideProgressIfAllError(progressBar);
      if (data.textStatus === "abort") {
        console.info("File upload cancelled");
      } else {
        if (data.jqXHR.responseText) {
          var obj = $.parseJSON(data.jqXHR.responseText);
          data.jqXHR.responseText = obj.exceptionMessage;
          RS.ajaxFailed("File upload", false, data.jqXHR);
        } else {
          apprise(
            "Couldn't upload to server - possibly either a folder (not supported), or too big (" +
              RS.humanFileSize(data.total) +
              ")"
          );
        }
      }
    },
    progressall: function (e, data) {
      var progress = parseInt((data.loaded / data.total) * 100, 10);
      progressBar.showProgress(progress);
    },
    progressInterval: 200,
    bitrateInterval: 200,
    dropZone: null /* dropzones handled by the calling page */,
  });
}

function _hideProgressIfAllError(progressBar) {
  errorCount++;
  if (errorCount >= totalFiles) {
    console.info("all files are errors, resetting");
    progressBar.hide();
    _resetFileUploader();
  }
}

function _resetFileUploader() {
  cancelRequested = false;
  fileCount = 0;
  toCancel = [];
  errorCount = 0;
  totalFiles = 0;
}

/*
 * droppable area handling
 */
var _areaMarkedAsDroppable = false;
var _areaMarkedAsDroppableTimeoutId;

function markAreaDroppable(areaSelector, manualCleanup) {
  if (_areaMarkedAsDroppable) {
    return;
  }

  $(".drag-drop-gallery").remove();
  $(".drag-drop-chem").remove();
  var $areaSelector = $(areaSelector);
  $areaSelector.prepend(
    "<span class='drag-drop-gallery chem-enabled'><p class='title'>Import File</p><p class='description'>Drag and drop files here.</br> They will be added as attachments</p></span>"
  );

  var width = $areaSelector.width();
  var height = $areaSelector.height();

  // correct for thick border
  if ($areaSelector.css("box-sizing") != "border-box") {
    width -= 10;
    height -= 10;
  }

  $(".drag-drop-gallery").css({ height: height });
  $(".drag-drop-gallery p").css({ marginTop: height / 2 - 30 });

  $(".drag-drop-gallery").css({ width: width });

  _areaMarkedAsDroppable = true;

  if (!manualCleanup) {
    _resetAreaCleanupTimeout(areaSelector);
  }
}

function clearAreaDroppable(areaSelector) {
  if (!_areaMarkedAsDroppable) {
    return;
  }
  $(areaSelector + " .drag-drop-gallery").remove();
  $(areaSelector + " .drag-drop-chem").remove();
  _areaMarkedAsDroppable = false;
}

function _resetAreaCleanupTimeout(areaSelector) {
  clearTimeout(_areaMarkedAsDroppableTimeoutId);
  _areaMarkedAsDroppableTimeoutId = setTimeout(function () {
    clearAreaDroppable(areaSelector);
  }, 500);
}

function initDragDropAreaHandling(
  areaSelector,
  uploadGalleryFileCallback,
  uploadChemFileCallback
) {
  $(document).on("dragover", function (e) {
    e.preventDefault();
    e.stopPropagation();
    markAreaDroppable(areaSelector);

    var $target = $(e.target);
    var insideDragDropAreaGallery =
      $target.is("span.drag-drop-gallery") ||
      $target.parent().is("span.drag-drop-gallery");
    var insideDragDropAreaChem =
      $target.is("span.drag-drop-chem") ||
      $target.parent().is("span.drag-drop-chem");
    if (insideDragDropAreaGallery || insideDragDropAreaChem) {
      e.originalEvent.dataTransfer.dropEffect = "copy";
    } else {
      e.originalEvent.dataTransfer.dropEffect = "none";
    }

    /* using cleanup timeout rather than dragenter/dragleave, as
     * I can't make the latter to work cross-browser */
    _resetAreaCleanupTimeout(areaSelector);
  });

  $(document).on("drop", function (e) {
    e.preventDefault();
    e.stopPropagation();
    var $target = $(e.target);
    var insideGalleryDragDropArea =
      $target.is("span.drag-drop-gallery") ||
      $target.parent().is("span.drag-drop-gallery");
    var insideChemDragDropArea =
      $target.is("span.drag-drop-chem") ||
      $target.parent().is("span.drag-drop-chem");
    if (insideGalleryDragDropArea) {
      // passing original event as it has datatransfer property
      uploadGalleryFileCallback(e);
    } else if (insideChemDragDropArea) {
      uploadChemFileCallback(e);
    }
    clearAreaDroppable(areaSelector);
  });
}
