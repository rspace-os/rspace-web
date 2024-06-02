
var mendeleyDlg = {
	insert: function (ed) {
		var data = {};
		var sel$ = $("input[class='mendeleyChoice']:checked");
		$.each(sel$, function (i, value) {
			var fileid = $(value).attr("data-id");
			var docid = $(value).attr("data-docId");
			var name = $(value).attr("data-fileName");
			var extension = RS.getFileExtension(name);
			var iconPath = RS.getIconPathForExtension(extension);
			var json = {
				id: 'mendeley-' + fileid,
				fileStore: 'Mendeley',
				recordURL: "https://www.mendeley.com/viewer/?fileId=" + fileid + "&documentId=" + docid,
				name: name,
				iconPath: iconPath,
				badgeIconPath: '/images/icons/mendeley.jpg'
			};
			RS.insertTemplateIntoTinyMCE('insertedExternalDocumentTemplate', json, ed);
		});
	},
};
var mendeleyAccessTokenName = "mendeleyAccessToken";
var currAction = "list";
$(document).ready(function () {
	// handle insert
	parent.tinymce.activeEditor.on('mendeley-insert', function () {
		if(parent && parent.tinymce) {	
			mendeleyDlg.insert(parent.tinymce.activeEditor);
		}		
	});

	if (RS.getCookieValueByName(mendeleyAccessTokenName) == '') {
		console.log("no cookie set - starting flow...");

		/* var iframeWidth = Math.min(500, window.innerWidth - 100),
				iframeHeight = Math.min(550, window.innerHeight - 110);  */
		var propertiesJxqr = $.get("/deploymentproperties/ajax/properties", function (properties) {
			var id = properties['mendeley.id'];
			var callback = properties['baseURL'];
			var url = createURL(id, callback);
			/* 	$('#mendeleyAuthIframe').css('width', 400)
							 .css('height', 300)
							 .css('border', '2px solid black');
	 $('#mendeleyAuthIframe').attr('src',url);*/
			$('#mendeley-dialog').show();
			//  var popupWindow = window.open("/connect/mendeley",'Authenticate to Mendeley','scrollbars=1,height=650,width=1050');
			/*    popupWindow.onload = function() {
				 popupWindow.onbeforeunload = function(){
			 console.log("window closed..refreshing....");
				 currAction ="list";
			 listMendeley ("title", "asc");
					}
			 }*/
		}); // end of 
	} else { // we've already got a cookie
		setupPage();
	}

	/*	$(document).on('click', '#authenticate', function (){
			var propertiesJxqr = $.get("/deploymentproperties/ajax/properties", function(properties) {
						var id = properties['mendeley.id'];
						var callback = properties['baseURL'];
						var url = createURL(id, callback);
						$('#mendeley-dialog').show(); 
							var popupWindow = window.open(url,'Authenticate to Mendeley','scrollbars=1,height=650,width=1050');
							  
				 });
		});*/

	$(document).on('click', '#postAuthRefresh', function (e) {

		var token = RS.getCookieValueByName(mendeleyAccessTokenName);
		console.log('token is [' + token + ']');
		if (token.match(/^$/)) {
			$('#mendeleyAuth-error').html("Authentication was not successful - an access token could not be retrieved." +
				"<p/> Please close this dialog and try again.");
			return;
		}
		$('#mendeleyAuth-error').html('');
		$('#mendeley-dialog').hide();
		setupPage();
	})

	$(document).on('click', '#searchMendeley', function (e) {
		e.preventDefault();
		currAction = "search";
		searchMendeley("title");
		return false;
	});
	$(document).on('click', '#listMendeley', function (e) {
		e.preventDefault();
		currAction = "list";
		listMendeley("title", "asc");
		return false;
	});
	$(document).on('click', '.mendeleySort', function (e) {
		e.preventDefault();
		if (currAction === "list") {
			listMendeley($(this).attr('data-orderby'), $(this).attr('data-sortorder'));
			_toggleSortOrder($(this));
		} else if (currAction === "search") {
			searchMendeley($(this).attr('data-orderby'));
		}
		return false;
	});

	$(document).on("keypress", "#mendeleySearchTerm", function (e) {
		var code = (e.keyCode ? e.keyCode : e.which);
		if (code === 13) {
			searchMendeley("title");
		}
	});
	$(document).on('click', '.mendeleyDoc', function (e) {
		e.preventDefault();
		var link$ = $(this);
		var id = $(this).attr("data-id");
		if (link$.parent().hasClass('open')) {
			link$.parent().find('.file').remove();
			link$.parent().removeClass('open');
		} else {
			link$.parent().find('.file').remove();
			MendeleySDK.API.files.list(id).done(function (files) {
				if (files.length === 0) {
					if (link$.closest('.document').find('.file').size() === 0) {
						link$.closest('.document').append("<p class='file'>No files associated with this document</p>");
						link$.parent().addClass('open');
					}
					return;
				}
				var template = $('#filesTemplate').html();
				var html = Mustache.render(template, { files: files });
				link$.parent().append(html);
				link$.parent().addClass('open');
			});
		}
		return false;
	});

	function _toggleSortOrder(orderLink) {
		if (orderLink.attr('data-sortorder') === 'asc') {
			orderLink.attr('data-sortorder', 'desc');
		} else if (orderLink.attr('data-sortorder') === 'desc') {
			orderLink.attr('data-sortorder', 'asc');
		}
	}

	function createURL(id, baseURL) {
		return "https://api.mendeley.com/oauth/authorize?client_id="
			+ id + "&redirect_uri=" + baseURL
			+ "/app/connect/mendeley&scope=all&response_type=token";
	}

	function setupPage() {
		var token = RS.getCookieValueByName(mendeleyAccessTokenName);
		MendeleySDK.API.setAuthFlow({
			getToken: function () {
				return token
			},
			refreshToken: function () {
				return false;
			}
		});
		console.log("cookie set.... " + token);
		$('#listings').show();
		listMendeley("title");
	}

	/* function getAccessTokenCookieOrUrl() {
			 var location = window.location,
					 hash = location.hash ? location.hash.split('=')[1] : '',
					 cookie = getAccessTokenCookie();
	
			 if (hash && !cookie) {
				//   setAccessTokenCookie(hash);
					 return hash;
			 }
			 if (!hash && cookie) {
					 return cookie;
			 }
			 if (hash && cookie) {
					 if (hash !== cookie) {
				//       setAccessTokenCookie(hash);
							 return hash;
					 }
					 return cookie;
			 }
	
			 return '';
	 }*/

	function setAccessTokenCookie(accessToken, expireHours) {
		var d = new Date();
		d.setTime(d.getTime() + ((expireHours || 1) * 60 * 60 * 1000));
		var expires = 'expires=' + d.toUTCString();
		var path = "path=/"
		document.cookie = mendeleyAccessTokenName + '=' + accessToken + '; ' + expires + '; ' + path;
	}

	function searchMendeley(orderBy) {
		var term = $('#mendeleySearchTerm').val();
		$('#docList').html('');
		console.log('searching for term' + term);
		MendeleySDK.API.documents.search({ title: term, sort: orderBy, order: "desc" }).done(function (docs) {
			_renderDocs(docs);
			$('#docList-controls').hide();//sorting doesn't seem enable for search
		}).fail(function (request, response) {
			var msg = 'Failed! - URL:' + request.url + ', Status:' + response.status;
			console.log(msg);
			apprise(msg);
		});
	}

	function listMendeley(orderBy, sortOrder) {
		$('#docList').html('');
		console.log('listing...');
		MendeleySDK.API.documents.list({ "sort": orderBy, "order": sortOrder }).done(function (docs) {
			_renderDocs(docs);
			$('#docList-controls').show();
		}).fail(function (request, response) {
			var msg = 'Failed! - URL:' + request.url + ', Status:' + response.status;
			console.log(msg);
			apprise(msg);
		});
	}

	function _renderDocs(docs) {
		if (docs.length === 0) {
			$('#docList').append("<p>No results found</p>");
			return;
		}
		$.each(docs, function (index, item) {
			item.created = new Date(item.created).toLocaleString();
			item.last_modified = new Date(item.last_modified).toLocaleString();
		})
		var template = $('#docTableTemplate').html();
		var html = Mustache.render(template, { docs: docs });
		$('#docList').append(html);
	}
});

