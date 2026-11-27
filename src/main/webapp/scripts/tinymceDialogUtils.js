/**
 * utility class for methods that we use across tinymce plugins
 */
var tinymceDialogUtils = {
  msg: function () {
    var args = Array.prototype.slice.call(arguments);
    try {
      if (window.RS && window.RS.msg) {
        return window.RS.msg.apply(window.RS, args);
      }
      if (window.parent && window.parent.RS && window.parent.RS.msg) {
        return window.parent.RS.msg.apply(window.parent.RS, args);
      }
    } catch (e) {
      return args[0];
    }
    return args[0];
  },

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

  openUrlWithCancel: function (editor, spec, onCancelRequest) {
    const api = editor.windowManager.openUrl(spec);
    const iframe = Array.from(document.querySelectorAll('.tox-dialog__body-iframe iframe'))
      .findLast((element) => element.getAttribute('src') === spec.url);
    const closeButton = iframe?.closest('.tox-dialog')
      ?.querySelector('.tox-dialog__header button.tox-button--naked');

    if (!closeButton) {
      console.warn('tinymceDialogUtils.openUrlWithCancel: title-bar close button not found');
      return api;
    }

    // TinyMCE 5 closes unconditionally after onCancel. Let the request handler decide when to close.
    closeButton.addEventListener('click', (event) => {
      event.stopImmediatePropagation();
      onCancelRequest();
    }, true);

    // Block Alloy's tap handler and let the browser generate the click.
    closeButton.addEventListener('touchend', (event) => {
      event.stopImmediatePropagation();
    }, true);

    // Stop TinyMCE's keyboard activation. Only Enter needs a synthetic click.
    closeButton.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      event.stopImmediatePropagation();
      if (event.key === 'Enter') {
        event.preventDefault();
        if (!event.repeat) closeButton.click();
      }
    }, true);

    // TinyMCE cancels Space keyup, which would suppress the browser's click.
    closeButton.addEventListener('keyup', (event) => {
      if (event.key === ' ') event.stopImmediatePropagation();
    }, true);
    return api;
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
