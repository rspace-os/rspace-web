import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import ElnRecordPicker from "@/Inventory/components/Fields/Link/ElnRecordPicker";

export type DocumentationSelection = { globalId: string; name: string } | null;

/**
 * Shared optional step: link the new records to an ELN document (a SOP) via an IsDocumentedBy link.
 * Fully controlled by the wizard, which owns the chosen document and persists it (with everything
 * else this run) under the single per-process "remember" bundle on Perform (see OperationWizard).
 * This component only presents and edits the current selection.
 */
export default function DocumentationStep({
  value,
  onChange,
}: {
  value: DocumentationSelection;
  onChange: (value: DocumentationSelection) => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const [pickerOpen, setPickerOpen] = React.useState(false);

  return (
    <Stack spacing={1}>
      <Typography variant="body2">{t("operations.documentation.description")}</Typography>
      <Typography variant="body2">
        {value ? t("operations.documentation.selected", { name: value.name }) : t("operations.documentation.none")}
      </Typography>
      <Stack direction="row" spacing={1}>
        <Button variant="outlined" onClick={() => setPickerOpen(true)}>
          {t("operations.documentation.choose")}
        </Button>
        {value ? <Button onClick={() => onChange(null)}>{t("common:actions.clear")}</Button> : null}
      </Stack>
      <ElnRecordPicker
        open={pickerOpen}
        onPick={(target) => {
          onChange({ globalId: target.globalId, name: target.name });
          setPickerOpen(false);
        }}
        onCancel={() => setPickerOpen(false)}
      />
    </Stack>
  );
}
