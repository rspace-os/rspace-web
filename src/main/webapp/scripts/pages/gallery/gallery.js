
/**
 * Global variables
 */
var recordId = "-1";
var isGalleryPage = true;
var showExpandedGalleryCrudops = true; // we want extra operations on gallery page

function setUpGalleryFileUpload() {

  setUpFileUpload({
    url: '/gallery/ajax/uploadFile/',
    fileChooserId: '.galleryFileInput',
    dropZoneId: '#galleryBlock',
    cancelButtonId: "#blockUICancel",
    progressFunction: RS.blockingProgressBar,
    formData: function () {
      // if uploading through 'replace' button
      var data = [];
      if (this.fileInput.hasClass("galleryFileReplace")) {
        data.push({ name: 'selectedMediaId', value: this.fileInput.attr('mediaFileId') });
      }

      data.push({ name: 'targetFolderId', value: $('#currentFolderId').val() });
      return data;
    },
    postStop: function (e, data) {
      gallery();
    }
  });

  initDragDropAreaHandling('#galleryBlock', function (e) {
    if (e.originalEvent.dataTransfer) {
      var files = e.originalEvent.dataTransfer.files;
      $('#fileBrowserDialog').fileupload('add', { files: files });
    }
  }, e => { });
}

/* We should debug this code and check which methods and values are right!!!! */
$(document).ready(function () {

  initMediaGalleryButtons();
  initMediaGalleryValues();

  var selectedGallery = galleryMediaTypeFromUrlParameter || "Images";
  $("#mediaTypeSelected").val(selectedGallery);

  var selectedGalleryFolderId = galleryCurrentFolderIdFromUrlParameter || 0;
  $('#currentFolderId').val(selectedGalleryFolderId);

  var searchTerm = galleryOptionalSearchTermFromUrlParameter || "";
  $('#urlSearchTerm').val(RS.unescape(searchTerm));

  /* These values are undefined initially */
  //	var tabSelected = $(this).data('tabSelected');

  $('#listThumbnails').html('');
  $('#listThumbnailsURL').html('');
  $('#prevURL').html('');
  $('#galleryTable').html('');
  $('#src').attr('value', '');

  initGalleriesCrudopsDialogs();
  searchTerm === "" ? gallery() : gallery(true);

  if (MEDIA_TYPE_FILESTORES === selectedGallery) {
    clickNetFileStoresGallery();
  }

  initFolderNameDialogFromMediaGallery();
  initVideoPreview();

  $('#galleryContentDiv').show();

  setUpGalleryFileUpload();
});