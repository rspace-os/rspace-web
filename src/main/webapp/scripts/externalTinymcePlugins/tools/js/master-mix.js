var pcrMasterMixDialog = {
	init: function (ed) {
		// this is from editor content - spans
		var $masterMixDiv = $(ed.selection.getNode());
		if ($masterMixDiv.hasClass("master-mix")) {
			$masterMixDiv.find('.input').each(function (i, item) {
				var inputName = $(item).attr('data-name');
				var val = $masterMixDiv.attr('data-' + inputName);
				// this is input fields in HTML template to go into dialog
				$(".input[name='" + inputName + "']").val(val);
			});
			// populate results
			doMMCalculation();
		}
	},
	insert: function (ed) {
		var htmlTemplate = $('#insertedCalculationTemplate');
		var data = {};

		// Insert calculated values into the table template
		$('.input').each(function () {
			var name = $(this).attr('name');

			// Insert value from input field
			var value = $(this).val();
			$(htmlTemplate).find('.input[data-name="' + name + '"]').text(value);
			data[name] = $(this).val();

			// Insert corresponding value from MasterMix Formulation if it exists
			var result = $(this).closest('.form-group').find('.result');
			if (result && result.length > 0) {
				var resultValue = result.text();
				$(htmlTemplate).find('.result[data-name="' + name + '"]').text(resultValue);
			}
		});

		// Wrap  in non-editable
		var html$ = $("<div class ='mceNonEditable master-mix labtools'></div>")
			.append(htmlTemplate);

		html$ = $("<div></div>").append(html$);

		// Store data fields
		html$.find('.labtools').each(function (item, i) {
			var labtools$ = $(this);
			for (var property in data) {
				if (data.hasOwnProperty(property)) {
					labtools$.attr('data-' + property, data[property]);
				}
			}
		});

		ed.execCommand('mceInsertContent', false, html$.html());

		// Close the front most window (dialog.htm)
		ed.windowManager.close();
	}
};


$(document).ready(function () {
	pcrMasterMixDialog.init(parent.tinymce.activeEditor);

	// handle insert
	parent.tinymce.activeEditor.on('master-mix-insert', function () {
		if (parent && parent.tinymce) {
			pcrMasterMixDialog.insert(parent.tinymce.activeEditor);
		}
	});

	$(document).on("submit", "#masterMixForm", function (event) {
		event.preventDefault();
		// detect invalid input values, prevent form from being submitted
		// and use HTML5 validation to notify the user
		if (this.checkValidity && !this.checkValidity()) {
			return;
		}

		doMMCalculation();
	});

	$(document).on("click", "#masterMixClear", function (event) {
		$('.input').val('0');
		$('.result').text("0");
	});
});

function doMMCalculation() {
	var numReactions = $("input[name='rtinput']").val();
	var sum = 0;
	$('.single').each(function () {
		var num = (Number)($(this).val());
		sum = sum + num;
		$(this).closest('.form-group').find('.masterVol').text(numReactions * num);
	});
	$(".PCRVolumeOutput").text(sum);
	$(".VolumeOutput").text(sum * numReactions);
	$(".rtOutput").text(numReactions);
}

