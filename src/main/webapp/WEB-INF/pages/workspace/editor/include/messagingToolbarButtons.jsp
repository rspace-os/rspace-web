<%@ include file="/common/taglibs.jsp"%>
<!-- code for handling display of messaging buttons on document/notebook toolbar !-->

<script nonce="${requestScope.cspNonce}">
  var SLACK = false,
    MSTEAMS = false;
</script>

<c:if test="${not empty extMessaging}">
  <script nonce="${requestScope.cspNonce}"
    src="<rst:assetUrl value='/scripts/pages/messaging/extMessagingCreation.js'/>"
  ></script>
  <c:forEach items="${extMessaging}" var="extMessageTarget">
    <c:if
      test="${not empty extMessageTarget and extMessageTarget.available and extMessageTarget.enabled}"
    >
      <script nonce="${requestScope.cspNonce}">
        if ("${f:escapeJavaScript(extMessageTarget.name)}" == "SLACK") {
          SLACK = true;
        } else if ("${f:escapeJavaScript(extMessageTarget.name)}" == "MSTEAMS") {
          MSTEAMS = true;
        }
      </script>
    </c:if>
  </c:forEach>
</c:if>

<script nonce="${requestScope.cspNonce}" src="<rst:assetUrl value='/scripts/pages/messaging/messageCreation.js'/>"></script>
