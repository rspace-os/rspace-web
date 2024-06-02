/*
 * mocked global dependencies (shared with all tests running in given spec-runner!)
 */
var tinymce = tinymce || { activeEditor : { execCommand: function() {} } };

describe("tinyMCE Scroll Handler - general", function() {
  
  var pFragmentViewMode = '<p>test paragraph with <strong>inline tags</strong></p>';
    
  var tableFragmentViewMode = '<div class="tableDownloadWrap" style="display:flex;">'
      + '<table style="border-collapse: collapse; width: 100%;" border="1" width="300" cellpadding="5">' 
      + '<tbody><tr><td style="width: 50%;"><strong>rspace table</strong></td>' 
      + '<td style="width: 50%;">&nbsp;</td></tr></tbody>' 
      + '</table><div class="tableContextButtons tableDownloadButton" title="Download as CSV"' 
      + ' style="bottom: 13px; left: 38px; align-self: flex-end; display: none;"></div></div>';
    
  var attachmentDivFragmentViewMode = '<div class="attachmentDiv mceNonEditable">'
      + '<div class="attachmentPanel previewableAttachmentPanel">'
      + '<button class="previewToggleBtn previewExpandBtn ignoreDblClick" type="button">'
      + '      <img class="ignoreDblClick" src="/images/icons/mag_glass_plus.png" height="20px">'
      + '</button>'
      + '<button class="previewToggleBtn previewCollapseBtn" type="button" style="display:none">'
      + '    <img class="ignoreDblClick" src="/images/icons/mag_glass_minus.png" height="20px">'
      + '</button>'
      + '<div class="attachmentThumbnailPanel">'
      + '    <img class="attachmentIcon" height="76" src="/image/docThumbnail/690/51" style="background-color: rgb(255, 255, 255);">'
      + '    <label class="attachmentName">estimates.txt</label>'
      + '</div>'
      + '<div class="inlineActionsPanel" style="margin-top: 10px;">'
      + '   <a href="#" class="inlineActionLink viewActionLink">View</a>'
      + '   <a href="/Streamfile/690" class="inlineActionLink downloadActionLink">Download</a>'
      + '  <a href="#" class="inlineActionLink infoActionLink">Info <img src="/images/getInfo12.png"></a>'
      + '</div>'
      + '<div class="attachmentPreviewPanel"></div>'
      + '<div class="attachmentPreviewInfoPanel">'
      + '    <div class="recordInfoPanel"></div>'
      + '</div>'
      + '</div></div>';   
  
  it("RS.tinymceScrollHandler initialised on page", function() {
    expect(RS.tinymceScrollHandler).not.toBe(undefined);
  });

  it("top level view mode element recognized from click target", function() {

      var viewModeTestHtml = pFragmentViewMode + tableFragmentViewMode + pFragmentViewMode + attachmentDivFragmentViewMode
              + pFragmentViewMode + tableFragmentViewMode + pFragmentViewMode + attachmentDivFragmentViewMode
              + pFragmentViewMode;
          
      var $viewModeTestHtml = $('<div id="div_rtf_131150" class="isResizable textFieldViewModeDiv">')
              .append(viewModeTestHtml);

      // check selections within second paragraph
      var $secondParagraph = $viewModeTestHtml.children('p:eq(1)');
      expect(RS.tinymceScrollHandler._findTopLevelViewModeElem($secondParagraph)).toEqual($secondParagraph);
      var $secondParagraphStrongText = $secondParagraph.children('strong:eq(0)');
      expect(RS.tinymceScrollHandler._findTopLevelViewModeElem($secondParagraphStrongText).is($secondParagraph)).toBe(true);

      // check selection within second attachment div
      var $secondAttachmentDiv = $viewModeTestHtml.children('.attachmentDiv:eq(1)');
      var $attachmentLink = $secondAttachmentDiv.find('.viewActionLink:eq(0)');
      expect(RS.tinymceScrollHandler._findTopLevelViewModeElem($attachmentLink).is($secondAttachmentDiv)).toBe(true);
      
      // check selection within second table
      var $secondTable = $viewModeTestHtml.children('.tableDownloadWrap:eq(1)');
      var $textInTable = $secondTable.find('strong:eq(0)');
      expect(RS.tinymceScrollHandler._findTopLevelViewModeElem($textInTable).is($secondTable)).toBe(true);
      
      // check nothing is returned when selection is the field itself
      expect(RS.tinymceScrollHandler._findTopLevelViewModeElem($viewModeTestHtml)).toBe(null);
  });
  
  
  it("top level view mode element translates properly to edit mode selector", function() {

      var viewModeTestHtml = pFragmentViewMode + tableFragmentViewMode + pFragmentViewMode + attachmentDivFragmentViewMode
          + pFragmentViewMode + tableFragmentViewMode + pFragmentViewMode + attachmentDivFragmentViewMode
          + pFragmentViewMode;
          
      var $viewModeTestHtml = $('<div id="div_rtf_131150" class="isResizable textFieldViewModeDiv">')
              .append(viewModeTestHtml);

      // check selector returned for second paragraph
      var $secondParagraph = $viewModeTestHtml.children('p:eq(1)');
      expect(RS.tinymceScrollHandler._getEditModeSelectorForTopLevelElem($secondParagraph)).toBe('p:eq(1)');

      // check selector returned for second attachment div
      var $secondAttachmentDiv = $viewModeTestHtml.children('.attachmentDiv:eq(1)');
      expect(RS.tinymceScrollHandler._getEditModeSelectorForTopLevelElem($secondAttachmentDiv)).toBe('div.mceNonEditable:eq(1)');

      // check selector returned for second table
      var $secondTable = $viewModeTestHtml.children('.tableDownloadWrap:eq(1)');
      expect(RS.tinymceScrollHandler._getEditModeSelectorForTopLevelElem($secondTable)).toBe('table:eq(1)');
      
  });
  
});

