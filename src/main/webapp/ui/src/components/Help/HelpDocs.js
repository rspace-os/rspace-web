//@flow strict

import React, {
  type Node,
  useEffect,
  useState,
  useContext,
  type ComponentType,
} from "react";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { when } from "mobx";
import { observer } from "mobx-react-lite";

type HelpDocsArgs = {|
  /**
   * This inner component is for rendering a button that triggers the opening
   * of the popup. This is so that the HelpDocs component can be used in various
   * different UIs, from sidebars to FABs.
   *
   * Note: if the HelpDocs component is not rendered inside of an Analytics
   * context then disabled will always be true.
   */
  Action: ({| onClick: () => void, disabled: boolean |}) => Node,
|};

function loadScript(url: string): void {
  const s = document.createElement("script");
  s.type = "text/javascript";
  s.async = true;
  s.defer = true;
  s.src = url;
  if (document.head) {
    document.head.appendChild(s);
  } else {
    throw new Error("Page is in invalid state");
  }
}

/**
 * Displays the HelpDocs popup window.
 */
function HelpDocs({ Action }: HelpDocsArgs): Node {
  const analyticsContext = useContext(AnalyticsContext);
  const { trackEvent } = analyticsContext;

  // this is simply to trigger a re-render once lighthouse is loaded
  const [_lighthouseIsLoaded, setLighthouseIsLoaded] = useState(false);

  async function loadLighthouse(): Promise<void> {
    await when(() => analyticsContext.isAvailable !== null);
    // $FlowFixMe[incompatible-type] analyticsContext.isAvailable cannot be null now
    const isAvailable: boolean = analyticsContext.isAvailable;
    if (!isAvailable) {
      /*
       * Analytics is disabled on many deployments due to GDPR and other data
       * privacy concerns of many customers. When this is the case, we disable
       * the intercom functionality that handles personal identifiable
       * information, such as the contact-us form.
       */
      window.hdlh.onNavigate = ({ page }) => {
        if (page !== "contact") return;
        window.open("mailto:support@researchspace.com", "_blank");
        window.Lighthouse.hide();
      };
    }

    loadScript("https://lighthouse.helpdocs.io/load?t=" + new Date().getTime());
  }

  useEffect(() => {
    void loadLighthouse();
  }, []);

  useEffect(() => {
    // HelpDocs Lighthouse configuration
    window.hdlh = {
      widget_key: "anqvq7xcs3n2jzflnzp7",
      logo: "/images/helplogo.svg",
      launcher_button_image: "/images/helplogo.svg",
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
      onReady: function () {
        if (typeof window.Intercom !== "undefined") {
          const intercom = window.Intercom;
          intercom("onShow", function () {
            window.Lighthouse.hide();
            window.Lighthouse.showButton();
          });
          intercom("onHide", function () {
            window.Lighthouse.show();
          });
          if (document.getElementById("intercom-container")) {
            window.Lighthouse.showButton();
          }
        }
        setLighthouseIsLoaded(true);
      },
      onShow: function () {
        if (typeof window.Intercom !== "undefined") {
          const intercom = window.Intercom;
          intercom("hide");
        }
      },
      onLoad: function () {
        window.Lighthouse.hideButton();
      },
      onHide: function () {
        window.Lighthouse.hideButton();
      },
    };
  }, []);

  return (
    <>
      <Action
        disabled={!window.Lighthouse}
        onClick={() => {
          // To show Lighthouse panel, Lighthouse button must be shown first
          window.Lighthouse.showButton();
          window.Lighthouse.show();
          trackEvent("NeedHelpClicked");
        }}
      />
    </>
  );
}

export default (observer(HelpDocs): ComponentType<HelpDocsArgs>);
