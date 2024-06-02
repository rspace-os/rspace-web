<%@ include file="/common/taglibs.jsp"%>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <meta charset="UTF-8">
  <title><spring:message code="system.communityList.button.label"/></title>
  <link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
  <script>
    RS.communityId=${community.id};
    var view =${view};
  </script>
</head>

<div id="topSection">
  <jsp:include page="/WEB-INF/pages/admin/admin.jsp" />
</div>

<p style="visibility:hidden;">spacing</p>

<div id="communityProps">
  <div id="propertyView" >
    <h2>
      <spring:message code="community.name"/>: <span id="displayName" class="editableProperty">${community.displayName}</span>, 
      <spring:message code="table.created.header"/>: <fmt:formatDate type="date" value="${community.creationDate}"/>
    </h2>
    
    <h3><spring:message code="community.profileHeading"/></h3>
    <span id="profileText" class="editableProperty">
      <c:choose>
        <c:when test="${empty community.profileText}">
            <spring:message code="community.noprofile.msg"/>
        </c:when>
        <c:otherwise>
            ${community.profileText}
        </c:otherwise>
      </c:choose>
    </span>
  </div>
</div>
<br /><br />

<div style="width:470px;float:left;">
  <h3> <spring:message code="community.labGroups.header"/></h3>
  <div id="labGroupContainer">
    <div id="labGroupList">
      <div class="topGreyBar"></div>
      <div class="communityViewInnerList">
        <c:choose>
          <c:when test="${empty community.labGroups}">
            <spring:message code="community.nolabGroups.msg"/>
          </c:when>
          <c:otherwise>
            <table>
             <c:forEach items="${community.labGroups}" var="group">
               <tr>
                  <c:choose>
                    <c:when test="${group.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToGroup(group)}">
                      <td colspan="2"> <spring:message code="unknown.group.label" /> </td>
                    </c:when>
                    <c:otherwise>
                      <c:if test="${canEdit}">
                        <td><input class="actionCbox" type="checkbox" id="group_${group.id}"/></td>
                      </c:if>
                      <td class="name"><a href="<c:url value='/groups/view/${group.id}'/>">${group.displayName}</a></td>
                    </c:otherwise>
                  </c:choose>
               </tr>
             </c:forEach>
             </table>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
  </div>
</div>

<div style="width:470px;float:right;">
  <h3><spring:message code="community.admins.header"/></h3>
  <div id="adminContainer">
    <div id="adminsList">
      <div class="topGreyBar"></div>
      <div class=communityViewInnerList">
        <table>
          <c:forEach items="${community.admins}" var="admin">
            <tr>
              <td>
                <c:choose>
                  <c:when test="${admin.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToUser(admin)}">
                      <spring:message code="unknown.user.label" />
                  </c:when>
                  <c:otherwise>
                      <a class="adminLink" href="<c:url value='/userform?userId=${admin.id}'/>">${admin.fullNameAndEmail}</a>
                  </c:otherwise>
                </c:choose>
              </td> 
            <tr>
          </c:forEach>
        </table>
      </div>
    </div>
  </div>
</div>
