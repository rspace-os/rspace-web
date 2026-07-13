<%@ include file="/common/taglibs.jsp"%>
<%-- Body fragment: emits NO <head> of its own (SiteMesh 3 keeps only the first head when heads
     nest). The <title> it used to declare now lives in the single <head> of the only caller,
     msTeamsSimpleDocView.jsp, which also marks the simplified view via a [data-simple-editor-view]
     attribute in the body (read by coreEditor.js). --%>

<style>
    #fileBrowsing,
    #showFileTreeSmall,
    .documentPanel #toolbar,
    .btn.attachmentButton {
        display:none;
    }

    .mce-saveCloseDocMenuItem,
    .mce-saveCloneDocMenuItem,
    .mce-saveNewDocMenuItem {
        display:none;
    }

    div#page {
        background-color: white;
    }
</style>


<jsp:include page="structuredDocument.jsp" />
