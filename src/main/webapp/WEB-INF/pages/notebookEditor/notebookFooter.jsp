<%@ include file="/common/taglibs.jsp"%>
<jsp:include page="../documentAndNotebookTemplates.jsp" />
<%@ include file="../workspace/editor/include/signDocumentDialog.jsp"%>
<%@ include file="../workspace/editor/include/witnessDocumentDialog.jsp"%>
<jsp:include page="../workspace/editor/include/photoswipe.jsp" />
<jsp:include page="notebookEditorTemplates.html" />
<!-- Dialog upon Create-Other Documents -->
<axt:formCreateMenuDialog parentFolderId="${selectedNotebookId}"></axt:formCreateMenuDialog>