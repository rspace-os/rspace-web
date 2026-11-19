import Typography from "@mui/material/Typography";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

/**
 * Explains, once above the fields, what a display condition does to mandatory
 * fields and to the values of hidden fields.
 */
export default function DisplayConditionsNote(): ReactNode {
  const { t } = useTranslation("common");
  return (
    <Typography variant="body2" color="text.secondary">
      {t("fieldEditor.condition.explanation")}
    </Typography>
  );
}
