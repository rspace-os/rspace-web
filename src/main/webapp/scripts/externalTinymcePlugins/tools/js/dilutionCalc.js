var dilutionCalcDialog = {

  insertAsText: function (ed) {
    var data = {
      finalConcentration: $('#finalConcentrationInput').val(),
      finalConcentrationUnit: $('#finalConcentrationUnitInput :selected').text(),
      finalVolume: $('#finalVolumeInput').val(),
      finalVolumeUnit: $('#finalVolumeUnitInput :selected').text(),
      stockConcentration: $('#stockConcentrationInput').val(),
      stockConcentrationUnit: $('#stockConcentrationUnitInput :selected').text(),
      stockVolume: $('#stockVolumeInput').val(),
      stockVolumeUnit: $('#stockVolumeUnitInput :selected').text()
    }

    if (!data.stockVolume) {
      console.log('calculation not yet triggered, skipping insert action');
      return;
    }

    var htmlTemplate = $('#insertedCalculationTemplate').html();
    var html = Mustache.render(htmlTemplate, data);
    if (html != "") {
      RS.tinymceInsertContent(html, ed);
      ed.windowManager.close();
    }
  }
}

$(document).ready(function () {

  parent.tinymce.activeEditor.on('dilution-calc-insert', function () {
    if (parent && parent.tinymce) {
      dilutionCalcDialog.insertAsText(parent.tinymce.activeEditor);
    }		
  });

  $('#dilutionCalculatorDiv form').on('submit', function (e) {
    e.preventDefault();

    // detect invalid input values, prevent form from being submitted
    // and use HTML5 validation to notify the user
    var form = $(this).closest("form");
    if (form[0].checkValidity && !form[0].checkValidity()) {
      return;
    }

    var $result = $('#stockVolumeInput');
    $result.val(""); // clear
    var $resultUnit = $('#stockVolumeUnitInput');

    var finalConcentration = $('#finalConcentrationInput').val();
    var finalConcentrationUnit = $('#finalConcentrationUnitInput').val();
    var finalVolume = $('#finalVolumeInput').val();
    var finalVolumeUnit = $('#finalVolumeUnitInput').val();
    var stockConcentration = $('#stockConcentrationInput').val();
    var stockConcentrationUnit = $('#stockConcentrationUnitInput').val();

    if (!finalConcentration || !finalVolume || !stockConcentration) {
      alert('Please provide values for all fields');
      return;
    }

    if (stockConcentration == 0) {
      alert('Stock concentration must be larger than 0');
      return;
    }

    var fcu = math.unit(math.bignumber(finalConcentration), finalConcentrationUnit);
    var fvu = math.unit(math.bignumber(finalVolume), finalVolumeUnit);
    var scu = math.unit(math.bignumber(stockConcentration), stockConcentrationUnit);

    if (math.compare(fcu, scu) > 0) {
      alert('Desired concentration cannot be larger than stock concentration');
      return;
    }

    var result = math.divide(math.multiply(fcu, fvu), scu);

    $result.val(result.toNumber());
    $resultUnit.val(result.formatUnits());
  });

});