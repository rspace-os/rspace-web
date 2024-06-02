/**
 * Toolbar plugin
 * Author: Kris
 * Date : 03/12/2019
 */

let actions, default_config, current_config, toolbar_plugin;

var custom_toolbar = {
  init: function() {
    var $this = this;
    $.when(
      $.getJSON("/scripts/externalTinymcePlugins/toolbar/config.json")
    ).done( function(json) {
      actions        = json.actions;
      default_config = json.default;
  
      $this.applyCurrentConfig();
    })
    return this;
  },
  
  applyCurrentConfig: function() {
    this.clearContent();
    current_config = this.getCurrentConfig();
  
    this.placeUsed();
    this.placeUnused();
  },

  placeUsed() {
    current_config.forEach((row, row_index) => {
      $('#current_toolbar tbody').append(`<tr id='row-${row_index}'></tr>`);
      var table_row = $(`#row-${row_index}`);
  
      row.forEach((group, group_index) => {
        table_row.append(`<td class='group-${group_index}' ondrop='toolbar_plugin.drop(event, this)' ondragover='toolbar_plugin.allowDrop(event)'></td>`);
        let table_group = $(`#row-${row_index} .group-${group_index}`);
  
        group.forEach(element => {
           table_group.append(`<img draggable='true' ondragstart='toolbar_plugin.drag(event)' data-toggle='tooltip' title='${ actions[element] }' \
                                    id='${element}' src='/scripts/externalTinymcePlugins/toolbar/img/${element}.png'>`);
        });
      });
      table_row.append(`<td class='group-${row.length}' ondrop='toolbar_plugin.drop(event, this)' ondragover='toolbar_plugin.allowDrop(event)'><button onclick='toolbar_plugin.appendGroup($(this).parent());'>new group</button></td>`)
    });
    $('#current_toolbar tbody').append(`<tr id='row-${current_config.length}'><td><button onclick='toolbar_plugin.appendRow($(this).parent());'>new row</button></td></tr>`);
  },

  placeUnused() {
    let unused = this.unusedActions();
    let container = $('#available_tools');
  
    unused.forEach( function(entry) {
      container.append(`<img draggable='true' ondragstart='toolbar_plugin.drag(event)' data-toggle='tooltip' title='${ actions[entry] }' \
                                    id='${entry}' src='/scripts/externalTinymcePlugins/toolbar/img/${entry}.png'>`);
    });
  },
  
  getCurrentConfig() {
    let saved_config = JSON.parse(localStorage.getItem('custom_toolbar'));
  
    if (saved_config) {
      return this.formatConfigToArray(saved_config);
    } else {
      return this.formatConfigToArray(default_config);
    }
  },

  formatConfigToArray(config) {
    let config_array = [];
  
    for (key in config) {
      let row = [];
      let groups = config[key].split(' | ');
  
      for (const group of groups) {
        row.push(group.split(' '));
      }
  
      config_array.push(row);
    }
  
    return config_array;
  },

  saveSettings: function() {
    this.exportSettings();
    localStorage.setItem('custom_toolbar', JSON.stringify(current_config));
    this.closeDialog();
  },

  exportSettings: function() {
    let rows = $('#current_toolbar tbody tr');
    current_config = {};
  
    rows.each(function(index) {
      let row = [];
      $(this).find('td').each(function() {
        let ids = [];
  
        if($(this).find('img').length > 0) {
          $(this).find('img').each(function(){ ids.push($(this).attr('id')); });
          row.push(ids.join(' '));
        }
      });
      if (row.join(' | ').length > 0) {
        current_config[index] = row.join(' | ');
      }
    });
  
    return current_config;
  },

  unusedActions: function() {
    let all = Object.keys(actions);
  
    // hash -> array of rows -> array of all -> separate actions -> filter empty entries
    let used = Object.values(this.exportSettings()).join(' ').split(/[\s\|\s|\s]/g).filter(arr => arr);
  
    let unused = all.filter(function(val) {
      return used.indexOf(val) == -1;
    });
  
    return unused;
  },

  allowDrop: function(ev) {
    ev.preventDefault();
  },

  drag: function(ev) {
    ev.dataTransfer.setData("text", ev.target.id);
  },

  drop: function(ev, container) {
    ev.preventDefault();
    var data = ev.dataTransfer.getData("text");
    container.appendChild(document.getElementById(data));
  },

  appendGroup: function(element) {
    let row = element.parent();
    let element_class = element.attr('class');
    let generated_class = 'group-' + (parseInt(element_class.replace(/\D/g,'')) + 1);
  
    row.append(element.clone().attr('class', generated_class));
    element.html('');
  },
  
  appendRow: function(element) {
    let row = element.parent();
    let row_id = row.attr('id');
    let generated_id = 'row-' + (parseInt(row_id.replace(/\D/g,'')) + 1);
  
    $('#current_toolbar tbody').append(row.clone().attr('id', generated_id));
    row.html('<td class="group-0" ondrop="toolbar_plugin.drop(event, this)" ondragover="toolbar_plugin.allowDrop(event)"></td>\
      <td class="group-1" ondrop="toolbar_plugin.drop(event, this)" ondragover="toolbar_plugin.allowDrop(event)">\
      <button onclick="toolbar_plugin.appendGroup($(this).parent());">new group</button></td>');
  },
  
  clearContent: function() {
    $('#current_toolbar tbody').html('');
    $('#available_tools').html('');
  },
  
  closeDialog: function() {
    top.tinymce.activeEditor.windowManager.close();
  },
  
  hardReset: function() {
    localStorage.removeItem('custom_toolbar');
    this.applyCurrentConfig();
  },
}

$(function() {
  toolbar_plugin = custom_toolbar.init();

  parent.tinymce.activeEditor.on('toolbar-reset', function () {
    if(parent && parent.tinymce) {
      toolbar_plugin.applyCurrentConfig();
    }
  });
  
  parent.tinymce.activeEditor.on('toolbar-hard-reset', function () {
    if(parent && parent.tinymce) {
      toolbar_plugin.hardReset();
    }
  });
  
  parent.tinymce.activeEditor.on('toolbar-save', function () {
    if(parent && parent.tinymce) {
      toolbar_plugin.saveSettings();
    }
	}); 
});
