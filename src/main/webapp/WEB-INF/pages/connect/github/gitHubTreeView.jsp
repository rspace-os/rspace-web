<%@ include file="/common/taglibs.jsp"%>

<ul class="jqueryFileTree" style="display: none;">
	<c:if test="${not empty error}">
		<div><c:out value="${error}"></c:out></div>
	</c:if>
	<c:forEach items="${treeNodes}" var="node">
		  <li class=${node.folder ? "directory collapsed" : "file"}>
            <a href="#" data-name="${node.path}" rel="${node.repository}#${node.sha}#${node.fullPath}/" >${node.path}</a>
          </li>
	</c:forEach>
</ul>
