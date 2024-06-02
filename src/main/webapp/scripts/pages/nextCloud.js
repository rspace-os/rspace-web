// FIXME - using the owncloud html elements (eg owncloudLoginDialog) - should add a new set of nextcloud elements and specific nextcloud.css?? (see below)
//  (this breaks owncloud - ie login to nextcloud then leaves owncloud also trying to login to nextcloud
// Owncloud html is in mediaGallery.jsp - these are linked to the ownCloud.css files and the css is needed for functionality
function mapNextCloudDataToFancyTreeNodes(data) {
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
			path: x.fileInfo['{http://owncloud.org/ns}fileid']?x.name +"__&&__"+x.fileInfo['{http://owncloud.org/ns}fileid']:x.name,
			key: (index + 1).toString(),
			lazy: isFolder,
			folder: isFolder,
			icon: (isFolder) ? null : iconPath
		};
	});
}

/**
 * Async function to support lazy loading of nextCloud structure to FancyTree
 * @param path Path of folder in nextCloud, e.g. "/" for base or "/folder1/folder2/document" for a nested structure
 * @returns
 */
async function getNextCloudData(path) {
	try {
		var response = await nextcloud.files.list(path);

		return mapNextCloudDataToFancyTreeNodes(response.filter(file => file.name !== path));
	} catch (error) {
		console.log(error);
		throw new Error("Please try to disconnect Nextcloud on the Apps page, then reconnect");
	}
}

/**
 * Show nextCloud login dialog, delegating based on auth type
 * @param success Function to be executed when a valid node is selected and import button on picker pressed
 * @param nextCloudURL Base URL of nextCloud instance, e.g. https://my-nextcloud:8083
 * @param nextCloudServerName Descriptive name of nextCloud server
 * @param nextCloudAuthType nextCloud authentication type
 * @returns
 */
function authenticateNextCloud(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudAuthType) {
	if (nextCloudAuthType == 'oauth') {
		authenticateNextCloudOAuth(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName);
	} 
	else {
		authenticateNextCloudBasic(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName);
	}
}

/**
 * Show OAuth nextCloud login dialog
 * @param success Function to be executed when a valid node is selected and import button on picker pressed
 * @param nextCloudURL Base URL of nextCloud instance, e.g. https://my-nextcloud:8083
 * @param nextCloudServerName Descriptive name of nextCloud server
 * @returns
 */
function authenticateNextCloudOAuth(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName) {
	nextcloud.setInstance(nextCloudURL);
    
	$.get('/apps/nextcloud/accessCredentials', function(response) {
		if (response) {
			// We already have a valid access token
			var nextCloudAccessToken = response["access_token"];
			var nextCloudUsername = response["username"];
			var expireTime = response["expire_time"];
			
			var currentTime = Date.now();
			
			// Check if the token has expired
			if (currentTime > expireTime) {
				// Token is expired, use refresh token to get new tokens
				refreshAccessToken(success, chooseButtonLabel, nextCloudURL, nextCloudURL, nextCloudServerName);
			} else {
				loginAndDisplayAccessPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudAccessToken);
			}
		} else {
			// We don't have a token stored, ask the user to authenticate			
			getNewToken(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName);
		}
	});	
}
const loginAndDisplayAccessPicker = (success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudAccessToken ) => {
	nextcloud.loginOAuth(nextCloudUsername, nextCloudAccessToken)//will currently throw an error, see below
		.then(function() {
			showNextCloudPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, null, nextCloudAccessToken);
		}, function(error) {
			console.error(error);
			//the oc library we use calls /capabilities. This is not needed and gets blocked by CORS even when Nextcloud
			//is configured to allow calls from the RSPace domain. We can just ignore this error for now.
			showNextCloudPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, null, nextCloudAccessToken);
		});
}

function refreshAccessToken(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName) {
	$.get('/apps/nextcloud/refreshToken', function(response) {
		if (response) {
			// We already have a valid access token
			var nextCloudAccessToken = response["access_token"];
			var nextCloudUsername = response["username"];
			loginAndDisplayAccessPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudAccessToken);
		} else {
			// We need a new access token			
			getNewToken(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName);
		}
	});	
}

function getNewToken(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName) {
	$.get("/deploymentproperties/ajax/properties", function(properties) {
		var nextCloudClientId = properties["nextcloud.client.id"];
		var nextCloudRedirect = properties["server.urls.prefix"] + "/nextcloud/redirect_uri";
		var encodedRedirect = encodeURIComponent(nextCloudRedirect);
		
	    var authURL = nextCloudURL + "/index.php/apps/oauth2/authorize?response_type=code&client_id=" + nextCloudClientId + "&redirect_uri=" + encodedRedirect;
		
		RS.openOauthAuthorizationWindow(authURL, 
		  '/nextcloud/redirect_uri', 
		  '#nextCloudAuthorizationSuccess', 
		  function(authWindow) {
			var nextCloudAccessToken = $(authWindow.document.body).find('#nextCloudAccessToken').val();
			var nextCloudUsername = $(authWindow.document.body).find('#nextCloudUsername').val();
			loginAndDisplayAccessPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudAccessToken);
		  }
		);	
	});
}

/**
 * Show Basic authentication nextCloud login dialog
 * @param success Function to be executed when a valid node is selected and import button on picker pressed
 * @param nextCloudURL Base URL of nextCloud instance, e.g. https://my-nextcloud:8083
 * @param nextCloudServerName Descriptive name of nextCloud server
 * @returns
 */
function authenticateNextCloudBasic(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName) {
	// If we have stored credentials, use those
	var jqxhr = $.get('/apps/nextcloud/sessionInfo', function(nextCloudCredentials) {
		var nextCloudUsername = nextCloudCredentials["username"];
		var nextCloudPassword = nextCloudCredentials["password"];
		
		if (nextCloudUsername !== null && nextCloudPassword !== null) {
			// We already have a successful username/password, use them
			nextcloud.setInstance(nextCloudURL);
			nextcloud.login(nextCloudUsername, nextCloudPassword).then(status => {
				showNextCloudPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudPassword);
			}).catch(error => {
		    	console.log(error);
		        apprise('An error occured when logging in to ' + nextCloudServerName + '. Please try again or contact your System Admin.');
		    });
		} else {
			$('#owncloudLoginDialog').prop("title", "Log in to " + nextCloudServerName);
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
		        	    		var nextCloudUsername = $("#owncloudUsernameField").val();
		        	    	    var nextCloudPassword = $("#owncloudPasswordField").val();
		        	    	    
		        	    	    // Set up global nextCloud object for configured nextCloud instance
		        	    	    nextcloud.setInstance(nextCloudURL);
		        	    	    
		        	    	    // Log in global nextCloud object using specified credentials
		        	    	    nextcloud.login(nextCloudUsername, nextCloudPassword).then(status => {
		        	    	  	  // Store credentials so the user doesn't have to enter them again this session
		        	    	      $.post("/apps/nextcloud/sessionInfo", { username: nextCloudUsername, password: nextCloudPassword } );
		        	    	      
		        	    	  	  showNextCloudPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudPassword);
		        	    	    }).catch(error => {
		        	    	    	console.log(error);
		        	    	        apprise('An error occured when logging in to ' + nextCloudServerName + '. Please try again or contact your System Admin.');
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
        apprise('An error occured when logging in to ' + nextCloudServerName + '. Please try again or contact your System Admin.');
    });
}

/**
 * Show nextCloud picker dialog
 * @param success Function to be executed when a valid node is selected and import button pressed
 * @param enableFolders Controls whether folders should be allowed to be imported
 * @param nextCloudURL Base URL of nextCloud instance, e.g. https://my-nextcloud:8083
 * @param nextCloudServerName Descriptive name of nextCloud server
 * @param nextCloudUsername User's nextCloud username
 * @param nextCloudPassword User's nextCloud password
 * @param nextCloudAccessToken Access token for OAuth
 * @returns
 */
function showNextCloudPicker(success, chooseButtonLabel, enableFolders, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudPassword, nextCloudAccessToken) {    
	try {
  	  	// Show the descriptive name for the nextCloud server
		$('#owncloudDialog').prop("title", chooseButtonLabel + " from " + nextCloudServerName);

		// Add a div for FancyTree to our dialog
		$("#owncloudDialog").append('<div id="owncloudDialogTree" data-source="ajax"></div>');
  
		// Initialize FancyTree with the data for the root of the nextCloud file structure
		$("#owncloudDialogTree").fancytree({
            source: getNextCloudData("/"),
            selectMode: 2,
            checkbox: true,
            lazyLoad: function(event, data) {
            	// This is executed when a folder node is expanded
            	data.result = getNextCloudData(data.node.data.path);
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
	
		// Show the nextCloud picker dialog
		$('#owncloudDialog').dialog({
			title:"Choose from Nextcloud",
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
    	    				success(selectedNodes, nextCloudURL, nextCloudServerName, nextCloudUsername, nextCloudPassword, nextCloudAccessToken);            	    	  
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
		throw new Error("Please try to disconnect Nextcloud on the Apps page, then reconnect");
    }
}