import Alert from "@mui/material/Alert";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import TemplatePicker from "@/Inventory/components/Picker/TemplatePicker";
import type TemplateModel from "@/stores/models/TemplateModel";
import { templateSelectionBlock } from "./templateResolution";

/** The user's template choice for the new sample (adr/0003). */
export type TemplateSelection = {
  // "remembered" = a specific template restored from the saved default: shown as a banner with no
  // radio selected until the user picks a radio to override it.
  mode: "none" | "pick" | "fromSample" | "remembered";
  templateId: number | null;
  templateName?: string;
  remember: boolean;
};

/**
 * Framework step (present for every operation): optionally choose the new sample's template - none
 * (ad-hoc), an existing template, or a template created from the origin's parent sample. When the
 * user picks an existing template, it is validated up front: a template with mandatory fields that
 * have no default value is blocked here with a clear message, rather than failing at submit
 * (adr/0003). The "remember" checkbox is persisted per user, per operation by the wizard.
 */
function TemplateStep({
  value,
  onChange,
  originSampleName,
}: {
  value: TemplateSelection;
  onChange: (value: TemplateSelection) => void;
  originSampleName: string;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [checking, setChecking] = React.useState(false);
  const [blockError, setBlockError] = React.useState<string | null>(null);

  const setMode = (mode: TemplateSelection["mode"]) => {
    setBlockError(null);
    onChange({
      ...value,
      mode,
      templateId: mode === "pick" ? value.templateId : null,
      templateName: mode === "pick" ? value.templateName : undefined,
    });
  };

  const onPickTemplate = (template: TemplateModel) => {
    // Clear the selection until the template is validated, so Next stays disabled meanwhile.
    setBlockError(null);
    setChecking(true);
    onChange({ ...value, templateId: null, templateName: template.name });
    void (async () => {
      try {
        await template.fetchAdditionalInfo();
        const fields = template.fields.map((f) => ({
          name: f.name,
          mandatory: f.mandatory,
          hasDefault:
            (f.selectedOptions?.length ?? 0) > 0 ||
            (f.content !== null && f.content !== undefined && String(f.content).trim() !== ""),
        }));
        const { blocked, missingFields } = templateSelectionBlock(fields);
        if (blocked) {
          setBlockError(t("operations.template.mandatoryFieldsError", { fields: missingFields.join(", ") }));
        } else {
          onChange({ ...value, templateId: Number(template.id), templateName: template.name });
        }
      } finally {
        setChecking(false);
      }
    })();
  };

  // Hand the picker a referentially stable callback (latest impl via ref). The picker fires this
  // from an effect keyed partly on the callback's identity, so a fresh function each render would
  // retrigger it in an infinite loop once a template is the active result.
  const onPickTemplateRef = React.useRef(onPickTemplate);
  onPickTemplateRef.current = onPickTemplate;
  const handlePickTemplate = React.useCallback((template: TemplateModel) => onPickTemplateRef.current(template), []);

  return (
    <Stack spacing={1}>
      {value.mode === "remembered" && value.templateName ? (
        <Alert severity="info" data-testid="SelectedTemplateName">
          {`${t("operations.template.selectedLabel")}: ${value.templateName}`}
        </Alert>
      ) : null}
      <Typography variant="body2">{t("operations.template.description")}</Typography>
      {/* An empty value means no radio is selected (the remembered template is in effect); picking a
          radio overrides it. */}
      <RadioGroup
        value={value.mode === "remembered" ? "" : value.mode}
        onChange={(e) => setMode(e.target.value as TemplateSelection["mode"])}
      >
        <FormControlLabel value="none" control={<Radio />} label={t("operations.template.none")} />
        <FormControlLabel value="pick" control={<Radio />} label={t("operations.template.pick")} />
        <FormControlLabel
          value="fromSample"
          control={<Radio />}
          label={t("operations.template.fromSample", { name: originSampleName })}
        />
      </RadioGroup>
      {value.mode === "pick" ? (
        <>
          <TemplatePicker setTemplate={handlePickTemplate} />
          {checking ? <Typography variant="body2">{t("operations.template.checking")}</Typography> : null}
          {blockError ? (
            <Typography variant="body2" color="error">
              {blockError}
            </Typography>
          ) : null}
        </>
      ) : null}
      <FormControlLabel
        control={
          <Checkbox checked={value.remember} onChange={(e) => onChange({ ...value, remember: e.target.checked })} />
        }
        label={t("operations.template.remember")}
      />
    </Stack>
  );
}

export default observer(TemplateStep);
