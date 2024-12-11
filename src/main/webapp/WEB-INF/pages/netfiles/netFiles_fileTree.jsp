<%@ include file="/common/taglibs.jsp"%>
<c:if test="${PROBLEM_LOADING_FILES}">
	<div id="fileLoadError">
		Problem Loading Files
	</div>
	<div>There was an error responding to this request, which has been logged. <br/> Your reference to report this incident is: <span>${errorId}</span></div>
</c:if>
<c:if test="${addingFileStore && showExtraDirs}">
	<div class="nfsTopLevelElement" style="display:none">
		<div class="nfsTopLevelOperationsRow">
			<div class="nfsShowIfNotHomeDir nfsTopLevelOperationsRowCurrDir">
				<spring:message code="netfilestores.current.path.label"/>:
				<span id="nfsCurrentDirSpan">${nfsTreeNode.nodePath}</span>
			</div>
			<div class="nfsShowIfNotTopDir nfsTopLevelOperationsRowBtn">
				<input type='button' id="nfsOpenParentDir" class="btn btn-default"
					value='<spring:message code="netfilestores.parentDir.button.label"/>' />
			</div>
			<div class="nfsShowIfNotHomeDir nfsTopLevelOperationsRowBtn">
				<input type='button' id="nfsOpenHomeDir" class="btn btn-default"
					value='<spring:message code="netfilestores.homeDir.button.label"/>' />
			</div>
		</div>
	</div>
</c:if>
<ul class="jqueryFileTree">

	<c:if test="${addingFileStore && showCurrentDir}">
			<c:if test='${dir ne ""}'>
				<c:set var="currentFolderPathToSave" value="${nfsTreeNode.nodePath}" />
			</c:if>
		<li class="currentDirNode nfsTopLevelElement" style="display:none">
            <input class="save_userfolder" type="checkbox" data-abspath="${currentFolderPathToSave}"></input>
            <a href="#" class="nfsFolderNode" rel="" style="display:inline; font-style: italic;">
            	<spring:message code="netfilestores.current.directory.label"/></a>
        </li>
	</c:if>

    <c:forEach items="${nfsTreeNode.nodes}" var="node">
         <c:choose>
             <c:when test="${node.isFolder}">
                 <li class="directory collapsed" >
                     <input class="save_userfolder inputCheckbox nfsCheckbox" type="checkbox" data-name="${node.fileName}" 
                        data-path="${node.logicPath}" data-abspath="${node.nodePath}" data-nfsid="${node.nfsId}" data-linktype="directory"></input>
                     <c:set var="dirToOpen" value="${dir}" />
                     <c:if test='${dir ne ""}'>
                       	<c:set var="dirToOpen" value="${dir}/" />
                     </c:if>
                     <a href="#" rel="${dirToOpen}${node.fileName}/" style="display:inline;">
                     	${node.fileName}</a>
                 </li>
             </c:when>
             <c:otherwise>
                 <c:if test="${not addingFileStore}">
	                 <li class="file record" >
	                     <span style="display: block; float:right; margin-right:20px;" >${node.fileDate}</span>
	                     <span style="display: block; float:right; margin-right:80px;" >${node.fileSize}</span>
	                     <input class="inputCheckbox nfsCheckbox" type="checkbox" data-name="${node.fileName}" 
	                         data-path="${node.logicPath}" data-nfsid="${node.nfsId}" data-linktype="file"></input>
	                     <a href="#" rel="${node.logicPath}" data-nfsid="${node.nfsId}" style="display:inline;"
	                         onclick="event.preventDefault(); RS.downloadNetFile($(this).attr('rel'), $(this).data('nfsid'));">
	                         ${node.fileName}</a>
			     <a href="#" style="display:inline;" onclick="$('#avu-${node.nfsId}').toggle();">Meta</a>
			     <div id="avu-${node.nfsId}" style="display: none" escape="false"><pre>${node.fileMetadata}</pre></div>	 
	                 </li>
                 </c:if>
             </c:otherwise>
         </c:choose>
    </c:forEach>
</ul>

<script type="text/javascript">
<!--
    var addingFileStore = false || ${addingFileStore};
    var fileSystemType = "${fileSystemType}";

    // don't show checkboxes when browsing file store tree through Gallery tab
    if (!isGalleryOpenedFromEditorDialog() && !addingFileStore) {
        $('.inputCheckbox.nfsCheckbox').hide();
    }
    if (loadingTopLevelFileTree) {
    	$('.nfsTopLevelElement').show();
    	var topDir = '${nfsTreeNode.nodePath}' === "/";
    	$('.nfsShowIfNotTopDir').toggle(!topDir);
    	var homeDir = '${dir}' === "";
    	$('.nfsShowIfNotHomeDir').toggle(!homeDir);
    	loadingTopLevelFileTree = false;
    }
	
//-->
</script>
