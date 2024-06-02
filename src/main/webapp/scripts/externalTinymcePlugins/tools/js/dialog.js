var toolsDialog = {

  selected$Node: null,
  labtoolsServerUrl: "",

  init: function(ed) {
    $.get("/deploymentproperties/ajax/properties", function(properties) {
      labtoolsServerUrl = properties['labtools.server.location'];
      var jxqr = $.get(labtoolsServerUrl + '/tools/categories', function(data) {
      	data = addCategoryIcons(data);
        var template = $('#rs-labtools-categories-template').html();
        var templateHtml = Mustache.render(template, { "data": data, "labtoolsServerUrl": labtoolsServerUrl });
        RS.appendMustacheGeneratedHtmlToElement(templateHtml, '#rs-tools-categories');
        addCategoryLinkHandler();
      });
      jxqr.fail(function(xhr) {
        RS.ajaxFailed("Loading tools", false, xhr);
      });
    });
  },

  insert: function(ed) {
    var html = $('.result').html();
    if (html !== undefined && (html == "" || html == " ")) {
      // no result, does tool have an 'insertable' class?
      html = $('.rs-insertable').wrap('<div></div>').parent().html();
    }
    if (html != "") {
      html += " ";
      ed.execCommand('mceInsertContent', false, html);
    }
  }
};

// TO-DO: Get these icons from the labtools app so we don't have to
// hard-code it here.
// See http://www.semo.edu/web/glyph/ for more icons...
var categoryIcons = {
	'radioactivity': 'glyphicons-hazard',
	'calculator': 'glyphicons-calculator',
	'converter': 'glyphicons-transfer',
	'molarity': 'glyphicons-scale-classic'
};
function addCategoryIcons(categories) {
	var catsWithIcons = [];
	$.each(categories, function(i, cat){
		var icon =  cat in categoryIcons ? categoryIcons[cat] : '';
		catsWithIcons.push({'name': cat, 'icon': icon});
	});
	return catsWithIcons;
}

function addCategoryLinkHandler() {
  $(".categoryLink").on('click', function(e) {
    e.preventDefault();

    $(".categoryLink").removeClass("selected");
    $(this).addClass("selected");

    var link$ = $(this).attr('href');
    var jxqr = $.get(link$, function(data) {
      $('#rs-tools-list').html('');
      $('#rs-selectedtool-impl').html('');
      var template = $('#rs-labtools-list-template').html();
      var templateHtml = Mustache.render(template, { "data": data, "labtoolsServerUrl": labtoolsServerUrl });
      RS.appendMustacheGeneratedHtmlToElement(templateHtml, '#rs-tools-list');
      addSelectedToolLinkHandler();
    });
    jxqr.fail(function(xhr) {
      RS.ajaxFailed("Loading tools", false, xhr);
    });
  });
}

function addSelectedToolLinkHandler() {
  $(".toolLink").on('click', function(e) {
    e.preventDefault();
    
    $(".toolLink").removeClass("selected");
    $(this).addClass("selected");
    
    var link$ = $(this).attr('href');
    $('#rs-selectedtool-impl').html('');
    $('#rs-selectedtool-impl').load(link$);
  });
}

$(document).ready(function(e) {
  toolsDialog.init(parent.tinymce.activeEditor);

  parent.tinymce.activeEditor.on('rs-lab-tools-insert', function () {
    if(parent && parent.tinymce) {
      toolsDialog.insert(parent.tinymce.activeEditor);
    }
	}); 
});