<%@ include file="/common/taglibs.jsp"%>
<c:url var="createFromFormURL" value="/workspace/editor/structuredDocument/create/${parentFolderId}"></c:url>
<axt:createFromFormDlg forms="${forms}"
	createFromFormURL="${createFromFormURL}"
	formsForCreateMenuPagination="${paginationList}">
</axt:createFromFormDlg>