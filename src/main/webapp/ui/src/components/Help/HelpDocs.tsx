import { Intercom } from "@intercom/messenger-js-sdk";
import HelpIcon from "@mui/icons-material/Help";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import { observer } from "mobx-react-lite";
import React, { useContext, useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import useUiNavigationData from "@/components/AppBar/useUiNavigationData";
import IconButtonWithTooltip from "@/components/IconButtonWithTooltip";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { Menu } from "../DialogBoundary";

declare global {
  interface Window {
    Lighthouse: {
      hide: () => void;
      show: () => void;
      showButton: () => void;
      hideButton: () => void;
    };
    hdlh: {
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
  }
}

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

// Fallback destination when the external Lighthouse help widget fails to load.
const RSPACE_DOCS_URL = "https://researchspace.helpdocs.io";

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
function HelpDocs() {
  const { t } = useTranslation("common");
  const uiNavigationData = useUiNavigationData();
  const api = useRef(
    axios.create({
      baseURL: "/session/ajax",
      timeout: ONE_MINUTE_IN_MS,
    }),
  );
  const analyticsContext = useContext(AnalyticsContext);
  const { trackEvent } = analyticsContext;

  const menuId = useId();
  const menuOpenButtonId = useId();

  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- this is simply to trigger a re-render once lighthouse is loaded
  const [_lighthouseIsLoaded, setLighthouseIsLoaded] = useState(false);

  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  function loadLighthouse(livechatEnabled: boolean): void {
    if (!livechatEnabled) {
      /*
       * Analytics is disabled on many deployments due to GDPR and other data
       * privacy concerns of many customers. When this is the case, we disable
       * the intercom functionality that handles personal identifiable
       * information, such as the contact-us form.
       */
      window.hdlh.onNavigate = ({ page }: { page: string }) => {
        if (page !== "contact") return;
        window.open("mailto:support@researchspace.com", "_blank");
        window.Lighthouse.hide();
      };
    }

    loadScript(`https://lighthouse.helpdocs.io/load?t=${Date.now()}`);
  }

  const hasExtraHelpLinks = uiNavigationData.tag === "success" && uiNavigationData.value.extraHelpLinks.length > 0;

  useEffect(() => {
    void (async () => {
      const {
        data: { livechatEnabled, livechatServerKey, livechatUserId },
      } = await api.current.get<{
        livechatEnabled: boolean;
        livechatServerKey: string;
        livechatUserId: string;
      }>("/livechatProperties");
      if (livechatEnabled) {
        Intercom({
          app_id: livechatServerKey,
          user_id: livechatUserId,
          hide_default_launcher: true,
        });
      }
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
      suggestions: ["article:pfsj1e1u7j", "article:xw0ds8tee1", "article:bzgr8ea9e3", "article:dagfzhl3yw"],
      i18n: {
        contact_button: t("helpDocs.chatWithUs"),
        search_placeholder: t("helpDocs.searchPlaceholder"),
        view_all: t("helpDocs.viewAllArticles"),
        suggested_articles: t("helpDocs.suggestedArticles"),
      },
      onReady() {
        try {
          if (typeof window.Intercom !== "undefined") {
            const intercom = window.Intercom;
            (intercom as unknown as (event: "onShow", cb: () => void) => void)("onShow", () => {
              window.Lighthouse.hide();
              window.Lighthouse.showButton();
            });
            (intercom as unknown as (event: "onHide", cb: () => void) => void)("onHide", () => {
              window.Lighthouse.show();
            });
            if (document.getElementById("intercom-container")) {
              window.Lighthouse.showButton();
            }
          }
        } catch (e) {
          // Wiring up the optional Intercom live-chat integration must never
          // block the help button from becoming usable.
          console.warn("Failed to wire up Intercom with the help widget", e);
        }
        setLighthouseIsLoaded(true);
      },
      onShow() {
        if (typeof window.Intercom !== "undefined") {
          const intercom = window.Intercom;
          (intercom as unknown as (action: "hide") => void)("hide");
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

  const _showLighthouse = () => {
    trackEvent("NeedHelpClicked");
    // The Lighthouse widget is loaded from an external script that can fail to
    // initialise on some deployments (e.g. a blocked third-party script). When
    // it isn't available, fall back to opening the documentation site directly
    // so the help link still works.
    if (!window.Lighthouse) {
      window.open(RSPACE_DOCS_URL, "_blank", "noopener,noreferrer");
      return;
    }
    // To show Lighthouse panel, Lighthouse button must be shown first
    window.Lighthouse.showButton();
    window.Lighthouse.show();
  };

  const handleMenuButtonClick = (event: React.MouseEvent<HTMLElement>) => {
    if (!hasExtraHelpLinks) {
      _showLighthouse();

      return;
    }

    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleHelpButtonClicked = () => {
    handleMenuClose();
    _showLighthouse();
  };

  return (
    <div>
      <IconButtonWithTooltip
        id={menuOpenButtonId}
        size="small"
        onClick={handleMenuButtonClick}
        icon={<HelpIcon />}
        title={t("helpDocs.openHelp")}
        aria-controls={open ? menuId : undefined}
        aria-haspopup={hasExtraHelpLinks ? "menu" : undefined}
        aria-expanded={open ? "true" : undefined}
      />
      <Menu
        id={menuId}
        aria-labelledby={menuOpenButtonId}
        anchorEl={anchorEl}
        open={open}
        onClose={handleMenuClose}
        anchorOrigin={{
          vertical: "top",
          horizontal: "left",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "left",
        }}
      >
        {hasExtraHelpLinks &&
          uiNavigationData.value.extraHelpLinks.map(({ label, url }) => (
            <MenuItem key={`${label}${url}`} component="a" href={url} rel="noreferrer">
              <ListItemIcon> </ListItemIcon>
              <ListItemText>{label}</ListItemText>
            </MenuItem>
          ))}
        <Divider />
        <MenuItem onClick={handleHelpButtonClicked}>
          <ListItemIcon>
            <Box
              component="img"
              sx={{ height: 24, width: 24 }}
              alt={t("helpDocs.rspaceAlt")}
              src="/images/icons/rspaceIcon2.svg"
            />
          </ListItemIcon>
          <ListItemText>{t("helpDocs.documentation")}</ListItemText>
        </MenuItem>
      </Menu>
    </div>
  );
}

/**
 * This is component that when the provided Action component is clicked, it
 * opens a popup window that displays the RSpace help documentation. This
 * allows us to centralise the logic for initialising the Lighthouse help
 * widget and the Intercom chat widget.
 */
export default observer(HelpDocs);
