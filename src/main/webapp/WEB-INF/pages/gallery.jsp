<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>

<%-- 
  This JSP is for the Gallery page and includes stylesheets etc for the Gallery page.
  All Gallery functionality is provided by mediaGallery.jsp which is also used in  
  document editor "Insert from Gallery" dialog.
 --%>
<head>

    <title><fmt:message key="gallery.title"/></title>
    <meta name="heading" content="<fmt:message key='gallery.heading'/>"/>
    <meta name="menu" content="MainMenu"/>
    
    <link rel="stylesheet" href="<c:url value='/styles/mediaGallery.css'/>" />
	<link rel="stylesheet" href="<c:url value='/styles/gallery.css'/>" />
	<link rel="stylesheet" href="<c:url value='/styles/rs-widgets.css'/>" />
	
    <script src="<c:url value='/scripts/pages/gallery/gallery.js'/>" ></script>
    <script src="<c:url value='/scripts/tags/shareDlg.js'/>"></script>

    <script>
        const urlParams = new URLSearchParams(window.location.search);
        var galleryMediaTypeFromUrlParameter = urlParams.get('mediaType');
        var galleryCurrentFolderIdFromUrlParameter = '${currentFolderId}';
        var galleryOptionalSearchTermFromUrlParameter = "";
        <c:if test="${not empty term}">
            galleryOptionalSearchTermFromUrlParameter = '${term}';
        </c:if>
    </script>

</head>

<div id="mediaGallery">
	<jsp:include page="mediaGallery.jsp" />
	<axt:shareDlg shareDlgGroups="${groups}" shareDlgUsers="${uniqueUsers}"/>
</div>

<jsp:include page="workspace/editor/include/photoswipe.jsp" />

<jsp:include page="recordInfoPanel.jsp" />

<axt:export/>

<!-- React Scripts -->
<div id="exportModal" style="display: inline-block;"></div>
<script src="<c:url value='/ui/dist/exportModal.js'/>"></script>
<!--End React Scripts -->
