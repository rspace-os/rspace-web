// username of current user initialised from default.jsp,
// but not from .../inventory/start.html
if (typeof window.currentUser === "undefined") {
  window.currentUser = "";
}

_loadAnalytics = function (analyticsServerType, analyticsServerHost, analyticsServerKey, analyticsUserId) {
  console.log("Loading analytics");

  if (analyticsServerType === "posthog") {
    if (!window.analytics) {
      window.analytics = {
        _queue: [],
        track: function (event, data) {
          this._queue.push([event, data]);
        },
      };
    }

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

    // there is no way to have segment and not have intercom
    document.dispatchEvent(
      new CustomEvent("livechatEnabled", {
        detail: { value: true },
      })
    );
  } catch (e) {
    console.warn("Analytics.js exception at: " + document.URL);
    console.log(e);
  }
};

$(document).ready(function () {
  const analyticsProps = $.get("/session/ajax/analyticsProperties");
  analyticsProps.done(function (analyticsData) {
    if (analyticsData.analyticsEnabled) {
      _loadAnalytics(
        analyticsData.analyticsServerType,
        analyticsData.analyticsServerHost,
        analyticsData.analyticsServerKey,
        analyticsData.analyticsUserId,
      );
    }
  });
  analyticsProps.fail(function () {
    console.log("couldn't retrieve analytics properties");
  });
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
