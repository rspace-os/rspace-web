import Intercom from "@intercom/messenger-js-sdk";
import * as React from "react";
import i18n from "@/modules/common/i18n";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";

export const LIGHTHOUSE_SCRIPT_ID = "rspace-lighthouse";
export const LIGHTHOUSE_SCRIPT_SRC = "https://lighthouse.helpdocs.io/load";
const HELP_DOCS_FALLBACK_URL = "https://researchspace.helpdocs.io";

type LighthouseConfig = {
  widget_key: string;
  logo: string;
  launcher_button_image: string;
  brand: string;
  color_mode: string;
  disable_authorship: boolean;
  suggestions: string[];
  i18n: {
    contact_button: string;
    search_placeholder: string;
    view_all: string;
    suggested_articles: string;
  };
  onReady: () => void;
  onShow: () => void;
  onLoad: () => void;
  onHide: () => void;
  onNavigate?: ({ page }: { page: string }) => void;
};

declare global {
  interface Window {
    Lighthouse: {
      hide: () => void;
      show: () => void;
      showButton: () => void;
      hideButton: () => void;
    };
    hdlh: LighthouseConfig;
  }
}

export type LivechatProperties = {
  livechatEnabled: boolean;
  livechatServerKey?: string;
};

type LighthouseAction = "hide" | "show" | "showButton" | "hideButton";

let intercomHandlersRegistered = false;
let currentLivechatProperties: LivechatProperties | undefined;
let lighthouseReadyCallback: (() => void) | undefined;

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
  // The widget reads configuration before async i18n initialization; loaded events replace this fallback.
  const words =
    i18n.isInitialized && i18n.hasLoadedNamespace("common")
      ? {
          brand: i18n.t("common:helpDocs.brand"),
          chatWithUs: i18n.t("common:helpDocs.chatWithUs"),
          searchPlaceholder: i18n.t("common:helpDocs.searchPlaceholder"),
          viewAllArticles: i18n.t("common:helpDocs.viewAllArticles"),
          suggestedArticles: i18n.t("common:helpDocs.suggestedArticles"),
        }
      : commonEn.helpDocs;
  window.hdlh = {
    widget_key: "anqvq7xcs3n2jzflnzp7",
    logo: "/images/helplogo.svg",
    launcher_button_image: "/images/helplogo.svg",
    brand: words.brand,
    color_mode: "light",
    disable_authorship: true,
    suggestions: ["article:pfsj1e1u7j", "article:xw0ds8tee1", "article:bzgr8ea9e3", "article:dagfzhl3yw"],
    i18n: {
      contact_button: words.chatWithUs,
      search_placeholder: words.searchPlaceholder,
      view_all: words.viewAllArticles,
      suggested_articles: words.suggestedArticles,
    },
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
    onNavigate({ page }: { page: string }) {
      if (page !== "contact" || currentLivechatProperties?.livechatEnabled) return;

      window.open("mailto:support@researchspace.com", "_blank");
      callLighthouse("hide");
    },
  };
}

export function configureLighthouse({
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
    hide_default_launcher: true,
  });
}

if (typeof window !== "undefined") {
  installLighthouseConfig();
  i18n.on("loaded initialized languageChanged", () => installLighthouseConfig());
}

export function showLighthouse() {
  if (!isLighthouseReady()) {
    window.open(HELP_DOCS_FALLBACK_URL, "_blank", "noopener,noreferrer");
    return;
  }

  callLighthouse("showButton");
  callLighthouse("show");
}

export function useLighthouseSdk(livechatProperties: LivechatProperties | undefined) {
  const [lighthouseReady, setLighthouseReady] = React.useState(isLighthouseReady);

  React.useEffect(() => {
    if (!livechatProperties) return;

    configureLighthouse({
      livechatProperties,
      onReady: () => {
        setLighthouseReady(isLighthouseReady());
      },
    });

    if (!document.getElementById(LIGHTHOUSE_SCRIPT_ID)) {
      const script = document.createElement("script");
      script.id = LIGHTHOUSE_SCRIPT_ID;
      script.src = LIGHTHOUSE_SCRIPT_SRC;
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }

    if (isLighthouseReady()) {
      setLighthouseReady(true);
    }
  }, [livechatProperties]);

  return {
    lighthouseReady,
    showLighthouse,
  };
}
