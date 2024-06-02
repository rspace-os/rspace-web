<%@ include file="/common/taglibs.jsp"%>
<head>
	<link href="<c:url value='/styles/chemSearchResults.css'/>" rel="stylesheet" />
</head>
<!-- Hidden div to store results properties -->
<div id='chemSearchResultsPropertiesHolder' data-totalPageCount="${totalPageCount}" data-totalHitCount="${totalHitCount}"></div>
<c:choose>
	<c:when test="${not empty searchResults}">
		<div>
			<ul class='chemSearchResultsList'>
				<c:forEach items="${searchResults}" var="searchResult">
					<c:set var='recordId' value='${searchResult.recordId}'/>
					<li class='chemSearchResultsListItem'>
						<c:choose>
							<c:when test="${searchResult.record['class'].name eq 'com.researchspace.model.EcatChemistryFile'}">
								<img class='chemSearchResultsImage' src='/images/icons/chemistry-file.png'/>								
							</c:when>
							<c:otherwise>
								<img class='chemSearchResultsImage' src='/chemical/getImageChem/${searchResult.chemId}/${currentTime}'/>								
							</c:otherwise>
							</c:choose>
						<ul class='chemSearchResultsItemDataList'>
							<c:choose>
								<c:when test="${searchResult.record['class'].name eq 'com.researchspace.model.EcatChemistryFile'}">
									<li class='chemSearchResultsName chemSearchResultsItemData'><a href='/gallery/${searchResult.record.parent.id}?term=GL${searchResult.recordId}'>${searchResult.recordName}</a></li>
								</c:when>
								<c:otherwise>
									<li class='chemSearchResultsName chemSearchResultsItemData'><a href='/workspace/editor/structuredDocument/${searchResult.recordId}'>${searchResult.recordName}</a></li>
								</c:otherwise>
							</c:choose>
							<li class='chemSearchResultsItemData'>Owner: ${searchResult.record.owner.username}</li>
							<li class='chemSearchResultsItemData'>Last modified: <fmt:formatDate value='${searchResult.record.modificationDateAsDate}'/></li>
							<li class='chemSearchResultsItemData'>
								<c:choose>
								<c:when test="${searchResult.record['class'].name eq 'com.researchspace.model.EcatChemistryFile'}">
									<axt:breadcrumb breadcrumb="${breadcrumbMap[recordId]}" breadcrumbTagId="chemSearchBcrumb_${searchResult.recordId}"></axt:breadcrumb>					
								</c:when>
								<c:otherwise>
									<axt:breadcrumb breadcrumb="${breadcrumbMap[recordId]}" breadcrumbTagId="chemSearchBcrumb_${searchResult.chemId}"></axt:breadcrumb>
								</c:otherwise>
							</c:choose>
							</li>
						</ul>
					</li>
				</c:forEach>
			</ul>
		</div>
<%-- 		<div>Results ${startHit}-${endHit} of ${totalHitCount}</div> --%>
	</c:when>
	<c:otherwise>
		No results found.
	</c:otherwise>
</c:choose>
