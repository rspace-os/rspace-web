<%@ include file="/common/taglibs.jsp"%>
<!-- code for handling display of messaging buttons on document/notebook toolbar !-->

<script>
  var SLACK = false,
    MSTEAMS = false;
</script>

<c:if test="${not empty extMessaging}">
  <script
    src="<c:url value='/scripts/pages/messaging/extMessagingCreation.js'/>"
  ></script>
  <c:forEach items="${extMessaging}" var="extMessageTarget">
    <c:if
      test="${not empty extMessageTarget and extMessageTarget.available and extMessageTarget.enabled}"
    >
      <script>
        if ("${extMessageTarget.name}" == "SLACK") {
          SLACK = true;
        } else if ("${extMessageTarget.name}" == "MSTEAMS") {
          MSTEAMS = true;
        }
      </script>
    </c:if>
  </c:forEach>
</c:if>

<script src="<c:url value='/scripts/pages/messaging/messageCreation.js'/>"></script>
