import CardMedia from "@mui/material/CardMedia";
import type React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../assets/branding/dmponline";
import DMPonlineIcon from "../../assets/branding/dmponline/logo.svg";
import AccentMenuItem from "../../components/AccentMenuItem";

type DMPonlineAccentMenuItemArgs = {
  /** Called when the user picks this source; the caller opens the dialog. */
  onSelect: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from DMPonline.
 */
export default function DMPonlineAccentMenuItem({ onSelect }: DMPonlineAccentMenuItemArgs): React.ReactNode {
  const { t } = useTranslation("apps");

  return (
    <AccentMenuItem
      title={t("dmpIntegrations.dmponline")}
      avatar={<CardMedia image={DMPonlineIcon} />}
      backgroundColor={LOGO_COLOR}
      foregroundColor={{ ...LOGO_COLOR, lightness: 30 }}
      onClick={() => {
        onSelect();
      }}
      aria-haspopup="dialog"
      compact
    />
  );
}
