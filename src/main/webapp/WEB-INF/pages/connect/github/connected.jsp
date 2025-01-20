<%@ include file="/common/taglibs.jsp"%>
<head>
	<title>Connected</title>
</head>

<div id="gitHubAuthorizationSuccess" class="bootstrap-custom-flat">
   Success -  RSpace was authorized to access repositories on GitHub. You can close this tab now.
</div>

<!-- BEGIN GitHub repository chooser dialog -->
<script type="text/template" data-template="rs-app-github-add-repository-apprise">
  <div class="bootstrap-namespace" style="margin: 0px !important">
    <div style="margin-bottom: 20px;">
      Save selected GitHub repositories in RSpace
    </div>

	<div style="overflow-y:scroll;height:300px !important">
        <c:choose>
            <c:when test="${empty gitHubRepositories}">
                <h3>The connected account has no repositories.</h3>
            </c:when>
            <c:otherwise>
                <c:forEach items="${gitHubRepositories}" var="repository">
                    <dl class="dl-horizontal">
                        <dt>Repository Name</dt>
                        <dd id="gitHubRepositoryFullName" style="padding-top: 3px;">${repository.fullName}</dd>
                        <dt>Description</dt>
                        <dd id="gitHubRepositoryDescription" style="padding-top: 3px;">${repository.description}</dd>
                        <dt>Add to RSpace</dt>
                        <dd style="padding-top: 3px;"><input class="GitHubRepositoryCheckbox" id="${repository.fullName}" type="checkbox"></dd>
                    </dl>
                </c:forEach>
            </c:otherwise>
        </c:choose>
	</div>

  </div>
</script>
<!-- END GitHub repository chooser dialog -->

<input id="gitHubAccessToken" type="hidden" value="${gitHubAccessToken}">