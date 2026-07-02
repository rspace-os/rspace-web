<%@ include file="/common/taglibs.jsp"%>
<%-- Body fragment: emits NO <head> of its own (SiteMesh 3 keeps only the first head when heads
     nest). The <title> and the isSimpleEditorView flag it used to declare now live in the single
     <head> of the only caller, msTeamsSimpleDocView.jsp. --%>

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
