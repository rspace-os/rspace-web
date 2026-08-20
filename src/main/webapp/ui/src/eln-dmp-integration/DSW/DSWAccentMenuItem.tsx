import CardMedia from "@mui/material/CardMedia";
import React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../assets/branding/dsw";
import DSWIcon from "../../assets/branding/dsw/logo.svg";
import AccentMenuItem from "../../components/AccentMenuItem";
import AnalyticsContext from "../../stores/contexts/Analytics";

export type DswConfig = {
  DSW_APIKEY: string;
  DSW_URL: string;
  DSW_ALIAS: string;
};

type DSWAccentMenuItemArgs = {
  /** Called when the user picks this connection; the caller opens the dialog. */
  onSelect: () => void;
  connection: DswConfig;
};

/**
 * The menu item for the create menu for importing DMPs from DSW/FAIR Wizard.
 */
export default function DSWAccentMenuItem({ onSelect, connection }: DSWAccentMenuItemArgs): React.ReactNode {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { t } = useTranslation("apps");

  return (
    <AccentMenuItem
      title={connection.DSW_ALIAS}
      subheader={t("dmpIntegrations.dsw")}
      avatar={<CardMedia image={DSWIcon} />}
      backgroundColor={LOGO_COLOR}
      foregroundColor={{ ...LOGO_COLOR, lightness: 30 }}
      onClick={() => {
        trackEvent("user:open:dsw_import:gallery");
        onSelect();
      }}
      aria-haspopup="dialog"
      compact
    />
  );
}
