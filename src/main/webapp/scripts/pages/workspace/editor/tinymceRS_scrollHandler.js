/**
 * When text view is double clicked, the tinymce should scroll 
 * to the place which was clicked (See RSPAC-1742).
 * 
 */

RS.tinymceScrollHandler = function() {
    
    function _findTopLevelViewModeElem($viewModeTarget) {
        
        var $topLevelElem = null;
        var parents = $viewModeTarget.parentsUntil('.textFieldViewModeDiv');
        if (parents.length > 0) {
            $topLevelElem = parents.eq(parents.length - 1);
        } else if ($viewModeTarget.parent('.textFieldViewModeDiv').length == 1) {
            // already a top level element
            $topLevelElem = $viewModeTarget;
        }
        return $topLevelElem;
    }

    function _getEditModeSelectorForTopLevelElem($topLevelElem) {
        
        var $viewModeDiv = $topLevelElem.parent();
        if (!$viewModeDiv.is('.textFieldViewModeDiv')) {
            console.log("passed elem was not top level element in view mode div");
            return null;
        }
        
        var viewModeSelectorForIndexCount = null;
        var editModeSelector = null;
        if ($topLevelElem.is("p")) {
            viewModeSelectorForIndexCount = "p";
            editModeSelector = "p";
        } else if ($topLevelElem.is("div.tableDownloadWrap")) {
            viewModeSelectorForIndexCount = "div.tableDownloadWrap";
            editModeSelector = "table";
        } else if ($topLevelElem.is("div.mceNonEditable")) {
            viewModeSelectorForIndexCount = "div.mceNonEditable";
            editModeSelector = "div.mceNonEditable";
        } 
        
        if (editModeSelector == null) {
            console.log("top level element not supported by tinymceRS_scrollHandler: " + $topLevelElem);
            return null;
        }
        
        // find clicked element type index within text field was clicked
        var editModeIndex = null;
        $.each($viewModeDiv.children(viewModeSelectorForIndexCount), function(index, child) {
            if ($(child).is($topLevelElem)) {
                editModeIndex = index;
                return false;
            }
        });
        
        editModeSelector += ":eq(" + editModeIndex + ")";
        return editModeSelector;
    }
    
    /* 
     * ==============================
     *  public API  
     * ==============================
     */
    
    /*
     * Returns function that scrolls tinymce content to the place that, 
     * in view mode, was represented as $viewModeTarget.
     * 
     * Supports $viewModeTarget being inside top level paragraph, 
     * table or rspace attachment.
     */
    function getScrollToCallback($viewModeTarget, textFieldId) {
        
        if ($viewModeTarget.parents('.textFieldViewModeDiv').length == 0) {
            console.log('tinymce scrolling skipped - target not within text field content');
            return null;
        }

        var $targetTopLevelElem = _findTopLevelViewModeElem($viewModeTarget);
        var editModeSelector = _getEditModeSelectorForTopLevelElem($targetTopLevelElem);

        var callback = function() {
            if (editModeSelector == null) {
                return;
            }
            console.log('tinymce scroll to selector: ' + editModeSelector);
            var $tinymceScrollToElem = $(tinymce.activeEditor.getBody()).find(editModeSelector);
            if ($tinymceScrollToElem.length > 0) {
                var tinymceParaTopOffset = $tinymceScrollToElem.offset().top;
                // if clicked paragraph is in hidden part of iframe, scroll to it
                if (tinymceParaTopOffset > 200) {
                    console.log('selector found and hidden, scrolling');
                    // scroll the editor iframe so paragraph is visible and close (100px) to the top 
                    var tinymceIframe = $('#field_' + textFieldId + ' .tox-edit-area iframe').get(0);
                    tinymceIframe.contentWindow.scrollTo(0, tinymceParaTopOffset - 100);
                }
            }
        };
        
        return callback;
    }

    return {
        /* main API */
        getScrollToCallback : getScrollToCallback,
        
        /* for tests */
        _findTopLevelViewModeElem: _findTopLevelViewModeElem,
        _getEditModeSelectorForTopLevelElem: _getEditModeSelectorForTopLevelElem
    };

}();
