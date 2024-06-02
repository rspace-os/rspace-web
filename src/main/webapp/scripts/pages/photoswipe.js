/* RSpace methods for opening photoswipe gallery */

var photoswipeImageArray = [];
var cachedImageDetails = {};
function openPhotoSwipeOnIndex(index) {
    
    var options = {
        index: index // index of slide to show
    };
    
    // initializes and opens PhotoSwipe
    var pswpElement = $('.pswp').get(0);
    var gallery = new PhotoSwipe(pswpElement, PhotoSwipeUI_Default, photoswipeImageArray, options);
    gallery.init();
}

function populatePhotoswipeImageArray($images) {
    /* The public view is for a document made accessible to non RSpace users */
    const publicDocument = $("#public_document_view").length > 0;
    if(publicDocument) {
        replaceWithPublicUrl($images);
    }
    photoswipeImageArray = [];
    
    var rawImages = [];
    var milliseconds = new Date().getTime();
    $images.each(function(i, img) {

        var $thisImg = $(img);
        var imgIdElements = $thisImg.attr("id").split("-");
        var imageId = imgIdElements[imgIdElements.length - 1];
        var fullImageSrc = img.src;

        var isRawImage = $thisImg.hasClass('inlineImageThumbnail') || $thisImg.hasClass('imageThumbnail');
        const publicUrlPrepend = (publicDocument ? '/public/publicView' : '');
        if (isRawImage) {
            fullImageSrc = publicUrlPrepend + `/Streamfile/${imageId}`;
        }

        var isChemicalElement = $thisImg.hasClass('chem')
        var isChemicalFile = $thisImg.hasClass('chemFile')
        if (isChemicalElement) {
            fullImageSrc = publicUrlPrepend + `/Streamfile/chemImage/${imageId}`;
        } else if (isChemicalFile) {
            fullImageSrc = publicUrlPrepend + `/Streamfile/chemFileImage/${imageId}`;
        }

        if (isRawImage || isChemicalElement) {
            var revisionId = $thisImg.data('rsrevision');
            if (revisionId) {
                fullImageSrc += `?revision=${revisionId}`;
            } else {
                fullImageSrc += `?time=${milliseconds}`; // refresh cache
            }
        }
        
        var imageDetails = {
                rspaceId: imageId,
                msrc: img.src,
                src: fullImageSrc,
                w: $thisImg.data('fullwidth') || img.width || $thisImg.data('widthresized') || 100,
                h: $thisImg.data('fullheight') || img.height || $thisImg.data('heightresized') || 100,
        };
        photoswipeImageArray.push(imageDetails);
        
        var currImageIndex = i;
        $thisImg.off().on('click', function(e) {
            e.preventDefault();
            openPhotoSwipeOnIndex(currImageIndex);
        });
        $thisImg.css('cursor', 'pointer');
        
        if (isRawImage) {
            rawImages.push(imageId);
        }
    });
    
    var rawImagesWithoutDetails = [];
    $.each(rawImages, function(i, imageId) {
       if (!cachedImageDetails[imageId]) {
           rawImagesWithoutDetails.push(imageId);
           cachedImageDetails[imageId] = {}; // so we don't add to rawImagesWithoutDetails again 
       } 
    });
    
    function applyCachedImageDetails() {
        $.each(photoswipeImageArray, function(i, imageDetails) {
            var cachedInfo = cachedImageDetails[imageDetails.rspaceId];
            if (cachedInfo) {
                if (cachedInfo.width) {
                    imageDetails.w = cachedInfo.width; 
                }
                if (cachedInfo.height) {
                    imageDetails.h = cachedInfo.height;
                }
                imageDetails.safeName = RS.escapeHtml(cachedInfo.name);
                imageDetails.safeDescription = RS.escapeHtml(cachedInfo.description || '');
                _setPhotoswipeImageDetailsTitle(imageDetails);
            }
        });
    }
    
    if (rawImagesWithoutDetails.length === 0) {
        applyCachedImageDetails();
    } else {
        var data = { ids : rawImagesWithoutDetails };
        var jqxhr = $.get('/image/ajax/imageInfo', data);
        jqxhr.done(function(imageInfos) {
            if (imageInfos) {
                $.each(imageInfos, function(i, info) {
                    cachedImageDetails[info.id] = info;
                });
            }
            applyCachedImageDetails();
        });
    }
}

function _setPhotoswipeImageDetailsTitle(imageDetails) {
    if (imageDetails.safeDescription) {
      imageDetails.title = imageDetails.safeDescription + "<br/><br/>" + imageDetails.safeName;
    } else {
      imageDetails.title = imageDetails.safeName;
    }
}

function updatePhotoswipeImageName(id, name) {
    $.each(photoswipeImageArray, function(i, imageDetails) {
        if (id == imageDetails.rspaceId) {
            imageDetails.safeName = RS.escapeHtml(name);
            _setPhotoswipeImageDetailsTitle(imageDetails);
        }
    });
    if (cachedImageDetails[id]) {
        cachedImageDetails[id].name = name;
    }
}

function updatePhotoswipeImageDescription(id, description) {
    $.each(photoswipeImageArray, function(i, imageDetails) {
        if (id == imageDetails.rspaceId) {
            imageDetails.safeDescription = RS.escapeHtml(description);
            _setPhotoswipeImageDetailsTitle(imageDetails);
        }
        if (cachedImageDetails[id]) {
            cachedImageDetails[id].description = description;
        }
    });
}


