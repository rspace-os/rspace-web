import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Step from "@mui/material/Step";
import StepContent from "@mui/material/StepContent";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import { omit } from "es-toolkit";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinnerButton from "@/components/SubmitSpinnerButton";
import useUiPreference, { PREFERENCES } from "@/hooks/api/useUiPreference";
import useViewportDimensions from "@/hooks/browser/useViewportDimensions";
import { mkAlert } from "@/stores/contexts/Alert";
import { CELSIUS } from "@/stores/definitions/Units";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import getRootStore from "@/stores/stores/getRootStore";
import { showToastWhilstPending } from "@/util/alerts";
import { getErrorMessage } from "@/util/error";
import ContextDialog from "../ContextMenu/ContextDialog";
import { buildOperationRequest } from "./buildOperationRequest";
import { applyComputedValues } from "./computedValues";
import type { DocumentationSelection } from "./DocumentationStep";
import DocumentationStep from "./DocumentationStep";
import OperationConfirmation from "./OperationConfirmation";
import OperationDetailsStep from "./OperationDetailsStep";
import OperationPicker from "./OperationPicker";
import { performOperation, sampleNameAvailable } from "./operationsApi";
import { type InventoryOperation, resolveProcessName } from "./operationsConfig";
import { amountTakenExceedsOrigin, detailsValid } from "./operationValidation";
import { addProcessName, processNameDefaultAfterPerform, rememberKey } from "./processNames";
import { normalizeProcessValues, type ProcessValues, processValuesAfterPerform } from "./processValues";
import { derivedSampleName, firstAvailableName } from "./sampleNaming";
import TemplateStep, { type TemplateSelection } from "./TemplateStep";
import {
  resolveTemplateId,
  templateSelectionFor,
  templateSelectionToDefault,
  templateStepValid,
} from "./templateResolution";
import type { OperationInputs, OperationOrigin } from "./types";
import { UNSET_UNIT } from "./types";

// How long to wait after the process name settles before querying existing names to de-duplicate the
// derived sample name. Keeps typing from firing a search per keystroke.
const DEDUP_DEBOUNCE_MS = 300;

function buildInitialValues(operation: InventoryOperation, origin: SubSampleModel): OperationInputs {
  const unitId = getUnitId(origin.quantity);
  const values: OperationInputs = {};
  for (const input of operation.inputs) {
    if (input.type === "text") {
      values[input.key] = typeof input.default === "string" ? input.default : "";
    } else if (input.type === "integer") {
      values[input.key] = typeof input.default === "number" ? input.default : (input.min ?? 1);
    } else if (input.type === "quantity") {
      values[input.key] = { numericValue: 0, unitId };
    } else {
      // Temperature: start at the input's configured default (revive: 4 °C, in its 4..120 range), or
      // -80 °C for an unconfigured one (cryopreserve), so the field opens on a valid value.
      values[input.key] = {
        numericValue: typeof input.default === "number" ? input.default : -80,
        unitId: CELSIUS,
      };
    }
  }
  return values;
}

/**
 * The amount fields for a fresh amounts step: count and both amounts default to 1, and the quantity
 * units are left unset (a blank dropdown) so the step is blocked until the user picks a unit in the
 * amount's category.
 */
function blankAmounts(operation: InventoryOperation): OperationInputs {
  const out: OperationInputs = {
    [operation.effect.countFrom]: 1,
    [operation.effect.eachAmountFrom]: { numericValue: 1, unitId: UNSET_UNIT },
  };
  if (operation.effect.amountTakenFrom) out[operation.effect.amountTakenFrom] = { numericValue: 1, unitId: UNSET_UNIT };
  return out;
}

/**
 * A fresh set of values for the "defaults" state (nothing remembered, or the user unticked remember):
 * every input at its config default, amounts blank (unset units), keeping only the current names.
 */
function freshValues(operation: InventoryOperation, origin: SubSampleModel, current: OperationInputs): OperationInputs {
  const values = { ...buildInitialValues(operation, origin), ...blankAmounts(operation) };
  values[operation.effect.nameFrom] = current[operation.effect.nameFrom] ?? "";
  const pnFrom = operation.effect.processNameFrom;
  if (pnFrom) values[pnFrom] = current[pnFrom] ?? "";
  return values;
}

function toOrigin(origin: SubSampleModel): OperationOrigin {
  return {
    id: Number(origin.id),
    globalId: origin.globalId ?? "",
    quantity: origin.quantity ? { numericValue: getValue(origin.quantity), unitId: getUnitId(origin.quantity) } : null,
  };
}

/**
 * The operation wizard: pick operation -> details (process name, derived sample name, remember) ->
 * template -> amounts -> (optional documentation) -> confirm -> perform. The origin subsample is
 * pre-selected (launched from its detail pane). The whole effect is one atomic backend call (adr/0001).
 *
 * A single "remember" checkbox governs everything kept for a process name: the template, the
 * documentation, and the collected amounts (adr/0004). Ticking it loads the saved bundle; unticking
 * resets the form to defaults without deleting what was saved.
 */
function OperationWizard({
  open,
  onClose,
  origin,
}: {
  open: boolean;
  onClose: () => void;
  origin: SubSampleModel;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const resolveLabel = t as unknown as (key: string, params?: Record<string, unknown>) => string;
  // On small viewports (phones/tablets) the horizontal label row gets cramped, so fall back to the
  // classic vertical stepper (labels stacked, active step's content inline beneath its label).
  const { isViewportSmall } = useViewportDimensions();
  const [operation, setOperation] = React.useState<InventoryOperation | null>(null);
  const [values, setValues] = React.useState<OperationInputs>({});
  const [documentation, setDocumentation] = React.useState<DocumentationSelection>(null);
  const [remember, setRemember] = React.useState(false);
  // Whether the user has hand-edited the derived sample name; once they have, the wizard stops
  // re-deriving it from the process name.
  const [sampleNameEdited, setSampleNameEdited] = React.useState(false);
  const [activeStep, setActiveStep] = React.useState(0);
  const [submitting, setSubmitting] = React.useState(false);
  const [templateSelection, setTemplateSelection] = React.useState<TemplateSelection>({
    mode: "unselected",
    templateId: null,
    remember: false,
  });
  const [processValues, setProcessValues] = useUiPreference<Record<string, ProcessValues>>(
    PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES,
    { defaultValue: {} },
  );
  const [processNames, setProcessNames] = useUiPreference<Record<string, Array<string>>>(
    PREFERENCES.INVENTORY_OPERATION_PROCESS_NAMES,
    { defaultValue: {} },
  );
  const [processNameDefaults, setProcessNameDefaults] = useUiPreference<Record<string, string>>(
    PREFERENCES.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS,
    { defaultValue: {} },
  );

  const stepKeys = operation
    ? ["details", "template", "amounts", ...(operation.documentationStep ? ["documentation"] : []), "confirm"]
    : [];
  const isLast = activeStep === stepKeys.length - 1;

  // Count / each-amount / amount-taken live on the "amounts" step; everything else (process name,
  // sample name, cryomedium, storage temperature) on "details". Template is its own step.
  const amountKeys: ReadonlySet<string> = operation
    ? new Set(
        [operation.effect.countFrom, operation.effect.eachAmountFrom, operation.effect.amountTakenFrom].filter(
          (k): k is string => Boolean(k),
        ),
      )
    : new Set();
  const detailKeys: ReadonlySet<string> = operation
    ? new Set(operation.inputs.map((i) => i.key).filter((k) => !amountKeys.has(k)))
    : new Set();
  // The amounts step offers the chosen template's units, or the origin subsample's when no specific
  // template is picked.
  const amountCategory = templateSelection.quantityCategory ?? origin.quantityCategory;
  // Only "use parent template" needs the parent's template; when the parent has none, that option is
  // disabled and the user must pick an existing template or none.
  const parentHasTemplate = (origin.sample.templateId ?? null) !== null;

  // The saved bundle for a given values set, and the wizard state (values + template + documentation +
  // whether remember is on) that a process name resolves to: its saved bundle if one exists, else the
  // defaults. Used on operation select and whenever the process name changes.
  const stateForKey = (op: InventoryOperation, vals: OperationInputs) => {
    const bundle = normalizeProcessValues(processValues?.[rememberKey(op, vals)]);
    const base = freshValues(op, origin, vals);
    if (bundle) {
      return {
        values: { ...base, ...bundle.values },
        templateSelection: templateSelectionFor(bundle.template),
        documentation: bundle.documentation,
        remember: true,
      };
    }
    return { values: base, templateSelection: templateSelectionFor(undefined), documentation: null, remember: false };
  };

  const selectOperation = (op: InventoryOperation) => {
    const initial = buildInitialValues(op, origin);
    // Pre-fill the most-recently-remembered process name for this operation, so a repeat run starts
    // from it (and loads its bundle).
    const savedProcessName = (op.effect.processNameFrom ? (processNameDefaults?.[op.key] ?? "") : "").trim();
    if (op.effect.processNameFrom && savedProcessName) initial[op.effect.processNameFrom] = savedProcessName;
    const s = stateForKey(op, initial);
    // Seed the derived sample name straight away (the dedup effect refines it once names are fetched).
    s.values[op.effect.nameFrom] = derivedSampleName(origin.sample.name, resolveProcessName(op, s.values));
    setOperation(op);
    setValues(s.values);
    setTemplateSelection(s.templateSelection);
    setDocumentation(s.documentation);
    setRemember(s.remember);
    setSampleNameEdited(false);
    setActiveStep(0);
  };

  // Details-step change handler. When the process name changes, reload that name's saved state (or
  // reset to defaults) and re-derive the sample name; otherwise a change to the sample-name field is
  // treated as a manual edit that stops further auto-derivation.
  const onDetailsChange = (next: OperationInputs) => {
    if (!operation) {
      setValues(next);
      return;
    }
    const pnFrom = operation.effect.processNameFrom;
    const nameFrom = operation.effect.nameFrom;
    if (pnFrom && next[pnFrom] !== values[pnFrom]) {
      const s = stateForKey(operation, next);
      let v = s.values;
      if (!sampleNameEdited)
        v = { ...v, [nameFrom]: derivedSampleName(origin.sample.name, resolveProcessName(operation, v)) };
      setValues(v);
      setTemplateSelection(s.templateSelection);
      setDocumentation(s.documentation);
      setRemember(s.remember);
      return;
    }
    if (next[nameFrom] !== values[nameFrom]) setSampleNameEdited(true);
    setValues(next);
  };

  // The single "remember" checkbox. Ticking loads the saved bundle for the current process name (if
  // any); unticking resets the form to defaults. Neither deletes the stored bundle (grill Q1).
  const onRememberChange = (checked: boolean) => {
    if (!operation) return;
    setRemember(checked);
    if (checked) {
      const bundle = normalizeProcessValues(processValues?.[rememberKey(operation, values)]);
      if (bundle) {
        setValues((v) => ({ ...v, ...bundle.values }));
        setTemplateSelection(templateSelectionFor(bundle.template));
        setDocumentation(bundle.documentation);
      }
    } else {
      setValues((v) => freshValues(operation, origin, v));
      setTemplateSelection(templateSelectionFor(undefined));
      setDocumentation(null);
    }
  };

  // Auto-derive the sample name "<origin> <process>" and de-duplicate it against existing sample
  // names, keyed on the base name so it re-runs only when the process name changes (not when it sets
  // the sample name). Skipped once the user hand-edits the name.
  const sampleBaseName =
    operation && !sampleNameEdited ? derivedSampleName(origin.sample.name, resolveProcessName(operation, values)) : "";
  React.useEffect(() => {
    if (!operation || sampleNameEdited || sampleBaseName === "") return;
    const nameFrom = operation.effect.nameFrom;
    let cancelled = false;
    const timer = setTimeout(() => {
      void firstAvailableName(sampleBaseName, sampleNameAvailable).then((name) => {
        if (!cancelled) setValues((v) => ({ ...v, [nameFrom]: name }));
      });
    }, DEDUP_DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [operation, sampleBaseName, sampleNameEdited]);

  const next = () => setActiveStep((s) => s + 1);

  const back = () => {
    if (activeStep === 0) {
      setOperation(null);
      return;
    }
    setActiveStep((s) => s - 1);
  };

  const stepValid = (): boolean => {
    if (!operation) return false;
    const key = stepKeys[activeStep];
    // An origin with no amount (0, or a quantity never set) cannot be operated on: block the first
    // step (OperationDetailsStep shows the matching error).
    if (key === "details") return detailsValid(operation, values, detailKeys) && getValue(origin.quantity) > 0;
    if (key === "template") return templateStepValid(templateSelection);
    if (key === "amounts")
      return (
        detailsValid(operation, values, amountKeys) &&
        !amountTakenExceedsOrigin(operation, values, toOrigin(origin).quantity)
      );
    return true;
  };

  const submit = async (): Promise<void> => {
    if (!operation) return;
    setSubmitting(true);
    try {
      // "fromSample" reads the origin sample's own template, and a computed value with a
      // parentSampleField arg reads a field on it (e.g. Passage number); either way the parent's
      // fields must be loaded before we read them.
      const computed = operation.effect.computed ?? [];
      const needsParentFields = computed.some((c) => Object.values(c.args).some((a) => "parentSampleField" in a));
      if (templateSelection.mode === "fromSample" || needsParentFields) await origin.sample.fetchAdditionalInfo();
      const templateId = resolveTemplateId({
        mode: templateSelection.mode,
        pickedTemplateId: templateSelection.templateId,
        originSampleTemplateId: origin.sample.templateId ?? null,
      });
      // Apply the operation's computed values (adr/0006), e.g. Passage number = parent's + 1, else 1.
      // Computed only for the request, so they never enter the remembered bundle below.
      const submitValues: OperationInputs = computed.length
        ? applyComputedValues(operation, {
            parentFields: origin.sample.fields,
            values,
            resolveFieldName: resolveLabel,
          })
        : values;
      const request = buildOperationRequest({
        operation,
        values: submitValues,
        origin: toOrigin(origin),
        resolveLabel,
        templateId,
        documentationLink: documentation
          ? { fieldName: t("operations.documentation.fieldName"), targetGlobalId: documentation.globalId }
          : undefined,
      });
      await showToastWhilstPending(t("operations.wizard.inProgress"), performOperation(request));
      // Persist the "remember" bundle only now that Perform has succeeded, and only when the box is
      // ticked. Keyed per operation + process name; unticking never deletes a prior bundle (adr/0004).
      if (remember) {
        const key = rememberKey(operation, values);
        const bundle: ProcessValues = {
          values: omit(
            values,
            [operation.effect.nameFrom, operation.effect.processNameFrom].filter((k): k is string => Boolean(k)),
          ),
          template: templateSelectionToDefault(templateSelection),
          documentation,
        };
        setProcessValues(processValuesAfterPerform(processValues ?? {}, key, bundle, true));
        if (operation.effect.processNameFrom) {
          const name = String(values[operation.effect.processNameFrom] ?? "");
          const list = processNames?.[operation.key] ?? [];
          const updated = addProcessName(list, name);
          if (updated !== list) setProcessNames({ ...(processNames ?? {}), [operation.key]: updated });
          setProcessNameDefaults(processNameDefaultAfterPerform(processNameDefaults ?? {}, operation.key, name, true));
        }
      }
      await origin.fetchAdditionalInfo();
      // Re-run the main search so the newly created sample appears without a manual re-search.
      getRootStore().searchStore.search.performSearch();
      onClose();
    } catch (error) {
      // Never fail silently: surface the reason (e.g. a backend rejection) instead of leaving the
      // Perform button looking dead. The wizard stays open so the user can retry.
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: t("operations.wizard.failed"),
          message: getErrorMessage(error, t("operations.wizard.failed")),
          variant: "error",
        }),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const stepLabel = (key: string): string => {
    switch (key) {
      case "details":
        return t("operations.wizard.step.details");
      case "template":
        return t("operations.wizard.step.template");
      case "amounts":
        return t("operations.wizard.step.amounts");
      case "documentation":
        return t("operations.wizard.step.documentation");
      default:
        return t("operations.wizard.step.confirm");
    }
  };

  const stepContent = (key: string): React.ReactNode => {
    if (!operation) return null;
    if (key === "details") {
      return (
        <OperationDetailsStep
          operation={operation}
          origin={origin}
          values={values}
          onChange={onDetailsChange}
          section="details"
          processNameOptions={
            operation.effect.processNameFrom ? (processNames?.[operation.key] ?? []).filter((n) => n.trim() !== "") : []
          }
          remember={remember}
          onRememberChange={onRememberChange}
        />
      );
    }
    if (key === "template") {
      return (
        <TemplateStep
          value={templateSelection}
          onChange={setTemplateSelection}
          originSampleName={origin.sample.name}
          parentHasTemplate={parentHasTemplate}
        />
      );
    }
    if (key === "amounts") {
      return (
        <OperationDetailsStep
          operation={operation}
          origin={origin}
          values={values}
          onChange={onDetailsChange}
          section="amounts"
          unitCategories={[amountCategory]}
        />
      );
    }
    if (key === "documentation") {
      return <DocumentationStep value={documentation} onChange={setDocumentation} />;
    }
    return (
      <OperationConfirmation
        operation={operation}
        values={values}
        documentation={documentation}
        templateSelection={templateSelection}
        originSampleName={origin.sample.name}
      />
    );
  };

  // The dialog heading names the operation on every step, appending a user-entered process name where
  // the operation has one (e.g. "Derive: dna extraction"); operations with a fixed process name
  // (Cryopreserve) show just the operation name.
  const headingProcessName = operation?.effect.processNameFrom ? resolveProcessName(operation, values) : "";
  const heading = operation
    ? `${resolveLabel(operation.labelKey)}${headingProcessName ? `: ${headingProcessName}` : ""}`
    : t("operations.wizard.title");

  return (
    <ContextDialog open={open} onClose={onClose} maxWidth="sm" fullWidth disableBackdropClick>
      <DialogTitle>{heading}</DialogTitle>
      <DialogContent dividers sx={{ minHeight: operation ? "60vh" : undefined }}>
        {operation ? (
          <>
            {/* Wide viewports: horizontal stepper (labels in one compact top row) so each step's
                content — especially the confirmation card and its varied display formats — gets the
                dialog's full width. Small viewports: vertical stepper with the active step's content
                rendered inline beneath its label, which reads better on a narrow screen. */}
            <Stepper
              activeStep={activeStep}
              orientation={isViewportSmall ? "vertical" : "horizontal"}
              alternativeLabel={!isViewportSmall}
              sx={{ mb: isViewportSmall ? 0 : 2 }}
            >
              {stepKeys.map((key, index) => (
                <Step key={key}>
                  <StepLabel>{stepLabel(key)}</StepLabel>
                  {isViewportSmall ? <StepContent>{index === activeStep ? stepContent(key) : null}</StepContent> : null}
                </Step>
              ))}
            </Stepper>
            {isViewportSmall ? null : stepContent(stepKeys[activeStep])}
          </>
        ) : (
          <OperationPicker onSelect={selectOperation} />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        {operation ? (
          <>
            <Button onClick={back} disabled={submitting}>
              {t("common:actions.back")}
            </Button>
            {isLast ? (
              <SubmitSpinnerButton
                onClick={() => void submit()}
                loading={submitting}
                disabled={submitting}
                label={t("operations.wizard.perform")}
              />
            ) : (
              <Button variant="contained" color="callToAction" disableElevation onClick={next} disabled={!stepValid()}>
                {t("common:actions.next")}
              </Button>
            )}
          </>
        ) : null}
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(OperationWizard);
