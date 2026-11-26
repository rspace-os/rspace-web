import DarkModeIcon from "@mui/icons-material/DarkMode";
import LightModeIcon from "@mui/icons-material/LightMode";
import SettingsBrightnessIcon from "@mui/icons-material/SettingsBrightness";
import { useColorScheme } from "@mui/material/styles";
import { useTranslation } from "react-i18next";
import { darkModeAvailable, darkModeUnsupported, setColorMode } from "@/colorScheme";
import AccentMenuItem from "../AccentMenuItem";

/**
 * Switches every React root on the page between the light and dark colour
 * schemes, and back to following the system setting once a mode is chosen.
 * Where the browser cannot show the page dark, it says so instead.
 */
export default function ColorModeMenuItem({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation("common");
  const { mode, systemMode } = useColorScheme();
  const resolved = mode === "system" ? systemMode : mode;
  const isDark = resolved === "dark";
  if (darkModeUnsupported) {
    return (
      <AccentMenuItem
        title={t("appBar.switchToDarkMode")}
        subheader={t("appBar.darkModeNeedsNewerBrowser")}
        avatar={<DarkModeIcon />}
        compact
        disabled
      />
    );
  }
  if (!darkModeAvailable) return null;
  return (
    <>
      <AccentMenuItem
        title={isDark ? t("appBar.switchToLightMode") : t("appBar.switchToDarkMode")}
        avatar={isDark ? <LightModeIcon /> : <DarkModeIcon />}
        compact
        onClick={() => {
          setColorMode(isDark ? "light" : "dark");
          onClose();
        }}
      />
      {mode !== undefined && mode !== "system" && (
        <AccentMenuItem
          title={t("appBar.useSystemColorMode")}
          avatar={<SettingsBrightnessIcon />}
          compact
          onClick={() => {
            setColorMode("system");
            onClose();
          }}
        />
      )}
    </>
  );
}
