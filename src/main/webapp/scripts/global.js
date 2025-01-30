/* jshint maxerr: 100 */
/* global RS: true */
// TODO identify methods just used in Gallery/Editor to move into separate js file

/**
 * Namespace for global variables and functions
 */
var RS = {};
var fadeTime = 400;
RS._entityMap = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': '&quot;',
  "'": '&#39;',
  "/": '&#x2F;'
};
var previousHeightOfBlockedElement = -1;
var temporaryHeightOfBlockedElement = 0;
RS.minSearchTermLength = 3;

/**
 * BEGINNING OF EXPERIMENTAL BOOTSTRAP DIALOGS (RSPAC-1287)
 */

// Change to 'true' for Bootstrap dialogs in Workspace > Create > Folder
RS.useBootstrapModals = false;
var bootstrapModalHandles = [];
var activeBootstrapModals = [undefined];
var firstTabbable = undefined;
var lastTabbable = undefined;
var bootstrapModalsContainer = undefined;
var currentModalHandle = undefined;

RS.setupExperimentalBootstrapModals = function () {
  bootstrapModalsContainer = $('<div class="bootstrap-custom-flat experimental-bootstrap-modal">');
  $('body').append(bootstrapModalsContainer);

  /**
   * Bring the modal to the front immediately when it is triggered
   * (not waiting for the opening animation to finish).
   */
  $(document).on('show.bs.modal', function (e) {
    var modal = $(e.target);
    // Put the most recent backdrop overlay above all previous dialogs and keep
    // only the newly opened dialog above it. From:
    // https://stackoverflow.com/questions/19305821/multiple-modals-overlay
    var zIndex = 10000 + (10 * (activeBootstrapModals.length));
    modal.css('z-index', zIndex);
    setTimeout(function () {
      $('.modal-backdrop').not('.modal-stack').css('z-index', zIndex - 1).addClass('modal-stack');
    }, 0);
  });

  /**
   * Store the modal when it finished opening itself, so we
   * can later go back to it in the modals stack.
   */
  $(document).on('shown.bs.modal', function (e) {
    var modal = $(e.target);

    // Attempt to sensibly focus in an element in the modal
    var focusables = modal.find('.modal-body :focusable');
    if (focusables.length) {
      focusables.first().focus();
    } else {
      var buttons = modal.find('.modal-footer button:focusable');
      if (buttons.length) {
        buttons.first().focus();
      } else {
        var allFocusables = modal.find(':focusable');
        if (allFocusables.length) {
          allFocusables.first().focus();
        } else {
          console.log("Couldn't focus any element in the current modal.");
        }
      }
    }

    // Store the modal on the stack
    activeBootstrapModals.push(modal);

    // Set up the tabbing context
    var tabbables = modal.find(':tabbable');
    if (tabbables.length) {
      firstTabbable = tabbables.first().get(0);
      lastTabbable = tabbables.last().get(0);
    } else {
      throw Error("No tabbable elements found in modal", modal);
    }

    // Attempt to store the modal's triggering element so we can focus it back
    // when the modal closes.
    if ('relatedTarget' in e) {
      bootstrapModalHandles.push(e.relatedTarget);
    } else {
      throw Error('You must pass a relatedTarget element when opening a modal!');
    }
  });

  /**
   * When a modal is closed, get the previous modal from the stack,
   * give it focus and activate its tabbing context.
   */
  $(document).on('hidden.bs.modal', function (e) {
    var relatedTarget = bootstrapModalHandles.pop();

    // The triggering element of the modal might have become unfocusable
    // in the meantime (e.g. got hidden). In such case, we'll focus the nearest
    // previous focusable element.
    if (relatedTarget && $(relatedTarget).is(':focusable')) {
      relatedTarget.focus();
    } else {
      var elementToFocus = RS.nearestElement($(relatedTarget), ':focusable');
      elementToFocus.focus();
    }

    // Remove the closed modal from the stack.
    activeBootstrapModals.pop();

    // Get the previous modal from the stack
    var modal = activeBootstrapModals[activeBootstrapModals.length - 1];

    // If there was a modal opened before the just closed one, activate its
    // tabbing context, else deactivate the tabbing context variables.
    if (modal) {
      var tabbables = modal.find(':tabbable');
      firstTabbable = tabbables.first().get(0);
      lastTabbable = tabbables.last().get(0);
      currentModalHandle = modal;
    } else {
      firstTabbable = undefined;
      lastTabbable = undefined;
      currentModalHandle = undefined;
    }
  });

  /**
   * Capture TAB events and, if a modal is active, control the tabbing flow such
   * that the focus never leaves the modal (i.e. stays trapped and circulates
   * within the modal).
   */
  $(document).on('keydown', function (e) {
    if (e.which === 9) {
      var elem = $(e.target).first().get(0);
      if (elem == firstTabbable && e.shiftKey) {
        e.preventDefault();
        lastTabbable.focus();
      } else if (elem == lastTabbable && !e.shiftKey) {
        e.preventDefault();
        firstTabbable.focus();
      }
    }
  });
};

/**
 * Find the closest element somewhere above the current element (either a prev
 * sibling or a parent or a parent's prev sibling).
 * From https://gist.github.com/carpeliam/945190
 */
RS.nearestElement = function (elem, selector) {
  if (elem.length == 0)
    return elem;
  var nearestSibling = elem.prevAll(selector + ':first');
  if (nearestSibling.length > 0)
    return nearestSibling;
  return RS.nearestElement(elem.parent(), selector);
};

/** Checks if the current page a the top-level page.*/
RS.isPageEmbedded = function () {
  try {
    return window.self !== window.top;
  } catch (e) {
    return true;
  }
}

/**
 * Shows a Bootstrap modal. Because this creates new DOM element every time,
 * the recommended use is to call this method once and then use the returned
 * handle to open and close the dialog as needed.
 *
 * When calling .modal('show') on the returned handle, always provide a triggering
 * element like .modal('show', elem). The element will gain focus after the dialog
 * is hidden. Without providing this argument, you will get an error. This is
 * meant to enforce the good practice that we always move the focus around our
 * app in a meaningful way - for good accessibility.
 *
 * @param  {string}   text      The contents of the dialog. Can be HTML or text.
 * @param  {boolean}  show      Whether to show the modal automatically after
 *                              creating it. Requires that the target is passed
 *                              as well, otherwise falls back to show = false.
 * @param  {element}  target    The DOM element or jQuery object that triggered
 *                              the modal; will get focused when the modal closes.
 * @param  {function} onConfirm The callback function which will be called before
 *                              closing the dialog on the OK button click. Must
 *                              return true for the dialog to close.
 * @param  {object}   options   Available options:
 *                              - textCancel: Custom caption for the Close button.
 *                              - textOk: Custom caption for the OK/Submit button.
 *                              - title: Modal title, without this the modal won't
 *                                       have the header.
 *                              - override: Currently not supported. Override
 *                                          browser navigation while modal is visible
 *                              - open: The callback to be executed when modal is
 *                                      opened.
 *                              - size: Size of the modal. 'small' and 'large'
 *                                      supported, corresponding to Bootstrap's
 *                                      modal-sm and modal-lg. Omit this for normal
 *                                      size.
 *                              - noCancelbutton: If provided, then the Cancel button
 *                                                will not be used. Otherwise, it is
 *                                                used automatically for modals which
 *                                                have a custom onConfirm action.
 * @return {element}           The modal's element on which .modal('show') and
 *                             similar can be called.
 */
RS.apprise = function (text, show, target, onConfirm, options) {
  var newOptions = {
    class: '',
    buttons: {}
  };

  if (onConfirm) {
    if (!options || (options && !('noCancelButton' in options))) {
      newOptions.buttons.cancel = {
        className: 'btn-default',
        id: 'cancel',
        text: 'Cancel'
      };
      if (options) {
        newOptions.buttons.cancel.text = options.textCancel || 'Cancel';
      }
    }
  }

  newOptions.buttons.confirm = {
    action: function (e) {
      if (onConfirm) {
        var result = onConfirm.call(this, e);
        if (result) RS.Apprise('close');
      } else {
        RS.Apprise('close');
      }
    },
    className: 'btn-primary',
    id: 'confirm',
    text: 'Ok'
  };

  if (options) {
    newOptions.buttons.confirm.text = options.textOk || 'Ok';

    if ('title' in options) {
      newOptions.title = options.title;
    } else {
      newOptions.class = 'simple';
    }

    if ('override' in options) {
      newOptions.override = options.override;
    }
    if ('open' in options) {
      newOptions.open = options.open;
    }
    if ('close' in options) {
      newOptions.close = options.close;
    }
    if ('size' in options) {
      newOptions.size = options.size;
    }
  } else {
    newOptions.class = 'simple';
  }

  return RS.Apprise(text, show, target, onConfirm, newOptions);
};

RS.Apprise = function (text, show, target, onConfirm, options) {
  // Prevent blank modals
  if (text === undefined || !text) {
    return;
  }

  // Can't show a modal unless we have its triggering element
  if (target === undefined) {
    show = false;
  }

  // The default is not to show the modal
  if (show === undefined) {
    show = false;
  }

  // Close current Apprise, exit
  if (text == 'close') {
    currentModalHandle.modal('hide');
    return;
  }

  // Necessary variables
  var me = undefined;
  var $_outer = $('<div class="modal">'),
    $_inner = $('<div class="modal-dialog">'),
    $_content = $('<div class="modal-content">'),
    $_body = $('<div class="modal-body">'),
    $_footer = $('<div class="modal-footer">');

  if (options && 'title' in options) {
    var $_header = $('<div class="modal-header">');
    $_header.html('<button type="button" class="close" data-dismiss="modal" ' +
      'aria-label="Close"><span aria-hidden="true">×</span></button>' +
      '<h4 class="modal-title">' + options.title + '</h4>');
  } else {
    var $_header = $('');
  }

  // Default settings
  var settings = {
    class: 'simple',
    buttons: {
      confirm: {
        action: null,
        className: 'btn-primary',
        id: 'confirm',
        text: 'Ok'
      }
    },
    override: false
  };

  // Merge settings with options
  $.extend(settings, options);

  // Add buttons
  $.each(settings.buttons, function (i, button) {
    if (button) {
      // Create button
      var $_button = $('<button type="button" class="btn">').append(button.text);

      // Add custom class names
      if (button.className) {
        $_button.addClass(button.className);
      }

      // Add custom id
      if (button.id) {
        $_button.attr('id', 'bsm-btn-' + button.id);
      }

      if (!button.action) {
        // Add simple dismiss with Bootstrap's data-dismiss="modal" if no specific
        // action was provided for the button
        $_button.attr('data-dismiss', 'modal');
      } else {
        // Add specified callback action to the button
        $_button.on("click", function (e) {
          if (button.action.call(me, e)) {
            me.modal('hide');
          }
        });
      }

      // Add to buttons
      $_footer.append($_button);
    }
  });

  // Options passed to Bootstrap's modal constructor
  var bootstrapModalOptions = {
    show: show // Whether to show the modal automatically after creating
  };

  // Disabled browser actions while open.
  // Currently not supported, as it's difficult to bind and unbind correctly
  // when there is a stack of modals, not just one modal.
  /*
  if (settings.override) {
    $(window).bind('beforeunload', function(e) {
      return "An alert requires attention.";
    });

    bootstrapModalOptions.backdrop = 'static'; // Don't allow closing by click on the backdrop
    bootstrapModalOptions.keyboard = false; // Don't allow closing by pressing ESC
  }
  */

  // Add optional modal size classes
  if (settings.class == 'simple') {
    $_inner.addClass('modal-sm');
  } else if ('size' in settings) {
    var sizeClass = '';
    if (settings.size == 'small') {
      sizeClass = 'modal-sm';
    } else if (settings.size == 'large') {
      sizeClass = 'modal-lg';
    }
    $_inner.addClass(sizeClass);
  }

  // Assemble and show the modal
  modal = $_content.append([$_header, $_body.html(text), $_footer]);
  modal = $_outer.append($_inner.append(modal));
  $_outer.addClass(settings.class);
  bootstrapModalsContainer.append(modal);

  if (show) {
    me = modal.modal(bootstrapModalOptions, target);
  } else {
    me = modal.modal(bootstrapModalOptions);
  }

  // Attach custom event handlers/callbacks
  if ('open' in options) {
    $(me).on('shown.bs.modal', function (e) {
      options.open.call(me, e);
    });
  }
  if ('close' in options) {
    $(me).on('hidden.bs.modal', function (e) {
      options.close.call(me, e);
    });
  }

  currentModalHandle = me;

  return me;
};
/**
 * END OF EXPERIMENTAL BOOTSTRAP DIALOGS (RSPAC-1287)
 */


RS.isKeypressEquivalentToClick = function (e) {
  return e.which == 13 || e.which == 32; // enter or (space)
};

RS.isEmpty = function (str) {
  return (!str || 0 === str.length);
};

RS.objIsEmpty = function(obj) {
  if(obj) {
    return Object.keys(obj).length === 0;
  } else {
    return true;
  }

}

RS.isBlank = function (str) {
  return (!str || /^\s*$/.test(str));
};

RS.defaultToastDisplayTime = 3000;

/**
 * Escapes HTML markup characters that may cause vulnerabilities to XSS
 * @param string The String to escape
 * @returns The escaped String
 */
RS.escapeHtml = function (string) {
  return String(string).replace(/[&<>"'\/]/g, function (s) {
    return RS._entityMap[s];
  });
};
/*
 * Unescapes a string of escaped HTML.
 * E.g., given '&lt;a&gt;' will return '<a>'.
 * Don't use this for untrusted input ( e.g., any user input); just use
 *  for content known to be benign, such as server-generated content.
 * Arguments
 *  -safe A string of escaped HTML that can safely be escaped
 */
RS.unescape = function (safe) {
  return $('<div />').html(safe).text();
};
/*
 * Filter out forbidden characters from a string, useful for frontend
 * sanitizing of some fields when they are not being immediately sent to backend.
 */
RS.removeForbiddenCharacters = function (string, forbiddenCharacters) {
  return string.replace(/./gi, function check(c) {
    if (forbiddenCharacters.indexOf(c) >= 0) return '';
    return c;
  });
};

RS.safelyParseHtmlInto$Html = function (html) {
  var $html;
  try {
    // convert pasted html to jquery object - done outside of current document to avoid xss
    var virtualDoc = document.implementation.createHTMLDocument('virtual');
    var $virtualDiv = $('<div></div>', virtualDoc);
    $virtualDiv.append(html);
    $html = $virtualDiv.contents();
  } catch (e) {
    /* couldn't parse - probably not a valid html */
    $html = $("");
  }
  return $html;
}

RS.convert$HtmlToHtmlString = function ($html) {
  var htmlString = '';
  $.each($html, function () {
    var element = $(this).get(0);
    if (element.nodeType === Node.ELEMENT_NODE) {
      htmlString += element.outerHTML;
    }
    else if (element.nodeType === Node.TEXT_NODE) {
      htmlString += element.nodeValue;
    }
  });
  return htmlString;
}

/*
 * Converts a file size(any +ve number) into an approximation in suitable size units - kB, MB etc
 */
RS.humanFileSize = function (size) {
  if (size === null) {
    return "";
  }
  var i = Math.floor(Math.log(size) / Math.log(1024));
  return (size / Math.pow(1024, i)).toFixed(2) * 1 + ' ' + ['B', 'kB', 'MB', 'GB', 'TB'][i];
};

/*
 * Progress bar callback for file upload.
 * Call show() to display the progres dialog, and make sure that hide() is always called regardless
 *  of whether the task succeeds or fails.
 */
RS.blockingProgressBar = (function () {
  var my = {};
  my._progressType = "";
  my._checkingInterval;
  my._timerId = [];

  /*
   * Blocks the page, with optional cancel button showing (default is false).
   * Depends on RS.blockPage functionality.
   * Takes one argument 'config', containinfg the following key:value pairs:
   *  - progressType - an identifier for the progress bar, currently should be a constant set in
   *  SessionAttributeUtils.java. This is *Mandatory*
   *  - showCancel whether to show a cancel button or not, optional, default false
   *  - msg - starting message, optional
   *  - checkingInterval - how often, in millis, to update progress status. Optional, default is 3000
   */
  my.show = function (config) {
    my._progressType = config.progressType;
    showCancel = config.showCancel || false;
    msg = config.msg || "Starting...";
    my._checkingInterval = config.checkingInterval || 3000;
    RS.blockPage(msg, showCancel);
    my._timerId.push(setInterval(_updateProgress, my._checkingInterval));
    console.log("Timer id  for msg " + msg + " is " + my._timerId);
  };
  /*
   * Removes blockPage and sets progress to 0, stops the update timer
   */
  my.hide = function () {
    $('#blockprogressBar').css('width', '0%');
    RS.unblockPage();
    $.each(my._timerId, function (i, v) {
      clearInterval(v);
    });
    //clearInterval(my._timerId);
    //console.log("Stopping timer " + my._timerId);
  };
  /*
   * A custom message indicating progress
   */
  my.message = function (message) {
    $('#blockUICustomMsg').html(message);
  };
  /*
   *  - progress - A %age width of the progress bar, should be 0 < progress< 100
   */
  my.showProgress = function (progress) {
    $('#blockprogressBar').css('width', progress + '%');
  };


  var _updateProgress = function () {
    $.get('/progress/' + my._progressType, function (progress) {
      $('#blockUICustomMsg').html();
      RS.blockingProgressBar.showProgress(progress.percentComplete);
      RS.blockingProgressBar.message(progress.description);
      if (progress.done) {
        RS.blockingProgressBar.message("Completed");
      }
    });

  };
  return my;
}());

/**
 * Alternative page blocker that takes an option to show a cancel button with id 'blockUICancel'.
 * Blocks whole page by default, or just a passed $element
 */
RS.blockPage = function (message, showCancelButton, $element) {
  var temp = $('#blockUIContentTemplate').html();
  var html = Mustache.render(temp, message);
  var blockSettings = { message: html };

  if ($element) {
    $element.block(blockSettings);
  } else {
    $.blockUI(blockSettings);
  }
  if (showCancelButton) {
    $('#blockUICancel').show();
  }
};

/**
 * Unblocks the page or element
 */
RS.unblockPage = function ($element) {
  if ($element) {
    $element.unblock();
  }
  $.unblockUI();
};

/**
 * Boolean test for returned status of a *completed* XMLHttpRequest.
 */
RS.isErrorStatus = function (xhr) {
  return xhr.readyState == 4 && /^4|5/.test(xhr.status);
};

/*
 * Enables a jquery UI dialog button containing argument text
 * args
 *  - label complete or partial button label text
 */
RS.enableJQueryDialogButtonWithLabel = function (label) {
  $(".ui-dialog-buttonpane button:contains('" + label + "')")
    .prop("disabled", false).removeClass('ui-state-disabled');
};
/*
 * Disables a jquery UI dialog button containing argument text
 * args
 *  - label complete or partial button label text
 */
RS.disableJQueryDialogButtonWithLabel = function (label) {
  $(".ui-dialog-buttonpane button:contains('" + label + "')")
    .prop("disabled", true).addClass('ui-state-disabled');
};

/**
 * Generates random alphanumeric string of given length.
 */
RS.randomAlphanumeric = function (length) {
  var charSet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  var randomString = '';
  for (var i = 0; i < length; i++) {
    var randomPoz = Math.floor(Math.random() * charSet.length);
    randomString += charSet.substring(randomPoz, randomPoz + 1);
  }
  return randomString;
};

/**
 * Retrieves cookie value of given name or '' if no such cookie exists.
 */
RS.getCookieValueByName = function (cname) {
  var ca = document.cookie.split(';');
  var name = cname + "=";
  for (var i = 0; i < ca.length; i++) {
    var c = ca[i];
    while (c.charAt(0) === ' ') {
      c = c.substring(1);
    }
    if (c.indexOf(name) !== -1) {
      return c.substring(name.length, c.length);
    }
  }
  return '';
};

/**
 * === Deprecated ===
 *
 * Generates a URL in Javascript for context.
 *
 * This method is used inconsistently across the application, so we should forget it,
 * and came with better / tested solution if there is ever a reason for supporting
 * deployments with context path.
 *
 * === Deprecated ===
 */
function createURL(path) {
  return AppContext_Glbal.concat(path);
}

RS.createAbsoluteUrl = function (relPath) {
  return window.location.protocol + '//' + window.location.host + (relPath ? relPath : "");
};

/*
 * Generates a single String from an AjaxReturnObject's error message
 *
 * TODO: 'sep' param seems unused
 * TODO: escaping should be done by default, so 'escapeResponseText' param
 *        should be opt-out rather than opt-in
 *
 */
function getValidationErrorString(errorList, sep, escapeResponseText) {
  var separator = sep || ", ";
  var joinedErrorMsg = errorList ? errorList.errorMessages.join(separator) : null;
  if (escapeResponseText) {
    joinedErrorMsg = RS.escapeHtml(joinedErrorMsg);
  }
  return joinedErrorMsg;
}

/**
 * A simple cache designed to temporarily cache HTML fragments or other data
 * pulled from an Ajax request.
 * The cache is emptied when the page is  refreshed.
 * @returns
 */
RS.Cache = function () {
  var _cache = {};

  var DEFAULT_TIMOUT = 1000 * 5;
  /*
   * Takes a key value pair, and a timeout period in milliseconds
   */
  this.put = function (key, value, timeout) {
    timeout = timeout || DEFAULT_TIMOUT;
    _cache[key] = value;
    setTimeout(function () {
      if (_cache[key] != undefined) {
        _cache[key] = undefined;
      }
    }, timeout);
  };

  /*
   * Stores a value with no timeout period
   */
  this.putPermanent = function (key, value) {
    _cache[key] = value;
  };

  /*
   * Retrives a value from the cache. Users should test this is not null before using
   *  in case it has been timed out
   */
  this.get = function (key) {
    return _cache[key];
  };

  /*
   * Deletes the cache entry; removes key and value
   */
  this.clear = function (key) {
    delete _cache[key];
  };

  /*
   * Empties the cache completely.
   */
  this.clearAll = function () {
    _cache = {};
  };
};

/*
 * Call and setup on page load
 */
RS.webResultCache = new RS.Cache();

/*
 * Class to hold functions that are called at interval. When user goes idle the interval increases.
 */
function _PollRegistry() {

  /* delay multiplier that will be applied with increasing inactiviy timer ticks */
  var DELAY_MULTIPLIER = 1.5;

  var pollFunctions = {};

  /* are all functions on their original intervals, or was the interval increased by idle time */
  var originalPollingIntervals = true;

  /*
   * Register a polling function.
   *
   * @param pollFunction - a function to be called at interval, required
   * @param interval - time interval in millis between calls, required
   */
  this.register = function (pollFunction, interval) {

    var pollRegistryEntry = {
      pollFunction: pollFunction,
      interval: interval,
      /* interval may increase with idle time */
      origInterval: interval,
      lastTimeoutId: null,

      /* exectues poll function and schedules next one to run after this.interval */
      runAndReschedule: function () {
        //console.log('executing poll function ' + this.pollFunction.name);
        this.pollFunction();
        this.reschedule();
      },

      /* schedules poll function to run after this.interval */
      reschedule: function () {
        var that = this;
        this.lastTimeoutId = setTimeout(function () { that.runAndReschedule(); }, this.interval);
        //console.log('scheduled poll function with interval: ' + this.interval + ' as ' + this.lastTimeoutId);
      },

      restartWithOrigInterval: function () {
        //console.log('cancelling timeout ' + this.lastTimeoutId);
        clearTimeout(this.lastTimeoutId);

        this.interval = this.origInterval;
        //console.log("resetting interval of function " + this.pollFunction.name + " to " + this.interval);

        this.runAndReschedule();
      }
    };

    pollRegistryEntry.reschedule();
    var handle = "pollRegistry?" + pollRegistryEntry.lastTimeoutId;
    pollFunctions[handle] = pollRegistryEntry;

    console.log("RS.PollRegistry registered new function " + pollFunction.name + ' with interval ' + interval + ' and handle ' + handle);
    console.log("RS.PollRegistry number of registered functions: " + Object.keys(pollFunctions).length);
  };

  /*
   * For all registered functions increase polling interval by DELAY_MULTIPLIER
   */
  this.increasePollingIntervals = function () {
    for (var handle in pollFunctions) {
      var curFunction = pollFunctions[handle];
      curFunction.interval = curFunction.interval * DELAY_MULTIPLIER;
      //console.log("RS.PollRegistry changing interval of function " + curFunction.pollFunction.name + " to " + curFunction.interval);
    }
    originalPollingIntervals = false;
  };

  /*
   * Restarts polling functions with their original interval,
   * or do nothing if original interval was never increased
   */
  this.restartPollingIntervals = function () {
    if (originalPollingIntervals) {
      return;
    }

    for (var handle in pollFunctions) {
      pollFunctions[handle].restartWithOrigInterval();
    }
    originalPollingIntervals = true;
  };

}

RS.pollRegistry = new _PollRegistry();

/*
 * Counter for idle time when this page is not being clicked or mouse-overed on.
 */
RS._idleTimerTicks = 0;

/*
 * Interval between _timerIncrement calls
 */
RS._idleCheckInterval = 30000;

/*
 * Zeroes _idleTimerTicks and resumes original poll functions intervals
 */
RS._resetInactivityTimer = function () {
  if (RS._idleTimerTicks === 0) {
    return; // do nothing, user was active recently so we're still on the original schedule
  }
  console.log("RS.pollRegistry resetting timer back to original schedule");
  RS._idleTimerTicks = 0;
  RS.pollRegistry.restartPollingIntervals();
};

/*
 * Increments _idleTimerTicks and after some idle time increases poll functions intervals
 */
RS._timerIncrement = function () {

  var MAX_DELAY_INCREASES_NUM = 10; /* stops multiplying poll delay indefinitely */

  if (RS._idleTimerTicks >= MAX_DELAY_INCREASES_NUM) {
    return;
  }

  RS._idleTimerTicks++;
  console.log("RS.pollRegistry incrementing _idleTimerTicks to " + RS._idleTimerTicks);

  if (RS._idleTimerTicks > 1 && RS._idleTimerTicks < MAX_DELAY_INCREASES_NUM) {
    RS.pollRegistry.increasePollingIntervals();
  }
};

RS._setIncomingMaintenanceMsg = function (nextMaint) {

  var headerInfoMsg = 'Scheduled maintenance will start on ' + nextMaint.formattedStartDate;
  var warningMsg = 'Scheduled maintenance is about to start on ' + nextMaint.formattedStartDate +
    '. Please finish your work and log off';
  var noticeMsg = 'RSpace will be down for a scheduled maintenance from ' +
    nextMaint.formattedStartDate + ' until ' +
    nextMaint.formattedEndDate + '. ' +
    RS.escapeHtml(nextMaint.message);

  if (!nextMaint.canUserLoginNow) {
    RS.maintenanceMsg(warningMsg);
  }

  $('#incomingMaintenanceDiv').show();
  $('#incomingMaintenanceDiv').prepend(headerInfoMsg);

  $('#maintenanceDetailsLink').click(function () {
    RS.confirm(noticeMsg, "notice", 8000);
  });
};

RS._checkIncomingMaintenace = function () {
  var jqxhr = $.get('/system/maintenance/ajax/nextMaintenance');
  jqxhr.done(function (nextMaint) {
    if (nextMaint) {
      RS._setIncomingMaintenanceMsg(nextMaint);
    }
  });
  jqxhr.fail(function () {
    console.warn("Couldn't retrieve info about incoming downtime");
  });
};

$(function () {

  if (typeof currentUser === 'undefined') {
    /* global.js is not loaded on standard RSpace page decorated by default.jsp, but possibly
     * on externalPage.jsp or inside jquery dialog. no need to execute the init code below. */
    return;
  }

  RS.setupExperimentalBootstrapModals();

  var idleInterval = setInterval(RS._timerIncrement, RS._idleCheckInterval);
  // this overrides defaults to provide a mechanism where 'a' tags can avoid being buttonized by use of 'nobutton' class
  // see rspac-892
  $(".toolbar").buttonset({
    items: "button, input[type=button], input[type=submit], input[type=reset], input[type=checkbox], input[type=radio]," +
      " a:not(.nobutton), :data(ui-button)"
  });

  $(window).focus(function () {
    //console.log('window getting focus, resume polling');
    RS._resetInactivityTimer();
  });

  /* Zero the idle timer on mouse movement. */
  $(window).mousemove(function (e) {
    //console.log('window mousemove, resetting inactivity timer');
    RS._resetInactivityTimer();
  });
  $(window).keypress(function (e) {
    //console.log('window keypress, resetting inactivity timer');
    RS._resetInactivityTimer();
  });

  RS._checkIncomingMaintenace();

  $(document).on('keyup', '.dynamicWidthInputField', function (e) {
    RS.resizeInputFieldToContent($(this));
  });

});

/*
 * Dynamically resize input width and height to wrap around text (applies e.g. to Name editor fields)
 */
RS.resizeInputFieldToContent = function (field) {
  var duration = 60;
  var font = field.css('font');
  var fontSize = parseInt(field.css('font-size'));
  var lineHeight = parseInt(field.css('line-height'));
  var paddingVertical = parseInt(field.css('padding-top')) + parseInt(field.css('padding-bottom'));
  var borderVertical = parseInt(field.css('border-top-width')) + parseInt(field.css('border-bottom-width'));
  var text = field.val();
  if (!text || text.length < 1) {
    text = field.attr("placeholder");
  }
  text = text.replace(/\s/g, '_');

  var elem = $('<span>').hide().appendTo(document.body).text(text).css('font', font);
  var width = elem.width() * 1.1 + 2.4 * fontSize;

  field.animate({ // resize horizontally (input and textarea elements)
    width: width
  }, duration, function () { // resize vertically (applies to textarea elements only)
    if (field.is('textarea') && field[0]) {
      field.css("height", "1px").css({ "height": (paddingVertical + borderVertical + Math.max(lineHeight, field[0].scrollHeight)) + "px" }, duration);
    }
  });
};

/*
 * General utility function to be called in the event any ajax request
 * fails,  with a response code that isn't 200 (e.g., a server error)
 * Arguments
 * action         - a 1 or 2 word string summarising the attempted action. E.g., 'PDF export', 'Delete'.
 * pageWasBlocked - a boolean. Should be true if RS.blockPage() was called for the post request.
 * jqxhr           - the Jquery Ajax response object returned by a call to post.
 * Usage:
 * var jqxhr = $.post(...
        ...);
   jqxhr.fail(function(){
            RS.ajaxFailed("Copy",true,jqxhr);
    });
 */
RS.ajaxFailed = function (action, pageWasBlocked, jqxhr, showAsToast) {
  if (pageWasBlocked) {
    RS.unblockPage();
  }
  var additionalInfo = "Status: " + jqxhr.status;
  if (jqxhr.status >= 500) {
    additionalInfo = additionalInfo + " - server error";
  } else if (jqxhr.status >= 400) {
    additionalInfo = additionalInfo + " - unauthorised or unavailable resource.";
  } else if (jqxhr.status == 0) {
    additionalInfo = additionalInfo + " - unauthorised or unavailable resource - is your network connection OK?";
  }

  var responseText = jqxhr.responseText;
  /* if the response text is a full page rather than page fragment, then extract relevant part */
  if (responseText && responseText.indexOf("</html>", responseText.length - "</html>\n\r".length) !== -1) {
    var doc$ = $(responseText);
    var content$ = doc$.find('#ajaxErrorMsg');
    console.log("error response: " + responseText);
    responseText = content$.html();
    var errorId$ = doc$.find('#ajaxErrorIdMsg');
    responseText += "<p/>" + errorId$.html();

  }
  if (!responseText) {
    responseText = "No error details in response";
  }

  var message = action + " could not complete: <br/>" + additionalInfo + "<br/>" + responseText;
  if (RS.useBootstrapModals) {
    var focusedElement = $(':focus');
    RS.apprise(message, true, focusedElement);
  } else if (showAsToast) {
    RS.confirm(message, "warning", 5000, { sticky: true });
  } else {
    apprise(message);
  }
};

/**
 * Check if our browser supports touch events
 * Manages show and hide of edit buttons in structured document fields dependent on touch screen or not
 * @returns {Boolean}
 */
RS.is_touch_device = function () {
  return 'ontouchstart' in window // works on most browsers
    || 'onmsgesturechange' in window; // works on ie10
};

/**
 * Checks if the event is a touch event
 */
RS.isTouchEvent = function (e) {
  return typeof e.touches === 'object';
};

/**
 * Boolean test for whether the given fileExtension can be handled by jw player
 */
function isPlayableOnJWPlayer(extension) {
  if (typeof extension !== 'undefined' && (extension == 'mp4' || extension == 'flv' || extension == 'acc' ||
    extension == 'mp3' || extension == 'ogg')) {
    return true;
  } else {
    return false;
  }
}

function _checkIfFlashAvailable() {
  var flashAvailable = ((typeof navigator.plugins != "undefined" && typeof navigator.plugins["Shockwave Flash"] == "object") ||
    (window.ActiveXObject && (new ActiveXObject("ShockwaveFlash.ShockwaveFlash")) != false));
  return flashAvailable;
}

/**
 * Given an id,name and extension, creates a JD media player
 * @param id - a DB id of an ECatVideo or EcatAudio
 * @param name - the file name of the media file to play
 * @param extension - the file extension - currently mp4,flv,acc,mp3,ogg are supported
 * @returns the HTML for the player, that can be inserted where appropriate by the calling code.
 */
function setUpJWMediaPlayer(id, name, extension) {

  //it has got the HTML code needed to generate the player code from the plugin
  var videoHTML = getMediaPlayerHTML(id, name, extension);

  //This is a hidden div that is used to generate the code of the
  $('#tempData').html(videoHTML);
  var file = createURL("/Streamfile/" + id + "/" + name);

  if (_checkIfFlashAvailable()) {
    // videoContainerId is unique just in case a user wants to insert the same video on the same document.
    // id is generated in getMediaPlayerHTML
    var videoContainerId = $('.videoTemp').attr('id');
    jwplayer(videoContainerId).setup({
      flashplayer: "/scripts/player.swf",
      file: file,
      height: 270,
      width: 480
    });
    //To disable menu on player
    $("#tempData").find('object [name="allowscriptaccess"]').attr('value', 'never');

  } else {
    $('#tempData').find('.videoTemp').text('Player not available');
  }
  videoHTML = $("#tempData").html();

  $('#tempData').html("");
  return videoHTML;
}

/**
 * This method gets Mustache template and inserts it into the tinyMCE field.
 *
 * @param templateId
 *            the id of the template. This should be a name recognised by the
 *            TemplateController java class.
 * @param json
 *            the data to merge with the template
 * @param ed tinyMCEeditor
 *            The tinyMCE editor reference (optional, usually it is accessible from global.js)
 * @param callback
 *            Calls the callback after the request has been finished (optional)
 */
RS.insertTemplateIntoTinyMCE = function (templateId, json, ed, callback) {
  if (templateId.indexOf('#') === 0) {
    templateId = templateId.substr(1);
  }
  var jqxhr = $.get("/fieldTemplates/ajax/" + templateId,
    function (elementTemplate) {
      var html = Mustache.render(elementTemplate, json);
      if (html !== "") {
        RS.tinymceInsertContent(html, ed);
      }
      if (callback !== undefined) {
        callback();
      }
    });
  jqxhr.fail(function (e) {
    console.log("failed " + e);
    if (callback !== undefined) {
      callback();
    }
  });
};

/**
 * Used to insert HTML into an active tinyMCE editor.
 * It handles non-editable areas (see RSPAC-799).
 * @param html the HTML to insert
 * @param ed the active tinyMCE editor. This is an optional argument if the tinyMCE global variable
 *  is not defined for this method (RSPAC-865)
 *
 */
RS.tinymceInsertContent = function (html, ed) {
  var activeEditor;
  if (typeof tinyMCE != 'undefined') {
    activeEditor = tinyMCE.activeEditor;
  } else if (typeof ed != 'undefined') {
    activeEditor = ed;
  }

  var selection = activeEditor.selection;
  var $currentSelection = $(selection.getNode());
  var replacing = false;

  // if user hasn't clicked inside the editor yet (RSPAC-799)
  if ($currentSelection.hasClass('mce-content-body')) {
    var $extraParagraph = $currentSelection.prepend("<p></p>").children().first();
    selection.select($extraParagraph.get(0));
  }

  // check if cursor or selection is inside of non-editable area
  var $enclosingNonEditable = $currentSelection.parents('.mceNonEditable:last');
  if ($enclosingNonEditable.length === 0) {
    if ($currentSelection.hasClass('mceNonEditable')) {
      $enclosingNonEditable = $currentSelection;
    }
  }

  // if cursor or selection is inside of non-editable area
  if ($enclosingNonEditable.length > 0) {

    var somethingSelected = !selection.isCollapsed();
    if (somethingSelected) {
      // mark existing non-editable area for removal
      $enclosingNonEditable.addClass('rsToRemove');
      replacing = true;
    }

    // move cursor outside non-editable area
    var $tempSel = $enclosingNonEditable.after("<p class='rsToRemove'>&nbsp;</p>").next();
    selection.setCursorLocation($tempSel.get(0), 1);
    $currentSelection = $(selection.getNode());
  }

  // when adding a block element we may want some extra spacing after it
  if (!replacing) {
    /* for block elements (taking whole horizontal space) we try to avoid adding empty paragraphs
     * around, as they are difficult to remove through tinymce UI */
    var $htmlToInsert = undefined;
    //check for existance of of <br> tag, see RS SUPPORT-40 for details.
    if (html.indexOf("<br>") == -1) {
      $htmlToInsert = $(html);
    }
    /*if the html string is not enclosed by a pair of <*> tags then jquery won't parse it
     this is most likely due to inserting a snippet of some text like "aaa<br>aaa". normally when
     a snippet is created a pair of <p></p> is added to the beginning and ending, but in this case it isn't. so we are adding it manually
    */
    else if (html.indexOf("<p>") == -1 && html.indexOf("</p>") == -1) {
      $htmlToInsert = $("<p>" + html + "</p>");
    }
    //this should not be reachable, but just in case
    //create an empty object so the hasClass won't throw exception
    else {
      $htmlToInsert = $().html(html);
    }
    var blockElement = $htmlToInsert.hasClass('attachmentDiv');
    if (blockElement) {

      // we don't want to keep an empty p above the element
      if ($currentSelection.is("p") && $currentSelection.html() === '&nbsp;') {
        $currentSelection.addClass('rsToRemove');
      }

      var isEndOfTheContent = $currentSelection.next().length === 0;
      if (isEndOfTheContent) {
        /* we are at the end of the field, we add a paragraph so user can continue typing after insert */
        html += "<p>&nbsp;</p>";
      } else {
        /* we are in the middle of a document, so adding a temporary paragraph in case user want to type something or insert more elements */
        html += "<p class='mceTmpParagraph'>&nbsp;</p>";
      }
    }
  }

  activeEditor.execCommand('mceInsertContent', false, html);
  var $toRemove = $(activeEditor.getBody()).find('.rsToRemove');

  if ($toRemove.length > 0) {
    $toRemove.remove();
  }
};

RS.tinymceInsertInternalLink = function (id, globalId, name, ed, callback) {
  var data = {
    id: id,
    globalId: globalId,
    name: name,
  };
  $.get("/fieldTemplates/ajax/linkedRecordLink", function (htmlTemplate) {
    var html = Mustache.render(htmlTemplate, data);
    if (html != "") {
      ed.execCommand('mceInsertContent', false, html);
      if (callback !== undefined) {
        callback();
      }
    }
  }).fail(function (error) {
    console.log(error);
  });
};

/*
 * Helper function that appedns content generated by mustache to the element.
 * It replaces data-src attribute of images to src attribute, this way images are lazy loaded
 *
 */
RS.appendMustacheGeneratedHtmlToElement = function (mustacheContent, element) {

  var updatedHTML = RS._replaceMustacheDataSrcWithSrcForImage(mustacheContent);
  var appnded = $(element).append(updatedHTML);
  return appnded;
};

/*
 * Helper function that replace data-src attribute of images to src attribute,
 * this way images are lazy loaded.
 *
 * Also, if data-try-replace-with-src attribute of an image is defined, then img pointed
 * by this attribute is preloaded, and when available it swaps src of the image.
 */
RS._replaceMustacheDataSrcWithSrcForImage = function (mustacheContent) {
  var $content = $("<div/>").append(mustacheContent);
  $content.find('img').each(function () {
    var $thisImg = $(this);
    var imgSrc = $thisImg.data("src");
    if (imgSrc !== undefined) {
      $thisImg.attr('src', imgSrc);
    }

    var tryImgSrc = $thisImg.data("try-replace-with-src");
    if (tryImgSrc !== undefined && tryImgSrc) {
      RS.preloadImage(tryImgSrc, $thisImg);
    }
  });
  return $content.children();
};

// Get file extension for icon when files are selected from Box, Dropbox and Google Drive in Tiny MCE
RS.getFileExtension = function (fileName) {
  var re = /(?:\.([^.]+))?$/;
  var ext = re.exec(fileName)[1];
  return ext;
};

/**
 * Some images can take a long time to load. This method silently preloads the image pointed by 'src',
 * and when it's available it modifies '$imgToReplace' to show it.
 */
RS.preloadImage = function (src, $imgToReplace) {
  var image = new Image();
  image.onload = function () {
    $imgToReplace.attr("src", src);
  };
  image.src = src;
};

RS.chemFileExtensions = ["skc", "mrv", "cxsmiles", "cxsmarts", "cdx", "cdxml", "csrdf", "cml", "csmol", "cssdf", "csrxn", "mol", "mol2", "pdb", "rxn", "rdf", "smiles", "smarts", "sdf", "inchi"];
RS.dnaFiles = ["fa", "gb", "gbk", "fasta", "fa", "dna", "seq", "sbd", "embl", "ab1"];
/*
 * returns relative path to icon linked to given extension,
 * or unknownDocument icon if extension is not recognised
 */
RS.getIconPathForExtension = function (extension) {
  var ext = "";
  if (extension) {
    ext = extension.toLowerCase();
  }
  if (RS.chemFileExtensions.includes(ext)) {
    return "/images/icons/chemistry-file.png";
  } else if (RS.dnaFiles.includes(ext)) {
    return "/images/icons/dna-file.svg";
  }
  switch (ext) {
    case "avi":
    case "bmp":
    case "doc":
    case "docx":
    case "flv":
    case "gif":
    case "jpg":
    case "jpeg":
    case "m4v":
    case "mov":
    case "mp3":
    case "mp4":
    case "mpg":
    case "ods":
    case "odp":
    case "csv":
    case "pps":
    case "odt":
    case "pdf":
    case "png":
    case "rtf":
    case "wav":
    case "wma":
    case "wmv":
    case "xls":
    case "xlsx":
    case "xml":
    case "zip":
      return "/images/icons/" + ext + ".png";
    case "htm":
    case "html":
      return "/images/icons/html.png";
    case "ppt":
    case "pptx":
      return "/images/icons/powerpoint.png";
    case "txt":
    case "text":
    case "md":
      return "/images/icons/txt.png";
    default:
      return "/images/icons/unknownDocument.png";
  }
};

/*
 * Checks if value of an input field is empty or whitespace
 * @param input a Jquery element of a form input field
 * return true if field value is empty
 * this is not used
 */
function isEmptyInput(input) {
  return input.val().trim().length == 0;
}

// shows success message for 3 seconds.
RS.defaultConfirm = function (message) {
  RS.confirm(message, 'success', RS.defaultToastDisplayTime);
};

/*
 * message
 * variant 'success' ,'notice', 'warning', 'error'
 * stayTime
 * cfg object to override default config, optional
 */
RS.confirm = function (message, variant, duration, cfg, callback) {
  var configuration = {
    message: message,
    variant: variant,
    duration: duration || 5000,
    infinite: duration == 'infinite'
  };
  if (callback) {
    configuration.callback = callback;
  }
  // Tell React to display a toest message
  if (window.parent) { // if in an iframe
    window.parent.document.dispatchEvent(new CustomEvent('show-toast-message', { 'detail': configuration }));
  } else {
    document.dispatchEvent(new CustomEvent('show-toast-message', { 'detail': configuration }));
  }
};

RS.confirmAndNavigateTo = function (message, variant, duration, url) {
  var event = new CustomEvent('show-toast-message', {
    'detail': {
      message: message,
      variant: variant,
      duration: duration,
      callback: function () {
        RS.navigateTo(url);
      }
    }
  });

  // Tell React to display a toest message
  if (window.parent) { // if in an iframe
    window.parent.document.dispatchEvent(event);
  } else {
    document.dispatchEvent(event);
  }
};

RS.navigateTo = function (url) {
  window.location = createURL(url);
};

RS.maintenanceMsg = function (warningMsg) {
  var event = new CustomEvent('show-toast-message', {
    'detail': {
      message: warningMsg,
      variant: 'warning',
      infinite: true
    }
  });

  // Tell React to display a toest message
  if (window.parent) { // if in an iframe
    window.parent.document.dispatchEvent(event);
  } else {
    document.dispatchEvent(event);
  }
};

RS.requestMsg = function (notificationMsg) {
  var toastDiv = $().toastmessage('showToast', {
    text: notificationMsg,
    sticky: true,
    type: 'notice'
  });
  $('.toast-position-top-right').css('top', '35px');
  $('.toast-type-notice').css('padding-top', '35px');
  $('.toast-item-close').css('background-image', 'url(/images/close.gif)');
  $(toastDiv).find('.toast-item-image-notice').css('background', 'url(/images/yesNo.png)');
};

RS.notificationMsg = function (notificationMsg, showCloseButton) {
  var closeAvailable = showCloseButton || false;
  var toastDiv = $().toastmessage('showToast', {
    text: notificationMsg,
    sticky: true,
    type: 'notice',
    close: function () { return closeAvailable; }
  });
  $('.toast-position-top-right').css('top', '35px');
  $('.toast-type-notice').css('padding-top', '35px');
  var $toastCloseBtn = $(toastDiv).find('.toast-item-close');
  if (closeAvailable) {
    $toastCloseBtn.css('background-image', 'url(/images/close.gif)');
  } else {
    $toastCloseBtn.remove();
  }
};

/**
 * Helper function to work with icon upload dialog used in form-editing and user profile.
 * Assumes that the following elements are present in the dialog:
 * - an image preview div with id 'imagePreview';
 * - a div for error messages called 'msgAreaImage'
 * cfg takes the following mandatory arguments:
 * - fileInputId The id of the file input field
 * - postParamName the name of the multipart file parameter expected in the controller
 * -postURL the URL to post the form to
 * -onloadSuccess(data) a function that is called when the file upload succeeds.
 *  If the controller returns a JSON object, this will be available in the 'data' argument passed to this function.
 *
 *  and one optional parameter:
 *  - maxFileSizekB the max file size (in kB)
 *
 * @param cfg
 */
RS.submitIconUpload = function (cfg) {
  var maxFileSizekB = cfg.maxFileSize || 1000;
  var file = cfg.fileBlob || $(cfg.fileInputId).prop('files')[0];

  var errorMsg;
  if (!file) {
    errorMsg = "Please select something to upload";
  } else if (!file.type.match('image.*')) {
    errorMsg = "The file type is not an image.";
  } else if (file.size > maxFileSizekB * 1000) {
    errorMsg = "Please upload a smaller file, that's less  than " + maxFileSizekB + "kB";
  }
  if (errorMsg) {
    $('#imagePreview').children().remove();
    $('#msgAreaImage').text(errorMsg);
    $('#msgAreaImage').slideDown('slow');
    return;
  }

  var fd = new FormData();
  fd.append(cfg.postParamName, file, cfg.filename);
  var xhr = new XMLHttpRequest();
  var postURL = createURL(cfg.postURL);
  //Async is false to upload files one to one
  xhr.open("post", postURL, false);
  xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");

  xhr.addEventListener("load", function (e) {
    var json = null;
    if (this.response != null && this.response.length > 0) {
      json = $.parseJSON(this.response);
    }
    cfg.onloadSuccess(json);
  });
  // Send data
  xhr.send(fd);
};

/**
 * Takes a set of JQuery elements of radio or choice input fields, and serialises it wiht escaping
 * characters that are usually escaped in URLs. This is a bit of  a hack to make RSpace Choice/Radio forms
 * handle some alpanumeric characters.
 */
RS._serializeWithNoEscapes = function (inputFields$, paramName) {
  var choiceString = "";
  if (paramName === undefined || paramName == "") {
    paramName = "fieldChoices";
  }
  inputFields$.each(function (index) {
    choiceString = choiceString + paramName + "=" + $(this).val() + "&";
  });
  choiceString = choiceString.substring(0, choiceString.length - 1);
  return choiceString;
};

RS.isPdfPreviewSupported = function (fileExtension) {
  let ext = "";
  if (fileExtension) {
    ext = fileExtension.toLowerCase();
  }

  if(ext === 'pdf'){
    // pdf files aren't converted but just streamed as is, so are always supported
    return true;
  }

  if(!RS.asposeEnabled){
    // not a pdf file so requires conversion, but aspose conversion is disabled
    return false;
  }

  return ['doc', 'docx','md', 'odt', 'rtf', 'txt', 'xls', 'xlsx', 'csv', 'ods', 'pdf', 'ppt', 'pptx', 'odp'].includes(ext)
};

RS.isSnapGeneFormat = function (fileExtension) {
  var ext = "";
  if (fileExtension) {
    ext = fileExtension.toLowerCase();
  }
  return RS.dnaFiles.includes(ext);
}

/**
 * returns promise that will be resolved with download link to pdf version of requested document
 */
RS.getPdfDownloadLink = function (documentId, revisionId, fileExtension, $elementToBlock) {

  var deferred = $.Deferred();
  var revisionUrlSuffix = revisionId != null ? "?revision=" + revisionId : "";

  // if already a pdf then conversion not required
  if (fileExtension === 'pdf') {
    deferred.resolve('/Streamfile/' + documentId + revisionUrlSuffix);
    return deferred.promise();
  }

  var blockMessage = 'Preparing the file...';
  RS.blockPage(blockMessage, false, $elementToBlock);

  // asking conversion service for link to resource, may return error if can't convert
  var jqxhr = $.ajax({
    url: "/Streamfile/ajax/convert/" + documentId + "?outputFormat=pdf" + revisionUrlSuffix.replace('?', '&'),
    timeout: 60000
  });

  jqxhr.always(function () {
    RS.unblockPage($elementToBlock);
  });

  jqxhr.done(function (result) {
    if (result.errorMsg !== null) {
      apprise("Couldn't convert the document");
      deferred.reject();
      return;
    }

    var convertedFile = result.data;
    if (convertedFile !== null) {
      var convertedFileUrl = "/Streamfile/direct/" + documentId + "?fileName=" + convertedFile;
      deferred.resolve(convertedFileUrl);
    }
  });

  jqxhr.fail(function (jqxhr, textStatus) {
    var errorMsgToDisplay;
    if (textStatus === "timeout") {
      errorMsgToDisplay = "PDF conversion takes longer than usual. Please try again later.";
    } else {
      errorMsgToDisplay = "Conversion to PDF could not complete.<br/>Status:" + jqxhr.status + "<br />" + jqxhr.responseText;
    }
    if ($elementToBlock) {
      $elementToBlock.html("<br />" + errorMsgToDisplay);
    } else {
      apprise(errorMsgToDisplay);
    }

    deferred.reject();
  });

  return deferred.promise();
};

RS.openWithPdfViewer = function (documentId, revisionId, name, fileExtension) {
  /* The public view is for a document made accessible to non RSpace users */
  const publicView = $("#public_document_view").length > 0;
  var pageNum = RS._pdfPageNumbers[documentId] || 1;

  RS.getPdfDownloadLink(documentId, revisionId, fileExtension).then(function (downloadLink) {

    var pdfViewerLink = '/scripts/pdfjs/web/viewer.html';
    var encodedLink = encodeURIComponent((publicView?'/public/publicView':'')+downloadLink);
    var encodedTitle = encodeURIComponent(name + ' - RSpace preview');

    var pageNumAnchor = '';
    if (pageNum) {
      pageNumAnchor = '#page=' + pageNum;
    }

    var newWindowLink = pdfViewerLink + "?file=" + encodedLink + '&filename=' + encodedTitle + pageNumAnchor;
    RS.openInNewWindow(newWindowLink);
  });
};

/**
 * Method for opening url in a new tab. Displays a dialog when window is blocked by pop-up blocker.
 * @returns a promise for window object reference
 */
RS.openInNewWindow = function (url) {

  var deferred = $.Deferred();
  var newWindow = window.open(url, '_blank');

  // some browsers block opening new tab from asynchronous js, allowing it only for user-initiated clicks
  if (newWindow === null || newWindow === undefined) {
    Apprise("Pop-up blocker is stopping RSpace from opening new tab.", {
      buttons: {
        confirm: {
          text: "Open anyway",
          className: "blue",
          action: function () {
            Apprise('close');
            newWindow = window.open(url, '_blank');
            deferred.resolve(newWindow);
          }
        }
      }
    });
  } else {
    deferred.resolve(newWindow);
  }

  return deferred.promise();
};

/**
 * Method for opening OAuth2 authorization window, which will be monitored
 * for user actions and closed automatically after successful authorization.
 */
RS.openOauthAuthorizationWindow = function (url, redirect_uri, successElemSelector, onSuccess) {
  var newWindowPromise = RS.openInNewWindow(url);
  newWindowPromise.done(function (authWindow) {
    var pollTimer = window.setInterval(function () {
      try {
        // trying to check content will throw Single Origin Policy error until redirects are done and user is back on RSpace page
        var url = authWindow.document.URL;
        // if we are here then no exception, and accessed redirect uri
        if (url.indexOf(redirect_uri) > 0 && $(authWindow.document.body).find(successElemSelector).length) {
          window.clearInterval(pollTimer);
          onSuccess(authWindow);
          authWindow.close();
        }
      } catch (e) {
        // expected Single Origin Policy errors until authorization complete
      }
    }, 1000);
  });
};

RS._downloadedPdfs = {};
RS._pdfPageNumbers = {};
RS._pdfRenderingInProgress = {};

/**
 * Appends pdfPreviewPanel to given $div and starts loading the PDF preview into pdfPreviewCanvas.
 */
RS.loadPdfPreviewIntoDiv = function (id, revisionId, fileName, extension, $div) {

  if ($div.find('.pdfPreviewPanel').data('previewLoaded')) {
    return;
  }

  var $previewPanel = $('#pdfPreviewPanelTemplate > .pdfPreviewPanel').clone();
  $previewPanel.data('id', id);
  $div.empty().append($previewPanel);

  var $previewMainPanel = $previewPanel.find('.pdfPreviewMainPanel');
  var $canvas = $previewMainPanel.find(".pdfPreviewCanvas");
  RS.getPdfDownloadLink(id, revisionId, extension, $previewMainPanel).then(function (pdfDownloadLink) {

    RS.blockPage("Generating preview...", false, $previewMainPanel);

    PDFJS.getDocument(pdfDownloadLink).then(function (pdf) {
      RS.unblockPage($previewMainPanel);
      $previewPanel.data('previewLoaded', true);

      RS._downloadedPdfs[id] = pdf;
      RS._pdfPageNumbers[id] = 1;

      $previewPanel.find('.pdfPageCount').text(pdf.numPages);
      $previewPanel.find('.pdfPreviewPageNumDiv, .previewPageChangeDiv')
        .css('visibility', 'visible');

      RS._renderPdfPage($previewPanel);
    }, function () {
      RS.unblockPage($previewMainPanel);
    });
  });

  $previewPanel.find('.previewPreviousPageBtn').click(function () {
    if (RS._pdfPageNumbers[id] <= 1) {
      return;
    }
    RS._pdfPageNumbers[id]--;
    RS._renderPdfPage($previewPanel);
  });
  $previewPanel.find('.previewNextPageBtn').click(function () {
    if (RS._pdfPageNumbers[id] >= RS._downloadedPdfs[id].numPages) {
      return;
    }
    RS._pdfPageNumbers[id]++;
    RS._renderPdfPage($previewPanel);
  });

  $canvas.click(function () {
    RS.openWithPdfViewer(id, revisionId, fileName, extension);
  });
};

RS._renderPdfPage = function ($previewPanel) {

  var id = $previewPanel.data('id');
  var pdf = RS._downloadedPdfs[id];
  var pageNum = RS._pdfPageNumbers[id];

  var $canvas = $previewPanel.find(".pdfPreviewCanvas");
  $previewPanel.find('.pdfPageNum').text(pageNum);

  var $attachmentDiv = $previewPanel.parents(".attachmentDiv");
  var attachmentDivWidth = $attachmentDiv.width();

  pdf.getPage(pageNum).then(function (page) {

    if (RS._pdfRenderingInProgress[id]) {
      return;
    }
    RS._pdfRenderingInProgress[id] = true;

    var canvas = $canvas.get(0);

    // lets measure the full-scale viewport
    var fullScale = 1;
    var viewport = page.getViewport(fullScale);

    // we need to fit preview into limited width & height
    var scaleHeight = $canvas.height() / viewport.height;
    var scaleWidth = $canvas.width() / viewport.width;
    var newScale = Math.min(1, scaleHeight, scaleWidth);

    viewport = page.getViewport(newScale);
    canvas.width = viewport.width;
    canvas.height = viewport.height;

    $canvas.css("width", viewport.width);
    $canvas.css("height", viewport.height);

    // widen info panel for portrait preview
    var $infoPanel = $attachmentDiv.find('.attachmentPreviewInfoPanel');
    $infoPanel.css("width", attachmentDivWidth - viewport.width - 130);

    var context = canvas.getContext('2d');
    var renderContext = {
      canvasContext: context,
      viewport: viewport
    };
    var renderTask = page.render(renderContext);
    renderTask.promise.then(function () {
      RS._pdfRenderingInProgress[id] = false;
      if (pageNum !== RS._pdfPageNumbers[id]) {
        // new page rendering is pending
        RS._renderPdfPage($previewPanel);
      }
    }, function () {
      RS._pdfRenderingInProgress[id] = false;
    });
  });
};

/*
  API for managing breadcrumb area. Breadcrumbs are used by workspace, gallery and in coreEditor.
  Breadcrumb elements are stored in surrounding breadcrumbTag div, in its 'data-bcrumb' attribute.

  Note that displayName should be escaped, otherwise xss is possible.
*/
RS.addBreadcrumbElement = function (bcrumbTagId, elemId, safeDisplayName) {

  var $bcrumbTag = $("#breadcrumbTag_" + bcrumbTagId);
  var bcrumb = $bcrumbTag.data('bcrumb') || [];
  bcrumb.push({
    id: elemId,
    displayname: safeDisplayName
  });

  $bcrumbTag.data('bcrumb', bcrumb);
};

RS.getBreadcrumbIds = function (bcrumbTagId) {
  var $bcrumbTag = $("#breadcrumbTag_" + bcrumbTagId);
  var $bcrumElemsDiv = $bcrumbTag.children('.breadcrumbElems');
  var bcrumb = $bcrumbTag.data('bcrumb') || [];

  var breadcrumbIds = [];
  $.each(bcrumb, function (i, elem) {
    breadcrumbIds.push(elem.id);
  });
  return breadcrumbIds;
};

RS.refreshBreadcrumbElems = function (bcrumbTagId) {

  var $bcrumbTag = $("#breadcrumbTag_" + bcrumbTagId);
  var $bcrumElemsDiv = $bcrumbTag.children('.breadcrumbElems');
  $bcrumElemsDiv.empty();

  var bcrumb = $bcrumbTag.data('bcrumb') || [];
  var bcrumbLength = bcrumb.length;

  if (bcrumbLength <= 1) {
    $bcrumbTag.hide(); // for some reason in worspace the breadcrumbs hide when empty but in gallery they don't
    return; // no need to display only one name
  }
  $bcrumbTag.show();

  var html = "";
  for (var i = 0; i < bcrumbLength - 1; i++) {
    html += " <a class='breadcrumbLink' id='breadcrumb_" + bcrumb[i].id +
      "'> " + bcrumb[i].displayname + "</a> / ";
  }
  // last element displayed as text
  html += "<span id='recordNameInBreadcrumb'>" + bcrumb[bcrumbLength - 1].displayname + "</span>";

  $bcrumElemsDiv.html(html);
};

/* to add an element to breadcrumb trail. displayName has to be escaped. */
RS.addBreadcrumbAndRefresh = function (bcrumbTagId, elemId, safeDisplayName) {

  var foundInBreadcrumbs = RS._resetToBreadcrumb(bcrumbTagId, elemId);
  if (!foundInBreadcrumbs) {
    RS.addBreadcrumbElement(bcrumbTagId, elemId, safeDisplayName);
  }
  RS.refreshBreadcrumbElems(bcrumbTagId);
};

/* if element with given id is among breadcrumbs all following elements will be removed */
RS._resetToBreadcrumb = function (bcrumbTagId, id) {

  var $bcrumbTag = $("#breadcrumbTag_" + bcrumbTagId);
  var bcrumb = $bcrumbTag.data('bcrumb') || [];
  var found = false;

  var breadcrumbsLength = bcrumb.length;
  if (breadcrumbsLength > 0) {
    for (var i = 0; i < breadcrumbsLength; i++) {
      var bcrumbElem = bcrumb[i];
      if (bcrumbElem.id === id) {
        var removed = bcrumb.splice(i + 1);
        // saving shortened breadcrumb back into element
        $bcrumbTag.data('bcrumb', bcrumb);
        found = true;
        break;
      }
    }
  }
  return found;
};

RS.clearBreadcrumb = function (bcrumbTagId) {
  var $bcrumbTag = $("#breadcrumbTag_" + bcrumbTagId);
  $bcrumbTag.data('bcrumb', []);
  RS.refreshBreadcrumbElems(bcrumbTagId);
};


/**
 * Does NOT work at the moment with jQuery UI dialogs open before the Apprise.
 * Might require moving to just one dialog plugin to get rid of this conflicts.
 *
 * Use after opening an Apprise dialog to bring it to focus.
 *
 * @param  {bool} useTimeOut Use true to force a small timeout; this aims to
 * be a workaround for possible use cases where other pieces of code give focus
 * to other controls, and you want to 'have the last word'.
 */
RS.focusAppriseDialog = function (useTimeOut) {
  var timeOut = useTimeOut ? 200 : 0;
  setTimeout(function () {
    $("#apprise-btn-confirm").focus();
  }, timeOut);
};

/**
 * Preferred over RS.addOnEnterHandlerToDocument where an equivalent of a click
 * event is to be emulated by processing keyboard events.
 *
 * Uses RS.isKeypressEquivalentToClick to listen to both Enter AND Space keys
 * as it should be.
 */
RS.addOnKeyboardClickHandlerToDocument = function (selector, handler) {
  $(document).on('keypress', selector, function (e) {
    if (RS.isKeypressEquivalentToClick(e)) {
      return handler.call(this, e);
    }
  });
};

/**
 * Use this where only listening to Enter key events is preferred, for example
 * on text input fields where Enter should do an action but Space shouldn't
 * do anything special. To enable keyboard users to 'click' on elements, DO NOT
 * use this, but rather RS.addOnKeyboardClickHandlerToDocument.
 */
RS.addOnEnterHandlerToDocument = function (selector, handler) {
  $(document).on("keypress", selector, function (e) {
    var code = (e.keyCode ? e.keyCode : e.which);
    if (code === $.ui.keyCode.ENTER) {
      return handler.call(this, e);
    }
  });
};

RS.addOnEnterHandlerToElement = function ($element, handler) {
  $element.on("keypress", function (e) {
    var code = (e.keyCode ? e.keyCode : e.which);
    if (code === $.ui.keyCode.ENTER) {
      handler(e);
    }
  });
};

RS.emulateKeyboardClick = function (selector) {
  $(document).on('keypress', selector, function (e) {
    if (RS.isKeypressEquivalentToClick(e)) {
      e.preventDefault();
      $(this).click();
    }
  });
};

RS.emulateKeyboardDoubleClick = function (selector) {
  $(document).on('keypress', selector, function (e) {
    if (RS.isKeypressEquivalentToClick(e)) $(this).dblclick();
  });
};

RS.onEnterSubmitJQueryUIDialog = function (selector) {
  $(document).on('keypress', selector, function (e) {
    if (e.which == 13) {

      // stop the event from bubbling up and opening the same dialog again
      e.stopPropagation();

      $(this).next('.ui-dialog-buttonpane')
        .find('.ui-dialog-buttonset button:eq(1)')
        .click();
    }
  });
};

RS.addOnClickPopoverToElement = function ($element) {
  $element.popover();
};

/*
 * execute passed function if user's browser supports media devices,
 * and user has at least one videoinput device
 */
RS.onVideoInputAvailable = function (videoAvailableCallback) {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !navigator.mediaDevices.enumerateDevices) {
    console.log('browser doesn\'t support enumerateDevices()');
    return;
  }

  navigator.mediaDevices.enumerateDevices()
    .then(function (devices) {
      var videoInputAvailable = false;
      devices.forEach(function (device) {
        if (device.kind == 'videoinput') {
          videoInputAvailable = true;
          return false;
        }
      });
      if (videoInputAvailable) {
        videoAvailableCallback();
      } else {
        console.log('no videoinput device available');
      }
    });
};

RS.isNullOrUndefined = function (value) {
  return (typeof value === "undefined" || value === null);
};

/* show nfs file info panel */
RS._populateNfsFileInfoPanelAndOpen = function ($link) {

  var relPath = $link.attr('rel');
  var name = $link.text();
  var linkType = $link.data('linktype');
  var nfsId = $link.data('nfsid');
  console.log('rel: ' + relPath + ', linktype: ' + linkType + ', nfsId: ' + nfsId);

  if (!RS.isNullOrUndefined(relPath)) {
    if (!relPath || relPath.indexOf(':') < 0) {
      console.warn('unparsable rel attribute: ' + relPath);
      return;
    }

    var sepId = relPath.indexOf(':');
    var fileStoreId = relPath.substr(0, sepId);
    var relFilePath = relPath.substr(sepId + 1);

    var jqxhr = $.get('/netFiles/ajax/nfsFileStoreInfo', { fileStoreId: fileStoreId });

    var $newInfoPanel = $('.nfsFileInfoPanel');
    jqxhr.done(function (result) {
      var data = result.data;
      console.log("retrieved: ", data);
      if (data) {
        var isFolder = linkType === 'directory';
        var headerText = isFolder ? "Folder details: " : "File details:";
        var isSmbj = data.fileSystem.clientType === 'SMBJ';
        var isIrods = data.fileSystem.clientType === 'IRODS';
        $newInfoPanel.find('.nfsInfoTableHeaderRow').text(headerText);
        $newInfoPanel.find('.nfsInfoPanel-name').text(name);

        var fullPath = data.path + relFilePath;
        $newInfoPanel.find('.nfsInfoPanel-path').text(fullPath);
        $newInfoPanel.find('.nfsInfoPanel-fileSystemName').text(data.fileSystem.name);
        $newInfoPanel.find('.nfsInfoPanel-fileSystemPath').text(data.fileSystem.url);
        $newInfoPanel.find('.nfsInfoShareNameRow').toggle(isSmbj);
        $newInfoPanel.find('.nfsUpdatePathBtn').toggle(isIrods);
        if (isSmbj) {
          $newInfoPanel.find('.nfsInfoPanel-fileSystemShareName').text(data.fileSystem.options.SAMBA_SHARE_NAME);
        } else if (isIrods && !isFolder) {
          $newInfoPanel.find('.nfsUpdatePathBtn').off('click').on("click", function () {
            RS.updateNfsPath(relPath, nfsId, data.fileSystem.id, $newInfoPanel);
          }).button();
        }

        $newInfoPanel.find('.nfsFileDownloadBtn').toggle(!isFolder);
        if (!isFolder) {
          $newInfoPanel.find('.nfsFileDownloadBtn').off('click').on("click", function () {
            RS.downloadNetFile(relPath, nfsId, data.fileSystem.id);
          }).button();
        }
        $('#nfsFileInfoDialog').dialog('open');
      }

    });
    jqxhr.fail(function (result) {
      apprise('An error occured on filestore info retrieval' + result.responseText);
    });
  }
};

var netFileInfoDialogInitialised = false;

RS.initAndOpenNetFileInfoDialog = function ($link) {
  if (!netFileInfoDialogInitialised) {
    $(document).ready(function () {
      RS.switchToBootstrapButton();
      $('#nfsFileInfoDialog').dialog({
        title: 'Filestore link details',
        autoOpen: false,
        modal: true,
        minWidth: 350,
        open: function () {
          $('.ui-dialog-buttonset button').focus();
        },
        buttons: {
          "OK": function () {
            $(this).dialog("close");
          }
        }
      });
      RS.switchToJQueryUIButton();
    });

    netFileInfoDialogInitialised = true;
  }

  RS._populateNfsFileInfoPanelAndOpen($link);
};

RS.addNetFileClickHandler = function () {
  $(document).on("click", ".nfs_file, .samba_file", function () {
    var $link = $(this);
    RS.initAndOpenNetFileInfoDialog($link);
    return false;
  });
};

RS.downloadNetFile = function (relPath, nfsId, fileSystemId) {
  if (!RS.isNullOrUndefined(relPath)) {
    var nfsparams = {
      namepath: relPath,
      nfsId: nfsId
    };
    RS.blockPage("Downloading...");
    var jqxhr = $.post('/netFiles/ajax/prepareNfsFileForDownload', nfsparams);
    jqxhr.done(function (result) {
      if (result == "ok") {
        window.location = "/netFiles/ajax/downloadNfsFile";
      } else if (result == "need.log.in") {
        var fileStoreId = relPath.split(':')[0];
        RS.showNetFileLoginDialog(fileSystemId, fileStoreId, function () {
          RS.downloadNetFile(relPath, nfsId, fileSystemId); // retry download after successful login
        });
      } else {
        apprise(result);
      }
    });
    jqxhr.fail(function (result) {
      apprise('An error occured on file download: ' + result.responseText);
    });
    jqxhr.always(function () {
      RS.unblockPage();
    });
  }
};

RS.updateNfsPath = function(relPath, nfsId, fileSystemId, $infoPanel) {
  if (!RS.isNullOrUndefined(relPath)) {
    var nfsparams = {
      namepath: relPath,
      nfsId: nfsId
    };
    console.log("NfsParams: ", nfsparams);
    RS.blockPage("Getting Current Path...");
    var jqxhr = $.post('/netFiles/ajax/getCurrentPath', nfsparams);
    jqxhr.done(function (result) {
      if (result !== "need.log.in" && !RS.isBlank(result)) {
        $infoPanel.find('.nfsInfoPanel-path').text(result);
      } else if (result === "need.log.in") {
        var fileStoreId = relPath.split(':')[0];
        RS.showNetFileLoginDialog(fileSystemId, fileStoreId, function () {
          RS.updateNfsPath(relPath, nfsId, fileSystemId, $infoPanel); // retry download after successful login
        });
      } else {
        apprise(result);
      }
    });
    jqxhr.fail(function (result) {
      apprise('An error occured retrieving current path: ' + result.responseText);
    });
    jqxhr.always(function () {
      RS.unblockPage();
    });
  }
};

RS.showNetFileLoginDialog = function (fileSystemId, fileStoreId, afterLoginCallback) {

  if (!fileSystemId && !fileStoreId) {
    console.warn("no fileSystemId nor fileStoreId provided");
    return;
  }

  var jqxhrScript = $.getScript('/scripts/pages/workspace/gallery/netfiles.js');
  var jqxhrPage = $.get('/netFiles/ajax/netFilesLoginView');

  $.when(jqxhrScript, jqxhrPage).done(function (script, page) {

    $(document.body).append(page[0]);

    /* init method defined in downloaded netfiles.js script that is loaded at this point  */
    initNetFilesLoginDialog();

    var fileSystem;
    if (fileSystemId != null) {
      fileSystem = getFileSystemById(fileSystemId);
    } else if (fileStoreId != null) {
      fileSystem = getFileStoreById(fileStoreId).fileSystem;
    }

    if (fileSystem.authType === "PASSWORD") {
      showUsernamePasswordDialog(fileSystem, afterLoginCallback);
    } else {
      apprise('Sorry, there is some problem with logging you into ' + fileSystem.name +
        ' please go to <a href="/gallery/netfiles" target="_blank">Gallery Filestores</a>' +
        ' and try login there');
    }

  }).fail(function (jqxhr, settings, exception) {
    apprise('An error occured on loading net files gallery');
  });

};

/*
 * Ensures that when all buttons of a button group in the toolbar are hidden,
 * we don't get two button group dividers next to each other. Needed e.g. when
 * you create a new notebook, as most buttons get hidden in this case.
 */
RS.checkToolbarDividers = function (selector) {
  $(selector).css({ opacity: 0, display: 'inline-block' });
  var bars = $(selector);

  if (bars.length >= 2) {
    // if there are at least 2 dividers, check every consecutive pair and hide second divider
    // if it is very close to first one (indicating that no visible elements are between the two dividers)
    var previousLeftPosition = 0;
    bars.each(function (i) {
      if (i == 0) {
        previousLeftPosition = $(this).position().left;
        $(this).css({ opacity: 1 });
      } else {
        if (Math.abs($(this).position().left - previousLeftPosition) < 15) {
          previousLeftPosition = $(this).position().left;
          $(this).hide();
        } else {
          previousLeftPosition = $(this).position().left;
          $(this).css({ opacity: 1 });
        }
      }
    });
  }
};

RS.addOrderByTooltips = function () {
  $(".orderByLink").each(function () {
    $(this).attr("title", "Order by " + $(this).text().trim());
  });
};

/* initially both set to jquery button */
$.fn.bootstrapButton = $.fn.button;
$.fn.jqueryUIButton = $.fn.button;

/* bootstrap's noConflict() should be called somewhere on the page before calling this method, otherwise it does nothing */
RS.switchToBootstrapButton = function () {
  $.fn.button = $.fn.bootstrapButton;
};

RS.switchToJQueryUIButton = function () {
  $.fn.button = $.fn.jqueryUIButton;
};

/*
 * Sets up tooltip titles for new version of pagination panel,
 * needs to be called every time the panel changes (when user
 * changes number of rows to display on a tabular page).
 */
RS.addPaginationTooltips = function (selector) {
  var paginationWrapper = $(selector);
  var title;
  paginationWrapper.find("a").each(function (i) {
    if ($(this).hasClass("blank_space")) return;

    // First and Last page sometimes get textual labels rather than numbers.
    if ($(this).text().indexOf("First") >= 0) {
      title = "First page";
    } else if ($(this).text().indexOf("Last") >= 0) {
      title = "Last page";
    } else {
      title = "Page " + parseInt($(this).html());
    }

    if ($(this).parent().hasClass("active")) {
      title += " (current page)";
    }
    $(this).attr("title", title);
  });
  // hide pagination panel entirely (including any undesirable margins) iff it's empty
  if (paginationWrapper.find("a").length) {
    paginationWrapper.show();
  } else {
    paginationWrapper.hide();
  }
};

/*
 * Takes care of everything from setting up event handlers and tooltips
 * to handling events (individual handler to be provided) and updating
 * tooltips when pagination changes. Only for new, Bootstrap-like pagination
 * with classnames following the conventions.
 */
RS.setupPagination = function (paginationEventHandler, linkSelector) {
  linkSelector = linkSelector || ".page_link";
  var paginationSelector = ".pagination.new";

  RS.addPaginationTooltips(paginationSelector);
  /* Pagination tooltips need refreshing every time pagination panel changes,
  on some pages (Directory) this is even when pagination is clicked */
  $(document).on("DOMNodeInserted", paginationSelector, function (e) {
    RS.addPaginationTooltips(paginationSelector);
  });

  /* Using provided handler for moving between pages */
  $('body').on('click', linkSelector, function (e) {
    e.preventDefault();
    if (!$(this).parent().hasClass("active")) { // if current page clicked, do nothing
      paginationEventHandler($(this), e);
      RS.addPaginationTooltips(paginationSelector);
    }
  });
};

/*
 * Generates new, list-based, Bootstrap-compatible pagination dynamically. Handles large number
 * of pages by inserting ... . Always displays N first pages, N last pages and +- N pages around
 * the current page; N specified as 'offset'. Inserts numerical value as a data attribute rather
 * than creating a href link.
 */
RS.generateBootstrapPagination = function (noOfPages, activePage, linkClass) {
  var pagination = $("<ul>").addClass("pagination new");
  var offset = 2;
  var displayButton = true;
  for (var i = 0; i < noOfPages; i++) {
    if (noOfPages < 15 || i < offset || (i + 2 > activePage - offset && i < activePage + offset) || (noOfPages - i - 1) < offset) {
      if (!displayButton) {
        pagination.append("<li><a class='blank_space'>...</a></li>");
        displayButton = true;
      }
      var link = $("<a>").addClass(linkClass).html(i + 1).attr("data-pagenumber", i + 1);
      var button = $("<li>").html(link);
      if (i + 1 == activePage) button.addClass("active");
      pagination.append(button);
    } else {
      displayButton = false;
    }
  }
  return pagination;
};

/*
 * Form JSON object from query parameters in URL
 */
RS.getJsonParamsFromUrl = function (url) {
  var result = {};
  if (url.indexOf("?") >= 0) {
    parts = url.split("?");
    url = parts[parts.length - 1];
  }
  url.split("&").forEach(function (part) {
    var item = part.split("=");
    result[item[0]] = decodeURIComponent(item[1]);
  });
  return result;
};

/*
 * Needed for retrieving text properties of an element. Not all browsers support
 * .css("font"); also, we might want to get other relevant properties, e.g. "letter-spacing"
 */
RS.getFontProperties = function (elem) {
  var names = ["font-style", "font-variant", "font-weight", "font-size",
    "line-height", "font-family", "letter-spacing"
  ];
  var properties = {};
  $.each(names, function (i, name) {
    properties[name] = elem.css(name);
  });
  return properties;
};


/**
 * Retrieve the latest state of the app, update it and post to /integration/update endpoint.
 * 'appName' is required, 'enablement' or 'options' are optional
 */
RS.updateRSpaceApp = function (appName, enablement, options) {

  var deferred = $.Deferred();
  var getAppReq = $.get('/integration/integrationInfo', { name: appName });
  getAppReq.done(function (getAppResponse) {
    var app = getAppResponse.data;
    if (!RS.isNullOrUndefined(enablement)) {
      app.enabled = enablement;
    }
    if (!RS.isNullOrUndefined(options)) {
      app.options = options;
    }
    var postAppReq = RS.sendJsonPostRequestToUrl('/integration/update', app);
    postAppReq.done(function (response) {
      deferred.resolve(response);
    });
    postAppReq.fail(function () {
      RS.ajaxFailed("App update request", false, postAppReq);
      deferred.reject(postAppReq);
    });
  });
  getAppReq.fail(function () {
    deferred.reject(getAppReq);
  });

  return deferred.promise();
};

RS.sendJsonPostRequestToUrl = function (url, jsonObject) {
  return $.ajax({
    type: 'POST',
    url: url,
    data: JSON.stringify(jsonObject),
    contentType: "application/json; charset=utf-8",
    dataType: "json"
  });
};

/**
 * Helper method to post data to the backend using the fetch API
 * @param {string} url the url to send the request too
 * @param {JSON} data the data to post
 * @returns the JSON response
 */
 RS.postData = async function postData(url = '', data = {}) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'

    },
    body: JSON.stringify(data)
  });
  return await response.json();
}

//If the page has display modifications, apply a function which will show/hide date modified on each field
if ($(".displayRevisions")) {
  $(this).click(function () {
    if ($(".displayRevisions").is(":checked")) {
      $(".lastModified").show('slow');
    } else {
      $(".lastModified").hide('slow');
    }

  });
}

RS.getGlobalIdWithoutVersionId = function (globalId) {
  return globalId.split('v')[0];
}

RS.getVersionIdFromGlobalId = function (globalId) {
  if (globalId.indexOf('v') > 0) {
    return globalId.split('v')[1];
  }
  return null;
}

/* Returns URL to view a document */
function getDocumentViewUrl(notebookId, recordId, withSettingsKey) {
  if (notebookId) {
    return "/notebookEditor/" + notebookId +
      (recordId ? "?initialRecordToDisplay=" + recordId : "") +
      (withSettingsKey ? "&settingsKey=" + settingsKey : "");
  } else {
    return "/workspace/editor/structuredDocument/" + recordId +
      (withSettingsKey ? "?settingsKey=" + settingsKey : "");
  }
}

RS.checkSnapgeneAvailablity = function () {
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
  }

  // array of ajax promises
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
  }).catch(function (error) {
	  RS.saveUserSetting('snapgene-available', "false");
  });
}

function DeviceMeta() { }

DeviceMeta.prototype.name = 'DeviceMeta';

/**
 * @return {boolean}
 * @throws {Error}
 */
DeviceMeta.isMobile = function () {
  var userAgent = this.getUserAgent();

  var userAgentPart = userAgent.substr(0, 4);

  return /(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino/i.test(userAgent)
    || /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(userAgentPart);
};

/**
 * @return {boolean}
 * @throws {Error}
 */
DeviceMeta.isMobileOrTablet = function () {
  var userAgent = this.getUserAgent();

  var userAgentPart = userAgent.substr(0, 4);

  return /(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino|android|ipad|playbook|silk/i.test(userAgent)
    || /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(userAgentPart)
    || this.isIpadOS();
};

/**
 * @return {string|null}
 * @throws {Error}
 */
DeviceMeta.getUserAgent = function () {
  var userAgent = navigator.userAgent
    || navigator.vendor
    || window.opera
    || null;

  if (!userAgent)
    throw new Error('Failed to look for user agent information.');

  return userAgent;
};

DeviceMeta.isIpadOS = function () {
  return navigator.maxTouchPoints &&
    navigator.maxTouchPoints > 2 &&
    /MacIntel/.test(navigator.platform);
}
