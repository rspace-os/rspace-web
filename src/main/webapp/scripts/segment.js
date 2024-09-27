// username of current user initialised from default.jsp,
// but not from .../inventory/start.html
if (typeof window.currentUser === "undefined") {
  window.currentUser = "";
}

_loadLiveChat = function (analyticsEnaled, analyticsServerType) {
  if(!analyticsEnaled || analyticsServerType === "posthog") {

    // setup intercom independently of segment
    fetch("/session/ajax/livechatProperties")
      .then(res => res.json())
      .then(props => {
        if (!props.livechatEnabled) return;
        var APP_ID = props.livechatServerKey;
        window.intercomSettings = {
          api_base: "https://api-iam.intercom.io",
          app_id: APP_ID,
          hide_default_launcher: true,
        };

        // this code is taken from the intercom docs
        // https://developers.intercom.com/installing-intercom/web/installation
        (function(){var w=window;var ic=w.Intercom;if(typeof ic==="function"){ic('update',w.intercomSettings);}else{var d=document;var i=function(){i.c(arguments);};i.q=[];i.c=function(args){i.q.push(args);};w.Intercom=i;var l=function(){var s=d.createElement('script');s.type='text/javascript';s.async=true;s.src='https://widget.intercom.io/widget/' + APP_ID;var x=d.getElementsByTagName('script')[0];x.parentNode.insertBefore(s, x);};if(document.readyState==='complete'){l();}else if(w.attachEvent){w.attachEvent('onload',l);}else{w.addEventListener('load',l,false);}}})();
    });

    return;
  }
  // if segment is being used for analytics, then intercom will be loaded via it
}

_loadAnalytics = function (analyticsServerType, analyticsServerHost, analyticsServerKey, analyticsUserId) {
  console.log("Loading analytics");

  // if posthog is configured, enable it rather than intercom+segment
  if(analyticsServerType === "posthog") {
    /*
     * 1. install on page
     * this line of minitied code is taken directly from the posthog docs
     * https://posthog.com/docs/product-analytics/installation
     */
    !function(t,e){var o,n,p,r;e.__SV||(window.posthog=e,e._i=[],e.init=function(i,s,a){function g(t,e){var o=e.split(".");2==o.length&&(t=t[o[0]],e=o[1]),t[e]=function(){t.push([e].concat(Array.prototype.slice.call(arguments,0)))}}(p=t.createElement("script")).type="text/javascript",p.async=!0,p.src=s.api_host+"/static/array.js",(r=t.getElementsByTagName("script")[0]).parentNode.insertBefore(p,r);var u=e;for(void 0!==a?u=e[a]=[]:a="posthog",u.people=u.people||[],u.toString=function(t){var e="posthog";return"posthog"!==a&&(e+="."+a),t||(e+=" (stub)"),e},u.people.toString=function(){return u.toString(1)+".people (stub)"},o="capture identify alias people.set people.set_once set_config register register_once unregister opt_out_capturing has_opted_out_capturing opt_in_capturing reset isFeatureEnabled onFeatureFlags getFeatureFlag getFeatureFlagPayload reloadFeatureFlags group updateEarlyAccessFeatureEnrollment getEarlyAccessFeatures getActiveMatchingSurveys getSurveys".split(" "),n=0;n<o.length;n++)g(u,o[n]);e._i.push([i,s,a])},e.__SV=1)}(document,window.posthog||[]);

    // 2. initialise with config options
    posthog.init(analyticsServerKey, {api_host: analyticsServerHost});

    // 3. mimic analytics object created by intercom+segment
    window.analytics = {
      track: (event, data) => posthog.capture(event, data),
    };

    return;
  }

  try {
    // Segment loads Intercom, hide the intercom icon as we use Lighthouse (helpButton.js)
    window.intercomSettings = {
      hide_default_launcher: true,
      vertical_padding: 110, // Display Intercom popups above helpButton.js
    };

    // Segment tracking
    !(function () {
      var analytics = (window.analytics = window.analytics || []);
      if (!analytics.initialize)
        if (analytics.invoked)
          window.console &&
            console.error &&
            console.error("Segment snippet included twice.");
        else {
          analytics.invoked = !0;
          analytics.methods = [
            "trackSubmit",
            "trackClick",
            "trackLink",
            "trackForm",
            "pageview",
            "identify",
            "group",
            "track",
            "ready",
            "alias",
            "page",
            "once",
            "off",
            "on",
          ];
          analytics.factory = function (t) {
            return function () {
              var e = Array.prototype.slice.call(arguments);
              e.unshift(t);
              analytics.push(e);
              return analytics;
            };
          };
          for (var t = 0; t < analytics.methods.length; t++) {
            var e = analytics.methods[t];
            analytics[e] = analytics.factory(e);
          }
          analytics.load = function (t) {
            var e = document.createElement("script");
            e.type = "text/javascript";
            e.async = !0;
            e.src =
              ("https:" === document.location.protocol
                ? "https://"
                : "http://") +
              "cdn.segment.com/analytics.js/v1/" +
              t +
              "/analytics.min.js";
            var n = document.getElementsByTagName("script")[0];
            n.parentNode.insertBefore(e, n);
          };
          analytics.SNIPPET_VERSION = "3.0.1";

          analytics.load(analyticsServerKey);
          analytics.identify(analyticsUserId);
          analytics.page();
        }
    })();

  } catch (e) {
    console.warn("Analytics.js exception at: " + document.URL);
    console.log(e);
  }
};

$(document).ready(function () {
  const analyticsData = RS.loadSessionSetting("analyticsDataForUser");
  const isAnalyticsDataCurrent =
    analyticsData && analyticsData.currentUser === window.currentUser;

  if (isAnalyticsDataCurrent) {
    const analyticsData = JSON.parse(RS.loadSessionSetting("analyticsData"));

    document.dispatchEvent(
      new CustomEvent("analyticsEnabled", {
        detail: { value: analyticsData.analyticsenabled },
      })
    );

    if (analyticsData.analyticsEnabled) {
      console.log(
        "saved analytics config current, using local analytics props"
      );
      _loadAnalytics(
        analyticsData.analyticsServerType,
        analyticsData.analyticsServerHost,
        analyticsData.analyticsServerKey,
        analyticsData.analyticsUserId,
      );
    }
    _loadLiveChat(analyticsData.analyticsEnabled, analyticsData.analyticsServerType);
  } else {
    console.log("no saved analytics config, retrieving...");

    const analyticsProps = $.get("/session/ajax/analyticsProperties");
    window.currentUser = analyticsProps.currentUser;

    analyticsProps.done(function (analyticsData) {
      document.dispatchEvent(
        new CustomEvent("analyticsEnabled", {
          detail: { value: analyticsData.analyticsEnabled },
        })
      );

      RS.saveSessionSetting("analyticsData", JSON.stringify(analyticsData));

      if (analyticsData.analyticsEnabled) {
        _loadAnalytics(
          analyticsData.analyticsServerType,
          analyticsData.analyticsServerHost,
          analyticsData.analyticsServerKey,
          analyticsData.analyticsUserId,
        );
      }
      _loadLiveChat(analyticsData.analyticsEnabled, analyticsData.analyticsServerType);
    });
    analyticsProps.fail(function () {
      console.log("couldn't retrieve analytics properties");
    });
  }
});

/*
 * Wrapper over analytics.js in case we swap provider in future.
 *
 * name - the event name, mandatory
 * properties - optional object of key-value pairs with additional data about the event
 */
RS.trackEvent = function (name, properties) {
  if (typeof analytics !== "undefined") {
    properties = properties || {};
    analytics.track(name, properties);
  }
};
