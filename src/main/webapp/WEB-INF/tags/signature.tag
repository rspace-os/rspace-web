<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ attribute name="record" required="true" type="com.researchspace.model.record.BaseRecord"%>

<script>
	var isSigned;
	var isWitnessed;

	var _signatureId;
	var _signer;
    var _witnesses;
	var _pendingWitnesses;
	var _declinedWitnesses;
    var _signatureStatus;
    var _contentHash;
	var _exportHashes;

	function signatureResetState() {
		isSigned = false;
		isWitnessed = false;

		_signatureId = -1;
		_signer = undefined;
		_witnesses = [];
		_pendingWitnesses = [];
		_declinedWitnesses = [];
		_signatureStatus = undefined;
		_contentHash = undefined;
		_exportHashes = [];
	}
	
	function _signatureAddWitness(name, date) {
		var hasWitnessed = !!date && date != "DECLINED";
		if (hasWitnessed) {
			_witnesses.push({ fullName: name, date: date });
		} else if (date == "DECLINED") {
            _declinedWitnesses.push(name);
		} else {
			_pendingWitnesses.push(name);
		}
	}
	
	function signatureSetFromJSON(signatureInfo) {
		signatureResetState();
		if (signatureInfo) {
			_signatureId = signatureInfo.id;
			_signer = {
	   			fullName: signatureInfo.signerFullName,
	   			date: signatureInfo.signDate
	   		};
			$.each(signatureInfo.witnesses, function(name, date) {
		        _signatureAddWitness(name, date);
			});
			$.each(signatureInfo.hashes, function(index, hash) {
				hash.type === 'CONTENT' ? _contentHash = hash : _exportHashes.push(hash);
			});
			_signatureStatus = signatureInfo.status;
		}
	}
	
	function _signatureGetSignerMsg() {
		return "This document was signed by <b>" + _signer.fullName + "</b> on "+ _signer.date +".";
	}
	
	function _signatureGetWitnessMsg(witness) {
		return "This document was witnessed by <b>"+ witness.fullName +"</b> on "+ witness.date + ".";
	}
	
	function _signatureGetPendingWitnessMsg() {
		return "Pending witness requests: " +  _pendingWitnesses.join(', ') + '.';
	}

    function _signatureGetDeclinedWitnessMsg() {
        return "Declined witness requests: " + _declinedWitnesses.join(', ') + '.';
    }

	function signatureRecalculateStatus() {
		isSigned = _signer != undefined; 
		isWitnessed = _witnesses.length > 0;
		
		$('.signatureContainer').empty();
		if (isSigned) {
			var signMsg = "<span class='signature'>" + _signatureGetSignerMsg() + "</span>";
			$('.signatureContainer').append(signMsg);
			
			$.each(_witnesses, function(index, witness) {
				var witnessMsg = "<span class='witnessLabel'>" + _signatureGetWitnessMsg(witness) + "</span>";
				$('.signatureContainer').append(witnessMsg);
			});
			if (_declinedWitnesses.length) {
                var declinedWitnessMsg = "<span class='witnessLabel'>" + _signatureGetDeclinedWitnessMsg() + "</span>";
                $('.signatureContainer').append(declinedWitnessMsg);
			}
			if (_pendingWitnesses.length) {
				var pendingWitnessMsg = "<span class='witnessLabel'>" + _signatureGetPendingWitnessMsg() + "</span>";
				$('.signatureContainer').append(pendingWitnessMsg);
			}
			displayStatus(_signatureStatus);
		}
        _appendSignatureHashesContainer();
	}

	function _appendSignatureHashesContainer() {

        $('#signatureHashesContainer').remove();
        if (_contentHash) {
          var initMsg = '<span>Secure checksums and exports were generated on signing: </span>'
              + '<a class="signatureHashesToggle">Show</a> ' 
              + '<a class="signatureHashesToggle" style="display:none">Hide</a>';
          $('.signatureContainer').append(initMsg);
              
          var container = '<div id="signatureHashesContainer">';
          container += "<span class='hashLabel'>" + _signatureGetContentHashMsg(_contentHash) + "</span><br/>";
          $.each(_exportHashes, function(index, hash) {
              if (hash.type !== 'CONTENT') { 
                  container += "<span class='hashLabel'>" + _signatureGetArchiveHashMsg(hash) + "</span><br/>";
              }
          });
          container += '</div>';
          $('.signatureContainer').after(container);
          
          $('#verifyContentHashBtn').on('click', function() {
              _verifyContentHash();
          });
          $('.signatureHashesToggle').on('click', function() {
              _signatureHashesToggleContainer();
          });
        }
    }
    
    function _signatureGetContentHashMsg(hash) {
        return "SHA-256 checksum of the signed content: " + hash.hexValue + "<button type=\"button\" class=\"btn\" id=\"verifyContentHashBtn\" >Verify</button>";
    }

    function _signatureGetArchiveHashMsg(hash) {
        return "SHA-256 checksum of " + _getArchiveUrl(hash) + ": " + hash.hexValue;
    }
    function _getArchiveUrl(hash) {
        if (hash) {
            return "<a href=\"/Streamfile/filestore/" + _signatureId + "/" + hash.filePropertyId + "\">" 
                + hash.type + '<img src="/images/download.png" class="hashExportDownloadImg"/></a>';
        }
        return "";
    }
    
    function _signatureHashesToggleContainer() {
    	$('.signatureHashesToggle, #signatureHashesContainer').toggle();
    }
    function _verifyContentHash() {
        var jxqr = $.get("/workspace/editor/structuredDocument/ajax/currentContentHash/" + getDocumentOrEntryId(), function (data, xhr){
        	if (data === _contentHash.hexValue) {
        		RS.defaultConfirm("Current content matches the checksum from the time of signing.");
        	} else {
        		var msg = "Current checksum: " + data;
        		msg += "<br />Checksum at the time of signing: " + _contentHash.hexValue; 
        		msg += "<br /><br />Either document content, or the way it is represented, has changed since the document was signed.";
        		msg += "If that's unexpected, contact your System Admin";
        	    apprise(msg);
        	}
        }).fail(function() {
            RS.ajaxFailed("Getting current hash value", false, jxqr);
        });
    }
	
	function signatureShowToastMessage() {
		var msg = '-- signature info unavailable --';
		if (isSigned) {
			msg = "<br><span>" + _signatureGetSignerMsg() + "</span>";
			$.each(_witnesses, function(index, witness) {
				msg += "<br><span>" + _signatureGetWitnessMsg(witness) + "</span>";
			});
			if (_pendingWitnesses.length) {
				msg += "<br><span>" + _signatureGetPendingWitnessMsg() + "</span>";
			}
            if (_declinedWitnesses.length) {
                msg += "<br><span>" + _signatureGetDeclinedWitnessMsg() + "</span>";
            }
		}
		$().toastmessage('showToast', { text: msg, stayTime: 5000 });
	}

	$(document).ready(function() {
		signatureResetState();

		<c:if test="${record.signed}">
			signatureInfoJson = '${signatureInfo.asJSON}';
			signatureSetFromJSON(JSON.parse(RS.unescape(signatureInfoJson)));
		</c:if>
	
	  	signatureRecalculateStatus();
 	});
</script>

<div class="signatureContainer">
    ...signature info unavailable...
</div>
