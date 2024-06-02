<%@ include file="/common/taglibs.jsp"%>
    <c:if test="${permDTO.createRecord}">
    <div id="createDocForm">
        <axt:importFromWord isNotebook="${isNotebook}" parentId="${selectedNotebookId}"/>
    </div>
        <axt:importFromProtocolsIo />
    </c:if>
    <c:url var="createFromTemplateURL" value="/workspace/editor/structuredDocument/create/${selectedNotebookId}"></c:url>
    <script src="<c:url value='/scripts/bower_components/file-saverjs/FileSaver.js'/>"></script>
    <script src="<c:url value='/scripts/bower_components/tableexport.js/dist/js/tableexport.js'/>"></script>
    <div class="bootstrap-custom-flat">
        <div class="breadcrumb">
            <div class="container">
                <axt:breadcrumb breadcrumb="${bcrumb}" breadcrumbTagId="editorBcrumb"></axt:breadcrumb>
                <c:if test="${!param.publicDocument}">
                    <div><img id="publishedStatusImg"
                              src="/images/icons/html.png"
                              alt="Published"
                              title="Published"
                              width="35" height="35"
                              <c:if test="${not isPublished}">hidden</c:if>
                    ><label style="cursor:default;" for="publishedStatusImg" <c:if test="${not isPublished}">hidden</c:if>>Published</label>
                    </div>
                    <script>
                        $(document).ready(function () {
                            setUpEditorBreadcrumbs();
                        });
                    </script>
                </c:if>
                <c:if test="${param.publicDocument}">
                    <script>
                        $(document).ready(function () {
                            makeImageLinksPublic();
                            disableLinkedDocumentsAndLinkedFiles();
                            makeSVGPublic();
                            hideInfoPopups();
                            hidePreviewButtons();
                            hideEditButtons();
                        });
                    </script>
                </c:if>
                <script>
                    $(document).ready(function () {
                        setUpEditorBreadcrumbs();
                    });
                </script>
                <axt:status></axt:status>
            </div>
        </div>
    </div>
    <div id="notebook"></div>
    <axt:signature record="${notebook}"></axt:signature>
    <div id="tempData" style="display: none"></div>
    <input
            type="file"
            class="fromLocalComputer fileReplaceInput"
            style="display:none"
            aria-label="Insert file from local computer"
    />