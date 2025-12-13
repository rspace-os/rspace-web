<%@ include file="/common/taglibs.jsp"%>

<head>
  <title>
    <fmt:message key="groups.edit.title" />
  </title>
  <meta name="heading" content="<fmt:message key='groups.heading'/>" />
  <meta name="menu" content="MainMenu" />
  <meta charset="UTF-8">
  <link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
  <style>
    .error {
      color: red;
    }
  </style>
  <script type="text/javascript">
    $(document).ready(function () {
      $('.help').click(function (e) {
        e.preventDefault();
        $('#helptext').toggle();
      });
    });
  </script>
</head>
<div id="topSection" class="bootstrap-custom-flat">
  <jsp:include page="/WEB-INF/pages/system/topBar.jsp"></jsp:include>
</div>
<%-- <shiro:hasAnyRoles name="ROLE_ADMIN,ROLE_SYSADMIN">
  <div class="breadcrumb" style="padding:6px;">
    <c:url value='/groups/admin/?new' var="newgroup"/>
    <a id="createGroupButton" href="${newgroup}"><spring:message code="system.createGroup.button.label"/></a>
    <a id="removeGroup" class="groupcrudops" href="#">Remove group</a>
  </div>
  </shiro:hasAnyRoles> --%>
<br>
<h2>
  <fmt:message key="groups.message" />
</h2>

<div id="newLabGroup"></div>

<!-- React Scripts -->
<script src="<c:url value='/ui/dist/newLabGroup.js'/>"></script>
<!--End React Scripts -->
