import { ThemeProvider, useTheme } from "@mui/material/styles";
import React from "react";
import createAccentedTheme, { type AccentColor } from "@/accentedTheme";

export default function DMPDialogThemeProvider({
  accentColor,
  children,
}: {
  accentColor: AccentColor;
  children: React.ReactNode;
}): React.ReactNode {
  const galleryTheme = useTheme();
  const dialogTheme = React.useMemo(() => {
    const theme = createAccentedTheme(accentColor);
    return {
      ...theme,
      zIndex: { ...theme.zIndex, modal: galleryTheme.zIndex.modal + 1 },
    };
  }, [accentColor, galleryTheme.zIndex.modal]);

  return <ThemeProvider theme={dialogTheme}>{children}</ThemeProvider>;
}
