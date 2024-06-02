var mathjaxDialog = {
	fieldId: null,
	mathId: null,

	init: function (ed) {
		// focus input
		$("#latex").focus();
		fieldId = ed.id.split('_')[1];
		mathId = null; // resetting

		var $node = $(ed.selection.getNode());
		if ($node.hasClass("rsEquationClickableWrapper") || $node.hasClass("rsEquation")) {
			console.log('opening existing equation');
			var equation = $node.data('equation');
			$('#latex').val(equation);
			$('#preview').data('equation', equation);
			mathId = $node.data('mathid');
		}
	},

	insert: function (ed) {
		var equation = $('#latex').val();
		if (!equation) {
			alert('No equation provided');
			return;
		}

		var $svg = $('#preview').find('svg');
		$svg.attr("xmlns", "http://www.w3.org/2000/svg"); // required for portable svg
		var svgHtml = $svg.parent().html();
		if (!svgHtml || equation !== $('#preview').data('equation')) {
			console.info('preview either not generated or stale, regenerating...');
			generateSvg(function () {
				mathjaxDialog.insert(ed);
			});
			return;
		}

		var formdata = {
			fieldId: fieldId,
			svg: svgHtml,
			latex: equation,
			mathId: mathId
		};

		var prefix = tinymceDialogUtils.getContextPath();
		$.ajaxSetup({ async: false }); // to wait until server give a response
		var jqxhr = $.post(prefix.concat("/svg"), formdata);
		jqxhr.done(function (data) {
			console.log("saved equation as: " + data);
			var json = {
				id: data,
				equation: equation,
				svgWidth: $svg.attr("width"),
				svgHeight: $svg.attr("height")
			};
			$.get("/fieldTemplates/ajax/equationLink", function (htmlTemplate) {
				var html = Mustache.render(htmlTemplate, json);
				if (html != "") {
					ed.execCommand('mceInsertContent', false, html);
				}
			});
			// close mathjax dialog
			ed.windowManager.close();
		});
		jqxhr.always(function () {
			$.ajaxSetup({ async: true });
		});
		jqxhr.fail(function () {
			tinymceDialogUtils.showErrorAlert("Inserting formula failed.");
		});
	}
};

function generateSvg(callback) {
	var previewElement = $('#preview');
	var latex = $('#latex').val();
	previewElement.data('equation', latex);

	var script = document.createElement('script');
	script.setAttribute('type', 'math/tex');
	script.textContent = latex;
	previewElement.html(script);

	$(this).data('equation-text', script);

	var queue = MathJax.Callback.Queue(MathJax.Hub.Register.StartupHook("End", {}));
	queue.Push(['Typeset', MathJax.Hub, previewElement[0]]);

	if (callback) {
		queue.Push(callback);
	}
}

function scaleSvgSize(factor) {
	var $svg = $('#preview').find('svg');
	$svg.attr('width', parseFloat($svg.attr('width')) * factor + "ex");
	$svg.attr('height', parseFloat($svg.attr('height')) * factor + "ex");
}

$(document).ready(function () {
	mathjaxDialog.init(parent.tinymce.activeEditor);
	parent.tinymce.activeEditor.mathjaxDialog = mathjaxDialog;

	$('#previewBtn').click(function () { generateSvg() });
	$('#zoomInBtn').click(function () { scaleSvgSize(1.5); });
	$('#zoomOutBtn').click(function () { scaleSvgSize(2 / 3); });

	if (mathId) {
		// Getting previously generated svg into preview area
		var jqxhr = $.get("/svg/" + mathId);
		jqxhr.done(function (result) {
			$('#preview').append($(result).find('svg'));
		});
		jqxhr.fail(function () {
			tinymceDialogUtils.showErrorAlert("Getting preview of the equation failed.");
		});
	}
});

