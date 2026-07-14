import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import useUiPreference, { PREFERENCES } from "@/hooks/api/useUiPreference";
import ElnRecordPicker from "@/Inventory/components/Fields/Link/ElnRecordPicker";

export type DocumentationSelection = { globalId: string; name: string } | null;

/**
 * Shared optional step: link the new records to an ELN document (a SOP) via an IsDocumentedBy link.
 * The chosen document can be remembered as this user's default for this operation (persisted in the
 * free-form UI_JSON_SETTINGS preference, keyed by operation key, so no per-operation backend code).
 * When a default exists, the checkbox is pre-checked and the document is applied automatically, so
 * the user just sees the remembered document and can pick a different one.
 */
export default function DocumentationStep({
  operationKey,
  value,
  onChange,
}: {
  operationKey: string;
  value: DocumentationSelection;
  onChange: (value: DocumentationSelection) => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const [pickerOpen, setPickerOpen] = React.useState(false);
  const [defaults, setDefaults] = useUiPreference<Record<string, DocumentationSelection>>(
    PREFERENCES.INVENTORY_OPERATION_DOC_DEFAULTS,
    { defaultValue: {} },
  );
  // Only accept a well-formed {globalId, name}: the stored shape has changed, so ignore anything
  // else (e.g. a value persisted by an earlier version) rather than applying a broken selection.
  const stored = defaults?.[operationKey] as { globalId?: unknown; name?: unknown } | null | undefined;
  const remembered: DocumentationSelection =
    stored && typeof stored.globalId === "string" && typeof stored.name === "string"
      ? { globalId: stored.globalId, name: stored.name }
      : null;
  const [remember, setRemember] = React.useState(remembered !== null);

  // Apply the user's remembered document as the initial selection when the step first opens with
  // nothing chosen yet, so a remembered default shows up ready to use.
  React.useEffect(() => {
    if (!value && remembered) onChange(remembered);
  }, []);

  const persist = (selection: DocumentationSelection) => setDefaults({ ...defaults, [operationKey]: selection });

  const choose = (globalId: string, name: string) => {
    const selection = { globalId, name };
    onChange(selection);
    setPickerOpen(false);
    if (remember) persist(selection);
  };

  const clear = () => {
    onChange(null);
    if (remember) persist(null);
  };

  const toggleRemember = (checked: boolean) => {
    setRemember(checked);
    // Checking remembers the current selection (if any); unchecking forgets the stored default.
    if (checked) {
      if (value) persist(value);
    } else {
      persist(null);
    }
  };

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
        {value ? <Button onClick={clear}>{t("common:actions.clear")}</Button> : null}
      </Stack>
      <FormControlLabel
        control={<Checkbox checked={remember} onChange={(e) => toggleRemember(e.target.checked)} />}
        label={t("operations.documentation.remember")}
      />
      <ElnRecordPicker
        open={pickerOpen}
        onPick={(target) => choose(target.globalId, target.name)}
        onCancel={() => setPickerOpen(false)}
      />
    </Stack>
  );
}
