/**
 * utility class for methods that we use across tinymce plugins
 */
var tinymceDialogUtils = {
  /**
   * if the relative url of this file contains something before 'scripts' element, 
   * this method will return that first part of the url.
   * i.e. if rspace is deployed at server.com/rspace then the method should return '/rspace'
   */
  getContextPath: function (path) {
    var pathname = path || window.location.pathname;
    var pathElements = pathname.split("/");
    var contextPath = "";
    for (var i = 1; pathElements[i] !== "scripts"; i++) {
      contextPath += "/" + pathElements[i];
    };
    return contextPath;
  },

  showErrorAlert: function (text) {
    alert(text);
  },

  resizeDialogToContent: function (activeEditor, $content, extraWidth, extraHeight) {
    var extraWidth = extraWidth || 50;
    var extraHeight = extraHeight || 160;

    var dialogWindow = $(window.parent.document.body).find('.tox-dialog');

    var newWidth = $content.width() + extraWidth;
    var newHeight = $content.height() + extraHeight;

    var maxWidth = $(parent.window).width() - 100;
    if (newWidth > maxWidth) {
      newWidth = maxWidth;
    }
    var maxHeight = $(parent.window).height() - 100;
    if (newHeight > maxHeight) {
      newHeight = maxHeight;
    }
    resizeTo(dialogWindow, newWidth, newHeight);
  }
};

function resizeTo(el, width, height) {
  el.css("width", width);
  el.css("height", height);
  el.css("max-width", width);
  el.css("max-height", height);
}