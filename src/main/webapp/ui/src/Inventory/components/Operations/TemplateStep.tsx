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

// Declared in templateResolution so the module and this component cannot drift apart, and
// re-exported here so every existing `from "./TemplateStep"` import keeps working.
export type { TemplateSelection };

/**
 * Framework step (present for every operation): optionally choose the new sample's template - none
 * (ad-hoc), an existing template, or a template created from the origin's parent sample. A picked
 * template with mandatory fields that have no default value is blocked here with a clear message,
 * rather than failing at submit. The "remember" checkbox is persisted per user, per operation by the
 * wizard.
 */
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
  /**
   * Accepts an updater as well as a value, since the post-lookup write below needs the latest
   * state rather than what this render captured. The synchronous handlers keep passing a plain
   * value.
   */
  onChange: React.Dispatch<React.SetStateAction<TemplateSelection>>;
  originSampleName: string;
  /**
   * Whether the origin's parent sample has its own template. When it does not, the "use parent
   * template" option is disabled: the wizard never creates one.
   */
  parentHasTemplate?: boolean;
  /**
   * Status of the "use parent template" check, which the WIZARD runs, not this step.
   *
   * It has to live there: the wizard renders only the active step, so a check owned by this
   * component never ran while the user was on step one - which is exactly where the one-click fast
   * path is offered, and it stayed permanently disabled. This step only
   * displays the outcome.
   */
  parentTemplateChecking?: boolean;
  parentTemplateError?: string | null;
  /**
   * Why a remembered template was dropped: it has been trashed, has gone, or is no longer usable.
   * Also owned by the wizard rather than this step, and for the same reason as the parent check: a
   * remembered bundle is resolved at step one, which this step is not rendered for.
   */
  rememberedTemplateError?: string | null;
}): React.ReactNode {
  const { t, i18n } = useTranslation("inventory");
  const [checking, setChecking] = React.useState(false);
  const [blockError, setBlockError] = React.useState<string | null>(null);
  // The most recently picked template id, so the latest pick always wins.
  const latestPickRef = React.useRef<string | null>(null);
  React.useEffect(
    () => () => {
      latestPickRef.current = null;
    },
    [],
  );

  /**
   * Runs the mandatory-without-default check against a template and applies the outcome. Shared by
   * both paths that end in a concrete template, so "use parent template" cannot drift away from the
   * rules a picked template is held to.
   */
  const applyTemplateCheck = async (
    token: string,
    load: () => Promise<TemplateModel>,
    describe: (template: TemplateModel) => Partial<TemplateSelection>,
  ) => {
    try {
      const template = await load();
      if (latestPickRef.current !== token) return;
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
      onChange((previous) => ({ ...previous, ...describe(template) }));
    } catch {
      // The lookup failed (offline, permission change, template deleted): without this the rejection
      // escaped the detached task unhandled and the user saw only the spinner stop, with no reason
      // and no way to tell a failed check from a passed one. Leave the
      // selection cleared so Next stays blocked, and say why.
      if (latestPickRef.current !== token) return;
      setBlockError(t("operations.template.lookupFailed"));
    } finally {
      if (latestPickRef.current === token) setChecking(false);
    }
  };

  const setMode = (mode: TemplateSelection["mode"]) => {
    setBlockError(null);
    latestPickRef.current = null;
    setChecking(false);
    onChange({
      ...value,
      mode,
      templateId: mode === "pick" ? value.templateId : null,
      templateName: mode === "pick" ? value.templateName : undefined,
      // A non-"pick" mode carries no specific template, so clear any category too; "fromSample"
      // sets its own below once the parent's template has passed the check.
      quantityCategory: mode === "pick" ? value.quantityCategory : undefined,
    });
  };

  const onPickTemplate = (template: TemplateModel | null) => {
    // The picker was cleared: drop the selection so Next blocks again and the wizard cannot submit
    // the template the box no longer shows.
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
  // step never churn the picker via a changing prop. The empty dependency array below is required,
  // not incidental: the Picker fires onAddition from an effect keyed on the callback's identity, so
  // an unstable callback plus a state update is an infinite render loop on selection.
  const onPickTemplateRef = React.useRef(onPickTemplate);
  // Written in a layout effect, not in the render body. React 19 may discard a render pass, and a
  // body assignment would leave the ref holding the abandoned render's closure over a stale `value`.
  // useLayoutEffect rather than useEffect so the ref is current before any
  // child effect - the Picker's included - can call it.
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
