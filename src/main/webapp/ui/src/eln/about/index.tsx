import Typography from "@mui/material/Typography";
import type * as React from "react";
import { useTranslation } from "react-i18next";
import { AboutRSpaceContent } from "../../components/AppBar/AboutRSpaceDialog";

export function AboutPage(): React.ReactElement {
  const { t } = useTranslation("about");
  return (
    <>
      <Typography variant="h4" component="h1" align="center" gutterBottom>
        {t("title")}
      </Typography>
      <AboutRSpaceContent />
    </>
  );
}
