<%@ include file="/common/taglibs.jsp"%>
<%--ajax-loaded table content for lists of communities --%>

<div id="defaultCommunityListing" style="display:none">
    <form id="addGroupsForm">
        <div class="topGreyBar">
            Please choose unassigned groups to add to this community
            <a id="addGroupSubmit" class="systemButton systemGoButton" href="#">Go</a> 
            <a id="addGroupCancel" class="cancel systemButton systemCancelButton" href="#">Cancel</a>
        </div>
        <div class="communityViewInnerList">
            <c:if test="${community.id ne -1}"> <%-- i.e. not a default 'All Groups' community --%>
                <c:choose>
                    <c:when test="${empty defaultCommunity.labGroups}">
                        <spring:message code="community.defaultCommunity.nolabGroups.msg" />
                    </c:when>
                    <c:otherwise>
                        <table>
                            <c:forEach items="${defaultCommunity.labGroups}" var="group">
                                <tr>
                                    <td><input type="checkbox" data-groupid="${group.id}" class="groupcbox" /></td>
                                    <td class="name"><a href="<c:url value='/groups/view/${group.id}'/>">${group.displayName}</a></td>
                                </tr>
                            </c:forEach>
                        </table>
                    </c:otherwise>
                </c:choose>
            </c:if>
        </div>
        <input type="hidden" name="from" value="-1"/> 
        <input type="hidden" name="to" value="${community.id}" />
        <input type="hidden" name="ids" id="addGroupIds" value="" />
    </form>
</div>
