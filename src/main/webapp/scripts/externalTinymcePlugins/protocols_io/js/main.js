$(document).ready(function () {
	var Protocols_IOBaseUrl = 'https://www.protocols.io/api/v3';
	var Protocols_IOGet = Protocols_IOBaseUrl + '/protocols?';
	var Protocols_IO_Key = undefined;

	var term = '';
	var filter = 'user_private';
	var orderBy = 'name';
	var sortOrder = 'asc';
	var currPage = 1;
	var maxPage = 1;
	var page_size = 10;
	var validAccessToken = true;
	var isTinyMCEPlugin = typeof parent.tinymce != 'undefined';

	var protocols_IODlg = {
		insert: function (ed) {
			var sel$ = $("input[class='protocols_ioChoice']:checked");
			if (sel$.size() === 0) {
				showError("Please select one or more protocols to import");
				return;
			}
			// Show loader and disable button not to import twice
			showLoader();
			toggleImportDisabled(true);

			var total = sel$.size();
			var count = 0;
			var pio_requests = [];
			var pio_results = [];
			$.each(sel$, function (i, value) {//iterate over each selection
				var id = $(this).attr("data-id");

				var getUrl = Protocols_IOBaseUrl + '/protocols/' + id;

				pio_requests.push($.ajax({
					url: getUrl,
					headers: { 'Authorization': Protocols_IO_Key },
					success: function (response) {
						pio_results.push(response.protocol);
					},
					error: function (response) {
						showError('Could not retrieve protocol  ' + id + ' from Protocols.io')
					}
				}));
			});
			//wait for all pio_Requests to come back
			$.when.apply($, pio_requests).done(function (resp) {
				// submit all protocols
        const targetFolderId = window.parent.document.querySelector("#protocolsIoChooserDlgIframe").dataset.parentid;
				$.ajax({
					url: `/importer/generic/protocols_io/${targetFolderId}`, dataType: 'json',
					"data": JSON.stringify(pio_results), type: "POST", contentType: "application/json;"
				})
					.done(function (response) {
						if (isTinyMCEPlugin) {
							//get list of recordInformation back and importFolder Id.
							$.each(response.data.results, function (i, doc) {
								//RA  would be better if this returned a Promise?
								RS.tinymceInsertInternalLink(
									doc.id, 'SD' + doc.id, doc.name, ed, function () {
										count++; // only close the dialog once all links have been made.
										if (count == total) {
											hideLoader();
											ed.windowManager.close();
										}
									});
							});
						} else {
						  RS.trackEvent("user:import:from_protocols_io:workspace");
							var importFolderId = response.data.importFolderId;
							window.parent.RS.confirmAndNavigateTo("All documents imported, reloading page...",
								'success', 3000, window.parent.createURL('/workspace/' + importFolderId));
						}
					})
			});
		},
	};

	if (isTinyMCEPlugin) {
		Protocols_IO_Key = parent.tinymce.activeEditor.settings.protocols_io_access_token;
		setupPage();
	} else {
		$.get('/integration/integrationInfo?name=PROTOCOLS_IO').done(function (integrationInfo) {
			Protocols_IO_Key = getProtocolsIoAccessToken(integrationInfo.data);
			setupPage();
		});
	}

	function resetVariables() {
		term = '';
		orderBy = 'name';
		sortOrder = 'asc';
		currPage = 1;
		maxPage = 1;
	}

	function setupPage() {
		if (Protocols_IO_Key && Protocols_IO_Key.trim().length > 0 && validAccessToken) {
			toggleImportDisabled(false);
			$('#protocols_ioListings').show();
			$('#protocols_noAccessTokenWarning').hide();
			searchProtocols_IO();
		} else {
			toggleImportDisabled(true);
			$('#protocols_ioListings').hide();
			$('#protocols_noAccessTokenWarning').show();
		}
	}

	function showLoader() {
		$('.protocols_ioDocument').fadeOut('fast');
		$('hr.col-xs-12').fadeOut('fast');
		$('#protocols_ioListings').append("<span class='ouro ouro3'><span class='left'><span class='anim'></span></span> \
    																	<span class='right'><span class='anim'></span></span> \
  																		</span>");
	}

	function hideLoader() {
		$('#protocols_ioListings').remove('.ouro');
	}

	// Search with the term specified
	$(document).on('click', '#protocols_ioSearch', function (e) {
		e.preventDefault();
		resetVariables();
		term = $('#protocols_ioSearchTerm').val();
		searchProtocols_IO();
	});

	$(document).on('click', '#searchProtocols_refresh', function (e) {
		e.preventDefault();
		$.get('/integration/integrationInfo?name=PROTOCOLS_IO').done(function (integrationInfo) {
			Protocols_IO_Key = getProtocolsIoAccessToken(integrationInfo.data);
			validAccessToken = true;
			setupPage();
		});
	});

	$(document).on('change', '.protocols_ioFilter', function (e) {
		filter = $('input[name=filter]:checked').val();
		searchProtocols_IO();
	});

	$(".target").change(function () {
		alert("Handler for .change() called.");
	});

	// Reset button
	$(document).on('click', '#protocols_ioList', function (e) {
		$('#protocols_ioSearchTerm').val('');
		resetVariables();

		searchProtocols_IO();
	});

	// Go to next page
	$('#prevPage').on('click', function (e) {
		e.preventDefault();
		if (currPage > 1) currPage--;

		searchProtocols_IO();
	});

	// Go to prev page
	$('#nextPage').on('click', function (e) {
		e.preventDefault();
		if (currPage < maxPage) currPage++;

		searchProtocols_IO();
	});

	// Reorder results
	$('.protocols_ioSort').on('click', function () {
		var target = $(this).data('orderby');
		// check if we have to reorder(a new way of ordering is selected) or sort(the same ordering is selected)
		if (orderBy == target) sortOrder = (sortOrder == 'asc' ? 'desc' : 'asc'); // sort
		else { orderBy = target; sortOrder = 'asc'; }           // reorder

		// Go to first page
		currPage = 1;

		searchProtocols_IO();
	});

	// handle submit
	if (isTinyMCEPlugin) {
		parent.tinymce.activeEditor.on('protocols-import', function () {
            if (parent && parent.tinymce) {
                protocols_IODlg.insert(parent.tinymce.activeEditor);
            }
		});
	} else {
		window.parent.$('.protocolsImportButton').on('click', function() {
			protocols_IODlg.insert(null);
		});
	}

	function showError(errorMessage) {
		if (isTinyMCEPlugin) {
			tinymceDialogUtils.showErrorAlert(errorMessage);
		} else {
			window.parent.$().toastmessage('showErrorToast', errorMessage);
		}
	}

	function searchProtocols_IO() {
		// make sure some protocols are loaded
		if (term === '') {
			term = 'e';
		}
        //Protocols.io API now adds +1 onto page requested - so requesting 'page_id=1' will request page 2.
		var url = Protocols_IOGet + 'filter=' + filter + '&key=' + term + '&order_field=' + orderBy + '&order_dir=' + sortOrder + '&page_id=' + (currPage-1) + '&page_size=' + page_size;
		showLoader();

		$.ajax({
			url: url,
			headers: { 'Authorization': Protocols_IO_Key }
		})
			.done(function (response) {
				if (console && console.log) console.log("Sample of data:", response);
				maxPage = response.total_pages;
				_renderDocs(response);
			})
			.error(function (response) {
				showError("Could not retrieve the list of protocols.");
				validAccessToken = false;
				setupPage();
			});
	}

	function _renderDocs(response) {
		$('#protocols_ioListing').html('');

		if (typeof (response.items) === 'undefined' || response.items.length === 0) {
			$('#protocols_ioListing').append("<p>No results found</p>");
			$('.ouro').remove();
			return;
		}

		// format dates
		$.each(response.items, function (index, item) {
			item.published_on = new Date(item.published_on * 1000).toLocaleString();
			item.created_on = new Date(item.created_on * 1000).toLocaleString();
		});

		var template = $('#protocols_ioTableTemplate').html();
		var html = Mustache.render(template, { "response": response, "count": response.items.length });

		// Render results
		$('#protocols_ioListing').append(html);
		prepare_page(response.items.length);
	}

	function prepare_page(result_count) {
		// Remove loader
		$('.ouro').remove();

		// Allow changing pages
		$('.pagination #prevPage').parent().addClass('disabled');
		$('.pagination #nextPage').parent().addClass('disabled');
		$('.pagination #currPage').html(currPage);
		if (currPage > 0) $('.pagination #prevPage').parent().removeClass('disabled');
		if (currPage < maxPage) $('.pagination #nextPage').parent().removeClass('disabled');

		//Which results are showing?
		$('.result-range').html((currPage - 1) * page_size + '-' + (currPage == maxPage ? (currPage - 1) * page_size + result_count % page_size : currPage * page_size));
	}

	function getProtocolsIoAccessToken(integration) {
		var accessToken = 'Bearer ' + integration.options['ACCESS_TOKEN'];
		return accessToken;
	}

	function toggleImportDisabled(disabled) {
		if(disabled) {
			window.parent.postMessage({mceAction: 'disable'}, '*');
		} else {
			window.parent.postMessage({mceAction: 'enable'}, '*');
		}
	}
});

