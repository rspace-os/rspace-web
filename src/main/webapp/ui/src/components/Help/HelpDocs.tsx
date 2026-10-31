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
import { configureLighthouse, LIGHTHOUSE_SCRIPT_SRC, showLighthouse } from "@/modules/common/app/lighthouse";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { Menu } from "../DialogBoundary";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

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

  const hasExtraHelpLinks = uiNavigationData.tag === "success" && uiNavigationData.value.extraHelpLinks.length > 0;

  useEffect(() => {
    void (async () => {
      const {
        data: { livechatEnabled, livechatServerKey },
      } = await api.current.get<{
        livechatEnabled: boolean;
        livechatServerKey?: string;
      }>("/livechatProperties");
      configureLighthouse({
        livechatProperties: { livechatEnabled, livechatServerKey },
        onReady: () => setLighthouseIsLoaded(true),
      });
      loadScript(`${LIGHTHOUSE_SCRIPT_SRC}?t=${Date.now()}`);
    })();
  }, []);

  const _showLighthouse = () => {
    trackEvent("NeedHelpClicked");
    // To show Lighthouse panel, Lighthouse button must be shown first
    showLighthouse();
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
