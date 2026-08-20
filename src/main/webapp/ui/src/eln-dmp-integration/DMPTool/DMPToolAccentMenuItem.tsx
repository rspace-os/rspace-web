import CardMedia from "@mui/material/CardMedia";
import type React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../assets/branding/dmptool";
import DMPToolIcon from "../../assets/branding/dmptool/logo.svg";
import AccentMenuItem from "../../components/AccentMenuItem";

type DMPToolAccentMenuItemArgs = {
  /** Called when the user picks this source; the caller opens the dialog. */
  onSelect: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from DMPTool.
 */
export default function DMPToolAccentMenuItem({ onSelect }: DMPToolAccentMenuItemArgs): React.ReactNode {
  const { t } = useTranslation("apps");

  return (
    <AccentMenuItem
      title={t("dmpIntegrations.dmptool")}
      avatar={<CardMedia image={DMPToolIcon} />}
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
