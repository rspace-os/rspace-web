import CardMedia from "@mui/material/CardMedia";
import type React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../assets/branding/dmpassistant";
import DMPAssistantIcon from "../../assets/branding/dmpassistant/logo.svg";
import AccentMenuItem from "../../components/AccentMenuItem";

type DMPAssistantAccentMenuItemArgs = {
  /** Called when the user picks this source; the caller opens the dialog. */
  onSelect: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from DMP Assistant.
 */
export default function DMPAssistantAccentMenuItem({ onSelect }: DMPAssistantAccentMenuItemArgs): React.ReactNode {
  const { t } = useTranslation("apps");

  return (
    <AccentMenuItem
      title={t("dmpIntegrations.dmpAssistant")}
      avatar={<CardMedia image={DMPAssistantIcon} />}
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
