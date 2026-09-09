import Alert from "@mui/material/Alert";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import type TemplateModel from "@/stores/models/TemplateModel";
import type { UnitCategory } from "@/stores/stores/UnitStore";
import { templateSelectionBlock } from "./templateResolution";
import WizardTemplatePicker from "./WizardTemplatePicker";

/** The user's template choice for the new sample (DevDocs/adr/0007). */
export type TemplateSelection = {
  // "remembered" = a specific template restored from the saved default: shown as a banner with no
  // radio selected until the user picks a radio to override it. "unselected" = the initial state
  // when nothing is remembered: no radio selected, and Next stays disabled until the user chooses.
  mode: "none" | "pick" | "fromSample" | "remembered" | "unselected";
  templateId: number | null;
  templateName?: string;
  // The picked template's quantity category (mass/volume/dimensionless...). Set when the user picks a
  // specific template so the amounts step can offer that template's units instead of the origin
  // subsample's (a volume template overrides a mass subsample). Undefined = fall back to the origin.
  quantityCategory?: UnitCategory;
  remember: boolean;
};

/**
 * Framework step (present for every operation): optionally choose the new sample's template - none
 * (ad-hoc), an existing template, or a template created from the origin's parent sample. When the
 * user picks an existing template, it is validated up front: a template with mandatory fields that
 * have no default value is blocked here with a clear message, rather than failing at submit
 * (DevDocs/adr/0007). The "remember" checkbox is persisted per user, per operation by the wizard.
 */
function TemplateStep({
  value,
  onChange,
  originSampleName,
  parentHasTemplate = true,
  parentTemplateChecking = false,
  parentTemplateError = null,
}: {
  value: TemplateSelection;
  onChange: (value: TemplateSelection) => void;
  originSampleName: string;
  /** Whether the origin's parent sample has its own template. When it does not, the "use parent
   *  template" option is disabled with a hint: the wizard never creates a template (DevDocs/adr/0007). */
  parentHasTemplate?: boolean;
  /**
   * Status of the "use parent template" check, which the WIZARD runs, not this step (F5).
   *
   * It has to live there: the wizard renders only the active step, so a check owned by this
   * component never ran while the user was on step one - which is exactly where the one-click fast
   * path is offered, and it stayed permanently disabled (parallel review, C2). This step only
   * displays the outcome.
   */
  parentTemplateChecking?: boolean;
  parentTemplateError?: string | null;
}): React.ReactNode {
  const { t, i18n } = useTranslation("inventory");
  const [checking, setChecking] = React.useState(false);
  const [blockError, setBlockError] = React.useState<string | null>(null);
  // The most recently picked template id. fetchAdditionalInfo is async, so if the user picks A then
  // B before A resolves, A can complete last; guarding on this ref discards a superseded lookup so
  // the latest pick always wins rather than the last response.
  const latestPickRef = React.useRef<string | null>(null);
  // Abandon any in-flight lookup when the step unmounts. The wizard renders only the active step, so
  // it moves off this step (Back, or a process-name / remembered-value / operation change that
  // replaces the template selection) by unmounting it. Without this, a lookup still pending at that
  // point resolves against the stale ref and restores the abandoned template onto the wizard's newer
  // selection (Greptile P1); nulling the ref makes the guards below discard it.
  React.useEffect(
    () => () => {
      latestPickRef.current = null;
    },
    [],
  );

  /**
   * Runs the mandatory-without-default check against a template and applies the outcome. Shared by
   * both paths that end in a concrete template, so "use parent template" cannot drift away from the
   * rules a picked template is held to. `token` identifies the request in `latestPickRef`, so a
   * superseded lookup (the user switched mode or picked again mid-fetch) is discarded rather than
   * writing over the newer selection.
   */
  const applyTemplateCheck = async (
    token: string,
    load: () => Promise<TemplateModel>,
    describe: (template: TemplateModel) => Partial<TemplateSelection>,
  ) => {
    try {
      const template = await load();
      if (latestPickRef.current !== token) return;
      const fields = template.fields.map((f) => ({
        name: f.name,
        mandatory: f.mandatory,
        hasDefault:
          (f.selectedOptions?.length ?? 0) > 0 ||
          (f.content !== null && f.content !== undefined && String(f.content).trim() !== ""),
      }));
      const { blocked, missingFields } = templateSelectionBlock(fields);
      if (blocked) {
        setBlockError(
          t("operations.template.mandatoryFieldsError", {
            count: missingFields.length,
            fields: formatList(missingFields, i18n.resolvedLanguage ?? i18n.language),
          }),
        );
        return;
      }
      // Only a PASSING check writes an id, which is what makes templateStepValid's id test the
      // signal that this step is done. The category comes along so the amounts step offers the
      // template's units in both modes rather than only after a pick.
      onChange({ ...value, ...describe(template) });
    } catch {
      // The lookup failed (offline, permission change, template deleted): without this the rejection
      // escaped the detached task unhandled and the user saw only the spinner stop, with no reason
      // and no way to tell a failed check from a passed one (Copilot review, PR #1090). Leave the
      // selection cleared so Next stays blocked, and say why.
      if (latestPickRef.current !== token) return;
      setBlockError(t("operations.template.lookupFailed"));
    } finally {
      // Only the latest request clears the spinner; a superseded lookup leaves it to the newer one.
      if (latestPickRef.current === token) setChecking(false);
    }
  };

  const setMode = (mode: TemplateSelection["mode"]) => {
    setBlockError(null);
    // Switching mode abandons any in-flight template lookup: invalidate it (so a late result can't
    // restore a template the user has moved away from) and clear the spinner it left on.
    latestPickRef.current = null;
    setChecking(false);
    onChange({
      ...value,
      mode,
      templateId: mode === "pick" ? value.templateId : null,
      templateName: mode === "pick" ? value.templateName : undefined,
      // A non-"pick" mode carries no specific template, so drop any category: the amounts step then
      // falls back to the origin subsample's category. "fromSample" then sets its own below, once
      // the parent's template has passed the check.
      quantityCategory: mode === "pick" ? value.quantityCategory : undefined,
    });
  };

  const onPickTemplate = (template: TemplateModel | null) => {
    // The picker was cleared: drop the selection so Next blocks again and the wizard cannot submit
    // the template the box no longer shows (Copilot review, PR #1090).
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
    void applyTemplateCheck(
      pickId,
      async () => {
        // The picker's search results are partial, so the fields the check reads are fetched here.
        await template.fetchAdditionalInfo();
        return template;
      },
      () => ({
        templateId: Number(template.id),
        templateName: template.name,
        quantityCategory: template.quantityCategory,
      }),
    );
  };

  // Hand the picker a referentially stable callback (latest impl via ref), so re-renders of this
  // step never churn the picker via a changing prop.
  const onPickTemplateRef = React.useRef(onPickTemplate);
  onPickTemplateRef.current = onPickTemplate;
  const handlePickTemplate = React.useCallback(
    (template: TemplateModel | null) => onPickTemplateRef.current(template),
    [],
  );

  return (
    <Stack spacing={1}>
      {value.mode === "remembered" && value.templateName ? (
        <Alert severity="info" data-testid="SelectedTemplateName">
          {`${t("operations.template.selectedLabel")}: ${value.templateName}`}
        </Alert>
      ) : null}
      <Typography variant="body2">{t("operations.template.description")}</Typography>
      {/* An empty value means no radio is selected: either a remembered template is in effect, or the
          user has not chosen yet ("unselected", which keeps Next disabled). Picking a radio overrides
          it. */}
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
      {/* Outside the "pick" branch: "use parent template" is checked too, so it needs the same
          spinner and the same reason when it blocks, or its failure is silent again. role="status"
          and role="alert" because both now appear with no user action at all, on the preselected
          mode, and a screen-reader user would otherwise get no indication why Next is disabled. */}
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
