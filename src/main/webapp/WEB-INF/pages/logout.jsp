<%@ taglib uri="http://researchspace.com/functions" prefix="f" %>
<html>
<body>
<script nonce="${requestScope.cspNonce}" type="text/javascript">
  // Remove the inventory JWT token
  window.sessionStorage.removeItem("id_token");
  window.location.href = "${f:escapeJavaScript(redirectLocation)}";
</script>
</body>
</html>
