<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Simple document view</title>
    
    <script> 
    	var isSimpleEditorView = true;
    </script>

</head>

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

