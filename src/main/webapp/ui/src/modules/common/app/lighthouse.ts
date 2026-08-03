import Intercom from "@intercom/messenger-js-sdk";
import * as React from "react";
import i18n from "@/modules/common/i18n";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import { createLighthouseConfig, LIGHTHOUSE_SCRIPT_ID, LIGHTHOUSE_SCRIPT_SRC } from "./helpWidget";
import type { LivechatProperties } from "./queries/livechatProperties";

export { LIGHTHOUSE_SCRIPT_ID, LIGHTHOUSE_SCRIPT_SRC };

type LighthouseAction = "hide" | "show" | "showButton" | "hideButton";

let intercomHandlersRegistered = false;
let currentLivechatProperties: LivechatProperties | undefined;
let lighthouseReadyCallback: (() => void) | undefined;
const helpDocsFallback = commonEn.helpDocs;

export function callLighthouse(action: LighthouseAction) {
  const lighthouseAction = window.Lighthouse?.[action];
  if (typeof lighthouseAction === "function") {
    lighthouseAction();
  }
}

export function isLighthouseReady() {
  return typeof window.Lighthouse?.show === "function" && typeof window.Lighthouse.showButton === "function";
}

function registerIntercomHandlers() {
  if (!window.Intercom || intercomHandlersRegistered) return;

  window.Intercom("onShow", () => {
    callLighthouse("hide");
    callLighthouse("showButton");
  });
  window.Intercom("onHide", () => {
    callLighthouse("show");
  });

  intercomHandlersRegistered = true;
}

function installLighthouseConfig() {
  window.hdlh = createLighthouseConfig({
    brand: i18n.t("helpDocs.brand", { defaultValue: helpDocsFallback.brand }),
    contactButton: i18n.t("helpDocs.chatWithUs", { defaultValue: helpDocsFallback.chatWithUs }),
    searchPlaceholder: i18n.t("helpDocs.searchPlaceholder", { defaultValue: helpDocsFallback.searchPlaceholder }),
    viewAll: i18n.t("helpDocs.viewAllArticles", { defaultValue: helpDocsFallback.viewAllArticles }),
    suggestedArticles: i18n.t("helpDocs.suggestedArticles", { defaultValue: helpDocsFallback.suggestedArticles }),
    onReady() {
      registerIntercomHandlers();
      if (window.Intercom) {
        callLighthouse("showButton");
      }
      lighthouseReadyCallback?.();
    },
    onShow() {
      window.Intercom?.("hide");
    },
    onLoad() {
      callLighthouse("hideButton");
    },
    onHide() {
      callLighthouse("hideButton");
    },
  });

  window.hdlh.onNavigate = ({ page }: { page: string }) => {
    if (page !== "contact" || currentLivechatProperties?.livechatEnabled) return;

    window.open("mailto:support@researchspace.com", "_blank");
    callLighthouse("hide");
  };
}

function configureLighthouse({
  livechatProperties,
  onReady,
}: {
  livechatProperties: LivechatProperties;
  onReady: () => void;
}) {
  currentLivechatProperties = livechatProperties;
  lighthouseReadyCallback = onReady;
  installLighthouseConfig();

  if (!livechatProperties.livechatEnabled) {
    return;
  }

  Intercom({
    app_id: livechatProperties.livechatServerKey ?? "",
    user_id: livechatProperties.currentUser,
    hide_default_launcher: true,
  });
}

if (typeof window !== "undefined") {
  installLighthouseConfig();
  i18n.on("loaded", () => installLighthouseConfig());
}

export function loadLighthouseSdk(livechatProperties: LivechatProperties, onReady: () => void) {
  configureLighthouse({ livechatProperties, onReady });
}

export function showLighthouse() {
  callLighthouse("showButton");
  callLighthouse("show");
}

export function useLighthouseSdk(livechatProperties: LivechatProperties | undefined) {
  const [lighthouseReady, setLighthouseReady] = React.useState(isLighthouseReady);

  React.useEffect(() => {
    if (!livechatProperties) return;

    loadLighthouseSdk(livechatProperties, () => {
      setLighthouseReady(isLighthouseReady());
    });

    if (isLighthouseReady()) {
      setLighthouseReady(true);
    }
  }, [livechatProperties]);

  return {
    lighthouseReady,
    showLighthouse,
  };
}
