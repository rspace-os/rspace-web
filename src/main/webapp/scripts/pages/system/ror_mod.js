define(function() {

    var fileSystemsArray;

    function loadRoR() {
        const loadRoR = new Event("loadROR");
        $('#mainArea').empty();

        var jqxhr = $.get('/system/ror/ajax/view');

        jqxhr.done(function (resp) {
            $('#mainArea').html(resp);
            window.dispatchEvent(loadRoR)
        });

        jqxhr.fail(function () {
             RS.ajaxFailed("Getting RoR Registry Systems page", false, jqxhr);
        });
    }

    $(document).ready(function() {
        $(document).on('click', '#rorRegistryLink', loadRoR);
    });

});
