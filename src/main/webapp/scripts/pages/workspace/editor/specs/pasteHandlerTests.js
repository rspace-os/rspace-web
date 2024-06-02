/*
 * mocked global dependencies (shared with all tests running in given spec-runner!)
 */
var tinymce = tinymce || { activeEditor : { execCommand: function() {} } };

var getFieldIdFromTextFieldId = function () { return "11" };
var _currentServerUrl = RS.createAbsoluteUrl();

describe("tinyMCE Paste Handler - general", function() {
  
  it("RS.tinymcePasteHandler initialised on page", function() {
    expect(RS.tinymcePasteHandler).not.toBe(undefined);
  });

  it("no xss through pasted html content", function() {
      
      var text = 'paragraph a, with an img script in itparagraph b';
      var html = '<div><p>paragraph a, with an img script in it</p>' +
          '<img onerror="alert(\'xss attempt\')" src="dummy.png"></div>' + 
          '<div><p>paragraph b</p></div>';
      expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(false);
      
      /* adding expectation on window.alert immediately doesn't work, as alert is 
       * called asynchronously (from img loading), which happens after the test is run.
       * 
       * but if xss is possible there should be alert popup on summary page, 
       * which will hopefully be noticeable. */
  });

});

describe("tinyMCE Paste Handler - pasting RSpace unique id (RSPAC-1473)", function() {

    beforeEach(function() {
        jasmine.Ajax.install();
    });

    afterEach(function() {
        jasmine.Ajax.uninstall();
    });
    
    it("shouldn't recognise 'asdf' text as RSpace link", function() {
        var text = 'asdf';
        var html = '<span>asdf</span>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(false);
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(false);
    });
    
    it("shouldn't recognise 'John, take a look at SD27.' text as RSpace link", function() {
        var text = 'John, take a look at SD27.';
        var html = '<span>John, take a look at SD27.</span>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(false);
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(false);
    });
    
    it("recognise plain text 'GL12345' and 'SD12345' as RSpace links", function() {
        var text = 'GL12345';
        var html = '<span>GL12345</span>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalId(text)).toBe('GL12345');
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=12345');

        var text2 = 'SD54321';
        var html2 = '<span>SD54321</span>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text2)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalId(text2)).toBe('SD54321');
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=54321');
    });
    
    it("recognise internal links to RSpace document", function() {
        // pasting copied URL
        var text = _currentServerUrl + '/globalId/SD27';
        var html = _currentServerUrl + '/globalId/SD27';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalIdUrl(html)).toBe(_currentServerUrl + '/globalId/SD27');
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=27');

        // pasting selected and copied link tag (Chrome)  
        var text2 = 'SD28';
        var html2 = '<span><span>&nbsp;</span></span><a href="' + _currentServerUrl + '/globalId/SD28">SD28</a>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text2)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalId(text2)).toBe('SD28');
        expect(RS.tinymcePasteHandler._containsGlobalIdHref($(html2))).toBe(_currentServerUrl + '/globalId/SD28');
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=28');
    });

    it("recognise internal links to Gallery item", function() {
        // pasting copied URL
        var text = _currentServerUrl + '/globalId/GL27';
        var html = _currentServerUrl + '/globalId/GL27';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalIdUrl(text)).toBe(_currentServerUrl + '/globalId/GL27');
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=27');
        
        // pasting selected and copied link tag (Chrome)  
        var text2 = 'GL21';
        var html2 = '<a class="infoPanel-objectIdLink" href="' + _currentServerUrl + '/globalId/GL21">GL21</a>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text2)).toBe(true);
        expect(RS.tinymcePasteHandler._containsGlobalIdHref($(html2))).toBe(_currentServerUrl + '/globalId/GL21');
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=21');
        
        // after selecting global id & invisible surroundings in Gallery info panel (Chrome)
        var text3 = 'GL23';
        var html3 = '<table class="infoPanelTable"><tbody><tr class="infoPanelObjectIdRow"><td><a class="infoPanel-objectIdLink" href="' + _currentServerUrl + '/globalId/GL23">GL23</a></td></tr></tbody></table>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text3)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalId(text3)).toBe('GL23');
        expect(RS.tinymcePasteHandler._containsGlobalIdHref($(html3))).toBe(_currentServerUrl + '/globalId/GL23');
        expect(RS.tinymcePasteHandler.processPastedContent(text3, html3)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=23');
    });
    
    it("recognise URL to RSpace document on another RSpace instance", function() {
        var spy = spyOn(tinymce.activeEditor, 'execCommand');

        // pasting copied URL
        var text = 'http://other.rspace:8080/globalId/GL27';
        var html = 'http://other.rspace:8080/globalId/GL27';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalIdUrl(html)).toBe('http://other.rspace:8080/globalId/GL27');
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent()).toBe(undefined);
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<a href="http://other.rspace:8080/globalId/GL27">GL27</a>');
        
        // pasting selected and copied link tag (Chrome)  
        var text2 = 'GL28';
        var html2 = '<a href="http://other.rspace:8080/globalId/GL28">GL28</a><span><span>&nbsp;</span></span>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text2)).toBe(true);
        expect(RS.tinymcePasteHandler._containsGlobalIdHref($(html2))).toBe('http://other.rspace:8080/globalId/GL28');

        spy.calls.reset();
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent()).toBe(undefined);
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<a href="http://other.rspace:8080/globalId/GL28">GL28</a>');
    });
    
    it("recognise internal links to revision of RSpace document", function() {
        var spy = spyOn(tinymce.activeEditor, 'execCommand');
        
        // pasting copied URL
        var text = _currentServerUrl + '/globalId/SD27v2';
        var html = _currentServerUrl + '/globalId/SD27v2';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalIdUrl(html)).toBe(_currentServerUrl + '/globalId/SD27v2');
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=27&version=2');
        
        // pasting selected and copied link tag (Chrome)  
        var text2 = 'SD27v3';
        var html2 = '<span><span>&nbsp;</span></span><a href="' + _currentServerUrl + '/globalId/SD27v3">SD27v3</a>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text2)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalId(text2)).toBe('SD27v3');
        expect(RS.tinymcePasteHandler._containsGlobalIdHref($(html2))).toBe(_currentServerUrl + '/globalId/SD27v3');
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/getRecordInformation?recordId=27&version=3');
    });
    
    it("recognise URL to RSpace document version on another RSpace instance", function() {
        var spy = spyOn(tinymce.activeEditor, 'execCommand');

        // pasting copied URL
        var text = 'http://other.rspace:8080/globalId/SD27v2';
        var html = 'http://other.rspace:8080/globalId/SD27v2';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text)).toBe(true);
        expect(RS.tinymcePasteHandler._isGlobalIdUrl(html)).toBe('http://other.rspace:8080/globalId/SD27v2');
        
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent()).toBe(undefined);
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<a href="http://other.rspace:8080/globalId/SD27v2">SD27v2</a>');
        
        // pasting selected and copied link tag (Chrome)  
        var text2 = 'SD28v3';
        var html2 = '<a href="http://other.rspace:8080/globalId/SD28v3">SD28v3</a><span><span>&nbsp;</span></span>';
        expect(RS.tinymcePasteHandler._isRSpaceLink(text2)).toBe(true);
        expect(RS.tinymcePasteHandler._containsGlobalIdHref($(html2))).toBe('http://other.rspace:8080/globalId/SD28v3');

        spy.calls.reset();
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(jasmine.Ajax.requests.mostRecent()).toBe(undefined);
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<a href="http://other.rspace:8080/globalId/SD28v3">SD28v3</a>');
    });
    
});

describe("tinyMCE Paste Handler - pasting html copied from RSpace view mode (RSPAC-1574)", function() {
    
    it("shouldn't find RSpace classes in random html", function() {
        var text = 'John, take a look at SD27.';
        var html = '<span>John, take a look at SD27.</span>';
        expect(RS.tinymcePasteHandler._containsRSpaceViewModeClasses($(html))).toBe(false);
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(false);
    });
    
    it("should filter RSpace classes from pasted notebook page fragment", function() {
        var spy = spyOn(tinymce.activeEditor, 'execCommand');
        
        // fragment of notebook example page, with selection invisibly ending in entry header area (Chrome)
        var text = 'paragraph';
        var html = '<button id="prevEntryButton_mobile" class="bootstrap-custom-flat" title="Previous entry">' 
            + '<span class="glyphicon glyphicon-chevron-left"></span></button><div id="journalPagePaddingId" class="journalPagePadding">'
            + '<div class="journalPageContent"><img src="' + _currentServerUrl + '/images/mainLogoN2.png"><p><b>paragraph</b></p></div></div>';

        expect(RS.tinymcePasteHandler._containsRSpaceViewModeClasses($(html))).toBe(true);
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);

        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<img src="' + _currentServerUrl + '/images/mainLogoN2.png"><p><b>paragraph</b></p>');
        
        // fragment of notebook example page, with selection invisibly ending in footer area (Firefox)
        var text2 = 'roles in mitosis.';
        var html2 = '<div id=\"content\" class=\"clearfix\"><div id=\"main\"><div class=\"mainDocumentView\"><div class=\"documentPanel\">'
            + '<div id=\"notebook\"><div id=\"journalPage\" style=\"display: block;\"><div id=\"journalPagePaddingId\" class=\"journalPagePadding\">'
            + '<div class=\"journalPageContent\"><p>roles in mitosis.</p><br><br></div>\n </div>\n\n \n </div>\n</div>\n\n \n\n\n\n\n'
            + '<div class=\"signatureContainer\"></div>\n\n\n \n \n \n\n </div>\n</div>\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n'
            + '\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n \n\n\n\n\n\n\n\n\n\n\n\n'
            + '<div class=\"bootstrap-custom-flat\">\n\n \n \n\n \n \n\n\n \n</div>\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n '
            + '\n \n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n \n\n \n\n\n\n\n\n\n\n\n\n\n\n\n \n \n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n'
            + '\t\n\t\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n'
            + '\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n'
            + '\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n'
            + '\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\t\t\t</div>\n\n\t\t</div>\n\t\t<div id=\"footer\" class=\"clearfix\">\n\t\t\t\n\n\n'
            + '\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n <div id=\"divider\"><div></div></div>\n <span class=\"left\" style=\"margin-top:5px;\"></span></div>';

        spy.calls.reset();
        expect(RS.tinymcePasteHandler._containsRSpaceViewModeClasses($(html2))).toBe(true);
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, '<p>roles in mitosis.</p><br><br>');
    });
    
    it("should unwrap table copied from RSpace view mode from wrapper div", function() {
        var spy = spyOn(tinymce.activeEditor, 'execCommand');
        
        // selection of a view mode table surrounded by text, context buttons caught in selection (Chrome)
        var text = 'textabcdmore text';
        var html = '<p>text</p><div class="tableDownloadWrap"><table width="300" cellpadding="5" border="1">'
            + '<tbody><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></tbody></table>'
            + '<div class="tableContextButtons tableDownloadButton" title="Download as CSV"></div></div>'
            + '<p>more text</p>';

        expect(RS.tinymcePasteHandler._containsRSpaceViewModeClasses($(html))).toBe(true);
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);

        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<p>text</p><table width="300" cellpadding="5" border="1"><tbody><tr><td>a</td><td>b</td></tr>'
                + '<tr><td>c</td><td>d</td></tr></tbody></table><p>more text</p>');
        
        // selection of a view mode table surrounded by text, no context buttons (Chrome)
        var text2 = 'textabcdmore text';
        var html2 = '<p>text</p><div class="tableDownloadWrap"><table width="300" cellpadding="5" border="1">'
            + '<tbody><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></tbody></table></div>'
            + '<p>more text</p>';

        spy.calls.reset();
        expect(RS.tinymcePasteHandler._containsRSpaceViewModeClasses($(html2))).toBe(true);
        expect(RS.tinymcePasteHandler.processPastedContent(text2, html2)).toBe(true);
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<p>text</p><table width="300" cellpadding="5" border="1"><tbody><tr><td>a</td><td>b</td></tr>'
                + '<tr><td>c</td><td>d</td></tr></tbody></table><p>more text</p>');
        
        // pasting view mode selection of two tables (Firefox)
        var text3 = 'text \n \n \n \n a \n b \n \n \n c \n d \n \n \n \nanother table \n \n \n \n e \n f \n g \n \n \n \nmore text';
        var html3 = '<p>text</p> \n<div class=\"tableDownloadWrap\" style=\"display:flex;\"><table width=\"300\" cellpadding=\"5\" border=\"1\">'
            + '\n <tbody> \n <tr> \n <td>a</td> \n <td>b</td> \n </tr> \n <tr> \n <td>c</td> \n <td>d</td> \n </tr> \n </tbody> \n</table></div>'
            + '\n<p>another table</p> \n<div class=\"tableDownloadWrap\" style=\"display:flex;\"><table width=\"300\" cellpadding=\"5\" border=\"1\">'
            + '\n <tbody> \n <tr> \n <td>e</td> \n <td>f</td> \n <td>g</td> \n </tr> \n </tbody> \n</table></div> \n<p>more text</p>';
            
        spy.calls.reset();
        expect(RS.tinymcePasteHandler._containsRSpaceViewModeClasses($(html3))).toBe(true);
        expect(RS.tinymcePasteHandler.processPastedContent(text3, html3)).toBe(true);
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 
                '<p>text</p> \n<table width=\"300\" cellpadding=\"5\" border=\"1\">'
                + '\n <tbody> \n <tr> \n <td>a</td> \n <td>b</td> \n </tr> \n <tr> \n <td>c</td> \n <td>d</td> \n </tr> \n </tbody> \n</table>\n'
                + '<p>another table</p> \n<table width=\"300\" cellpadding=\"5\" border=\"1\">'
                + '\n <tbody> \n <tr> \n <td>e</td> \n <td>f</td> \n <td>g</td> \n </tr> \n </tbody> \n</table> \n<p>more text</p>');
    });

    it("should unwrap image copied from RSpace view mode from wrapper div", function() {
        var spy = spyOn(tinymce.activeEditor, 'execCommand');
        
        // selection of a view mode image surrounded by text, context buttons caught in selection (Chrome)
        var text = 'xt tset test2.png  asd';
        var html = '<span>xt tset&nbsp;</span><div class="imageViewModePanel"><div class="imagePanel">' 
            + '<img id="65569-1995" class="imageDropped inlineImageThumbnail" src="http://localhost:8080/thumbnail/data?sourceType=IMAGE&amp;' 
            + 'sourceId=1995&amp;sourceParentId=65569&amp;width=644&amp;height=483&amp;rotation=0&amp;time=1582719892404" alt="image test2.png"'
            + ' data-size="644-483 data-rotation=0" width="260" height="195"></div><div class="imageData"><span class="imageFileName">test2.png</span>'
            + '<span>&nbsp;</span><div class="imageInfoBtnDiv"><img src="http://localhost:8080/images/getInfo12.png"></div></div></div><span>'
            + '<span>&nbsp;</span>asd</span>';

        var $html = RS.safelyParseHtmlInto$Html(html);
        
        expect(RS.tinymcePasteHandler._containsRSpaceViewModeClasses($html)).toBe(true);
        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);

        var pastedHtml = RS.convert$HtmlToHtmlString(RS.tinymcePasteHandler._getHtmlWithoutRSpaceViewModeClasses($html));
        expect(pastedHtml).toBe('<span>xt tset&nbsp;</span><img id="65569-1995" class="imageDropped inlineImageThumbnail" ' 
                + 'src="http://localhost:8080/thumbnail/data?sourceType=IMAGE&amp;sourceId=1995&amp;sourceParentId=65569&amp;width=644&amp;height=483&amp;rotation=0&amp;time=1582719892404" ' 
                + 'alt="image test2.png" data-size="644-483 data-rotation=0" width="260" height="195"><span><span>&nbsp;</span>asd</span>');
    });

});

describe("tinyMCE Paste Handler - content with RSpace elems should point to copied elems after paste", function() {

    beforeEach(function() {
        jasmine.Ajax.install();
    });

    afterEach(function() {
        jasmine.Ajax.uninstall();
    });
    
    it("should recognise RSpace field elements in content", function() {
        // check chem fragment
        var text = 'test content before and after';
        var $html = RS.safelyParseHtmlInto$Html('<p>test content before</p>'
            + '<p><img id="425984" class="chem" src="/thumbnail/data?sourceType=CHEM&amp;sourceId=425984&amp;sourceParentId=557056&amp;width=300&amp;height=118&amp;time=1575813040681" '
            + 'alt="chemistry structure image" width="300" height="118" data-fullwidth="300" data-fullheight="118"></p>'
            + '<p>&nbsp; and after</p>');
        expect(RS.tinymcePasteHandler._containsRSpaceFieldElements($html)).toBe(true);

        // check sketch fragment
        text = 'test content before  and after';
        $html = RS.safelyParseHtmlInto$Html('test content before&nbsp;<img id="327680" class="sketch" src="/image/getImageSketch/327680/1575812783054" '
            + 'alt="user sketch" width="148" height="162">&nbsp;and after');
        expect(RS.tinymcePasteHandler._containsRSpaceFieldElements($html)).toBe(true);
        
        // check annotation fragment
        text = 'test content before  and after';
        $html = RS.safelyParseHtmlInto$Html('text before&nbsp;<img id="557056-15541" class="imageDropped" src="/image/getAnnotation/393216/1575882780885" '
                + 'alt="image" width="489" height="307" data-id="393216" data-type="annotation">&nbsp;text after');
        expect(RS.tinymcePasteHandler._containsRSpaceFieldElements($html)).toBe(true);

        // check math fragment 
        text = 'test content before  and after';
        $html = RS.safelyParseHtmlInto$Html('<p>test content before&nbsp;</p>'
            + '<div class="rsEquation mceNonEditable" data-mathid="32768" data-equation="asdf + (2* 2)"><a class="rsEquationClickableWrapper"> '
            + '<object data="/svg/32768" type="image/svg+xml" data-svgwidth="13.984ex" data-svgheight="2.66ex"></object> </a></div>'
            + '<p>and after</p>');
        expect(RS.tinymcePasteHandler._containsRSpaceFieldElements($html)).toBe(true);

        // check comment fragment
        text = 'test content before  and after';
        $html = RS.safelyParseHtmlInto$Html('test content before <img id="10" class="commentIcon" '
            + 'src="/images/commentIcon.gif" alt="Comment link icon">&nbsp;and after');
        expect(RS.tinymcePasteHandler._containsRSpaceFieldElements($html)).toBe(true);
        
        // shouldn't find anything in plain text content 
        text = 'John, take a look at SD27.';
        $html = RS.safelyParseHtmlInto$Html('<span>John, take a look at SD27.</span>');
        expect(RS.tinymcePasteHandler._containsRSpaceFieldElements($html)).toBe(false);

    });

    it("should call server copy if RSpace elements present", function() {
        var spy = spyOn(tinymce.activeEditor, 'execCommand');
        
        // fragment with chemical element (RSPAC-1957)
        var text = 'We have perfor';
        var html = '<p><img id=\"360450\" class=\"chem\" src=\"/thumbnail/data?sourceType=CHEM&amp;sourceId=360450&amp;sourceParentId=163891&amp;'
                + 'width=300&amp;height=71&amp;time=1575586524729\" alt=\"chemistry structure image\" width=\"300\" height=\"71\" ' 
                + 'data-fullwidth=\"336\" data-fullheight=\"80\"></p><p>&nbsp;We have perfor</p>';

        expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);

        expect(jasmine.Ajax.requests.mostRecent().url).toBe('/workspace/editor/structuredDocument/copyContentIntoField');
        jasmine.Ajax.requests.mostRecent().respondWith({
            "status": 200,
            "contentType": 'text/plain',
            "responseText": 'mocked_copy_response'
        });
        
        expect(tinymce.activeEditor.execCommand).toHaveBeenCalledWith('mceInsertContent', false, 'mocked_copy_response');
    });

});

describe("tinyMCE Paste Handler - pasting image data", () => {
  it("should be processed", () => {
    const text = '';
    const html = '<img src="data:image/png;base64," alt="">';
    expect(RS.tinymcePasteHandler.processPastedContent(text, html)).toBe(true);
  });
});
