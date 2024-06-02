<%-- 
	produces an image the correct icon based on the record that has been passed into the tag
--%>
<%@ attribute name="record" required="true"
	type="com.researchspace.model.record.BaseRecord"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<c:set var="imgWidth" value="32" scope="page" />
<c:set var="imgHeight" value="32" scope="page" />
<c:set var="type" value="${record.type}" scope="page" />

<c:choose>

    <c:when test="${type eq 'FOLDER:SYSTEM' and record.name eq 'Shared'}">
         <c:set var="alt" value="Shared Folder" scope="page" />
         <c:set var="src" value="/images/icons/folder-shared.png" scope="page" />
         <c:set var="href" value="/workspace/${record.id}" scope="page" />
    </c:when>

    <c:when test="${type eq 'FOLDER:SYSTEM:TEMPLATE'}">
         <c:set var="alt" value="Templates Folder" scope="page" />
         <c:set var="src" value="/images/icons/folder-templates.png" scope="page" />
         <c:set var="href" value="/workspace/${record.id}" scope="page" />
    </c:when>

    <c:when test="${type eq 'FOLDER:SYSTEM:API_INBOX'}">
         <c:set var="alt" value="Shared Folder" scope="page" />
         <c:set var="src" value="/images/icons/folder-api-inbox.png" scope="page" />
         <c:set var="href" value="/workspace/${record.id}" scope="page" />
    </c:when>

	<c:when test="${type eq 'FOLDER' or type eq 'FOLDER:SYSTEM' 
		or type eq 'FOLDER:INDIVIDUAL_SHARED_FOLDER_ROOT' 
		or type eq 'FOLDER:SHARED_GROUP_FOLDER_ROOT' 
		or type eq 'FOLDER:SHARED_FOLDER'
		or type eq 'FOLDER:SYSTEM:IMPORTS'}">
         <c:set var="alt" value="Folder" scope="page" />
         <c:set var="src" value="/images/icons/folder.png" scope="page" />
         <c:set var="href" value="/workspace/${record.id}" scope="page" />
    </c:when>
    
   	<c:when test="${fn:contains(type, 'ROOT')}">
         <c:set var="alt" value="User Folder" scope="page" />
         <c:set var="src" value="/images/icons/folder-user.png" scope="page" />
         <c:set var="href" value="/workspace/${record.id}" scope="page" />
    </c:when>
    
    <c:when test="${type eq 'NOTEBOOK'}">
        <c:set var="alt" value="Notebook" scope="page" />
        <c:set var="src" value="/images/icons/notebook.png" scope="page" />
    </c:when>
   
    <c:when test="${type eq 'NORMAL:TEMPLATE'}">
        <c:set var="alt" value="Template (Form Name: '${record.asStrucDoc().formName}', ID: ${record.asStrucDoc().form.oid})" scope="page" />
        <c:set var="src" value="/image/getIconImage/${record.iconId}" scope="page" />
        <c:set var="href" value="/workspace/editor/structuredDocument/${record.id}?settingsKey=${settingsKey}" scope="page" />
    </c:when>
    
    <c:when test="${type eq 'NORMAL' or type eq 'NORMAL:NORMAL_EXAMPLE'}">
  	   	<c:set var="alt" value="Structured Document (Form Name: '${record.asStrucDoc().formName}', ID: ${record.asStrucDoc().form.oid})" scope="page" />
		<c:set var="src" value="/image/getIconImage/${record.iconId}" scope="page" />
		 <c:set var="href" value="/workspace/editor/structuredDocument/${record.id}?settingsKey=${settingsKey}" scope="page" />
    </c:when>
   
    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatImage'}">
        <c:set var="alt" value="Image" scope="page" />
        <c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
         <c:set var="href" value="/gallery/${record.parent.id}?term=${record.globalIdentifier}" scope="page" />
    </c:when>

    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatAudio'}">
        <c:set var="alt" value="Audio" scope="page" />
        <c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
          <c:set var="href" value="/gallery/${record.parent.id}?term=${record.globalIdentifier}" scope="page" />
    </c:when>
    
    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatVideo'}">
        <c:set var="alt" value="Video" scope="page" />
        <c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
          <c:set var="href" value="/gallery/${record.parent.id}?term=${record.globalIdentifier}" scope="page" />
    </c:when>

    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatChemistryFile'}">
        <c:set var="alt" value="Chemistry" scope="page" />
        <c:set var="src" value="/images/icons/leftIconChem.png" scope="page" />
        <c:set var="href" value="/gallery/${record.parent.id}?term=${record.globalIdentifier}" scope="page" />
    </c:when>
    
    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatDocumentFile'}">
          <c:set var="href" value="/gallery/${record.parent.id}?term=${record.globalIdentifier}" scope="page" />
        <c:choose>
            <c:when test="${record.documentType eq 'Miscellaneous'}">
            	<c:set var="alt" value="File" scope="page" />
          		<c:set var="src" value="/images/icons/unknownDocument.png" scope="page" />
            </c:when>
            <c:otherwise>
          		<c:set var="alt" value="File" scope="page" />
          		<c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
            </c:otherwise>
        </c:choose>
    </c:when>
    
    <c:when test="${record['class'].name eq 'com.researchspace.model.record.Snippet'}">
        <c:set var="alt" value="Snippet" scope="page" />
        <c:set var="src" value="/images/icons/leftIconSnippets.png" scope="page" />
        <c:set var="imgWidth" value="28" scope="page" />
        <c:set var="imgHeight" value="31" scope="page" />
    </c:when>

    <c:otherwise>
        <c:set var="alt" value="Unknown Record ${record['class']}" scope="page" />
        <c:set var="src" value="/images/icons/chm.png" scope="page" />
    </c:otherwise>
    
</c:choose>

<c:choose>
    <c:when test="${record.notebook}">
  	    <%-- This opens the notebook directly in Notebook view --%>
		<a data-id="${record.id}" class="notebook" href="<c:url value='/notebookEditor/${record.id}?settingsKey=${settingsKey}'/>">
			 <img src="<c:url value='${src}'/>" alt="${alt}" title="${alt}" height="${imgHeight}" width="${imgWidth}" data-type="${type}"/>
		</a>
    </c:when>
    <c:otherwise>
    <a data-id="${record.id}" href="${href}">
        <img src="<c:url value='${src}'/>" alt="${alt}" title="${alt}" height="${imgHeight}" width="${imgWidth}" data-type="${type}"/>
     </a>
    </c:otherwise>
</c:choose>