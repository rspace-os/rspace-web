var tableElement = ".mainTable tbody";

$(document).ready(function (){
	// this input field will only be available in cloud version
	applyAutocomplete('.user_autocompletable');

	$('#createFormLink').click (function (e){
		e.preventDefault();
		var form$=$(this).closest('form');
		form$.submit();
	});
	const piCanCreateLabGroupsReq = $.get("/deploymentproperties/ajax/editableProperties");
	piCanCreateLabGroupsReq.done(function (resp) {
		const piCanCreateLabGroups = resp["self_service_labgroups"];
		if (piCanCreateLabGroups !== 'ALLOWED') {
			$('#self_service_labgroups').hide();
		}
	});

	const allowProjectGroupsReq = $.get("/deploymentproperties/ajax/editableProperties");
	allowProjectGroupsReq.done((resp) => {
	    const allowProjectGroups = resp["allow_project_groups"];
	    if (allowProjectGroups !== 'ALLOWED') {
	        $('#new_project_group').hide();
	    }
	});

	var paginationEventHandler = function(source, e) {
		RS.blockPage("Loading the chosen page...", false, $(tableElement));
		let url = createURL(source.attr('id').split("_").slice(1).join("_"));
		if (RS.webResultCache.get(url) != undefined) {
			$('#directoryContainer').html(RS.webResultCache.get(url));
			RS.unblockPage($(tableElement));
		} else {
			var jxqr = $.get(url, function(data) {
				$('#directoryContainer').html(data);
				RS.webResultCache.put(url, data, 30 * 1000 );//30 seconds
				RS.unblockPage($(tableElement));
			});
			jxqr.fail(function() {
				RS.unblockPage($(tableElement));
				RS.ajaxFailed("Getting user activity information", false, jxqr);
			});
		}
	};

	/*
	 * Menu slider: initialising, toggling visibility of the arrows, adjusting number of panels shown...
	 */
	var numberOfSlides = $("#menuMover .menuInnerPanel").length;
	var availableSliderWidth = parseInt($("#menuScrollContainer").innerWidth());
	var menuPanelWidth = parseInt($(".menuInnerPanel").outerWidth()) +
						 parseInt($(".menuInnerPanel").css("margin-left")) +
						 parseInt($(".menuInnerPanel").css("margin-right"));
	var slidesToShow = Math.floor(availableSliderWidth/menuPanelWidth);
	// Toggle arrows as appropriate (when either edge of the slider is reached)
	var toggleSliderArrows = function(nextSlide) {
		if (nextSlide == 0) {
			$(".leftScroller").animate({opacity: 0}, fadeTime, function() { $(this).hide(); });
		} else {
			$(".leftScroller").show().animate({opacity: 1}, fadeTime);
		}
		if (nextSlide >= numberOfSlides - slidesToShow) {
			$(".rightScroller").animate({opacity: 0}, fadeTime, function() { $(this).hide(); });
		} else {
			$(".rightScroller").show().animate({opacity: 1}, fadeTime);
		}
	}
	// Hide arrows as appropriate on slider initialisation; scroll to slide
	// based on detected active My RSpace tab.
	$('#menuMover').on('init', function(event, slick){


		// If we know what My RSpace tab we're on, highlight it as active
		// in the slider and scroll the slider so the active tab is visible
		if ($('.menuInnerPanel.currentPanel').length) {
			// Doesn't work without the timeout; see:
			// https://github.com/kenwheeler/slick/issues/1802
			setTimeout(function () {
				$('.menuInnerPanel').attr('tabindex', '0');
				$('.menuInnerPanel a').attr('tabindex', '-1');

				// Get the current My RSpace slide index
				var currentSlideIndex = $('.menuInnerPanel.currentPanel').data('slick-index');

				if (currentSlideIndex >= slidesToShow - 2) {
    				// Scrolling doesn't behave as expected, if the slider wouldn't be filled
    				// with slides after scrolling. So, only ever scroll as far as we can while
    				// keeping the number of slides at the calculated 'slidesToShow' value.
    				slideToGoTo = Math.min(currentSlideIndex, numberOfSlides - slidesToShow);
    				// Scroll in the slider
    				slick.$slider.slick('slickGoTo', slideToGoTo);

						// Adjust the arrows
						toggleSliderArrows(currentSlideIndex);
				} else {
					toggleSliderArrows(0);
				}
			}, 0);
		} else {
			toggleSliderArrows(0);
		}
	});
	// Hide arrows as appropriate on moving to different slide
	$('#menuMover').on('beforeChange', function(event, slick, currentSlide, nextSlide){
	  toggleSliderArrows(nextSlide);
	});
	// Do check for '.slick-initialized', because any attempts to reinit Slick
	// once it's been initialised already will break the page
	$('#menuMover').not('.slick-initialized').slick({
		infinite: false,
		slidesToShow: slidesToShow,
		slidesToScroll: 1,
		dots: false,
		arrows: true,
		nextArrow: ".rightScroller",
		prevArrow: ".leftScroller",
		swipeToSlide: true,
		initialSlide: 0
	});

	$('.menuInnerPanel').keyup(function(e){
		if (e.keyCode === 13) {
			$(this).find('img').click();
		}
	});

	RS.setupPagination(paginationEventHandler);
});
