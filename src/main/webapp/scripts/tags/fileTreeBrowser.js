
var fileTreeBrowserRecordId;
var fileTreeBrowserCurrentBreadcrumbIds;
var fileTreeBrowser;
var navigateToDocument = _defaultNavigationHandler; // function that is called to go to a doc
var showGallery = true;

/* initialises the tree, loads the root level and opens 'fullPathIdSequence' 
 * nodes to selects a record with given id */ 
function setUpFileTreeBrowser(settings) {
  if (fileTreeBrowser) {
    return; // already set up
  }
  if (settings.navigationHandler) {
    navigateToDocument = settings.navigationHandler;
  }
  if (settings.showGallery != null) {
    showGallery = settings.showGallery;
  }
  fileTreeBrowserRecordId = settings.initialRecordId;
  fileTreeBrowserCurrentBreadcrumbIds = [];

  $('#fancyTree').fancytree({
    extensions: ["dnd"],
    dnd: {
      autoExpandMS: 400,
      draggable: { 
        appendTo: "body",
        revert: "invalid"
      },
      preventRecursiveMoves: true, // Prevent dropping nodes on own descendants
      preventVoidMoves: true, // Prevent dropping nodes 'before self', etc.
      dragStart: function(node, data) {
        if (settings.supportTinymceDnd) {
          markTinyMCEAreaDroppable(true);
          $('.tox-edit-area').droppable({
            drop: function(event, ui) {
              if (!node) {
                return false;
              }
              // insert images directly
              if (node.data.globalId.startsWith("GL")) {
                var fileId = node.data.globalId.substring(2);
                var url = `/workspace/getRecordInformation?recordId=${fileId}`;
                $.get(url, function(result) {
                  let data = result.data;
                  if (data) { // if record is found
                    addFromGallery(data);
                  } else {
                    console.log("Failed to retrieve image data for " + node.data.globalId);
                  }
                });
                return;
              }
              // else insert internal link
              var id = node.key;
              var globalId = node.data.globalId;
              var name = node.data.name;
              RS.tinymceInsertInternalLink(id, globalId, name, tinyMCE.activeEditor);
              RS.trackEvent('InternalLinkCreated', { source: 'tree_browser', linkedDoc: globalId });
            }
          });
          return true;
        }
      },
      dragOver: function(node, data) {
        //console.log('tree dragover');
      },
      dragStop: function(node, data) {
        if (settings.supportTinymceDnd) {
          clearTinyMCEAreaDroppable();
        }
      }
    },
    source: function(event, data) {
      return _loadFileTreeBrowserNode(data, '/');
    },
    lazyLoad: function(event, data) {
      var node = data.node;
      var rel = encodeURIComponent(node.data.rel.match( /.*\// ));
      return _loadFileTreeBrowserNode(data, rel);
    },
    click: function(event, data) {
      if (settings.nodeClickHandler) {
        settings.nodeClickHandler(data.node);
        return;
      }
      var node = data.node;
      if (node.folder) {
        _defaultFolderClickHandler(node);
      } else {
        _defaultRecordClickHandler(node);
      }
      return false;
    },
    init: function(event, data) { 
      fileTreeBrowser = $('#fancyTree').fancytree('getTree');
      var selection = fileTreeBrowser.lastSelectedNode;

      if (fileTreeBrowserRecordId && (!selection || selection.key != fileTreeBrowserRecordId)) {
        selectFileTreeBrowserRecordById(fileTreeBrowserRecordId);
      }
    },
    loadChildren: function(event, data) {
      var node = data.node;
      // to handle folder being loaded as a part of record selection
      $.each(node.children, function(i, nodeChild) {
        _selectRecordInFileTreeBrowserBranch(nodeChild)
      });
      setTimeout(sortFileTreeBrowser, 0);
    },
    renderNode: function(event, data) {
      _onFileTreeBrowserNodeRender(data.node);
    } 
  });

  $(document).on('click', '#fileTreeSettingsToggle', function() {
    $('.fileTreeButtons .sortingSettings').toggle();
    return false;
  });

  if (!settings.fullSizeTree) {
    // tree browser visibility depends on window size
    _adjustFileTreeBrowserDiv();
    $(window).resize(_adjustFileTreeBrowserDiv);
  }
}
/* 
 * can be called after tree is initialised, to switch to a different 
 * branch/record. 
 * 
 * requires 'fullPathIdSequence' to be correct path to recordId, 
 * otherwise it won't find the record.
 */
function selectFileTreeBrowserRecordById(recordId) {
  if (!fileTreeBrowser) {
    return;
  }
    
  fileTreeBrowserCurrentBreadcrumbIds = RS.getBreadcrumbIds('editorBcrumb');
  fileTreeBrowserRecordId = recordId;

  // remove previous selection
  if (fileTreeBrowser.countSelected()) {
    $.each(fileTreeBrowser.getSelectedNodes(), function(i, selectedNode) {
      selectedNode.setSelected(false);
    });
  }
  var activeNode = fileTreeBrowser.getActiveNode();
  if (activeNode) {
    activeNode.setActive(false);
  }

  // expand the branch/select new node
  $.each(fileTreeBrowser.rootNode.children, function(i, rootChild) {
    _selectRecordInFileTreeBrowserBranch(rootChild)
  });
  
  _adjustFileTreeBrowserDiv();
}

/* depending on the current viewport width (and, in future, on some toggle)
 * the file tree browser is either visible or hidden */
function isFileTreeBrowserVisible() {
  return $('#fileBrowsing').css('display') === 'block';
}

/* reloads the tree browser and reopens branch to record doc */
function reloadFileTreeBrowser() {
  if (fileTreeBrowser) {
    fileTreeBrowser.reload();
  }
}

function _loadFileTreeBrowserNode(data, nodeRel) {
  var dfd = new $.Deferred();
  data.result = dfd.promise();
  var req = $.ajax({
    url: '/fileTree/ajax/filesInModel/',
    method: 'POST',
    data: {
      dir: nodeRel,
      initialLoad: true,
      showGallery: showGallery
    }
  }).done(function(data) {
    // Load client UI settings for tree view ordering
    if (clientUISettingsPref != null) {
      clientUISettingsPref = $(data).find('#clientUISettingsPref').attr('data-settings');
      parseClientUISettingsPref();
    }
    // Load tree nodes
    var nodes = [];
    var lis = $(data).find('li');
    lis.each(function(i, li) {
      var $li = $(li);
      var $link = $li.find('a');
      var name = $link.attr('data-name');
      var id = $link.attr('data-id');
      var globalId = $link.attr('data-globalid');
      var creationDate = $link.attr('data-creationdate');
      var modificationDate = $link.attr('data-modificationdate');
      var rel = $link.attr('rel');
      var classes = $li.attr('class').split(' ');
      var isFolder = classes.includes('directory') || classes.includes('notebook');
      var node = {
        title: RS.escapeHtml(name),
        key: id,
        globalId: globalId,
        creationDate: creationDate,
        modificationDate: modificationDate,
        name: name,
        rel: rel,
        lazy: true,
        folder: isFolder,
        extraClasses: $li.attr('class'),
        checkbox: false,
        tooltip: RS.escapeHtml(name)
      };
      if (!isFolder) {
        node.children = [];
      } else {
        node.children = null;
      }
      nodes.push(node);
    });
    dfd.resolve(nodes);
  });
  return dfd;
};

function _onFileTreeBrowserNodeRender(node) {
  var $li = $(node.li); 
  $li.attr('data-id', node.key);
  $li.attr('data-globalid', node.data.globalId);
  $li.attr('data-name', node.title);
  $li.attr('data-creationdate', node.data.creationDate);
  $li.attr('data-modificationdate', node.data.modificationDate);
}

function _adjustFileTreeBrowserDiv() {
  var browserHeight = $(window).height();
  var fancyTreeHeight = browserHeight - 250;
  $('#fileBrowsing #fancyTree').css('max-height', fancyTreeHeight + 'px');
  $('#fileBrowsing').css('width', 295 +  'px');
}

function _defaultRecordClickHandler(node) {
  // photos get downloaded
  isPhoto = node.data.globalId.startsWith("GL");
  if (isPhoto) {
      var recordId = node.data.globalId.substring(2);
      window.open('/Streamfile/' + recordId)
      return;
  }
  
  isSnippet = node.data.globalId.startsWith("ST");
  if (isSnippet) {
      window.open('/globalId/' + node.data.globalId)
      return;
  }
  // files get opened
  if (fileTreeBrowserRecordId == node.key) {
      console.log('click on the currently displayed record, ignoring');
      return;
  }
    
  var clickHandled = false;
  var parentNode = node.parent;
  var isNotebookEntryNode = parentNode.extraClasses && parentNode.extraClasses.includes('notebook');
  var parentNotebookId = isNotebookEntryNode ? parentNode.key : undefined;

  // handle clicks between entries in the same notebook
  var breadcrumbsLength = fileTreeBrowserCurrentBreadcrumbIds.length;
  if (breadcrumbsLength > 1) {
    var currentParentId = fileTreeBrowserCurrentBreadcrumbIds[breadcrumbsLength - 2];
    if (!isDocumentEditor && parentNotebookId === currentParentId) {
      console.log('clicked node ' + node.key + ' is an entry within the same notebook, switching');
      $("#notebook").journal("loadEntryById", "journalEntry" + node.key);
      clickHandled = true; // notebook entry is reloading on the right
    }
  }
  // clicks on docs/entries outside of current notebook
  if (!clickHandled) {
    // get url without settings key, as workspaceSettings may not be valid for new selection
    var urlToOpen = getDocumentViewUrl(parentNotebookId, node.key, false);
    navigateToDocument(urlToOpen, node);
  }
}

function _defaultFolderClickHandler(node) {
  var isExpanded = node.expanded === true;
  var isNotebook = node.extraClasses.includes('notebook');
  // if empty notebook, then click will open it
  if (isExpanded && isNotebook && !node.children.length) {
    var urlToOpen = getDocumentViewUrl(node.key, null, false);
    navigateToDocument(urlToOpen, node);
    return;
  }
  // toggle expand/collapse state
  node.setExpanded(!isExpanded);
}

function _defaultNavigationHandler(urlToOpen, clickedNode) {
  console.log('handling tree browser click, navigating to: ' + urlToOpen);
  RS.navigateTo(urlToOpen);
}

function _selectFileTreeBrowserNode(node) {
  node.setSelected(true);
  node.setActive(true);

  var $tree = $('#fancyTree');
  var topMargin = -($tree.height() / 3); // so selected record is about the middle
  $('#fancyTree').scrollTo(node.li, 300, {axis: 'y', offset: {top: topMargin}});
}

function _selectRecordInFileTreeBrowserBranch(node) {
  if (node.isFolder() && fileTreeBrowserCurrentBreadcrumbIds.includes(node.key)) {
    node.setExpanded();
    if (node.isLoaded()) {
      // if node was loaded earlier iterate child nodes 
      $.each(node.children, function(i, nodeChild) {
        _selectRecordInFileTreeBrowserBranch(nodeChild)
      });
    } else if (!node.isLoading()) {
      node.load();
    }
  }

  if (node.key === fileTreeBrowserRecordId.toString()) {
    if (_isNodeAncestorMatchingBreadcrumbs(node)) {
      // in timeout, as maybe the tree is just being expanded
      setTimeout(function () {
        _selectFileTreeBrowserNode(node);
      }, 0);
    }
  }
}

/* prt-962, the node with given key may be in a few places of the filetree,
   e.g. in Workspace and in Shared folder. The method checks if the node parents
    are matching the nodes shown by breadcrumbs */
function _isNodeAncestorMatchingBreadcrumbs(node) {
  const breadcrumbsLength = fileTreeBrowserCurrentBreadcrumbIds.length;
  if (node === null || node.parent === null || breadcrumbsLength < 1) {
    return false; // unexpected
  }
  if (breadcrumbsLength === 2 && node.parent.parent === null) {
    return true; // top level workspace
  }
  if (breadcrumbsLength === 3) {
    if (node.parent.key === fileTreeBrowserCurrentBreadcrumbIds.slice(-2)[0]
        && node.parent.parent.parent === null) {
      return true; // workspace subfolder/notebook
    }
  }
  if (breadcrumbsLength > 3) {
    if (node.parent.key === fileTreeBrowserCurrentBreadcrumbIds.slice(-2)[0]
        && node.parent.parent.key
        === fileTreeBrowserCurrentBreadcrumbIds.slice(-3)[0]) {
      return true; // both parent and grandparent ids are matching
    }
  }
  return false;
}