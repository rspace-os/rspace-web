<%@ include file="/common/taglibs.jsp" %>

<%-- Various content that is shared between notebook page, document editor, and gallery --%>

<script src="<c:url value='/scripts/pdfjs/build/pdf.js'/>"></script>
<script>
    PDFJS.workerSrc = '<c:url value='/scripts/pdfjs/build/pdf.worker.js'/>';
</script>

<div id="pdfPreviewPanelTemplate" style="display: none">
    <div class="pdfPreviewPanel">
        <div class="previewPageChangeDiv">
            <button class='previewPreviousPageBtn ignoreDblClick' type='button'> &lt; </button>
        </div>
        <div class="pdfPreviewMainPanel">
            <canvas class="pdfPreviewCanvas"></canvas>
            <div class="pdfPreviewPageNumDiv">
                Page: <span class="pdfPageNum"></span> / <span class="pdfPageCount"></span>
            </div>
        </div>
        <div class="previewPageChangeDiv">
            <button class='previewNextPageBtn ignoreDblClick' type='button'> &gt; </button>
        </div>
    </div>
</div>

<div id="nfsFileInfoDialog" style="display: none">
    <div class="nfsFileInfoPanel">
        <table class="nfsInfoTable">
            <tr><td colspan="2" class="nfsInfoTableHeaderRow"></td></tr>
            <tr><td class="nfsInfoLabelCell">Name: </td><td class="nfsInfoPanel-name"> </td></tr>
            <tr><td class="nfsInfoLabelCell">Original path: </td><td class="nfsInfoPanel-path"> </td></tr>
            <tr><td>&nbsp;</td><td></td></tr>
            <tr><td colspan="2">Stored on a File System:</td></tr>
            <tr><td class="nfsInfoLabelCell">Name: </td><td class="nfsInfoPanel-fileSystemName"> </td></tr>
            <tr><td class="nfsInfoLabelCell">URL: </td><td class="nfsInfoPanel-fileSystemPath"> </td></tr>
            <tr class="nfsInfoShareNameRow">
                <td class="nfsInfoLabelCell">Share: </td><td class="nfsInfoPanel-fileSystemShareName"> </td>
            </tr>
        </table>
        <div class="nfsInfoPanelButtons">
            <button type='button' title="Update Current Path" class='nfsInfoPanelBtn nfsUpdatePathBtn'>Update Path</button>
            <button type='button' title="Download through RSpace" class='nfsInfoPanelBtn nfsFileDownloadBtn'>Download</button>
        </div>
    </div>
</div>
