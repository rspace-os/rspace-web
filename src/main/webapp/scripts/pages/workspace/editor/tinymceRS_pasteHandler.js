/**
 * Implementation of various rules when pasting content into tinyMCE.
 */

RS.tinymcePasteHandler = function() {
    
    /* 
     * ==========================
     * global id paste handling 
     * ==========================
     */

    var globalIdPattern = '(FL|NB|SD|GL|GF)(\\d+)(v\\d+)?';
    var globalIdUrlPattern = 'http[s]*:\/\/[-a-zA-Z0-9._:]{2,256}\\/globalId\\/' + globalIdPattern;
    
    var globalIdRegex = new RegExp("^" + globalIdPattern + "$");
    var globalIdUrlRegex = new RegExp("^" + globalIdUrlPattern + "$");
    
    function _isRSpaceLink(pastedText) {
        var linkPart = _isGlobalId(pastedText) || _isGlobalIdUrl(pastedText); 
        return linkPart !== null;
    }

    // match globalId from a string
    function _isGlobalId(text) {
        if (text == null) {
            return null;
        }
        var answer = text.match(globalIdRegex);
        return (answer == null ? null : answer[0]);
    }

    function _isGlobalIdUrl(text) {
        if (text == null) {
            return null;
        }
        var url = text.match(globalIdUrlRegex);
        return (url == null ? null : url[0]);
    }

    // check if provided <a> tag points to global id url
    function _containsGlobalIdHref($html) {
        if ($html == null) {
            return null;
        }
        var hrefUrl = null;
        var $a = $html.find('a').add($html.filter('a'));
        if ($a.length == 1) {
            hrefUrl = $a.attr('href');
        }
        return hrefUrl;
    }

    function _insertIntoTinymce(text) {
        if (tinymce && tinymce.activeEditor) {
            tinymce.activeEditor.execCommand('mceInsertContent', false, text);
        }
    }
    
    function _retrieveRecordDetailsAndInsert(globalId, pastedText) {
        var id = globalId.replace(/v\d+/g, '').replace(/\D+/g, '');
        var url = `/workspace/getRecordInformation?recordId=${id}`;
        var versionId = RS.getVersionIdFromGlobalId(globalId);
        if (versionId) {
            url += "&version=" + versionId;
        }
        $.get(url, function(result) {
            let data = result.data;
            if (data) { // if record is found
                if (globalId.startsWith("GL") || globalId.startsWith("GF")) { 
                    addFromGallery(data);
                } else {
                    RS.tinymceInsertInternalLink(data.id, globalId, data.name, tinymce.activeEditor);
                }
            } else { // if not found
                _insertIntoTinymce(pastedText);
            }
        }).fail(function() { // if failed
            _insertIntoTinymce(pastedText);
        });
    }

    function _pasteAsRSpaceLink(pastedText, $pastedHtml) {

        var pasteHandled = false;
        if (_isGlobalId(pastedText) || _isGlobalIdUrl(pastedText)) {
            let globalId = _isGlobalId(pastedText);
            let globalIdUrl = _isGlobalIdUrl(pastedText);
            
            // pasting html selected on other instance
            if (globalId && _containsGlobalIdHref($pastedHtml)) {
                globalIdUrl = _containsGlobalIdHref($pastedHtml);
            }

            if (globalIdUrl) {
                // let's retrieve id
                globalId = globalIdUrl.match(/(FL|NB|SD|GL|GF)\d+(v\d+)?$/)[0];
                // if it's a link to a different RSpace instance let's create simple <a> link
                if (!globalIdUrl.includes(window.location.hostname)) {
                    _insertIntoTinymce('<a href="' + globalIdUrl + '">' + globalId + '</a>');
                    pasteHandled = true;
                }
            }
            
            // insert internal link to record on this RSpace instance
            if (!pasteHandled && globalId) {
                _retrieveRecordDetailsAndInsert(globalId, pastedText);
                pasteHandled = true;
            }
        }

        return pasteHandled;
    }
    
    /* 
     * ==========================
     * iframe paste handling 
     * ==========================
     */    
    
    function _pasteAsEmbedIframe(pastedText) {
      // could be multiple iframes, wrap each of them
      var wrappedHtml = pastedText
                .replaceAll('<iframe', '<div class="embedIframeDiv mceNonEditable"><iframe')
                .replaceAll('</iframe>', '</iframe></div><p>&nbsp;</p>');
       _insertIntoTinymce(wrappedHtml);
        return true;
    }

    /* 
     * ==============================
     *  content filtering on paste  
     * ==============================
     */

    function _searchForSelectorIn$html(classes, $html) {
        return $html.find(classes).add($html.filter(classes));
    }

    var notebookBorderClass = '.journalPageContent';
    
    function _containsDocSurroundings($html) {
        return _searchForSelectorIn$html(notebookBorderClass, $html).length > 0;
    }
    
    function _removeNotebookPageSurrounding($html) {
        if (!_containsDocSurroundings($html)) {
            return $html;
        }

        var $newHtml = $("");
        $.each($html, function() {
            var $elem = $(this);
            // search for notebook border class
            var $nbContent = _searchForSelectorIn$html(notebookBorderClass, $elem);
            if ($nbContent.length > 0) {
                // if found, make cleared HTML from just the content inside notebook border class
                $newHtml = $($nbContent.children());
                return false;
            }
        });
        return $newHtml;
    }

    function _unwrapElemsWithClass($html, clazz, unwrapDownToClassSelector) {
        var $newHtml = $("");
        $.each($html, function() {
            var $elem = $(this);
            var $newElem = $elem;
            if ($elem.is(clazz)) {
                if (unwrapDownToClassSelector) {
                  $newElem = $elem.find(unwrapDownToClassSelector);
                } else {
                  $newElem = $elem.children();
                }
            } else {
                if ($elem.find(clazz)) {
                    $.each($elem.find(clazz), function() {
                        $elemWithClazz = $(this);
                        var $unwrapped;
                        if (unwrapDownToClassSelector) {
                            $unwrapped = $(elemWithClazz.find(unwrapDownToClassSelector));
                        } else {
                            $unwrapped = $($elemWithClazz.children());
                        }
                        $elemWithClazz.replaceWith($unwrapped);
                    });
                } 
            }
            $newHtml = $newHtml.add($newElem);
        });
        return $newHtml;
    }
    
    function _removeElemsWithClass($html, clazz) {
        var $newHtml = $("");
        $.each($html, function() {
            var $elem = $(this);
            var $newElem = $elem;
            if ($elem.is(clazz)) {
                $newElem = $("");
            } else {
                $newElem.remove(clazz);
            }
            $newHtml = $newHtml.add($newElem);
        });
        return $newHtml;
    }
    
    var tableWrapperClass = '.tableDownloadWrap';
    var tableButtonsClass = '.tableContextButtons';
    var imageViewPanelClass = '.imageViewModePanel';
    var imageViewPanelTargetClass = '.imageDropped';

    function _getHtmlWithoutRSpaceViewModeClasses($html) {
        $html = _removeNotebookPageSurrounding($html);
        $html = _unwrapElemsWithClass($html, tableWrapperClass);
        $html = _removeElemsWithClass($html, tableButtonsClass);
        $html = _unwrapElemsWithClass($html, imageViewPanelClass, imageViewPanelTargetClass);
        return $html;
    }

    function _containsTableViewModeClasses($html) {
        return _searchForSelectorIn$html(tableWrapperClass + ',' + tableButtonsClass, $html).length > 0;
    }

    function _containsImageViewModeClasses($html) {
        return _searchForSelectorIn$html(imageViewPanelClass, $html).length > 0;
    }

    function _containsRSpaceViewModeClasses($html) {
        return _containsDocSurroundings($html) || _containsTableViewModeClasses($html) || _containsImageViewModeClasses($html);
    }
    
    function _isIframeEmbedText(text) {
        return text && text.startsWith("<iframe ");
    }

    /* 
     * ======================================================================
     *  copying RSpace elements if present in content (sketches, chems etc.)
     * ======================================================================
     */
    var rspaceElemsSelector = "img.chem, img.sketch, img.imageDropped, div.rsEquation, img.commentIcon"; 
    
    function _containsRSpaceFieldElements($html) {
        return _searchForSelectorIn$html(rspaceElemsSelector, $html).length > 0;
    }
    
    function _pasteHtmlWithCopiedRSpaceElements($html) {
        var fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
        // wrap in block screen
        RS.blockPage("Copying elements in pasted content...");
        var jqxhr = $.post("/workspace/editor/structuredDocument/copyContentIntoField", {
          content: RS.convert$HtmlToHtmlString($html),
          fieldId: fieldId
        });
        jqxhr.always(function() {
            RS.unblockPage();
        });
        jqxhr.done(function(copiedContent) {
            _insertIntoTinymce(copiedContent);
        })
        jqxhr.fail(function() {
            RS.ajaxFailed("Copying content", true, jqxhr);
        })
        
    }

    /* 
     * ==============================
     *  public API  
     * ==============================
     */
    function processPastedContent(pastedText, pastedHtml) {
        
        var $pastedHtml = RS.safelyParseHtmlInto$Html(pastedHtml);

        if (_isRSpaceLink(pastedText)) {
            // pasted text should be converted to internal link (RSPAC-1473)
            return _pasteAsRSpaceLink(pastedText, $pastedHtml);
        } 
        if (_isIframeEmbedText(pastedText)) {
            return _pasteAsEmbedIframe(pastedText);
        }

        var pasteHandled = false;
        var contentProcessed = false;
        
        // if pasting image data, add to Gallery as new image file and insert thumbnail (RSPAC-1593)
        if($pastedHtml.length > 0 && $pastedHtml[0].nodeName === "IMG") {
          const src = $pastedHtml[0].attributes.getNamedItem("src");
          if(/^data/.test(src.nodeValue)) {
            fetch(src.nodeValue).then(res => res.blob()).then(blob => {
              const fieldId = getFieldIdFromTextFieldId(tinymce.activeEditor.id);
              const file = new File([blob], "PastedImage.png");
              $('#fromLocalComputerToGallery_' + fieldId).fileupload('add', { files: [file] });
            });
            return true;
          }
        }

        // pasted text should be stripped of view mode elements (RSPAC-1574)
        if (_containsRSpaceViewModeClasses($pastedHtml)) {
            $pastedHtml = _getHtmlWithoutRSpaceViewModeClasses($pastedHtml);
            contentProcessed = true;
        }
        
        // copy rspace field elements in content (RSPAC-1957) 
        if (_containsRSpaceFieldElements($pastedHtml)) {
            // will paste asynchronously
            _pasteHtmlWithCopiedRSpaceElements($pastedHtml);
            pasteHandled = true;
        } else if (contentProcessed) {
            // paste processed content straight away
            var newHtml = RS.convert$HtmlToHtmlString($pastedHtml);
            _insertIntoTinymce(newHtml);
            pasteHandled = true;
        }
        
        return pasteHandled;
    }

    return {
        /* main API */
        processPastedContent : processPastedContent,

        /* for testing */
        _isRSpaceLink : _isRSpaceLink,
        _isGlobalId: _isGlobalId,
        _isGlobalIdUrl: _isGlobalIdUrl,
        _containsGlobalIdHref: _containsGlobalIdHref,
        _pasteAsRSpaceLink: _pasteAsRSpaceLink,
        _containsRSpaceViewModeClasses: _containsRSpaceViewModeClasses,
        _containsRSpaceFieldElements: _containsRSpaceFieldElements,
        _getHtmlWithoutRSpaceViewModeClasses: _getHtmlWithoutRSpaceViewModeClasses
    };

}();
