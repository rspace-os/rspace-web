/*
 * Add this script to configure and load the Helpdocs Lighthouse help button
 */

/*
 * Global utilities for dealing with Lighthouse.
 * Originally located here instead of global.js because global.js is not loaded
 * by Inventory, but this is not loaded by Inventory either anymore. This
 * separation is purely for historical reasons.
 */

RS.waitForCondition = function (
  conditionFactory,
  timeoutInS,
  exceededTimeoutMessage
) {
  const counterLimit = (timeoutInS * 1000) / 100;
  let counter = 0;

  return new Promise((resolve, reject) => {
    const timerId = setInterval(() => {
      if (!conditionFactory() && counter < counterLimit) {
        counter += 1;
        return;
      }
      if (counter >= counterLimit) {
        console.error(exceededTimeoutMessage);
        clearInterval(timerId);
        reject();
      } else {
        clearInterval(timerId);
        resolve();
      }
    }, 100);
  });
};

RS.waitForLighthouse = function () {
  return RS.waitForCondition(
    () => typeof Lighthouse !== "undefined",
    10,
    "Lighthouse failed to load within 10 seconds"
  );
};

RS.hideHelpButton = function () {
  return RS.waitForLighthouse().then(() => {
    Lighthouse.hideButton();
  });
};

RS.showHelpButton = function () {
  return RS.waitForLighthouse().then(() => {
    Lighthouse.showButton();
  });
};

/*
 * Configure and load Helpdocs Lighthouse
 */

// Construct help button image URL.
const _questionMarkImageUrl = (() => {
  const nth_occurrence = function (str, char, nth) {
    let occ_count = 0;
    for (let i = 0; i < str.length; i++) {
      if (str.charAt(i) === char) {
        if (occ_count + 1 === nth) {
          return i;
        }
        occ_count += 1;
      }
    }
    return -1;
  };

  const currentServerUrl = window.location.href.slice(
    0,
    nth_occurrence(window.location.href, "/", 3) // First two '/' are part of http(s)://
  );
  return currentServerUrl + "/images/helplogo.svg";
})();

/*
 * Pages that wish to not show Lighthouse until the user has requeseted help
 * should set `hideHelpButtonForever` to true. Originally, this was for
 * Inventory, but now that uses a React solution to display Lighthouse.
 */
const _shouldHideHelpButtonForever = function () {
  return (
    typeof window.hideHelpButtonForever !== "undefined" &&
    window.hideHelpButtonForever
  );
};

// HelpDocs Lighthouse configuration
window.hdlh = {
  widget_key: "anqvq7xcs3n2jzflnzp7",
  logo: _questionMarkImageUrl,
  launcher_button_image: _questionMarkImageUrl,
  brand: "Support",
  color_mode: "light",
  disable_authorship: true,
  suggestions: [
    "article:pfsj1e1u7j",
    "article:xw0ds8tee1",
    "article:bzgr8ea9e3",
    "article:dagfzhl3yw",
  ],
  i18n: {
    contact_button: "Chat with us",
    search_placeholder: "Type to search for articles...",
    view_all: "View All Articles",
    suggested_articles: "Suggested Articles",
  },
  // Lighthouse opens the native Intercom popup and hides itself for some mystical reason
  onReady: function () {
    if (typeof Intercom !== "undefined") {
      Intercom("onShow", function () {
        Lighthouse.hide();
        Lighthouse.showButton();
      });
      Intercom("onHide", function () {
        // Show again for identical behaviour between ELN and Inventory
        Lighthouse.show();
      });

      if (
        _shouldHideHelpButtonForever() &&
        document.getElementById("intercom-container")
      ) {
        Lighthouse.showButton();
      }
    }
  },
  onShow: function () {
    if (typeof Intercom !== "undefined") Intercom("hide");
  },
  // In Inventory, the Lighthouse help button is hidden
  onLoad: function () {
    if (_shouldHideHelpButtonForever()) Lighthouse.hideButton();
  },
  onHide: function () {
    if (_shouldHideHelpButtonForever()) Lighthouse.hideButton();
  },
};

/*
 * Lighthouse needs Intercom to be in global scope before being loaded, otherwise
 * the integration between Lighthouse and intercom will not work.
 *
 * Intercom is loaded through Segment, which means that when analytics are disabled,
 * Intercom will not exist for analytics OR chat support. In that case, we allow users
 * to contact support@researchsapce.com through a contact form with their details pre-set.
 */
document.addEventListener(
  "livechatEnabled",
  function (event) {
    const livechatEnabled = event.detail.value;

    const _loadLighthouse = function () {
      // Load Lighthouse
      (function (h, d) {
        var s = d.createElement("script");
        s.type = "text/javascript";
        s.async = true;
        s.defer = true;
        s.src = h + "?t=" + new Date().getTime();
        d.head.appendChild(s);
      })("https://lighthouse.helpdocs.io/load", document);
    };
    const _setContactSettingsAndLoadLighthouse = function () {
      const getNameAndEmail = $.get("/session/ajax/fullNameAndEmail");
      getNameAndEmail.done(function (response) {
        window.hdlh.user = {
          name: response?.data?.fullName,
          email: response?.data?.email,
        };
        window.hdlh.onNavigate = ({page}) => {
          if(page !== "contact") return;
          window.open('mailto:support@researchspace.com', '_blank');
          Lighthouse.hide();
        };
        _loadLighthouse();
      });
    };

    if (livechatEnabled) {
      RS.waitForCondition(
        () => typeof Intercom !== "undefined",
        10,
        "Intercom failed to load within 10 seconds"
      )
        .then(() => {
          _loadLighthouse();
        })
        .catch(() => {
          _setContactSettingsAndLoadLighthouse();
        });
    } else {
      _setContactSettingsAndLoadLighthouse();
    }
  },
  false
);
