<%@ include file="/common/taglibs.jsp"%>

<!-- jsp fragment included in header.jsp if RSpace instance has Egynte as back-end filestore  -->

<script>

    var egnyteFilestoreEnabled = true;

    var _sessionScopeExtConnectionOk = "${sessionScope.extFilestoreConnectionOK}";
    var egnyteFilestoreConnectionOK = _sessionScopeExtConnectionOk === 'true';

    function showEgnyteNotConfiguredToast() {
        $().toastmessage('showToast', {
          text: 'Connection to your Egnyte account is not configured correctly, please fix it on' 
                 + '<a href="/egnyte/egnyteConnectionSetup">connection setup page</a>',
          sticky: true,
          type: 'warning'
        });
        $('.toast-position-top-right').css('top', '35px');
        $('.toast-type-warning').css('padding-top', '35px');
        $('.toast-item-close').css('background-image', 'url(/images/close.gif)');
      };
    
    $(document).ready(function() {

        if (!egnyteFilestoreConnectionOK && (typeof isEgnyteConnectionSetupPage === "undefined")) {
            showEgnyteNotConfiguredToast();
        }
        
    });

</script>

