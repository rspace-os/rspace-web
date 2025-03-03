//@flow strict

import React, {
  type Node,
  useEffect,
  useState,
  useContext,
  useRef,
  type ComponentType,
} from "react";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { observer } from "mobx-react-lite";
import axios from "@/common/axios";
import Intercom from "@intercom/messenger-js-sdk";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

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
  const [chatEnabled, setChatEnabled] = useState(false);
  const api = useRef(
    axios.create({
      baseURL: "/session/ajax",
      timeout: ONE_MINUTE_IN_MS,
    })
  );
  const analyticsContext = useContext(AnalyticsContext);
  const { trackEvent } = analyticsContext;

  // this is simply to trigger a re-render once lighthouse is loaded
  const [_lighthouseIsLoaded, setLighthouseIsLoaded] = useState(false);

  function loadLighthouse(livechatEnabled: boolean): void {
    if (!livechatEnabled) {
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
    void (async () => {
      const {
        data: {
          livechatEnabled,
          livechatServerHost,
          livechatServerKey,
          livechatUserId,
        },
      } = await api.current.get<{|
        livechatEnabled: boolean,
        livechatServerHost: string,
        livechatServerKey: string,
        livechatUserId: string,
      |}>("/livechatProperties");
      if (livechatEnabled) {
        Intercom({
          app_id: livechatServerKey,
          user_id: livechatUserId,
          hide_default_launcher: true,
        });
      }
      setChatEnabled(livechatEnabled);
      loadLighthouse(livechatEnabled);
    })();
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
      onReady() {
        if (typeof window.Intercom !== "undefined") {
          const intercom = window.Intercom;
          intercom("onShow", () => {
            window.Lighthouse.hide();
            window.Lighthouse.showButton();
          });
          intercom("onHide", () => {
            window.Lighthouse.show();
          });
          if (document.getElementById("intercom-container")) {
            window.Lighthouse.showButton();
          }
        }
        setLighthouseIsLoaded(true);
      },
      onShow() {
        if (typeof window.Intercom !== "undefined") {
          const intercom = window.Intercom;
          intercom("hide");
        }
      },
      onLoad() {
        window.Lighthouse.hideButton();
      },
      onHide() {
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
