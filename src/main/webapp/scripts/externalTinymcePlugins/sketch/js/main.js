// the zwibbler context
var context;
var draggedImage;

var sketchDialog = {
  init: function (ed) {
    this.fieldId = ed.id.split("_")[1];
    var n = ed.selection.getNode();
    var listElem = null;
    var prefix = tinymceDialogUtils.getContextPath();

    sketchDialog.addZwibblerButtons();

    var ZwibblerOptions = {
      "showMenu": false,
      "showPropertyPanel": true,
      "showDebug": false,
      "showBrushTool": false,
      "sloppy": false,
      "showColourPanel": true,
      "defaultSmoothness": "sharper",
      "imageFolder": "/scripts/zwibbler2",
      "defaultFillStyle": "rgba(255,255,255,0.5)"
    };

    var data = null;
    var url = null;
    var src = null;

    // if sketch was selected it loads sketch
    if ((n.nodeName = "IMG") && ($(n).hasClass("sketch"))) {
      var sketchId = n.getAttribute("id");
      $('.sketchId').attr('data-id', sketchId);
      // we might be loading a revision, e.g., RSPAC-311
      var revision = n.getAttribute("data-rsrevision");
      data = { sketchId: sketchId, revision: revision }
      url = prefix.concat("/image/ajax/loadSketchImageAnnotations");

      // if image was selected it loads image annotations	
    } else if ((n.nodeName = "IMG") && ($(n).hasClass("imageDropped"))) {

      var id = n.getAttribute("id");
      var parentId = id.split("-")[0];
      var imageId = id.split("-")[1];
      var revision = n.getAttribute("data-rsrevision");
      $('.sketchId').attr('data-id', imageId);
      $('.dataSize').attr('data-size', n.getAttribute("data-size"));
      $('.typeSketch').attr('data-type', "imageAnnotationType");

      data = {
        parentId: parentId,
        imageId: imageId,
        revision: revision
      };
      src = prefix.concat("/image/getImageToAnnotate/" + id + "/1");
      url = prefix.concat("/image/ajax/loadImageAnnotations");
    }

    if (url != null) {
      $.ajaxSetup({ async: false });
      var jxqr = $.get(url, data,
        function (result) {
          if (result != null && result.data != "") {
            listElem = result.data;
          }
        }, "json");
      jxqr.fail(function () {
        tinymceDialogUtils.showErrorAlert("Loading annotations failed.");
      });
      jxqr.always(function () {
        $.ajaxSetup({ async: true });
      });
    }

    var editorSketch = document.getElementById("image-editorSketch");
    editorSketch.style.width = window.innerWidth - 160 + "px";
    editorSketch.style.height = window.innerHeight - 20 + "px";

    context = Zwibbler.create("image-editorSketch", ZwibblerOptions);
    if (listElem !== null) {
      context.load("zwibbler3", listElem);
    }

    if (src) {
      sketchDialog.addImageAsBackgroundNode(src);
    }

    var canvas = document.getElementById("image-editorSketch");

    // needed to support image dragging.
    $('#image-editorSketch').on("dragover", function (e) {
      e.preventDefault();
    });

    /*
     * when the image is dropped onto the zwibbler canvas, then insert it into the
     * document, by calling the function defined in addImageTool().
     * 
     * FIXME: that doesn't seem to work though, raised as RSPAC-280.
     */
    var functionDrop = function (ev) {
      if (draggedImage) {
        var offset = sketchDialog.offsetOf(canvas);
        draggedImage.dropfn(ev.pageX - offset.left - 100, ev.pageY - offset.top);
        ev.preventDefault();
      }
    }
    canvas.addEventListener("drop", functionDrop, false);

		/*
		 * whenever items are selected, enable or disable the "Add to template" button
		 */
    context.on("selected-nodes", function () {
      var selected = context.getSelectedNodes();
      if (selected.length) {
        $("#save-template").removeAttr("disabled");
      } else {
        $("#save-template").attr("disabled", "disabled");
      }
    });

    $("#save-template").on("click", function (e) {
      var data = context.copy(true);
      var templateIndex = sketchDialog.calculateNextIndex();
      sketchDialog.storeTemplate(data, templateIndex, function (error) {
        if (error) {
          alert(error);
          return;
        }
        sketchDialog.addTemplate(data, templateIndex);
      });
    });

    $("#delete-template").on("click", function (e) {
      $(this).prop("disabled", true);
      $("input[type=checkbox][name=template-input]:checked").each(function () {
        var templateIndex = $(this).attr('id').split('-')[1];
        $("#div-" + templateIndex).remove();
        localStorage.removeItem("template-" + templateIndex);
      });
    });

    $('input[type=radio][name=template-radio-item]').on('change', function () {
      switch ($(this).val()) {
        case 'Gallery':
          $("#gallery-editorSketch").show(100, function () {
            $("#template-editorSketch").hide();
            $("#save-template").hide();
            $("#delete-template").hide();
          });
          break;
        case 'Template':
          $("#template-editorSketch").show(100, function () {
            $("#gallery-editorSketch").hide();
            $("#save-template").show();
            $("#delete-template").show();
          });
          break;
      }
    });

    // Add the static images to the image selector.
    //		sketchDialog.addImageTool("/scripts/zwibbler2/icon1.png");
    //		sketchDialog.addImageTool("/scripts/zwibbler2/icon2.png");
    //		sketchDialog.addImageTool("/scripts/zwibbler2/icon3.png");
    //		sketchDialog.addImageTool("/scripts/zwibbler2/icon4.png");
    //		sketchDialog.addImageTool("/scripts/zwibbler2/icon5.png");

    var jqxhr = $.get("/gallery/getImageListFromRootImageFolder");
    jqxhr.done(function (result) {
      var results = result.data;
      results.forEach(function (imageId) {
        sketchDialog.addGalleryImageTool("/gallery/getViewerImage/" + imageId);
      });
    });

    sketchDialog.listTemplates(function (templates, error) {
      for (var i = 0; i < templates.length; i++) {
        sketchDialog.addTemplate(templates[i], i);
      }
    });
  },

	/** 
	 * This method will calculate the next template index checking only 
	 * key in localStorage with the type "template". 
	 * 
	 * For example, for zwibbler templates:
	 * 
	 * localStorage.key(0) => if the key is related to zwibbler templates, the key would be like "template-X".
	 * Then, we could retrieve the data using localStorage.getItem("template-X");
	 * 
	 */
  calculateNextIndex: function () {
    var templateKeys = [];
    for (var key in localStorage) {
      if (key.indexOf("template") !== -1) {
        templateKeys.push(key);
      }
    }
    return templateKeys.length === 0 ? 0 : (parseInt(templateKeys[templateKeys.length - 1].split('-')[1]) + 1);
  },

  addImageAsBackgroundNode: function (src) {
    var bgNodeId = context.createNode('ImageNode', { url: src });
    context.sendToBack();
    context.setNodeProperty(bgNodeId, "locked", true);
    /* we are tagging the node so we can find it and remove when saving to database */
    context.setNodeProperty(bgNodeId, "tag", "bgImage");
    context.clearUndo();
    sketchDialog.unselectCurrentNode();
  },

  unselectCurrentNode: function () {
    context.setActiveLayer("default"); // selects underlying layer
  },

	/*
	 * a function to add an image to the template selector
	 */
  addImageTool: function (urlOrCanvas, templateIndex, actionfn) {
    var img;
    if (typeof urlOrCanvas === "string") {
      img = document.createElement("img");
      img.src = urlOrCanvas;
    } else {
      img = urlOrCanvas;
    }

    var div = document.createElement("div");
    div.id = "div-" + templateIndex;
    div.appendChild(img);

    var checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.id = 'template-' + templateIndex;
    checkbox.name = 'template-input';
    div.appendChild(checkbox);

    $("#template-editorSketch").append(div);

    img.setAttribute("draggable", "true");
    img.ondragstart = function (e) {
      console.log("ondragstart");
      draggedImage = img;
    };
    img.onselectstart = function (e) {
      return false;
    };
    img.dropfn = function (x, y) {
      if (actionfn) {
        actionfn(x, y);
      } else {
        context.beginTransaction();
        var id = context.createNode("ImageNode", {
          url: urlOrCanvas
        });
        context.translateNode(id, x - img.naturalWidth / 2, y - img.naturalHeight / 2);
        context.commitTransaction();
      }
    };

    img.onclick = function () {
      img.dropfn(100, 100);
    };

    checkbox.onclick = function () {
      $('#delete-template').prop("disabled", !$(this).is(":checked"));
    };
  },

  loadImage: function (url) {
    return new Promise(function (resolve, reject) {
      var image = new Image();
      image.onload = function () {
        resolve(image);
      }
      image.onerror = function () {
        var msg = "Could not load image at " + url;
        reject(new Error(msg));
      }
      image.src = url;
    });
  },

  addGalleryImageTool: function (urlOrCanvas, actionfn) {
    var img;
    if (typeof urlOrCanvas === "string") {
      img = document.createElement("img");
      img.src = urlOrCanvas;
    } else {
      img = urlOrCanvas;
    }

    var div = document.createElement("div");
    div.appendChild(img);
    $("#gallery-editorSketch").append(div);

    img.setAttribute("draggable", "true");
    img.ondragstart = function (e) {
      console.log("ondragstart");
      draggedImage = img;
    };
    img.onselectstart = function (e) {
      return false;
    };
    img.dropfn = function (x, y) {
      if (actionfn) {
        actionfn(x, y);
      } else {
        context.beginTransaction();
        var id = context.createNode("ImageNode", {
          url: urlOrCanvas
        });
        context.translateNode(id, x - img.naturalWidth / 2, y - img.naturalHeight / 2);
        context.commitTransaction();
      }
      /** 
       * After adding gallery image to the sketch, the system will show the template 
       * section by default. Remove following line if we don't want this behaviour. 
       */
      $("#template-radio").trigger("click");
    };
    img.onclick = function () {
      img.dropfn(300, 300);
    };

  },

	/*
	 * Adds the template to the template tool
	 */
  addTemplate: function (data, templateIndex) {
    // render to an image and add to templates
    var canvas = Zwibbler.createPreview(data, {
      width: 100,
      height: 100
    });

    sketchDialog.addImageTool(canvas, templateIndex, function (x, y) {
      var p = context.paste(data);

      /**
       * TODO: Tricky to calculate new X/Y coordinates to fix RSPAC-280.
       * We have to getSelectedNodes() and use translateNode(id,x,y) to
       * move every selected node to the new position.
       */
    });
  },

  offsetOf: function (e) {
    var box = e.getBoundingClientRect();

    var addX = 0, addY = 0;
    if ("pageYOffset" in window) {
      addX = window.pageXOffset;
      addY = window.pageYOffset;
    } else {
      addX = document.body.scrollLeft;
      addY = document.body.scrollTop;
    }
    return {
      top: box.top + addY,
      left: box.left + addX
    };
  },

	/**
	 * Here is some functions for storing and retrieving the template images that
	 *  Zwibbler uses. You will have to modify them to store them on your server.
	 *
	 * data: The data in Zwibbler format of the template
	 *
	 * doneFn: a function to call when done. Pass null if successful, or an error message.
	 */
  storeTemplate: function (data, templateIndex, doneFn) {
    localStorage["template-" + templateIndex] = data;
    // simulate a delay
    setTimeout(function () {
      // If there's an error, pass a string to doneFn
      doneFn(null);
    }, 500);
  },

	/**
	 * A function we call to retrieve all the template images.
	 *
	 * doneFn: a function to call when done. 
	 *
	 * In the successful case, the first parameter is the list of templates and the second is null.
	 *
	 * Otherwise, make the second parameter an error message.
	 */
  listTemplates: function (doneFn) {
    var templates = [];
    for (var i = 0; i < localStorage.length; i++) {
      if (localStorage.key(i).indexOf("template-") === 0) {
        templates.push(localStorage.getItem(localStorage.key(i)));
      }
    }
    // console.log(templates);

    // simulate a delay
    setTimeout(function () {
      // If there's an error, pass a string to doneFn
      doneFn(templates, null);
    }, 500);
  },

  addZwibblerButtons: function () {

    Zwibbler.addButton({
      name: "Delete",
      image: "/scripts/zwibbler2/wd-delete.png",
      onclick: function (zwibbler) {
        zwibbler.deleteSelection();
      }
    });

    Zwibbler.addButton({
      name: "Small brush",
      image: "/scripts/zwibbler2/wd-brush-small.png",
      onclick: function (zwibbler) {
        zwibbler.useBrushTool(null, 3);
      }
    });

    Zwibbler.addButton({
      name: "Large brush",
      image: "/scripts/zwibbler2/wd-brush-large.png",
      onclick: function (zwibbler) {
        zwibbler.useBrushTool(null, 12);
      }
    });

    Zwibbler.addButton({
      name: "Zoom-In",
      image: "/scripts/zwibbler2/wd-zoom-in.png",
      onclick: function (zwibbler) {
        zwibbler.zoomIn();
      }
    });

    Zwibbler.addButton({
      name: "Zoom-out",
      image: "/scripts/zwibbler2/wd-zoom-out.png",
      onclick: function (zwibbler) {
        zwibbler.zoomOut();
      }
    });

  },

  insert: function (ed) {
    var prefix = tinymceDialogUtils.getContextPath();
    var sketchId = $('.sketchId').attr('data-id');

    // saving the png first, as we want it with background image
    var dataPng = context.save("png");

    // removing background image so it's not saved into db as a part of annotation
    var bgImageNodeId = context.findNode('bgImage');
    if (bgImageNodeId) {
      context.deleteNode(bgImageNodeId);
    }

    // saving zwibbler format (no bg image)
    var dataZwibbler = context.save("zwibbler3");

    // reapplying backround image (in case of save failure)
    if (bgImageNodeId) {
      context.undo();
    }

    var data = {
      annotations: dataZwibbler,
      image: dataPng
    };

    var url;
    var templateUrl;
    var compositeId;
    var milliseconds = new Date().getTime();

    // the plugin is used for sketch or image annotations
    var typeSketch = $('.typeSketch').attr('data-type');
    if (typeSketch == "sketchType") {
      data.fieldId = this.fieldId;
      data.sketchId = sketchId;

      url = prefix.concat("/image/ajax/saveSketch");
      templateUrl = "/fieldTemplates/ajax/sketchLink";
    } else {
      data.parentId = this.fieldId;
      data.imageId = sketchId;
      compositeId = this.fieldId + "-" + sketchId;

      url = prefix.concat("/image/ajax/saveImageAnnotation");
      templateUrl = "/fieldTemplates/ajax/annotatedImageLink";
    }

    var jxqr = $.post(url, data,
      function (result) {
        $.get(templateUrl, function (htmlTemplate) {
          var props = {
            id: compositeId || result.data.id,
            annotationId: result.data.id,
            height: result.data.height,
            width: result.data.width,
            unused: milliseconds
          };
          var html = Mustache.render(htmlTemplate, props);
          if (html != "") {
            ed.execCommand('mceInsertContent', false, html);
          }
          // close the front most window (dialog.htm)
          ed.windowManager.close();
        });
      });
    jxqr.fail(function () {
      tinymceDialogUtils.showErrorAlert("Saving sketch/annotation failed.");
    });
  }

};

$(document).ready(function (e) {
  sketchDialog.init(parent.tinymce.activeEditor);

  	// listen for insert button click
	parent.tinymce.activeEditor.on('sketch-insert', function () {
    if(parent && parent.tinymce) {
      sketchDialog.insert(parent.tinymce.activeEditor);
    }
	});
});