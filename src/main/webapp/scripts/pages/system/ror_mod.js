function loadRoR() {
    const loadRoREvent = new Event("loadROR");
    $('#mainArea').empty();

    var jqxhr = $.get('/system/ror/ajax/view');

    jqxhr.done(function (resp) {
        $('#mainArea').html(resp);
        window.dispatchEvent(loadRoREvent);
    });

    jqxhr.fail(function () {
         RS.ajaxFailed("Getting RoR Registry Systems page", false, jqxhr);
    });
}

$(document).ready(function() {
    $(document).on('click', '#rorRegistryLink', loadRoR);
});
