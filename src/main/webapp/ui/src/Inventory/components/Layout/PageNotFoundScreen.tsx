import Box from "@mui/material/Box";
import type React from "react";
import { useTranslation } from "react-i18next";

export default function PageNotFoundScreen(): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <Box sx={{ p: 1 }}>
      <h1>{t("layout.pageNotFound.heading")}</h1>
      <p>{t("layout.pageNotFound.message")}</p>
    </Box>
  );
}
