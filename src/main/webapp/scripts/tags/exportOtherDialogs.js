/* Before opening exportPdf dialog, 2 data attributes need to be supplied to the dialog:
 - 'url' the URL to submit the dialog form info
 - 'initialName' - the initial name of the PDF dialog
*/

/* to be called once after adding export_otherDialogs to the page */
function initAdditionalExportDialogs() {
  	RS.switchToBootstrapButton();
    _setUpPdfDialog();
    _setUpWordDialog();
    _setUpArchiveDialog();
    _setUpPageSizeDefault();
    RS.switchToJQueryUIButton();
}

function _setUpPdfDialog() {
    $('#pdf-config').dialog({
        modal : true,
        autoOpen : false,
        title : "PDF Export Configuration",
        width: 460,
        open: function (event, ui) {
            var initialName = $(this).data('initialExportName');
            $('#pdf_name').val(initialName);
            $('#pagesizeDefaultRow').remove();
            var jqxhr = $.get("/export/ajax/defaultPDFConfig", function (resp) {
                if(resp.data) {
                    $('#pdf_a1size').val(resp.data.pageSize);
                    $('#pdf_a1size').data('defaultVal', resp.data.pageSize);
                } 
            });
            jqxhr.fail(function (e){
                console.log("Couldn't retrieve PDF preferences");
            });
        },
        buttons : {
            Cancel : function() {
                $(this).dialog('close');
            },
            Export : function() {               
                var provenance = $('#pdf_a1proven').is(':checked');
                var comments = $('#pdf_a1cmmnt').is(':checked') ;
                var includeFieldLastModifiedDate = $('#pdf_a1IncludeFieldLastModifiedDate').is(':checked') ;
                var annotations = $('#pdf_a1annotated').is(':checked');
                var restartPageNumberPerDoc = $('#pdf_a1pagenbr').is(':checked') ;
                var pageSize = $('#pdf_a1size').val();
                var dateType = $('#pdf_a1date').val();
                var includeFooter = $('#pdf_a1footer').is(':checked') ;
                var pdfName = $('#pdf_name').val();
                var setPageSizeAsDefault = $('#setPageSizeAsDefault').is(':checked') ;
                if (pageSize === 'UNKNOWN') {
                    apprise("Please choose a page size format");
                    return;
                }

                var exportSelection = $(this).data('exportSelection');
                var exportConfig = {
                    exportFormat : "PDF",
                    exportName : pdfName,
                    provenance : provenance,
                    comments: comments,
                    annotations: annotations,
                    restartPageNumberPerDoc : restartPageNumberPerDoc,
                    pageSize : pageSize,
                    dateType: dateType,
                    includeFooterAtEndOnly : includeFooter,
                    setPageSizeAsDefault: setPageSizeAsDefault,
                    includeFieldLastModifiedDate: includeFieldLastModifiedDate
                };
                var endpointUrl = $(this).data('url');
                _exportImmediatelyOrAfterRepositoryConfigDialog(exportSelection, exportConfig, null, endpointUrl);

                $(this).dialog('close');
            }
        }
    });
}

function _setUpWordDialog() {
    $('#word-config').dialog({
        modal : true,
        autoOpen : false,
        width: "auto",
        title : "Word Export Configuration",
        open: function( event, ui ) {
            var initialName = $(this).data('initialExportName');
            $('#word_name').val(initialName);

            $('#pagesizeDefaultRow').remove();
            var jqxhr = $.get("/export/ajax/defaultPDFConfig", function (resp) {
                if(resp.data) {
                    $('#word_a1size').val(resp.data.pageSize);
                    $('#word_a1size').data('defaultVal', resp.data.pageSize);
                } 
            });
            jqxhr.fail(function (e){
                console.log("Couldn't retrieve page size preferences");
            });
        },
        buttons : {
            Cancel : function() {
                $(this).dialog('close');
            },
            Export : function() {
                var pageSize = $('#word_a1size').val();
                var wordName = $('#word_name').val();
                var setPageSizeAsDefault = $('#setPageSizeAsDefault').is(':checked');
                if (pageSize === 'UNKNOWN') {
                    apprise("Please choose a page size format");
                    return;
                }

                var exportSelection = $(this).data('exportSelection');
                var exportConfig = {
                        exportFormat : "WORD",
                        exportName : wordName,
                        pageSize : pageSize,
                        setPageSizeAsDefault: setPageSizeAsDefault
                };
                var endpointUrl = $(this).data('url');
                _exportImmediatelyOrAfterRepositoryConfigDialog(exportSelection, exportConfig, null, endpointUrl);

                $(this).dialog('close');
            }
        }
    });
}

function _setUpArchiveDialog() {
    $('#archive-additional-config').dialog({
        modal : true,
        autoOpen : false,
        height: 310, /* height updated on open() */
        width: 575,
        title : "Archive Export Configuration",
        open: function( event, ui ) {
            var archiveType = $("input[name='archiveType']:checked").val();
        	var isXmlArchive = archiveType === 'xml';
        	$("#archiveVersionsConfigRow").toggle(isXmlArchive);
        	if (!isXmlArchive) {
        		$('#archiveVersionsConfig').prop('checked', false);
        	}
        	// update dialog height
        	var dialogHeight = 310;
            if (isXmlArchive) { 
                dialogHeight += 30; // to fit 'versions' checkbox
            } 
            var isNfsExportAvailable = $("#nfsExportCheckbox").size();
            if (isNfsExportAvailable) {
                dialogHeight += 30; // to fit 'include nfs links' checkbox 
            }
            $('#archive-additional-config').dialog("option", "height", dialogHeight);
        },
        buttons : {
            Cancel : function() {
                $(this).dialog('close');
            },
            Export : function() {
                var exportSelection = $(this).data('exportSelection');
                var exportConfig = {
                        'maxLinkLevel': $('#maxLinkLevel').val(),
                        'archiveType': $("input[name='archiveType']:checked").val(),
                        'description': $("#archive-additional-config textarea[name='description']").val(),
                        'allVersions': $("#archiveVersionsConfig").is(':checked')
                }
                var endpointUrl = $(this).data('url');
                var includeNfsFiles = $('#nfsExportCheckbox').is(':checked');
                if (includeNfsFiles) {
                    _createNfsDialog(exportSelection, exportConfig, endpointUrl);
                } else {
                    _exportImmediatelyOrAfterRepositoryConfigDialog(exportSelection, exportConfig, null, endpointUrl);
                }

                $(this).dialog('close');
            }
        }
    });
}

function _setUpPageSizeDefault() {
    $(document).on("change", '#pdf_a1size, #word_a1size', function (e){
        var defaultVal = $(this).data('defaultVal');
        var currSel = $(this).val();
        if( currSel !== defaultVal && currSel !== 'UNKNOWN') {
            $('#pagesizeDefaultRow').remove();
            var template = $('#setPageSizeAsDefaultTemplate').html();
            var html = Mustache.render(template, {currSel:currSel});
            $(html).hide().insertAfter($(this).closest('tr')).fadeIn();
        } else if ( currSel === defaultVal || currSel === 'UNKNOWN') {
            $('#pagesizeDefaultRow').remove();
        }
    });
}

var _nfsDialogExportConfigData;
var _nfsDialogExportPlan;

function _createNfsDialog(exportSelection, exportConfig, endpointUrl) {
    $('#nfs-config').dialog({
        modal : true,
        title : "Filestore Links Export Configuration",
        width: 520,
        height: 600,
        open: function (event, ui) {
            $('#nfs-config').text('Loading export plan...');

            _nfsDialogExportConfigData = {
                'exportSelection': exportSelection,
                'exportConfig': exportConfig,
                'nfsConfig': { 
                    includeNfsFiles: true,
                    maxFileSizeInMB: 50,
                    excludedFileExtensions: ''
                }
            };
                
            var jqxhr = _retrieveExportPlan();
            jqxhr.done(_refreshNfsExportPlanDialog);
        },
        buttons : {
            Cancel : function() {
                $(this).dialog('close');
            },
            Export : function() {
                _proceedFromExportPlanDlg(exportSelection, exportConfig, endpointUrl);
            }
        }
    });
    $('#nfs-config').dialog('open');
}

function _retrieveExportPlan(fullScan) {
    var createPlanUrl;
    if (fullScan) {
        createPlanUrl = '/nfsExport/ajax/createFullExportPlan?planId=' + _nfsDialogExportPlan.planId;
    } else {
        createPlanUrl = '/nfsExport/ajax/createQuickExportPlan';
    }

    var blockPageMsg = fullScan ? "Scanning filestore links..." : "Preparing filestore files export plan...";
    RS.blockPage(blockPageMsg);

    var jqxhr = $.ajax({
        'url': createPlanUrl,
        'type': 'POST',
        'data': JSON.stringify(_nfsDialogExportConfigData),
        'contentType': 'application/json',
        'processData': false,
        'error': function() {
            RS.ajaxFailed("Export plan generation", true, jqxhr);
        }
    });
    jqxhr.done(function(plan) {
        _nfsDialogExportPlan = plan;
    });
    jqxhr.always(function(plan) {
        RS.unblockPage();
    });
    return jqxhr;
}

function _refreshNfsExportPlanDialog() {
    var plan = _nfsDialogExportPlan;
    if (!plan) {
        $('#nfs-config').text('There was a problem with preparing export plan.')
        return;
    }
    _redrawExportPlanDialogContent(plan);
}

function _redrawExportPlanDialogContent() {
    console.log('redrawing export plan dialog');
    var plan = _nfsDialogExportPlan;
    
    _processNfsExportPlanBeforeDisplaying();
    var templateHTML = $('#nfsExportConfigDialogTemplate').html();
    var exportPlanHTML = Mustache.render(templateHTML, plan);
    $('#nfs-config').html(exportPlanHTML);
           
    _addNfsExportDialogActionHandlers(plan);
}

function _processNfsExportPlanBeforeDisplaying() {
    var plan = _nfsDialogExportPlan;
    var foundNfsLinksCount = 0;
    var foundNfsFolderLinksCount = 0;
    $.each(plan.foundNfsLinks, function(key, link) {
        foundNfsLinksCount++;
        if (link.linkType === 'directory') {  
            foundNfsFolderLinksCount++; 
        }
    });

    plan.foundNfsLinksCount = foundNfsLinksCount;
    plan.foundNfsFolderLinksCount = foundNfsFolderLinksCount;
    plan.foundFileSystemsCount = plan.foundFileSystems.length;
    
    if (foundNfsLinksCount > 0) {
        plan.foundLinksSummary = "Exported content contains " + foundNfsLinksCount + " filestore link" + (foundNfsLinksCount == 1 ? "" : "s") 
            + " from " + (plan.foundFileSystemsCount == 1 ? "one File System" : plan.foundFileSystemsCount + " File Systems") + "."
    }
    
    plan.fileSystemsToLoginCount = plan.foundFileSystems.filter(fs => fs.loggedAs == null).length;
    if (plan.fileSystemsToLoginCount > 0) {
        plan.fileSystemsToLoginSummary = "There " + (plan.fileSystemsToLoginCount == 1 ? "is one File System" 
                : "are " + plan.fileSystemsToLoginCount + " File Systems") + " referenced by filestore links "
                + "that your're not logged into. Please login to remaining File Systems to include these in the export.";
    }
    
    plan.maxFileSizeInMB = _nfsDialogExportConfigData.nfsConfig.maxFileSizeInMB;
    plan.excludedFileExtensions = _nfsDialogExportConfigData.nfsConfig.excludedFileExtensions;
}

function _addNfsExportDialogActionHandlers() {
    var plan = _nfsDialogExportPlan;
    
    $('#nfs-config .nfsExportPlanShowLinksBtn').click(function() {
        plan.foundNfsLinksForDisplay = [];
        $.each(plan.foundNfsLinks, function(key, link) {
            var fsNameAndPath = _getNfsFileSystemNameAndPathFromLinkKey(key);
            var linkForDisplay = { 
                fileSystemName: fsNameAndPath.fileSystemName, 
                fullPath: fsNameAndPath.path,
                linkType: link.linkType,
                isFolder: (link.linkType === 'directory')
            };
            var fileSystemForDisplay = plan.foundNfsLinksForDisplay.filter(fs => fs.fileSystemName == linkForDisplay.fileSystemName);
            if (fileSystemForDisplay.length == 1) {
                fileSystemForDisplay[0].links.push(linkForDisplay);
            } else {
                fileSystemForDisplay = { fileSystemName : linkForDisplay.fileSystemName, links : [ linkForDisplay ] };
                plan.foundNfsLinksForDisplay.push(fileSystemForDisplay);   
            }
        });
        
        var foundLinksDialogTemplateHTML = $('#nfsExportConfigFoundLinksDialogTemplate').html();
        var foundLinksDialogHTML = Mustache.render(foundLinksDialogTemplateHTML, plan);
        apprise(foundLinksDialogHTML);        
    });
    
    $('#nfs-config .nfsShowFileSystemLoginDialogBtn').click(function() {
        var fileSystemsLoginDialogTemplateHTML = $('#nfsExportConfigFileSystemsLoginDialogTemplate').html();
        var fileSystemsLoginDialogHTML = Mustache.render(fileSystemsLoginDialogTemplateHTML, plan);
        apprise(fileSystemsLoginDialogHTML);   
        
        $('.nfsExportPlanLoginBtn').click(function() {
            var fileSystemId = $(this).data('filesystemid');
            Apprise('close'); // close the currently displayed apprise so file system login dialog is top visible
            RS.showNetFileLoginDialog(fileSystemId, null, function(nfsusername) {
                $.each(plan.foundFileSystems, function() {
                    if (this.id == fileSystemId) {
                        this.loggedAs = nfsusername;
                    }
                });
                _redrawExportPlanDialogContent(plan);
            });
        });
    });

    $('#nfs-config .nfsShowFiltersDialogBtn').click(function() {
        var fileFiltersDialogTemplateHTML = $('#nfsExportConfigFiltersDialogTemplate').html();
        var fileFiltersDialogHTML = Mustache.render(fileFiltersDialogTemplateHTML, plan);
        apprise(fileFiltersDialogHTML, { textOk : "Save"}, function() {
            _nfsDialogExportConfigData.nfsConfig.maxFileSizeInMB  = $('.apprise .nfsFilesizeLimit').val();
            _nfsDialogExportConfigData.nfsConfig.excludedFileExtensions = $('.apprise .nfsExcludedFileExtensions').val();
            _redrawExportPlanDialogContent(plan); 
        });

        $('.apprise .nfsFilesizeLimit').val(_nfsDialogExportConfigData.nfsConfig.maxFileSizeInMB);
        $('.apprise .nfsExcludedFileExtensions').val(_nfsDialogExportConfigData.nfsConfig.excludedFileExtensions);
    });
    
    $('#nfs-config .nfsScanLinksBtn').click(function() {

        function _addLinkToScanResultsForDisplay(key, linkDetails) {
            var plan = _nfsDialogExportPlan;
            var msgForLink = plan.checkedNfsLinkMessages[key];
            var resultForDisplay = { 
                name: linkDetails ? linkDetails.fileSystemFullPath : key.substring(key.indexOf("_") + 1),
                msg: msgForLink
            };
            resultForDisplay.fileSystemName = _getNfsFileSystemNameAndPathFromLinkKey(key).fileSystemName;
            if (!linkDetails) {
                resultForDisplay.available = false;
            } else {
                resultForDisplay.available = !msgForLink;
                resultForDisplay.size = linkDetails.size;
                resultForDisplay.sizeToDisplay = linkDetails.size ? RS.humanFileSize(linkDetails.size) : " - ";
                resultForDisplay.type = linkDetails.type;
                resultForDisplay.isFolder = linkDetails.type === 'folder';
                if (resultForDisplay.isFolder) {
                    $.each(linkDetails.content, function() {
                        var childDetails = this;
                        var fldChildKey = childDetails.fileSystemId + "_" + childDetails.fileSystemFullPath;
                        _addLinkToScanResultsForDisplay(fldChildKey, childDetails);
                    });
                }
            }
            
            var fileSystemForDisplay = plan.scanResultsForDisplay.filter(fs => fs.fileSystemName == resultForDisplay.fileSystemName);
            if (fileSystemForDisplay.length == 1) {
                fileSystemForDisplay[0].results.push(resultForDisplay);
            } else {
                fileSystemForDisplay = { fileSystemName : resultForDisplay.fileSystemName, results : [ resultForDisplay ] };
                plan.scanResultsForDisplay.push(fileSystemForDisplay);   
            }
        };
        
        var jqxhr = _retrieveExportPlan(true);
        jqxhr.done(function(plan) {
            if (plan && plan.checkedNfsLinks) {
                plan.scanResultsPresent = true;
                plan.scanResultsOmittedCount = Object.keys(plan.checkedNfsLinkMessages).length;

                plan.scanResultsAvailableCount = 0;
                plan.scanResultsTotalFileSize = 0;
                plan.scanResultsForDisplay = [];
                $.each(plan.checkedNfsLinks, function(key, linkDetails) {
                  _addLinkToScanResultsForDisplay(key, linkDetails);
                });
                $.each(plan.scanResultsForDisplay, function(i, fsResults) {
                  $.each(fsResults.results, function(i, result) {  
                    if (result.available && !result.isFolder) {
                      plan.scanResultsAvailableCount++;
                      plan.scanResultsTotalFileSize += result.size;
                    }
                  });
                });
                plan.scanResultsTotalFileSizeToDisplay = RS.humanFileSize(plan.scanResultsTotalFileSize);

                if (plan.scanResultsOmittedCount == 1) {
                    plan.scanResultsOmittedCountMsg = "One filestore file or folder";
                } else {
                    plan.scanResultsOmittedCountMsg = plan.scanResultsOmittedCount + " filestore files or folders";
                }
                if (plan.scanResultsAvailableCount == 1) {
                    plan.scanResultsAvailableCountMsg = "One filestore file";
                } else {
                    plan.scanResultsAvailableCountMsg = plan.scanResultsAvailableCount + " filestore files";
                }  
                
                _redrawExportPlanDialogContent(plan);
            }
        });
    });
    
    $('#nfs-config .nfsSeeScanReportBtn').click(function() {
        var scanResultsTemplateHTML = $('#nfsExportConfigScanResultsTemplate').html();
        var scanResultsHTML = Mustache.render(scanResultsTemplateHTML, plan);
        apprise(scanResultsHTML);        
    });
};

function _proceedFromExportPlanDlg(exportSelection, exportConfig, endpointUrl, skipFileSystemsToLoginCheck, skipFullScanCheck) {
    
    if (_nfsDialogExportPlan.fileSystemsToLoginCount > 0 && !skipFileSystemsToLoginCheck)  {
        apprise("You are not logged into all required File Systems and some filestore links won't be exported. "
                + "Do you want to proceed without logging in?", { confirm : true, textOk : "Yes, proceed", textCancel : "Cancel"}, 
            function() { _proceedFromExportPlanDlg(exportSelection, exportConfig, endpointUrl, true); });
    } else if (!_nfsDialogExportPlan.scanResultsPresent && _nfsDialogExportPlan.foundNfsFolderLinksCount && !skipFullScanCheck) { 
        apprise("You have not performed links availability scan. We strongly recommend running it, as it will report any "
                + "potential problems with accessing filestore link. Do you want to proceed without running the scan?",
                { confirm : true, textOk : "Yes, proceed", textCancel : "Cancel"}, 
            function() { _proceedFromExportPlanDlg(exportSelection, exportConfig, endpointUrl, true, true); });
    } else { 
        var scannedFilesOverSizeLimitMsg = _checkScannedFilesNotLargerThanLimit();
        if (scannedFilesOverSizeLimitMsg) {
            apprise(scannedFilesOverSizeLimitMsg);
            return;
        }

        _exportImmediatelyOrAfterRepositoryConfigDialog(exportSelection, exportConfig, _nfsDialogExportConfigData.nfsConfig, endpointUrl);
        $('#nfs-config').dialog('close');
    }
}

function _checkScannedFilesNotLargerThanLimit() {
    var plan = _nfsDialogExportPlan;
    if (plan.maxArchiveSizeMBProp == 0) {
        return; // no full scan or limits not set
    }
    var currentLinksSizeMB = plan.scanResultsTotalFileSize / (1024 * 1024);
    if (currentLinksSizeMB < plan.currentlyAllowedArchiveSizeMB) {
        return; // current files size is fine 
    }
    
    var tooLargeMsg = "The size of filestore files to be included in export (" + plan.scanResultsTotalFileSizeToDisplay + ") ";
    if (currentLinksSizeMB > plan.maxArchiveSizeMBProp) {
        tooLargeMsg += "exceeds the global limit set for size of RSpace archive file. Use file filters to exclude some " +
                "files, or ask your System Admin to raise the limit on archive size."; 
    } else {
        tooLargeMsg += "exceeds disk space currently available on RSpace server. Use file filters to exclude some " +
                " files, or contact your System Admin.";
    }
    return tooLargeMsg;
}

function _getNfsFileSystemNameAndPathFromLinkKey(key) {
    var result = {};
    if (key) {
        var splitIndex = key.indexOf('_');
        result.path = key.substring(splitIndex + 1);

        var fileSystemId = key.substring(0, splitIndex);
        $.each(_nfsDialogExportPlan.foundFileSystems, function() {
            if (this.id == fileSystemId) {
                result.fileSystemName = this.name;
                return false;
            }
        });
    }
    return result;
}

function _exportImmediatelyOrAfterRepositoryConfigDialog(exportSelection, exportConfig, nfsConfig, endpointUrl) {
    var depositToRepository = $('#exportToRepository').is(':checked');
    if (depositToRepository) {
        createDatashareDialog(function(repositoryConfig) {
            repositoryConfig.depositToRepository = true;
            _exportFiles(exportSelection, exportConfig, nfsConfig, repositoryConfig, endpointUrl);
            $('#dataShare-dlg').dialog('close');
        });
        $('#dataShare-dlg').dialog('open');
    } else {
        _exportFiles(exportSelection, exportConfig, nfsConfig, null, endpointUrl);
    }
}

function _exportFiles(exportSelection, exportConfig, nfsConfig, repositoryConfig, endpointUrl) {
    var combinedData = {
        'exportSelection': exportSelection,
        'exportConfig': exportConfig,
        'repositoryConfig': repositoryConfig || { depositToRepository: false },
        'nfsConfig': nfsConfig || { includeNfsFiles: false },
    };
    
    RS.blockPage("Exporting...");

    var jqxhr = $.ajax({
        'url': endpointUrl,
        'data': JSON.stringify(combinedData),
        'contentType': 'application/json',
        'type': 'POST',
        'processData': false,
        'success': function(result) {
            if (result && result.includes('Please contact your System Admin')) {
                apprise(result);
            } else {
                RS.defaultConfirm(result);
            }
        },
        'error': function() {
            RS.ajaxFailed("Export", true, jqxhr);
        }
    });
    jqxhr.always(function() {
        RS.unblockPage();
    });
    return jqxhr;
}

//# sourceURL=exportOtherDialogs.js