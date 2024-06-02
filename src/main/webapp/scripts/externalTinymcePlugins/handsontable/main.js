/**
 * Script loaded inside Handsontable tinymce plugin dialog 
 */

var activeEditor;

var hotDialog = {
	hot: null,
	init: function (ed) {
		var nodeTableData = null;
		var calcTableData = null;
		var $node = $(ed.selection.getNode());
		if ($node.hasClass("rsCalcTableDiv")) {
			nodeTableData = $node.data('tabledata');
			calcTableData = nodeTableData.data;
		}

		var container = document.getElementById('hotTableDiv');

		function resizeDialogCallback(resizeAgainAfterRender) {
			setTimeout(function () {
				tinymceDialogUtils.resizeDialogToContent(activeEditor, $('.wtHider:first'), 50, 240);
				hotDialog.hot.render();
				if (resizeAgainAfterRender) {
					tinymceDialogUtils.resizeDialogToContent(activeEditor, $('.wtHider:first'), 50, 240);
				}
			}, 0);
		}

		hotDialog.hot = new Handsontable(container, {
			data: calcTableData,
			rowHeaders: true,
			colHeaders: true,
			contextMenu: ["row_above", "row_below", "col_left", "col_right", "remove_row", "remove_col", "---------", "undo", "redo"],
			formulas: true,
			minRows: 8,
			minCols: 12,
			renderAllRows: true,
		});

		hotDialog.hot.addHook('afterCreateCol', resizeDialogCallback);
		hotDialog.hot.addHook('afterCreateRow', resizeDialogCallback);
		hotDialog.hot.addHook('afterRemoveCol', resizeDialogCallback);
		hotDialog.hot.addHook('afterRemoveRow', resizeDialogCallback);
		resizeDialogCallback(true);

		$('#addColumnLink').click(function () {
			hotDialog.hot.alter('insert_col');
		});
		$('#addRowLink').click(function () {
			hotDialog.hot.alter('insert_row');
		});
	},

	insert: function (ed) {
		// in case cell editor is active
		hotDialog.hot.deselectCell()
		var data = getDataFromHot();
		var dataAsHtml = convertHotToHtmlTable();

		$.ajaxSetup({ async: false }); // to wait until server give a response
		var jqxhr = $.get("/fieldTemplates/ajax/calcTableLink", function (htmlTemplate) {
			var json = {
				data: data,
				tableHtml: dataAsHtml
			};
			var html = Mustache.render(htmlTemplate, json);
			if (html != "") {
				ed.execCommand('mceInsertContent', false, html);
			}
			// close mathjax dialog
			ed.windowManager.close();
		});
		jqxhr.always(function () {
			$.ajaxSetup({ async: true });
		});
		jqxhr.fail(function () {
			tinymceDialogUtils.showErrorAlert("Inserting calculation table failed.");
		});
	},
};

function convertHotToHtmlTable() {
	var data = hotDialog.hot.getData();
	var html = "";
	var rowCount = 0;
	var colHeaders = hotDialog.hot.getColHeader();

	// add header row
	var headerRow = "";
	$.each(colHeaders, function (i, cell) {
		headerRow += "<th scope='col'>" + cell + "</th>";
	});
	html += "<thead><tr><th></th>" + headerRow + "</th></thead>";

	$.each(data, function () {
		var rowHeader = hotDialog.hot.getRowHeader(rowCount);
		var row = "<th scope='row'>" + rowHeader + "</th>";
		var colCount = 0;
		$.each(this, function (i, cell) {
			var cellRef = colHeaders[colCount] + rowHeader;
			var cellValue = cell == null ? '' : getCalculatedHotCellValue(cellRef);
			row += "<td>" + cellValue + "</td>";
			colCount++;
		})
		html += "<tr>" + row + "</tr>";
		rowCount++;
	})

	var tableHtml = "<table class='rsCalcTable'>" + html + "</table>";
	return tableHtml;
}

function getCalculatedHotCellValue(cellRef) {
	var value;
	try {
		value = hotDialog.hot.plugin.helper.cellValue(cellRef);
	} catch (e) {
		value = e.message;
	}
	return value;
}

function getDataFromHot() {
	var rawData = hotDialog.hot.getData();
	var jsonData = JSON.stringify({ data: rawData, settings: 'dummy' });
	return jsonData;
}

$(document).ready(function () {
	activeEditor = parent.tinymce.activeEditor;

	hotDialog.init(activeEditor);
	activeEditor.handsonDialog = hotDialog;
});
