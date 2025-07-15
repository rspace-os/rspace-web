
// Currently 'public' extensions are used to make this work for public notebooks
// These default_extensions are for the 'non-public' version of the code.
const default_extensions = {
  toolbarModifications: () => {
  },
  header: () => "",
  springMVCUrlReroutePrefix: "",
  topHeaderModify: () => {
  },
  imageSrcPrefix: "",
  scriptsPrefix: "",
  loadPageModify: () => {
  },
  selectFileTreeBrowserRecordById: (dataID) => {
    //call fileTreeBrowser, imported by jsp
    selectFileTreeBrowserRecordById(dataID);
  },
  isFileTreeBrowserVisible: () => {
    //call fileTreeBrowser, imported by jsp
    return isFileTreeBrowserVisible();
  },
  //dont know why default behaviour is to show last entry in notebook. Public notebook will show first entry instead
  useFirstRecord: false
}
/* jshint maxerr: 100 */
function journal($, extensions = default_extensions) {
  var $plugin;
  var journalSettings;
  var journalPrivateVars;
  var reactToolbarHasRendered;
  var methods = {

    init: function(options) {
      // Create some defaults, extending them with any options that were provided
      var settings = $.extend({}, options);
      settings.imgsPrefix = extensions.imageSrcPrefix;
      var journalTemplate = $('#journalPageTemplate').html();
      var journalHTML = Mustache.render(journalTemplate, settings);
      var privateVars = {
        pageName: null,
        recordPosition: 0,
        historyPosition: 1,
        selectedRecordId: null,
        journalPageHtml: journalHTML,
        journalEmpty: true,
        searchModeOn: false,
        notebookName: options.notebookName,
        entryCount: options.entryCount,
        displayedRecordIds: null,
        baseURL: null,
        globalId: null,
        modificationDate: null,
        uriFragment: null
      };

      reactToolbarHasRendered = new Promise((resolve) => {
        window.addEventListener("ReactToolbarMounted", () => {
          resolve();
        });
      });

      // initialise global (plugin) vars
      $plugin = $(this);
      journalSettings = settings;
      journalPrivateVars = privateVars;

      methods.setup();
    },

    setup: function() {

      if (journalSettings.parentRecordId == null) {
        alert("No parent record ID was set, journal view cannot be rendered");
        return false;
      }

      var pluginIdSelector = "#" + $plugin.attr("id");
      var tdata = {
        pluginIdSelector: pluginIdSelector,
        imgsPrefix: extensions.imageSrcPrefix,
        scriptsPrefix: extensions.scriptsPrefix
      };
      var toolbarTemplate = $('#toolbarTemplate').html();
      var toolbarHTML = Mustache.render(toolbarTemplate, tdata);
      var toolbarDisplay = $(toolbarHTML).html();
        extensions.toolbarModifications();
      $plugin.before(extensions.header(notebookName, publicationSummary, contactDetails, publishOnInternet === "true")+ toolbarDisplay);

      $(".journalToolbarButton").button();

      RS.emulateKeyboardClick('#entriesButton');
      $(document).on('keyup', '.journalEntry', function(e) {
        if (RS.isKeypressEquivalentToClick(e)) {
          $(this).mousedown();
          $(this).mouseup();
        }
      });

      $('#entriesButton').click(function() {
        var showing = !$("#journalEntriesRibbon").is(":visible");
        methods._toggleEntriesRibbon(showing);
        var prefToUpdate = extensions.isFileTreeBrowserVisible() ? "showRibbonWithTree" : "showRibbon";
        var newValue = showing ? "y" : "n";
        updateClientUISetting(prefToUpdate, newValue);
      });
      methods._hideEntriesRibbon(); // hidden by default

      var treeBrowserLastVisibleCheck;
      $(window).resize(function() {
         var newTreeBrowserVisible = extensions.isFileTreeBrowserVisible();
         if (newTreeBrowserVisible === treeBrowserLastVisibleCheck) {
             return; // visibility didn't change
         }
         treeBrowserLastVisibleCheck = newTreeBrowserVisible;
         var showRibbonToggle;
         if (newTreeBrowserVisible) {
             showRibbonToggle = clientUISettings.showRibbonWithTree === 'y';
         } else {
             showRibbonToggle = clientUISettings.showRibbon === 'y' && journalPrivateVars.entryCount > 1;
         }
         methods._toggleEntriesRibbon(showRibbonToggle);
      });

      RS.emulateKeyboardClick('#prevEntryButton, #prevEntryButton_mobile');
      $(document).on('click', '#prevEntryButton_mobile, #prevEntryButton', 'click', function() {
        $('#prevEntryButton_mobile, #nextEntryButton_mobile').hide();
        if (tagsEditMode) {
            collapseTagForm("#notebookTags");
        }
        methods._previousPage();
      });

      RS.emulateKeyboardClick('#nextEntryButton, #nextEntryButton_mobile');
      $(document).on('click', '#nextEntryButton_mobile, #nextEntryButton', function() {
        $('#prevEntryButton_mobile, #nextEntryButton_mobile').hide();
        if (tagsEditMode) {
            collapseTagForm("#notebookTags");
        }
        methods._nextPage();
      });

      if (journalSettings.initialRecordToDisplay != null) {
        methods.loadEntryById(journalSettings.initialRecordToDisplay);
      } else {
        // if initial entry not specified then load the last entry
        if (typeof journalPrivateVars.entryCount !== 'undefined') {
          journalPrivateVars.recordPosition = extensions.useFirstRecord ? 0: (journalPrivateVars.entryCount - 1);
        }
        methods.loadEntry(0);
      }

      RS.addOnEnterHandlerToDocument('#journalSearch', methods.search);
      $(document).on('click tap', '#journalSearch-submit', function (e) {
        methods.search();
      });

      document.ontouchmove = function(event) {
        if (typeof window.orientation !== "undefined") {
          var orientate = Math.abs(window.orientation);
          if (orientate == 180 || orientate == 0) {
            event.preventDefault();
          }
        }
      };

      if (window.location.hash) {
          journalPrivateVars.uriFragment = window.location.hash.substring(1);
      }
    },

    loadEntry: function(pagePositionModifier, recordId) {
      $("#prevEntryButton, #nextEntryButton, #prevEntryButton_mobile, #nextEntryButton_mobile").hide();

      /* retrieve entry either by record id, or by using current position and
         pagePositionModifier (for next/prev page navigation) */
      var url = extensions.springMVCUrlReroutePrefix;
      if (recordId !== undefined) {
        url += "/journal/ajax/retrieveEntryById/" + journalSettings.parentRecordId + "/" + recordId;
      } else {
        url += "/journal/ajax/retrieveEntry/" + journalSettings.parentRecordId + "/"
            + journalPrivateVars.recordPosition + "/" + pagePositionModifier;
      }
      $.get(url,
        function(data) {
          tagMetaData = data.tagMetaData ? data.tagMetaData: "";
          if (data.name == "EMPTY") {
            journalPrivateVars.journalEmpty = true;
            $plugin.append(journalPrivateVars.journalPageHtml);
            editable = "VIEW_MODE";

            $("#journalPage").show("fade", {}, 400, function() {
              $(".journalPageContent").html("<div id=\"journalEmpty\">There are no entries to display.</div>");
              $("#journalSearch").hide();
              $("#entryTagsButton").hide();
              $("#editEntry, #deleteEntry").hide();
              reactToolbarHasRendered.then(() => {
                $("#signDocument, #witnessDocument").hide();
                $("#shareRecord").hide();
              });
              renderToolbar({ conditionalRender: {
                print: false,
                export: false,
              }});
              RS.checkToolbarDividers(".toolbar-divider");
              journalPrivateVars.selectedRecordId = null;
              journalPrivateVars.recordPosition = 0;
              methods._statusMessage("There are no entries to display.");
              displayStatus(editable);
            });
          } else {
            $("#journalToolbar>button").show();
            $("#journalSearch").show();

            $("#cachedData").text(data.html);

            //rspac-1396
            document.title = data.name;
            // this works in history list but not in back button
//            var historyUrl = "/notebookEditor/" + journalSettings.parentRecordId+"?initialRecordToDisplay="+data.id;
//            history.pushState({  }, data.name, historyUrl);

            // load data into the page
            methods._carryOnEntry(data);

            // set showJournalRibbon from user preference, depending on tree visibility
            methods.toggleEntriesRibbonDependingOnTreeVisibility();

            editable = data.editStatus;
            isEditable = editable == 'VIEW_MODE' || editable == 'EDIT_MODE';

            // if the entry can be edited by current user
            $("#editEntry, #deleteEntry").toggle(isEditable);

            reactToolbarHasRendered.then(() => {

              // if the entry can be signed by current user
              $("#signDocument").toggle(data.canSign);

              // if the entry can be witnessed by current user
              $("#witnessDocument").toggle(data.canWitness);

              // if the entry can be witnessed by current user
              $("#shareRecord").toggle(data.canShare);
            });

            renderToolbar({ conditionalRender: {
              print: true,
              export: true,
            }});

            RS.checkToolbarDividers(".toolbar-divider");

            signatureResetState();
            if (data.signed || data.witnessed) {
              signatureSetFromJSON(data.signatureInfo);
            } else {
              displayStatus(data.editStatus);
            }
            signatureRecalculateStatus();
          }
        });
    },

    _carryOnEntry: function(data) {
      // replace old content with spinning loader
      $(".journalPageContent").html("<div id=\"journalPageLoading\"><img src=\"" + journalSettings.appPath + extensions.imageSrcPrefix + "images/ajax-loading.gif\"/></div>");
        methods._statusMessage("Loading entry...");
        journalPrivateVars.entryTags = data.tags ? data.tags : "";
        journalPrivateVars.entryTags = RS.unescape(journalPrivateVars.entryTags);
        journalPrivateVars.baseURL = data.baseURL;
        journalPrivateVars.globalId = data.globalId;
        journalPrivateVars.modificationDate = data.modificationDate;

      if ($('#journalPage').length == 0) {
        // first page logic
        $plugin.append(journalPrivateVars.journalPageHtml);
        $("#journalPage").show("fade", {}, 400);
      }

      journalPrivateVars.selectedRecordId = data.id;
      journalPrivateVars.recordPosition = data.position;

      // delay by 1 seconds for HTML to be set in div
      setTimeout(function() {
        journalPrivateVars.journalEmpty = false;
        journalPrivateVars.pageName = data.name;

        if (data.name == "NO_RESULT") {
          // call reverse action
          methods._statusMessage("No more records in this direction.");
          if (journalPrivateVars.recordPosition < 0) {
            methods._nextPage();
          } else {
            methods._previousPage();
          }
        } else {
          methods._loadPage();
        }
        $("#prevEntryButton, #prevEntryButton_mobile").toggle(canSeeNotebook && journalPrivateVars.recordPosition > 0);
        $("#nextEntryButton, #nextEntryButton_mobile").toggle(canSeeNotebook && journalPrivateVars.recordPosition < journalPrivateVars.entryCount - 1);
        repositionEntryNavButtons();

        window.dispatchEvent(new CustomEvent("listOfMaterialInit"));
      }, 800);
    },

    _updateTopHeaderWithEntryData: function() {
      var mdata = {
        name: journalPrivateVars.pageName,
        globalId: "SD" + journalPrivateVars.selectedRecordId,
        recordId: journalPrivateVars.selectedRecordId
      };

      var htmlData = Mustache.render($('#newRecordHeaderTemplate').html(), mdata);

      $('.rs-record-header-line').html(htmlData);
        recordTags = journalPrivateVars.entryTags;
        initTagMetaData('#notebookTags');
        setUpViewModeInfo(recordTags);
        const tags = initTagMetaData('#notebookTags');
        addOnClickToTagsForViewMode(tags);
        extensions.topHeaderModify();
      $('.rs-record-header-line .journalToolbarButton').button();

      var entryNumberText = 'Entry ' + (journalPrivateVars.recordPosition + 1) + ' of ' + journalPrivateVars.entryCount;
      $('#notebookNameAndEntryNumber').html(entryNumberText); // notebook name already escaped

      //Remove show last modified from view.
      $(".displayRevisionsContainer").hide();


      // Set up header info edit features iff they are not set up yet; to prevent duplicate event handlers
      // when new entry is loaded but the edit features are set up already.
      if (isEditable && !headerInfoFeaturesSetUp) {
        headerInfoFeaturesSetUp = true;
        // set up Tags
        $("#editTags").show(fadeTime);
        $(document).on("click", "#editTags", function() {
          if (!tagsEditMode) {
            initTagForm("#notebookTags");
          }
        });
        $(document).on("click", "#saveTags", function(e) {
          e.preventDefault();
          collapseTagForm("#notebookTags");
        });
        initInlineRenameRecordForm("#inlineRenameRecordForm");
      } else {
        // Hide header info edit features if current entry is not editable; these features might be enabled
        // if the previous entry was editable. Does not deactivate event handlers, but edit methods won't execute
        // if isEditable is false
        if (!isEditable) {
          $("#editTags").hide(fadeTime);
          $("#inlineRenameRecordForm").find("#recordNameInHeaderEditor").removeClass("editable").prop("readonly", true);
          $("#inlineRenameRecordForm").find("#renameRecordEdit").hide(fadeTime);
          // Re-enable edit features if entry is editable, event handlers are still there and edit methods will execute as expected.
        } else {
          $("#editTags").show(fadeTime);
          $("#inlineRenameRecordForm").find("#recordNameInHeaderEditor").addClass("editable").prop("readonly", false);
          $("#inlineRenameRecordForm").find("#renameRecordEdit").show(fadeTime);
        }
      }

      recordName = mdata.name;
    },

    _loadPage: function() {

      $(".journalPageContent").html($("#cachedData").text());

      $(".commentIcon").click(function() {
        showCommentDialog(this, true);
      });

      /* load images details for full-size browsing. this must be inside
      * cleanAdjustPageFunction as it should be called after all images are loaded */
      document.addEventListener('images-replaced', function (e) {
        var $images = $('.journalPageContent').find('img.imageDropped, img.sketch, img.chem');
        populatePhotoswipeImageArray($images);
      });

      // some beautification of text field content
      updateAttachmentDivs($('.journalPageContent .attachmentDiv'));
      updateInternalLinks($('.journalPageContent a.linkedRecord'));
      updateEmbedIframes($('.journalPageContent div.embedIframeDiv'));
      addImagePreviewToChems($('.journalPageContent img.chem'));
      updateMediaFileThumbnails();
      applyCodeSampleHighlights($('.journalPageContent'));
      addDownloadButtonToTables($('.journalPageContent table'));
      extensions.loadPageModify();
      // Tell React that a new document was placed into the dom
      document.dispatchEvent(new Event('document-placed'));

      $("#previousPageJournalToolbarButton").prop("disabled", journalPrivateVars.recordPosition <= 0);
      $("#nextPageJournalToolbarButton").prop("disabled", (journalPrivateVars.recordPosition >= journalPrivateVars.entryCount - 1));

      if (tagsEditMode) {
        console.log("Tags editing active; deactivating... (RSPAC-1399)");
        tagsEditMode = false;
      }

      methods._readjustJournalPageAfterImagesLoaded();
    },

    _readjustJournalPageAfterImagesLoaded: function() {
      // readjusts the journal page based on page number and where the elements lie on the page
      var cleanAdjustPageFunction = function () {
        var $images = $('.journalPageContent').find('img.imageDropped, img.sketch, img.chem');
        populatePhotoswipeImageArray($images);

        methods._updateTopHeaderWithEntryData();
        updateEntryNameInBreadcrumbs(journalPrivateVars.selectedRecordId, journalPrivateVars.pageName);
        extensions.selectFileTreeBrowserRecordById(journalPrivateVars.selectedRecordId);

        // updating top scrollbar
        var journalPageElement = $(".journalPageContent")[0];
        var journalPageScrollWidth = journalPageElement.scrollWidth;
        if (journalPageScrollWidth > journalPageElement.clientWidth) {
          $(".journalPageTopScrollbarDiv").show();
          $(".journalPageTopScrollbarExpander").width(journalPageScrollWidth);
        } else {
          $(".journalPageTopScrollbarDiv").hide();
        }

        $(".journalPageTopScrollbarDiv").scroll(function() {
          $(".journalPageContent").scrollLeft($(".journalPageTopScrollbarDiv").scrollLeft());
        });
        $(".journalPageContent").scroll(function() {
          $(".journalPageTopScrollbarDiv").scrollLeft($(".journalPageContent").scrollLeft());
        });

        if (journalPrivateVars.uriFragment) {
          methods.scrollJournalPageToAnchor(journalPrivateVars.uriFragment)
          journalPrivateVars.uriFragment = null; // reset, so navigation happens only on first load
        }
      };

      // convoluted code to wait for all the images to load in .journalPageContent, because no html being added to div
      // callback function is supported in JQuery

      var images = $('.journalPageContent img');
      var imageCounter = images.length;

      // if no images adjust page straight away
      if (imageCounter === 0) {
        cleanAdjustPageFunction();
      } else {
        var imageLoaded = function() {
          imageCounter--;
          if (imageCounter === 0) {
            cleanAdjustPageFunction();
          }
        };

        images.each(function() {
          if (this.complete) {
            imageLoaded();
          } else {
            $(this).one('load error', imageLoaded);
          }
        });
      }
    },

    scrollJournalPageToAnchor : function (anchorId) {
      var anchorElement = document.getElementById(anchorId);
      if (anchorElement) {
        $('#journalPage #' + anchorId)[0].scrollIntoView();
      }
    },

    _statusMessage: function(message) {
      $(".rs-record-header-line").html("<span class=\"rs-header-msg\">" + message + "</span>");
      $("#notebookNameAndEntryNumber").html("");
    },

    _nextPage: function() {
      methods._animateNewPage("left", "right", 1);
    },

    _previousPage: function() {
      methods._animateNewPage("right", "left", -1);
    },

    _animateNewPage: function(oldPageDirection, newPageDirection, pagePositionModifier) {
      // called by next and previous methods logic same just different directions
      $("#journalPage").hide("slide", { direction: oldPageDirection }, 400, function() {
        $("#journalPage").remove();
        $plugin.append(journalPrivateVars.journalPageHtml);
        $("#journalPage").show("slide", { direction: newPageDirection }, 400, function() {
          methods.loadEntry(pagePositionModifier);
        });
      });
    },

    /*
     * Loads an entry based on record ID.
     * Passed numeric record id must be prefixed with 'journalEntry'.
     */
    loadEntryById: function(journalEntryRecordId) {
        var recordId = journalEntryRecordId.substring('journalEntry'.length);
        methods.loadEntry(null, recordId);
    },

    /* =========================
     *  entries ribbon methods
     * ========================= */

    nextEntriesInRibbon: function() {
      if (journalPrivateVars.moreItemsAvailable) {
        methods._changeEntriesInRibbon("left");
      }
    },

    previousEntriesInRibbon: function() {
      if (journalPrivateVars.historyPosition > 1) {
        methods._changeEntriesInRibbon("right");
      }
    },

    _changeEntriesInRibbon: function(loadDirection) {
      var positionModifier = loadDirection == "left" ? 1 : -1;
      var ribbonHtml = methods._generateRibbonHtml(positionModifier);

      $("#journalEntriesRibbonContent").hide("slide", { direction: loadDirection }, 400, function() {
        $("#journalEntriesRibbonContent").remove();
        $("#journalEntriesRibbon").html(ribbonHtml);
        $("#journalEntriesRibbonContent").show("slide", { direction: loadDirection }, 400, function() {
          journalPrivateVars.historyPosition = journalPrivateVars.historyPosition + positionModifier;
          if (journalPrivateVars.searchModeOn) {
            methods._loadSearch();
          } else {
            methods.loadEntriesRibbon();
          }
        });
      });
    },

    _generateRibbonHtml: function(positionModifier) {
      var pluginIdSelector = "#" + $plugin.attr("id");

      var atLeftEnd = ((journalPrivateVars.historyPosition + positionModifier) <= 1);
      var atRightEnd = !journalPrivateVars.moreItemsAvailable; // if we're at the end of the list, no right arrow

      if (!journalPrivateVars.searchModeOn && typeof journalPrivateVars.entryCount !== 'undefined') {
        atRightEnd = (journalPrivateVars.entryCount <= ((journalPrivateVars.historyPosition + positionModifier) * 7));
      }

      var ribbonHtml = "";
      if (!atLeftEnd) {
        ribbonHtml += "<div id=\"journalEntriesRibbonLeftArrow\" class=\"ribbonNavButton journalEntriesRibbonLeftDiv\" onClick=\"$('"
          + pluginIdSelector + "').journal('previousEntriesInRibbon');\"><img src=\"" + journalSettings.appPath + "images/icons/LeftArrow25.png\"/></div>";
      } else {
        ribbonHtml += "<div class=\"ribbonNavButton journalEntriesRibbonLeftDiv\"></div>";
      }
      ribbonHtml += "<div id=\"journalEntriesRibbonContent\"><img src=\"" + journalSettings.appPath + "images/ajax-loading.gif\"/></div>";
      if (!atRightEnd) {
        ribbonHtml += "<div id=\"journalEntriesRibbonRightArrow\" class=\"ribbonNavButton journalEntriesRibbonRightDiv\" onClick=\"$('"
          + pluginIdSelector + "').journal('nextEntriesInRibbon');\"><img src=\"" + journalSettings.appPath + "images/icons/RightArrow25.png\"/></div>";
      } else {
        ribbonHtml += "<div class=\"ribbonNavButton journalEntriesRibbonRightDiv\"></div>";
      }

      return ribbonHtml;
    },

    toggleEntriesRibbonDependingOnTreeVisibility: function() {
        var showJournalRibbon;
        if (extensions.isFileTreeBrowserVisible()) {
            showJournalRibbon = clientUISettings.showRibbonWithTree === 'y';
        } else {
            showJournalRibbon = clientUISettings.showRibbon === 'y';
        }
        var showRibbonToggle = showJournalRibbon && journalPrivateVars.entryCount > 1;
        methods._toggleEntriesRibbon(showRibbonToggle);
    },

    _toggleEntriesRibbon: function(show) {
        if (show) {
            methods._showEntriesRibbon();
        } else {
            methods._hideEntriesRibbon();
        }
    },

    _showEntriesRibbon: function() {
      $("#journalEntriesRibbon, #journalEntriesRibbonContent, #hideAllEntriesSpan").show();
      $('#showAllEntriesSpan').hide();
      journalPrivateVars.searchModeOn = false;
      $('#journalSearch').val('');
      var ribbonHtml = methods._generateRibbonHtml(0);
      $("#journalEntriesRibbon").html(ribbonHtml);
      methods.loadEntriesRibbon(true);
    },

    _hideEntriesRibbon: function(forceReload) {
      $("#hideAllEntriesSpan").hide();
      $('#showAllEntriesSpan').show();
      var top = $(".rs-record-info-panel").outerHeight(); // Re-position ribbon when height of header changes
      $("#journalEntriesRibbon").css("top", top).hide();
    },

    loadEntriesRibbon: function(refresh) {
      delete journalPrivateVars.moreItemsAvailable;
      if (typeof refresh === "undefined") {
        refresh = false;
      }
      if (refresh) {
          journalPrivateVars.historyPosition = Math.floor((journalPrivateVars.recordPosition / 7) + 1);
      }
      const path = extensions.springMVCUrlReroutePrefix +"/journal/ajax/retrieveHistory/";
      $.ajax({
        url: path + journalSettings.parentRecordId + "/" + journalPrivateVars.historyPosition + "/" + refresh,
        success: function(data, status, xhr) {
          journalPrivateVars.moreItemsAvailable = xhr.getResponseHeader('moreItemsAvailable') == 'true';
          journalPrivateVars.displayedRecordIds = [];
          methods._generateRibbonHtmlForEntryList(data);
        }
      });
    },

    _generateRibbonHtmlForEntryList: function(entryList) {
        // Reload ribbon now that we know if there are more items
        var ribbonHtml = methods._generateRibbonHtml(0);
        $("#journalEntriesRibbon").html(ribbonHtml);

        $("#journalEntriesRibbonContent").html(""); // remove loading gif
        var journalEntryOnRibbonTemplate = $('#journalEntryOnRibbonTemplate').html();

        for (var i = 0; i < entryList.length; i++) {
          var entry = entryList[i];
          var date = new Date(entry.creationDate);
          var minutes = (date.getMinutes() < 10) ? "0" + date.getMinutes() : date.getMinutes();
          var dateString = date.toISOString().split('T')[0] + " " + date.getHours() + ":" + minutes;
          var selectedEntryClass = (journalPrivateVars.selectedRecordId == entry.id) ? "journalSelectedEntry" : "";
          journalPrivateVars.displayedRecordIds[i] = "journalEntry" + entry.id;
          var entryData = {
            id: "journalEntry" + entry.id,
            name: entry.name,
            dateString: dateString,
            selectedEntryClass: selectedEntryClass,
            formIconId: entry.formIconId,
            formName: entry.formName,
            imgPrefix: extensions.springMVCUrlReroutePrefix,
            imgsPrefix: extensions.imageSrcPrefix,
            scriptsPrefix: extensions.scriptsPrefix
          };
          var journalEntriesRibbonHtml = Mustache.render(journalEntryOnRibbonTemplate, entryData);
          RS.appendMustacheGeneratedHtmlToElement(journalEntriesRibbonHtml, "#journalEntriesRibbonContent");

          $("#journalEntry" + entry.id).unbind();
          $("#journalEntry" + entry.id).click(function(eventData) {
            methods._journalEntryInRibbonClickHandler(this.id);
          });
        }
        if (!$("#journalEntriesRibbon").is(":visible")) {
          // Re-position ribbon when height of header changes
          var top = $(".rs-record-info-panel").outerHeight();
          $("#journalEntriesRibbon").css("top", top).show("blind", 400, function() { $(this).css("top", "100%"); });
        }
    },

    _journalEntryInRibbonClickHandler: function(recordId) {
        $(".journalEntry").removeClass("journalSelectedEntry");
        $("#journalEntry" + recordId).addClass("journalSelectedEntry");

        var position = 0;

        if (journalPrivateVars.searchModeOn) {
          methods.loadEntryById(recordId);
        } else {
          position = ((journalPrivateVars.historyPosition - 1) * 7) + journalPrivateVars.displayedRecordIds.indexOf(recordId);
          journalPrivateVars.recordPosition = position;
          journalPrivateVars.selectedRecordId = recordId;
          methods.loadEntry(0);
        }
    },

    /* =========================
     *  journal search methods
     * ========================= */

    search: function() {
      if ($('#journalSearch').val() === "") {
        journalPrivateVars.searchModeOn = false;
        methods.loadEntriesRibbon(true);
        return;
      }

      journalPrivateVars.historyPosition = 1; // reset positioning if in history mode

      var ribbonHtml = methods._generateRibbonHtml(0);
      $("#journalEntriesRibbon").html(ribbonHtml);
      $("#journalEntriesRibbon").show("blind", 400, function() {
        methods._loadSearch();
      });
      $('#journalSearch').val();
    },

    _loadSearch: function() {
      var searchText = $('#journalSearch').val();

      if (searchText === "") {
        searchText = "*"; // need value to allow spring to recognise request
      }
      const path = extensions.springMVCUrlReroutePrefix + "/journal/ajax/quicksearch/";
      $.ajax({
        url: path + journalSettings.parentRecordId + "/" + (journalPrivateVars.historyPosition - 1) + "/" + encodeURIComponent(searchText) + "/",
        fail: function(data, status, xhr) {
          apprise("Search failed: " + xhr);
        },
        success: function(data, status, xhr) {
          journalPrivateVars.moreItemsAvailable = xhr.getResponseHeader('moreItemsAvailable') == 'true';
          journalPrivateVars.displayedRecordIds = [];

          if (data.length === 0) {
            var ribbonHtml = methods._generateRibbonHtml(0);
            $("#journalEntriesRibbon").html(ribbonHtml);

            if ((journalPrivateVars.historyPosition - 1) === 0) {
              $("#journalEntriesRibbonContent").html("<div id=\"journalEntriesRibbonNoResults\">No results were found</div>");
            }
          } else {
            journalPrivateVars.searchModeOn = true;
            methods._generateRibbonHtmlForEntryList(data);
          }
        }
      });
    },

    selectedRecordId: function() {
      return journalPrivateVars ? journalPrivateVars.selectedRecordId : null;
    },

    decrementEntryCount: function() {
      journalPrivateVars.entryCount--;
    },

  };

  /**
   * Positions the notebook entry navigation buttons (prev/next) so they are in
   * the vertical centre of the currently visible part of the notebook entry.
   */
  repositionEntryNavButtons = function() {
    // space above the entry viewer visible in the viewport
    var topOffset = $('#journalPage').offset() ? $('#journalPage').offset().top : 0;

    // total height of the entry viewer
    var entryHeight = $('#journalPage').height();

    // how much of the beginning (top) of the viewer is out of the viewport
    var startHidden = Math.max($(document).scrollTop() - topOffset, 0);

    // how much of the end (bottom) of the viewer is out of the viewport
    var endHidden = Math.max((topOffset + entryHeight) - ($(document).scrollTop() + $(window).height()), 0);

    // how much of the entry viewer is actually visible in the viewport
    var portionShown = entryHeight - startHidden - endHidden;

    // default: put the buttons at the top of the visible part of the entry viewer
    var navButtonsTop = startHidden;

    var entryNavButtonHeight = 150;
    if (portionShown > entryNavButtonHeight) {
      navButtonsTop += (portionShown - entryNavButtonHeight) / 2;
    }

    $('#nextEntryButton_mobile, #prevEntryButton_mobile').css('top', navButtonsTop);
  };

  // Method calling plugin's logic
  $.fn.journal = function(method) {
    if (methods[method]) {
      return methods[method].apply(this, Array.prototype.slice.call(arguments, 1));
    } else if (typeof method === 'object' || !method) {
      return methods.init.apply(this, arguments);
    }
    $.error('Method ' + method + ' does not exist on jQuery.journal');
  };

  // dynamically reposition the entry navigation buttons on scroll...
  $(document).on('scroll', function(e) {
    repositionEntryNavButtons();
  });
  // ...and on resize
  $(window).on('resize', function(e) {
    repositionEntryNavButtons();
  });
};
