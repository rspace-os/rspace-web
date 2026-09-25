import Alert from "@mui/material/Alert";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import type TemplateModel from "@/stores/models/TemplateModel";
import { type TemplateSelection, templateBlockReason } from "./templateResolution";
import WizardTemplatePicker from "./WizardTemplatePicker";

export type { TemplateSelection };

function TemplateStep({
  value,
  onChange,
  originSampleName,
  parentHasTemplate = true,
  parentTemplateChecking = false,
  parentTemplateError = null,
  rememberedTemplateError = null,
}: {
  value: TemplateSelection;
  onChange: React.Dispatch<React.SetStateAction<TemplateSelection>>;
  originSampleName: string;
  parentHasTemplate?: boolean;
  parentTemplateChecking?: boolean;
  parentTemplateError?: string | null;
  rememberedTemplateError?: string | null;
}): React.ReactNode {
  const { t, i18n } = useTranslation("inventory");
  const [checking, setChecking] = React.useState(false);
  const [blockError, setBlockError] = React.useState<string | null>(null);
  const latestPickRef = React.useRef<string | null>(null);
  React.useEffect(
    () => () => {
      latestPickRef.current = null;
    },
    [],
  );

  const setMode = (mode: TemplateSelection["mode"]) => {
    setBlockError(null);
    latestPickRef.current = null;
    setChecking(false);
    onChange({
      ...value,
      mode,
      templateId: mode === "pick" ? value.templateId : null,
      templateName: mode === "pick" ? value.templateName : undefined,
      quantityCategory: mode === "pick" ? value.quantityCategory : undefined,
    });
  };

  const onPickTemplate = (template: TemplateModel | null) => {
    if (template === null) {
      latestPickRef.current = null;
      setBlockError(null);
      setChecking(false);
      onChange({ ...value, templateId: null, templateName: undefined, quantityCategory: undefined });
      return;
    }
    // Clear the selection until the template is validated, so Next stays disabled meanwhile.
    const pickId = String(template.id);
    latestPickRef.current = pickId;
    setBlockError(null);
    setChecking(true);
    onChange({ ...value, templateId: null, templateName: template.name });
    void (async () => {
      try {
        // The picker's search results are partial, so the fields the check reads are fetched here.
        await template.fetchAdditionalInfo();
        if (latestPickRef.current !== pickId) return;
        const reason = templateBlockReason(template, i18n.resolvedLanguage ?? i18n.language);
        if (reason.blocked) {
          setBlockError(
            t("operations.template.mandatoryFieldsError", {
              count: reason.count,
              fields: reason.fields,
            }),
          );
          return;
        }
        onChange((previous) => ({
          ...previous,
          templateId: Number(template.id),
          templateName: template.name,
          quantityCategory: template.quantityCategory,
        }));
      } catch {
        if (latestPickRef.current !== pickId) return;
        setBlockError(t("operations.template.lookupFailed"));
      } finally {
        if (latestPickRef.current === pickId) setChecking(false);
      }
    })();
  };

  // The Picker fires onAddition from an effect keyed on the callback's identity, so an unstable
  // callback plus a state update is an infinite render loop on selection: the callback below must
  // stay referentially stable, and its empty dependency array is required, not incidental.
  const onPickTemplateRef = React.useRef(onPickTemplate);
  // Written in a layout effect, not the render body: React 19 may discard a render pass, and a
  // body assignment would leave the ref holding that render's closure over a stale `value`. Layout
  // rather than passive, so the ref is current before any child effect can call it.
  React.useLayoutEffect(() => {
    onPickTemplateRef.current = onPickTemplate;
  });
  const handlePickTemplate = React.useCallback(
    (template: TemplateModel | null) => onPickTemplateRef.current(template),
    [],
  );

  return (
    <Stack spacing={1}>
      {value.mode === "remembered" && value.templateName ? (
        <Alert severity="info" data-testid="SelectedTemplateName">
          {t("operations.template.selectedLabel", { name: value.templateName })}
        </Alert>
      ) : null}
      {rememberedTemplateError ? (
        <Alert severity="warning" data-testid="RememberedTemplateError">
          {rememberedTemplateError}
        </Alert>
      ) : null}
      <Typography variant="body2">{t("operations.template.description")}</Typography>
      <RadioGroup
        value={value.mode === "remembered" || value.mode === "unselected" ? "" : value.mode}
        onChange={(e) => setMode(e.target.value as TemplateSelection["mode"])}
      >
        <FormControlLabel
          value="fromSample"
          control={<Radio />}
          disabled={!parentHasTemplate}
          label={t("operations.template.fromSample", { name: originSampleName })}
        />
        <FormControlLabel value="pick" control={<Radio />} label={t("operations.template.pick")} />
        <FormControlLabel value="none" control={<Radio />} label={t("operations.template.none")} />
      </RadioGroup>
      {!parentHasTemplate ? (
        <Typography variant="body2" color="text.secondary">
          {t("operations.template.parentHasNoTemplate")}
        </Typography>
      ) : null}
      {value.mode === "pick" ? (
        <WizardTemplatePicker
          setTemplate={handlePickTemplate}
          selectedTemplateId={value.templateId}
          selectedTemplateName={value.templateName}
        />
      ) : null}
      {/* role="status"/"alert": these can appear with no user action, on the preselected mode, so a
          screen-reader user still needs to be told why Next is disabled. */}
      {checking || parentTemplateChecking ? (
        <Typography variant="body2" role="status">
          {t("operations.template.checking")}
        </Typography>
      ) : null}
      {(blockError ?? parentTemplateError) ? (
        <Typography variant="body2" color="error" role="alert">
          {blockError ?? parentTemplateError}
        </Typography>
      ) : null}
    </Stack>
  );
}

export default observer(TemplateStep);
