import WarningIcon from "@mui/icons-material/Warning";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";

/**
 * This component is for showing a warning label just above a dialog's submit
 * button that there are unsaved changes to the contents of the dialog that
 * will be lost if the user closes the dialog without submitting.
 */
export default function WarningBar(): React.ReactNode {
  const { t } = useTranslation("common");

  return (
    <Grid
      container
      spacing={1}
      sx={{
        justifyContent: "flex-end",
        alignItems: "center",
        mt: 0.5,
        pr: 1,
        color: "warningRed",
      }}
    >
      <WarningIcon />
      <Typography variant="caption">{t("warningBar.unsavedChanges")}</Typography>
    </Grid>
  );
}
