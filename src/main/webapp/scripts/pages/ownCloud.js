/**
 * Helper function that maps ownCloud structure response to FancyTree nodes
 * @param data Response data from ownCloud list function
 * @returns
 */
function mapOwnCloudDataToFancyTreeNodes(data) {
	return data.map(function(x, index) {
		// Make a nice display name from our path
		var displayName = x.name;
		if (displayName.endsWith("/")) {
			displayName = displayName.slice(0, -1);
		}
		displayName = displayName.substring(displayName.lastIndexOf("/") + 1)
		
		var extension = RS.getFileExtension(displayName);
		var iconPath = RS.getIconPathForExtension(extension);
		
		var isFolder = x.type == "dir"
			
		// Return a node format that FancyTree expects
		return {
			title: displayName, 
			path: x.name, 
			key: (index + 1).toString(), 
			lazy: isFolder,
			folder: isFolder,
			icon: (isFolder) ? null : iconPath
		};
	});
}

/**
 * Async function to support lazy loading of ownCloud structure to FancyTree
 * @param path Path of folder in ownCloud, e.g. "/" for base or "/folder1/folder2/document" for a nested structure
 * @returns
 */
async function getOwnCloudData(path) {
	var response = await oc.files.list(path);
	
	return mapOwnCloudDataToFancyTreeNodes(response.filter(file => file.name !== path));
}

/**
 * Show ownCloud login dialog, delegating based on auth type
 * @param success Function to be executed when a valid node is selected and import button on picker pressed
 * @param ownCloudURL Base URL of ownCloud instance, e.g. https://my-owncloud:8083
 * @param ownCloudServerName Descriptive name of ownCloud server
 * @param ownCloudAuthType ownCloud authentication type
 * @returns
 */
function authenticateOwnCloud(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, ownCloudAuthType) {
	if (ownCloudAuthType == 'oauth') {
		authenticateOwnCloudOAuth(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName);
	} else {
		authenticateOwnCloudBasic(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName);
	}
}

/**
 * Show OAuth ownCloud login dialog
 * @param success Function to be executed when a valid node is selected and import button on picker pressed
 * @param ownCloudURL Base URL of ownCloud instance, e.g. https://my-owncloud:8083
 * @param ownCloudServerName Descriptive name of ownCloud server
 * @returns
 */
function authenticateOwnCloudOAuth(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName) {
	oc.setInstance(ownCloudURL);
    
	$.get('/apps/owncloud/accessCredentials', function(response) {
		if (response) {
			// We already have a valid access token
			var ownCloudAccessToken = response["access_token"];
			var ownCloudUsername = response["username"];
			var expireTime = response["expire_time"];
			
			var currentTime = Date.now();
			
			// Check if the token has expired
			if (currentTime > expireTime) {
				// Token is expired, use refresh token to get new tokens
				refreshOwnCloudAccessToken(success, chooseButtonLabel, ownCloudURL, ownCloudServerName);
			} else {
				// Token is still good, use it
				oc.loginOAuth(ownCloudUsername, ownCloudAccessToken)//will currently throw an error, see below
					.then(function() {
						showOwnCloudPicker(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, ownCloudUsername, null, ownCloudAccessToken);
					}, function(error) {
						//the oc library we use calls /capabilities. This is not needed and gets blcoked by CORS even when Nextcloud
						//is configured to allow calls from the RSPace domain. We can just ignore this error for now.
						showOwnCloudPicker(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, ownCloudUsername, null, ownCloudAccessToken);
					});
			}
		} else {
			// We don't have a token stored, ask the user to authenticate			
			getNewOwnCloudToken(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName);
		}
	});	
}

function refreshOwnCloudAccessToken(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName) {
	$.get('/apps/owncloud/refreshToken', function(response) {
		if (response) {
			// We already have a valid access token
			var ownCloudAccessToken = response["access_token"];
			var ownCloudUsername = response["username"];
			
			oc.loginOAuth(ownCloudUsername, ownCloudAccessToken);
				
			showOwnCloudPicker(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, ownCloudUsername, null, ownCloudAccessToken);
		} else {
			// We need a new access token			
			getNewOwnCloudToken(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName);
		}
	});	
}

function getNewOwnCloudToken(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName) {
	$.get("/deploymentproperties/ajax/properties", function(properties) {
		var ownCloudClientId = properties["owncloud.client.id"];
		var ownCloudRedirect = properties["server.urls.prefix"] + "/owncloud/redirect_uri";
		var encodedRedirect = encodeURIComponent(ownCloudRedirect);
		
	    var authURL = ownCloudURL + "/index.php/apps/oauth2/authorize?response_type=code&client_id=" + ownCloudClientId + "&redirect_uri=" + encodedRedirect;
		
		RS.openOauthAuthorizationWindow(authURL, 
		  '/owncloud/redirect_uri', 
		  '#ownCloudAuthorizationSuccess', 
		  function(authWindow) {
			var ownCloudAccessToken = $(authWindow.document.body).find('#ownCloudAccessToken').val();
			var ownCloudUsername = $(authWindow.document.body).find('#ownCloudUsername').val();
			
			oc.loginOAuth(ownCloudUsername, ownCloudAccessToken);
		    
		    showOwnCloudPicker(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, null, null, ownCloudAccessToken);
		  }
		);	
	});
}

/**
 * Show Basic authentication ownCloud login dialog
 * @param success Function to be executed when a valid node is selected and import button on picker pressed
 * @param ownCloudURL Base URL of ownCloud instance, e.g. https://my-owncloud:8083
 * @param ownCloudServerName Descriptive name of ownCloud server
 * @returns
 */
function authenticateOwnCloudBasic(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName) {
	// If we have stored credentials, use those
	var jqxhr = $.get('/apps/owncloud/sessionInfo', function(ownCloudCredentials) {
		var ownCloudUsername = ownCloudCredentials["username"];
		var ownCloudPassword = ownCloudCredentials["password"];
		
		if (ownCloudUsername !== null && ownCloudPassword !== null) {
			// We already have a successful username/password, use them
			oc.setInstance(ownCloudURL);
			oc.login(ownCloudUsername, ownCloudPassword).then(status => {
				showOwnCloudPicker(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, ownCloudUsername, ownCloudPassword);
			}).catch(error => {
		    	console.log(error);
		        apprise('An error occured when logging in to ' + ownCloudServerName + '. Please try again or contact your System Admin.');
		    });
		} else {
			$('#owncloudLoginDialog').prop("title", "Log in to " + ownCloudServerName);
			$("#owncloudPasswordField").val('');
			
			// Listen for changes in credential fields, update login button accordingly
			$("#owncloudLoginDialog").keydown(function (event) {
		        if (event.keyCode == $.ui.keyCode.ENTER) {
		        	if ($("#owncloudUsernameField").val().length && $("#owncloudPasswordField").val().length) {
		        		$(this).dialog("close");
		        		$("#owncloudLoginDialogSubmit").click();
		        	}
		        }
		    });
			
			// Enable/disable login button based on credential fields' state
			$('#owncloudUsernameField, #owncloudPasswordField').bind("propertychange change click keyup input paste", function(event){
		        var credentialsEntered = $("#owncloudUsernameField").val().length > 0 && $("#owncloudPasswordField").val().length > 0;
		        
				if (credentialsEntered) {
					$("#owncloudLoginDialogSubmit").prop("disabled", false).removeClass("ui-state-disabled");
				} else {
					$("#owncloudLoginDialogSubmit").prop("disabled", true).addClass("ui-state-disabled");
				}
		    });
			
			// Show login dialog
			$('#owncloudLoginDialog').dialog({
	        	buttons: {
	        	    Submit: {
	        	    	text: "Log In",
	        	    	id: "owncloudLoginDialogSubmit",
	        	    	disabled: true,
		                click: function() {
		        	    	if ($("#owncloudUsernameField").val() !== "" && $("#owncloudPasswordField").val() !== "") {
		        	    		$(this).dialog("close");        	              	    	
	
		        	    		// Get credentials from the login dialog fields
		        	    		var ownCloudUsername = $("#owncloudUsernameField").val();
		        	    	    var ownCloudPassword = $("#owncloudPasswordField").val();
		        	    	    
		        	    	    // Set up global ownCloud object for configured ownCloud instance
		        	    	    oc.setInstance(ownCloudURL);
		        	    	    
		        	    	    // Log in global ownCloud object using specified credentials
		        	    	    oc.login(ownCloudUsername, ownCloudPassword).then(status => {
		        	    	  	  // Store credentials so the user doesn't have to enter them again this session
		        	    	      $.post("/apps/owncloud/sessionInfo", { username: ownCloudUsername, password: ownCloudPassword } );
		        	    	      
		        	    	  	  showOwnCloudPicker(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, ownCloudUsername, ownCloudPassword);
		        	    	    }).catch(error => {
		        	    	    	console.log(error);
		        	    	        apprise('An error occured when logging in to ' + ownCloudServerName + '. Please try again or contact your System Admin.');
		        	    	    });
		        	    	}
		        	    }
		            },
	        	    Cancel: function() {
	          	      $( this ).dialog( "close" );
	          	    }
	        	  }
	        });
		}
	}).fail(error => {
    	console.log(error);
        apprise('An error occured when logging in to ' + ownCloudServerName + '. Please try again or contact your System Admin.');
    });
}

/**
 * Show ownCloud picker dialog
 * @param success Function to be executed when a valid node is selected and import button pressed
 * @param enableFolders Controls whether folders should be allowed to be imported
 * @param ownCloudURL Base URL of ownCloud instance, e.g. https://my-owncloud:8083
 * @param ownCloudServerName Descriptive name of ownCloud server
 * @param ownCloudUsername User's ownCloud username
 * @param ownCloudPassword User's ownCloud password
 * @param ownCloudAccessToken Access token for OAuth
 * @returns
 */
function showOwnCloudPicker(success, chooseButtonLabel, enableFolders, ownCloudURL, ownCloudServerName, ownCloudUsername, ownCloudPassword, ownCloudAccessToken) {    
	try {
  	  	// Show the descriptive name for the ownCloud server
		$('#owncloudDialog').prop("title", chooseButtonLabel + " from " + ownCloudServerName);
		
		// Add a div for FancyTree to our dialog
		$("#owncloudDialog").append('<div id="owncloudDialogTree" data-source="ajax"></div>');
  
		// Initialize FancyTree with the data for the root of the ownCloud file structure
		$("#owncloudDialogTree").fancytree({
            source: getOwnCloudData("/"),
            selectMode: 2,
            checkbox: true,
            lazyLoad: function(event, data) {
            	// This is executed when a folder node is expanded
            	data.result = getOwnCloudData(data.node.data.path);
            },
            beforeSelect: function(event, data){
                // A node is about to be selected: prevent this for folders:
                if(!data.node.isFolder() || enableFolders) {
                	var selectedNodes = $("#owncloudDialogTree").fancytree('getTree').getSelectedNodes();
                	
                	var importable = !data.node.isSelected() || selectedNodes.length > 1;
                	
                	if (importable) {
        				$("#owncloudDialogImport").prop("disabled", false).removeClass("ui-state-disabled");
        			} else {
        				$("#owncloudDialogImport").prop("disabled", true).addClass("ui-state-disabled");
        			}
                	
                    return true;
                } else {
                	return false;
                }
            }
        });
	
		if (enableFolders) {
			$("#owncloudDialogTree").removeClass("disable-choose-folders");
			$("#owncloudDialogTree").addClass("enable-choose-folders");
		} else {
			$("#owncloudDialogTree").removeClass("enable-choose-folders");
			$("#owncloudDialogTree").addClass("disable-choose-folders");
		}
	
		// Show the ownCloud picker dialog
		$('#owncloudDialog').dialog({
			title:"Choose from Owncloud",
			width: "600px",
			buttons: {
				Cancel: function() {
    	    	  $(this).dialog("close");
          	    },
        	    Import: {
        	    	text: chooseButtonLabel,
        	    	id: "owncloudDialogImport",
        	    	disabled: true,
        	    	click: function() {        	    		
        	    		var tree = $('#owncloudDialogTree').fancytree('getTree');					  
        	    		var selectedNodes = tree.getSelectedNodes();
        	    		
    	    			// Check that we've selected at least one node,
    	    			// then execute the success function we've been passed if so.
        	    		var importable = selectedNodes.length > 0;
        	    		
    	            	if (importable) {
    	    				success(selectedNodes, ownCloudURL, ownCloudServerName, ownCloudUsername, ownCloudPassword, ownCloudAccessToken);            	    	  
    	    			}
    	    			
    	            	$(this).dialog("close");
        	    	}
        	    }        	    
        	  },
        	close: function(event, ui) {
        		$('#owncloudDialog').empty();
        	}
        });
	} catch (error) {
    	console.log(error);
        apprise('An error occured when opening the ' + ownCloudServerName + ' file picker. Please try again or contact your System Admin.');
    }    
}