// for backward-compatibility with apprise 1.5 
function apprise(text, options, onConfirm) {
    // simple apprise
    if (!options) {
        Apprise(text);
        return;
    }
    
    // configured apprise
    var newOptions = {
        buttons : {
            cancel: {
                action: function() { Apprise('close'); },
                className: null,
                id: 'cancel',
                text: options.textCancel || 'Cancel'
            },
            confirm: {
                action: function() {
                    Apprise('close'); 
                    if (onConfirm) {
                        onConfirm();
                    }
                },
                className: 'blue',
                id: 'confirm',
                text: options.textOk || 'Ok'
            },
        }
    };
    Apprise(text, newOptions);
}

// Global Apprise variables
var $Apprise = null,
		$overlay = null,
		$body = null,
		$window = null,
		$cA = null,
		AppriseQueue = [];

// Add overlay and set opacity for cross-browser compatibility
$(function() {
	
	$Apprise = $('<div class="apprise">');
	$overlay = $('<div class="apprise-overlay">');
	$body = $('body');
	$window = $(window);
	
	$body.append( $overlay.css('opacity', '.94') ).append($Apprise);
});

function Apprise(text, options) {
	
	// Restrict blank modals
	if(text===undefined || !text) {
		return false;
	}
	
	// Necessary variables
	var $me = this,
			$_inner = $('<div class="apprise-inner">'),
			$_buttons = $('<div class="apprise-buttons">'),
			$_input = $('<input type="text">');
	
	// Default settings (edit these to your liking)
	var settings = {
	
		animation: 0,	// Animation speed
		top: '20%', // top margin of a dialog 
		buttons: {
			confirm: {
				action: function() { $me.dissapear(); }, // Callback function
				className: null, // Custom class name(s)
				id: 'confirm', // Element ID
				text: 'Ok' // Button text
			}
		},
		input: false, // input dialog
		override: true // Override browser navigation while Apprise is visible
	};
	
	// Merge settings with options
	$.extend(settings, options);
	
	// Close current Apprise, exit
	if(text=='close') { 
		$cA.dissapear();
		return;
	}
	
	// If an Apprise is already open, push it to the queue
	if($Apprise.is(':visible')) {

		AppriseQueue.push({text: text, options: settings});
	
		return;
	}
	
	// Width adjusting function
	this.adjustSize = function() {
		
		var window_width = $window.width(), w = "20%", l = "40%";
		var window_height = $window.height(), contentMaxHeight;

		if(window_width<=800) {
			w = "90%", l = "5%";
		} else if(window_width <= 1400 && window_width > 800) {
			w = "70%", l = "15%";
		} else if(window_width <= 1800 && window_width > 1400) {
			w = "50%", l = "25%";
		} else if(window_width <= 2200 && window_width > 1800) {
			w = "30%", l = "35%";
		}
		
		if (window_height > 1000) {
		    settings.top = "20%";
		    contentMaxHeight = window_height * 0.6 - 50;
		} else if (window_height > 800) {
		    settings.top = "15%";
		    contentMaxHeight = window_height * 0.7 - 50;
		} else {
		    settings.top = "10%";
		    contentMaxHeight = window_height * 0.8 - 50;
		}
		
		$Apprise.css('width', w).css('left', l).css('top', settings.top);
		$Apprise.find('.apprise-content').css('max-height', contentMaxHeight);
		
	};
	
	// Close function
	this.dissapear = function() {
		
		$Apprise.animate({
			top: '-100%'
		}, settings.animation, function() {
			
			$overlay.fadeOut(300);
			$Apprise.hide();
			
			// Unbind window listeners
			$window.unbind("beforeunload");
			$window.unbind("keydown");

			// If in queue, run it
			if(AppriseQueue[0]) { 
				Apprise(AppriseQueue[0].text, AppriseQueue[0].options);
				AppriseQueue.splice(0,1);
			}
		});
		
		return;
	};

	// Add buttons
	$.each(settings.buttons, function(i, button) {
		
		if(button) {
			
			// Create button
			var $_button = $('<button id="apprise-btn-' + button.id + '">').append(button.text);
			
			// Add custom class names
			if(button.className) {
				$_button.addClass(button.className);
			}
			
			// Add to buttons
			$_buttons.append($_button);
			
			// Callback (or close) function
			$_button.on("click", function() {
				
				// Build response object
				var response = {
					clicked: button, // Pass back the object of the button that was clicked
					input: ($_input.val() ? $_input.val() : null) // User inputted text
				};
				
				button.action( response );
				//$me.dissapear();
			});
		}
	});
	
	// Disabled browser actions while open
	if(settings.override) {
		$window.bind('beforeunload', function(e){ 
			return "An alert requires attention";
		});
	}
	
	// Append elements, show Apprise
	$Apprise.html('').append( $_inner.append('<div class="apprise-content">' + text + '</div>') ).append($_buttons);
	$cA = this;

	// Adjust dimensions based on window
    $me.adjustSize();
    $window.resize( function() { $me.adjustSize() } );
	
	if(settings.input) {
		$_inner.find('.apprise-content').append( $('<div class="apprise-input">').append( $_input ) );
	}
	
	$overlay.fadeIn(300);
	$Apprise.show().animate({
	        top: settings.top
	    }, 
		settings.animation
	);
	
	// Focus on input
	if(settings.input) {
		$_input.focus();
	}
	
	// RSpace fix - stop focus hijacking by jqueryui form inputs
	$_inner.on('focusin', function(e) {
      e.stopImmediatePropagation();
	});
	
} // end Apprise();


// Keypress handling
$(document).keydown(function(e) {
    if ($('.apprise-overlay').is(':visible')) {
        if (e.keyCode === 13) {
        		// Custom changes: if Enter key is pressed but the Cancel button
    				// is focused, do not submit the dialog and cancel it instead.
            if ($(":focus").attr('id') === 'apprise-btn-cancel'){
            	$("#apprise-btn-cancel").click();            	
            } else {
            	$("#apprise-btn-confirm").click();            	
            }
            return false;
        }
        if (e.keyCode === 27) {
            $("#apprise-btn-cancel").click();
            return false;
        }
    }
});
