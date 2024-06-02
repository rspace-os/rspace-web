const makeImageLinksPublic = () => {
    const imgs = $("img");
    replaceWithPublicUrl(imgs);
}

const isNotebookLink = (link) =>
    $(link).attr('data-globalId').startsWith('NB');

const disableLinkedDocumentsAndLinkedFiles = () => {
    const links = $(".linkedRecord");
    links.each(function (index) {
        if($(links[index]).attr('href').indexOf('public/publicView/globalId')===-1 ) {
            const prevUrl = $(links[index]).attr('href');
            const docID = prevUrl.replaceAll("/globalId/", "")
            const publicExistsRequest = $.get('/public/publishedView/publiclink?globalId=' + docID);
            publicExistsRequest.done(function (resp) {
                if (resp) {
                    let linkToUnpublishedEntryInPublishedNotebook = false;
                    //link to an entry in a published notebook, the entry itself was NOT published
                    if(resp.indexOf("initialRecordToDisplay"!==-1)){
                        linkToUnpublishedEntryInPublishedNotebook = true;
                    }
                    if (linkToUnpublishedEntryInPublishedNotebook || isNotebookLink(links[index])) {
                        $(links[index]).attr('href', '/public/publishedView/notebook/' + resp);
                    } else {
                        $(links[index]).attr('href', '/public/publishedView/document/' + resp);
                    }
                } else {
                    $(links[index]).removeAttr('href').css('opacity', 0.3);
                }
            });
        };
    });
    const files = $(".nfs_file");
    files.each(function (index) {
        $(files[index]).removeAttr('href').css('opacity', 0.3);
    });
    //selector for any href coming after the externalLinkBadge inside an attachmentP class -
    // this category includes owncloud and nextcloud, box and others?
    const externalLinksUsingBadge = $(".attachmentP > .externalLinkBadge + a");
    externalLinksUsingBadge.each(function (index) {
        $(externalLinksUsingBadge[index]).removeAttr('href').css('opacity', 0.3);
    });
    //external attachments (note nextcloud owncloud have both an ext attach link and a linksBadge link
    const externalAttachments = $(".externalAttachmentDiv > a");
    externalAttachments.each(function (index) {
        $(externalAttachments[index]).removeAttr('href').css('opacity', 0.3);
    });
}

const replaceWithPublicUrl = (imgs) => {
    imgs.each(function (index) {
        imgs[index].src = makeUrlPublic(imgs[index].src);
    });
}
const makeUrlPublic = (src) => {
    if(src.indexOf('/image')!==-1 && src.indexOf('public/publicView/image')===-1 && src.indexOf('images') === -1) {
        return src.replace('image', 'public/publicView/image');
    }
    else if(src.indexOf('/images')!==-1 && src.indexOf('public/images')===-1) {
       return src.replace('images', 'public/images');
    }
    else if(src.indexOf('/chemical')!==-1 && src.indexOf('public/publicView/chemical')===-1) {
      return src.replace('chemical', 'public/publicView/chemical');
    }
    else if(src.indexOf('/thumbnail')!==-1 && src.indexOf('public/publicView/thumbnail')===-1) {
        return src.replace('thumbnail', 'public/publicView/thumbnail');
    }
    return src;
}

// No idea how generic this is for other types of svg, it should work for embedded equations
// See the file: equationLink.vm
makeSVGPublic = () => {
    const equations =$("object");
    setTimeout(()=>
        equations.each(function (index) {
        if(equations[index].data.indexOf('public/publicView')===-1) {
            equations[index].data = equations[index].data.replace('svg', 'public/publicView/svg');
        }
    }), 200);
}

const hideInfoPopups = () => {
    $(".imageInfoBtnDiv").hide();
    $(".breadcrumb").hide();
    $(".infoActionLink").hide();
}
const hidePreviewButtons = () => {
    $(".previewToggleBtn").hide();
}

const addMetaToHeader = (publicationSummary) => {
    $('head').append("<meta name='description' content='"+publicationSummary+"'/>");
    $('head').append("<meta name='robots' content='noarchive'/>");
}

const forbidBots = () => {
    $('head').append("<meta name='robots' content='noindex, nofollow'/>");
}

const hideEditButtons = () => {
    $("#editEntry").css('visibility', 'hidden');
    $("#renameRecordEdit").css('visibility', 'hidden');
    $("#editTags").css('visibility', 'hidden');
    $(".nonEditableIcon").css('visibility', 'hidden');
}

//purely so that coreEditor does not crash as it calls these functions, expecting the full workspace to be present
const setUpFileTreeBrowser = () => {};

const setUpFileUpload = () => {};

const doPoll = () => {};

const initCreateFromTemplateDlg = () => {};

const initDragDropAreaHandling = () => {};

const runAfterTinymceActiveEditorInitialized = () => {};

RS.hideHelpButton = () => {};

RS.showHelpButton = () => {};

window.renderToolbar = () => {};