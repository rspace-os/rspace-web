import CardMedia from "@mui/material/CardMedia";
import type React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR } from "../../assets/branding/argos";
import ArgosIcon from "../../assets/branding/argos/logo.svg";
import AccentMenuItem from "../../components/AccentMenuItem";

type ArgosAccentMenuItemArgs = {
  /** Called when the user picks this source; the caller opens the dialog. */
  onSelect: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from Argos.
 */
export default function ArgosMenuItem({ onSelect }: ArgosAccentMenuItemArgs): React.ReactNode {
  const { t } = useTranslation("apps");

  return (
    <AccentMenuItem
      title={t("dmpIntegrations.argos")}
      avatar={<CardMedia image={ArgosIcon} />}
      backgroundColor={LOGO_COLOR}
      foregroundColor={{ ...LOGO_COLOR, lightness: 28 }}
      onClick={() => {
        onSelect();
      }}
      aria-haspopup="dialog"
      compact
    />
  );
}
