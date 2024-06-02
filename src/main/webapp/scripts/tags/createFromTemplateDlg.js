/**
 * Javascript for the Create -> From Template dialog.
 */

var currentFolderId;

function initCreateFromTemplateDlg() {
  $('#createFromTemplateDlg').dialog({
    modal : true,
    autoOpen: false,
    title: "Select a template",
    width: 500,
    height: 600,
    buttons: {
      Cancel: function() { $(this).dialog('close') },
      Create: {
        text: 'Create',
        id: 'createFromTemplateDlgCreateBtn',
        click: function() { 
          var $selected = $('#createFromTemplateDlg .active:first');
          if ($selected.length === 0) {
            apprise('Please select a template by clicking on it');
            return;
          }
              
          var templateId = $selected.data('id');
          var docName = $("#createFromTemplateNameInput").val();
              
          if (!docName || docName.trim().length === 0 || docName.match(/\//) ) {
            apprise("Please enter a name - excluding '/' characters.");
            return false;
          }
              
          var $form = $('#createFromTemplateForm');
          $form.attr('action', "/workspace/editor/structuredDocument/createFromTemplate/" + currentFolderId);
          $form.find('#createFromTemplateId').val(templateId);
          $form.find('#createFromTemplateNewName').val(docName);                    
          $form.submit();
          
          RS.blockPage('Creating a document...');
        }
      }
    },
    open: function () {
      loadTemplatesFromServer();
    }
  });
    
  $('#templateDlgTabs').tabs();
  
  $('#templateFilterForm').submit(function() {
    var filterTerm = $('#templateFilterInput').val();
    filterTables(filterTerm);
    return false; 
  });

  $(".sortByName").on('click', function(e) {
    e.preventDefault();
    var sorted = $(this).data("sorted");
    sortByName(sorted);
    $(".sortByName").data("sorted", !sorted);
    $(".sortByCreation").data("sorted", "false");
    $(".sortByOwner").data("sorted", "false");
    filterTables($('#templateFilterInput').val());
  });

  $(".sortByCreation").on('click', function(e) {
    e.preventDefault();
    var sorted = $(this).data("sorted");
    sortByCreation(sorted);
    $(".sortByCreation").data("sorted", !sorted);
    $(".sortByName").data("sorted", "false");
    $(".sortByOwner").data("sorted", "false");
    filterTables($('#templateFilterInput').val());
  });

  $(".sortByOwner").on('click', function(e) {
    e.preventDefault();
    var sorted = $(this).data("sorted");
    sortByOwner(sorted);
    $(".sortByOwner").data("sorted", !sorted);
    $(".sortByCreation").data("sorted", "false");
    $(".sortByName").data("sorted", "false");
    filterTables($('#templateFilterInput').val());
  });
}

function openCreateFromTemplateDlg(recordId) {
  $('#createFromTemplateDlg').dialog('open');
  currentFolderId = recordId;
}

function loadTemplatesFromServer() {
  loadTemplatesIntoTable($('#myTemplatesTab'), '/workspace/ajax/getMyTemplates');
  loadTemplatesIntoTable($('#sharedTemplatesTab'), '/workspace/ajax/getTemplatesSharedWithMe');
}

function loadTemplatesIntoTable($tab, url) {
  var jqxhr = $.get(url);
  jqxhr.done(function(result) {
    var templates = result.data;
    if (!templates || templates.length === 0) {
      $tab.html('<div style="width: 100%; text-align: center; margin-top: 50px;">No templates found</div>');
      return;
    }
    $.each(templates, function(i, temp) {
      temp.creationDateWithClientTimezoneOffset = temp.creationDateWithClientTimezoneOffset.slice(0, -6);
    });
    addTemplatesIntoTable($tab, templates);
  });
}

function addTemplatesIntoTable($tab, templates) {
  var $rowTemplate = $tab.find('.rowTemplate table tbody').html();
  var $tableBody = $tab.find('.templatesTableBody');
  $tableBody.empty();
  
  $.each(templates, function(i, temp) {
    var templateRowHtml = Mustache.render($rowTemplate, {template: temp});
    $tableBody.append(templateRowHtml);
  });
  
  var $rows = $tableBody.find('tr');
  $rows.click(function() {
    var $row = $(this); 
    $('.active').removeClass('active');
    $row.addClass('active');
    $('#createFromTemplateNameInput').val("from_" + $row.find('.templateNameCell').text());
  });
}

function filterTables(filterTerm) {
  var $rows = $('#createFromTemplateDlg .templatesTableBody tr');
  $.each($rows, function(i, row) {
    var tempName = $(row).find('.templateNameCell').text();
    var isMatch = !filterTerm || tempName.toLowerCase().indexOf(filterTerm.toLowerCase()) >= 0;
    $(row).toggle(isMatch);
  });
}

function sortByName(reverse) {
  $.each([$('#myTemplatesTab'), $('#sharedTemplatesTab')], function(i, $tab) {
    var templates = parseRows($tab);
    templates = templates.sort(dynamicSort("name"));
    if(reverse) {
      templates = templates.reverse();
    }
    addTemplatesIntoTable($tab, templates);
  });
}

function sortByCreation(reverse) {
  $.each([$('#myTemplatesTab'), $('#sharedTemplatesTab')], function(i, $tab) {
    var templates = parseRows($tab);
    templates = templates.sort(dynamicSort("creationDateWithClientTimezoneOffset"));
    if(reverse) {
      templates = templates.reverse();
    }
    addTemplatesIntoTable($tab, templates);
  });
}

function sortByOwner(reverse) {
  var $tab = $('#sharedTemplatesTab');
  var templates = parseRows($tab);
  templates = templates.sort(dynamicSort("ownerFullName"));
  if(reverse) {
    templates = templates.reverse();
  }
  addTemplatesIntoTable($tab, templates);
}

function parseRows($tab) {
  var templates = [], name, creationDate, owner, iconId, templateId;
  var $rows = $tab.find('.templatesTableBody tr');

  $.each($rows, function(i, row) {
    templateId = $(row).data('id');
    iconId = $(row).find('span').data("icon");
    name = $(row).find('.templateNameCell').text();
    creationDate = $(row).find('.templateCreatedCell').text();
    owner = $(row).find('.templateOwnerCell').text();
    templates.push({
      name: name,
      creationDateWithClientTimezoneOffset: creationDate,
      ownerFullName: owner,
      iconId: iconId,
      id: templateId
    })
  });
  return templates;
}

function dynamicSort(property) {
  return function (a,b) {
    return (a[property].toUpperCase() < b[property].toUpperCase()) ? -1 : (a[property].toUpperCase() > b[property].toUpperCase()) ? 1 : 0;
  }
}
