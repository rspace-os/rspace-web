import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import ElnRecordPicker from "@/Inventory/components/Fields/Link/ElnRecordPicker";

export type DocumentationSelection = { globalId: string; name: string } | null;

/**
 * Shared optional step: link the new records to an ELN document (a SOP) via an IsDocumentedBy link.
 * Fully controlled by the wizard: the wizard owns the chosen document and the "remember" flag, and
 * persists the remembered choice on Perform, keyed by process name where the operation has one (see
 * OperationWizard / processNames.ts). This component only presents and edits the current selection.
 */
export default function DocumentationStep({
  value,
  onChange,
  remember,
  onRememberChange,
  processName,
}: {
  value: DocumentationSelection;
  onChange: (value: DocumentationSelection) => void;
  remember: boolean;
  onRememberChange: (remember: boolean) => void;
  /** The chosen process name, if the operation has one: the "remember" label names it so the user
   *  understands the document is remembered per process name rather than per operation. */
  processName?: string;
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
      <FormControlLabel
        control={<Checkbox checked={remember} onChange={(e) => onRememberChange(e.target.checked)} />}
        label={
          processName
            ? t("operations.documentation.rememberForProcess", { name: processName })
            : t("operations.documentation.remember")
        }
      />
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
