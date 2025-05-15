// requires notification.js, messages.js, myrequests.js, ../workspace/calendarDialog.js
$(document).ready(function() {
  var messageListCSSclass = "messageList";
  var myrequestListCSSclass = "myrequestList";
  var notificationListCSSclass = "notificationList";
  var dashboardContainer = ".dashboardContainer";
  // we need to toggle class names of the message container 
  // here to agree with the variaous dialogs 
  // in workspace. 
  $(document).on('click','#mor', function (e) {
    e.preventDefault();
    $(dashboardContainer).addClass(messageListCSSclass)
      .removeClass(notificationListCSSclass + " " + myrequestListCSSclass);

    $.get(createURL("/dashboard/ajax/allMessages"), function (data) {
      $(dashboardContainer).html(data);
      unescapeMessageContent();
    });
  });
  
  $(document).on('click','#mynotifications', function (e) {
    e.preventDefault();
    $(dashboardContainer).removeClass(messageListCSSclass+ " " + myrequestListCSSclass)
      .addClass(notificationListCSSclass);
    $.get(createURL("/dashboard/ajax/listNotifications"), function(data){
      $(dashboardContainer).html(data);
      unescapeMessageContent();
    });  
  });
   
  $(document).on('click','#myrequests',function (e) {
    e.preventDefault();
    $(dashboardContainer).removeClass(messageListCSSclass + " " + notificationListCSSclass)
      .addClass(myrequestListCSSclass);
    $.get(createURL(createURL("/dashboard/ajax/listMyRequests?orderBy=creationTime&sortOrder=DESC")), function(data){
      $(dashboardContainer).html(data);
      unescapeMessageContent();
    });  
  });
   
  
  initialiseRequestDlg({
    targetFinderPolicy:'ALL',
    availableMessageTypes:'SIMPLE_MESSAGE,GLOBAL_MESSAGE,REQUEST_EXTERNAL_SHARE'
  });
  
  $('#createRequest, #msgIconLink').click(function(e) {
    e.preventDefault();
    $('#createRequestDlg').dialog('open');
  });
   
  unescapeMessageContent();

  initCreateCalendarEntryDlg();
  $('#createCalendarEntryDlgLink').click(function () {
    $('#createCalendarEntryDlg').dialog('open');
  });
});
