<html>
<body>
<script type="text/javascript">
  // Remove the inventory JWT token
  window.sessionStorage.removeItem("id_token");
  window.location.href = "${redirectLocation}";
</script>
</body>
</html>
