

$(document).ready (function () {
		/* console.log("hash is [" + location.hash +"]");
         var hash = location.hash ? location.hash.split('=')[1] : '';
        if(hash) {
        	console.log("Setting authentication cookie")
        	setAccessTokenCookie(hash, 1);
        	MendeleySDK.API.setAuthFlow({getToken: function (){
        											return hash},
        											refreshToken: function () {
        										        return false; }});
        	$('#onImplicitFlowSuccess').show(); 
       // 	window.close();
        } else {
        	console.log("No hash value of returned URL")
        	$('#onServerFlow').show();
        	$('#onServerFlow').append('Debug information: hash[' + location.hash + '],url [' + location.url +']' );
        }*/
			
	/* $(document).on('click','#implicit', function (e){
		 e.preventDefault();
		 if(RS.getCookieValueByName (mendeleyAccessTokenName) == '') {
				console.log("no cookie set starting flow...");
		var iframeWidth = Math.min(600, window.innerWidth - 100),
        iframeHeight = Math.min(650, window.innerHeight - 110); 
        $('#mendeleyAuthIframe').css('width', iframeWidth);
        $('#mendeleyAuthIframe').css('height', iframeHeight);   
        $('#mendeleyAuthIframe').attr('src', "https://api.mendeley.com/oauth/authorize?client_id=2423&redirect_uri=http://localhost:8080/app/connect/mendeley&scope=all&response_type=token");

        var dialogOptions = {
                title: "Authenticate to Mendeley",
                width: iframeWidth,
                height: iframeHeight + 100,
                buttons: [{
                    text: 'Close',
                    click: function() {
                        $(this).dialog( "close" );  
                      }
                }]
        };     
        $('#mendeleyAuth').dialog(dialogOptions);
		}
	});
    
	  function setAccessTokenCookie(accessToken, expireHours) {
	        var d = new Date();
	        d.setTime(d.getTime() + ((expireHours || 1)*60*60*1000));
	        var expires = 'expires=' + d.toUTCString();
	        var path="path=/"
	        document.cookie = mendeleyAccessTokenName + '=' + accessToken + '; ' + expires + '; ' + path;
	    }*/
});
