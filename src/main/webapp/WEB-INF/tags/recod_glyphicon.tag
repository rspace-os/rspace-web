<%-- 
	http://getbootstrap.com/components/#glyphicons
	produces an glyphicon the correct icon based on the record that has been passed into the tag
--%>
<%@ attribute name="record" required="true"
	type="com.researchspace.model.record.BaseRecord"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<c:set var="imgWidth" value="32" scope="page" />
<c:set var="imgHeight" value="32" scope="page" />
<c:set var="type" value="${record.type}" scope="page" />

<c:choose>

	<c:when test="${type eq 'FOLDER' or type eq 'FOLDER:SYSTEM'
		or type eq 'FOLDER:INDIVIDUAL_SHARED_FOLDER_ROOT'
		or type eq 'FOLDER:SHARED_GROUP_FOLDER_ROOT'
		or type eq 'FOLDER:SHARED_FOLDER'}">
         <spring:message code="record.icon.folder" var="alt" scope="page"/>
         <c:set var="src" value="/images/icons/folder.png" scope="page" />
    </c:when>

   	<c:when test="${fn:contains(type, 'ROOT')}">
         <spring:message code="record.icon.folder" var="alt" scope="page"/>
         <c:set var="src" value="/images/icons/folder-user.png" scope="page" />
    </c:when>

    <c:when test="${type eq 'NOTEBOOK'}">
        <spring:message code="record.icon.notebook" var="alt" scope="page"/>
        <c:set var="src" value="/images/icons/notebook.png" scope="page" />
    </c:when>

    <c:when test="${type eq 'NORMAL' or type eq 'NORMAL:NORMAL_EXAMPLE'}">
  	   	<spring:message code="record.icon.structuredDocument" var="alt" scope="page"/>
		<c:set var="src" value="/image/getIconImage/${record.iconId}" scope="page" />
    </c:when>

    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatImage'}">
        <spring:message code="record.icon.image" var="alt" scope="page"/>
        <c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
    </c:when>

    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatAudio'}">
        <spring:message code="record.icon.image" var="alt" scope="page"/>
        <c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
    </c:when>

    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatVideo'}">
        <spring:message code="record.icon.image" var="alt" scope="page"/>
        <c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
    </c:when>

    <c:when test="${record['class'].name eq 'com.researchspace.model.EcatDocumentFile'}">
        <c:choose>
            <c:when test="${record.documentType eq 'Miscellaneous'}">
            	<spring:message code="record.icon.image" var="alt" scope="page"/>
          		<c:set var="src" value="/images/icons/unknownDocument.png" scope="page" />
            </c:when>
            <c:otherwise>
          		<spring:message code="record.icon.image" var="alt" scope="page"/>
          		<c:set var="src" value="/images/icons/${record.extension}.png" scope="page" />
            </c:otherwise>
        </c:choose>
    </c:when>

    <c:when test="${record['class'].name eq 'com.researchspace.model.record.Snippet'}">
        <spring:message code="record.icon.snippet" var="alt" scope="page"/>
        <c:set var="src" value="/images/icons/leftIconSnippets.png" scope="page" />
        <c:set var="imgWidth" value="28" scope="page" />
        <c:set var="imgHeight" value="31" scope="page" />
    </c:when>

    <c:otherwise>
        <spring:message code="record.icon.unknownFormat" arguments="${record['class']}" var="alt" scope="page"/>
        <c:set var="src" value="/images/icons/chm.png" scope="page" />
    </c:otherwise>

</c:choose>

<c:choose>
    <c:when test="${record.notebook}">
  	    <%-- This opens the notebook directly in Notebook view --%>
		<a data-id="${record.id}" class="notebook" href="<c:url value='/notebookEditor/${record.id}?settingsKey=${settingsKey}'/>">
			 <img src="<c:url value='${src}'/>" alt="${alt}" height="${imgHeight}" width="${imgWidth}" data-type="${type}"/>
		</a>
    </c:when>
    <c:otherwise>
        <img src="<c:url value='${src}'/>" alt="${alt}" height="${imgHeight}" width="${imgWidth}" data-type="${type}"/>
    </c:otherwise>
</c:choose>
