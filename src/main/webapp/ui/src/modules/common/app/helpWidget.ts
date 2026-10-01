export const LIGHTHOUSE_SCRIPT_ID = "rspace-lighthouse";
export const LIGHTHOUSE_SCRIPT_SRC = "https://lighthouse.helpdocs.io/load";

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

export function createLighthouseConfig({
  brand,
  contactButton,
  searchPlaceholder,
  viewAll,
  suggestedArticles,
  onReady,
  onShow,
  onLoad,
  onHide,
}: {
  brand: string;
  contactButton: string;
  searchPlaceholder: string;
  viewAll: string;
  suggestedArticles: string;
  onReady: () => void;
  onShow: () => void;
  onLoad: () => void;
  onHide: () => void;
}): LighthouseConfig {
  return {
    widget_key: "anqvq7xcs3n2jzflnzp7",
    logo: "/images/helplogo.svg",
    launcher_button_image: "/images/helplogo.svg",
    brand,
    color_mode: "light",
    disable_authorship: true,
    suggestions: ["article:pfsj1e1u7j", "article:xw0ds8tee1", "article:bzgr8ea9e3", "article:dagfzhl3yw"],
    i18n: {
      contact_button: contactButton,
      search_placeholder: searchPlaceholder,
      view_all: viewAll,
      suggested_articles: suggestedArticles,
    },
    onReady,
    onShow,
    onLoad,
    onHide,
  };
}
