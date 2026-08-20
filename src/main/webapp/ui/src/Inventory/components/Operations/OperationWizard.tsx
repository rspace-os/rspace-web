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
import { CELSIUS, toCommonUnit } from "@/stores/definitions/Units";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import getRootStore from "@/stores/stores/getRootStore";
import { showToastWhilstPending } from "@/util/alerts";
import { getErrorMessage } from "@/util/error";
import ContextDialog from "../ContextMenu/ContextDialog";
import { buildOperationRequest } from "./buildOperationRequest";
import { applyComputedValues, gatherParentFields } from "./computedValues";
import type { DocumentationSelection } from "./DocumentationStep";
import DocumentationStep from "./DocumentationStep";
import OperationConfirmation from "./OperationConfirmation";
import OperationDetailsStep from "./OperationDetailsStep";
import OperationPicker from "./OperationPicker";
import { performOperation, sampleNameAvailable } from "./operationsApi";
import {
  type InventoryOperation,
  resolveDefaultAmountMode,
  resolveProcessName,
  usesAmountModes,
} from "./operationsConfig";
import { amountTakenExceedsOrigin, detailsValid, quantityExceedsOrigin } from "./operationValidation";
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
import type { AmountMode, OperationInputs, OperationOrigin, PerSubsampleAmounts } from "./types";
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
  const out: OperationInputs = {};
  // A terminal operation (Destroy) declares no count/each-amount; only set those that exist.
  if (operation.effect.countFrom) out[operation.effect.countFrom] = 1;
  if (operation.effect.eachAmountFrom) out[operation.effect.eachAmountFrom] = { numericValue: 1, unitId: UNSET_UNIT };
  if (operation.effect.amountTakenFrom) out[operation.effect.amountTakenFrom] = { numericValue: 1, unitId: UNSET_UNIT };
  return out;
}

/**
 * A fresh set of values for the "defaults" state (nothing remembered, or the user unticked remember):
 * every input at its config default, amounts blank (unset units), keeping only the current names.
 */
function freshValues(operation: InventoryOperation, origin: SubSampleModel, current: OperationInputs): OperationInputs {
  const values = { ...buildInitialValues(operation, origin), ...blankAmounts(operation) };
  const nameFrom = operation.effect.nameFrom;
  if (nameFrom) values[nameFrom] = current[nameFrom] ?? "";
  const pnFrom = operation.effect.processNameFrom;
  if (pnFrom) values[pnFrom] = current[pnFrom] ?? "";
  return values;
}

function toOrigin(origin: SubSampleModel): OperationOrigin {
  return {
    id: Number(origin.id),
    globalId: origin.globalId ?? "",
    name: origin.name ?? "",
    quantity: origin.quantity ? { numericValue: getValue(origin.quantity), unitId: getUnitId(origin.quantity) } : null,
  };
}

/** A subsample's quantity in its category's atomic unit, or 0 when it has no quantity. */
function commonQuantity(origin: SubSampleModel): number {
  return origin.quantity ? toCommonUnit(getValue(origin.quantity), getUnitId(origin.quantity)) : 0;
}

/**
 * The origin the wizard treats as representative for a (possibly multi-origin) run: the one holding
 * the *least* material. Because a Pool takes the same shared amount from every origin, the smallest
 * origin is the binding constraint - checking the amount against it (over-removal, the empty-origin
 * block) is equivalent to checking every origin, so the single-origin wizard logic needs no change.
 * For a single-origin operation this is just that origin.
 */
function representativeOrigin(origins: Array<SubSampleModel>): SubSampleModel {
  return origins.reduce((smallest, o) => (commonQuantity(o) < commonQuantity(smallest) ? o : smallest));
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
  origins,
}: {
  open: boolean;
  onClose: () => void;
  /** The selected origin subsamples: one for a single-origin operation, two or more for Pool. */
  origins: Array<SubSampleModel>;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const resolveLabel = t as unknown as (key: string, params?: Record<string, unknown>) => string;
  // The representative origin (the smallest; see representativeOrigin) drives the single-origin wizard
  // logic - units, the derived name base, over-removal - unchanged. Multi-origin specifics (all the
  // origins, the shared amount, the per-origin links) are threaded in only where they differ.
  const origin = representativeOrigin(origins);
  // Pool requires every selected origin to share one measurement category (the shared amount is in one
  // unit); the picker only enables Pool when this holds, but keep it here for the picker's own gating.
  const allSameCategory = origins.every((o) => o.quantityCategory === origins[0].quantityCategory);
  // On small viewports (phones/tablets) the horizontal label row gets cramped, so fall back to the
  // classic vertical stepper (labels stacked, active step's content inline beneath its label).
  const { isViewportSmall } = useViewportDimensions();
  const [operation, setOperation] = React.useState<InventoryOperation | null>(null);
  const [values, setValues] = React.useState<OperationInputs>({});
  const [documentation, setDocumentation] = React.useState<DocumentationSelection>(null);
  const [remember, setRemember] = React.useState(false);
  // The multi-origin amount mode (adr/0009) and, for "perSubsample", the per-origin amounts by global
  // id. Single-origin operations stay on "same".
  const [amountMode, setAmountMode] = React.useState<AmountMode>("same");
  const [perSubsampleAmounts, setPerSubsampleAmounts] = React.useState<PerSubsampleAmounts>({});
  // When a complete remembered bundle loads, step one offers Perform straight away; "reviewing" is set
  // once the user chooses to step through the wizard instead (adr/0009).
  const [reviewing, setReviewing] = React.useState(false);
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

  // The wizard steps: an operation may declare an explicit `steps` subset (Destroy skips template and
  // amounts); otherwise the default full sequence, with the documentation step gated by documentationStep.
  const stepKeys: Array<string> = operation
    ? (operation.steps ?? [
        "details",
        "template",
        "amounts",
        ...(operation.documentationStep ? ["documentation"] : []),
        "confirm",
      ])
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
  // disabled and the user must pick an existing template or none. A multi-origin operation (Pool) has
  // several parent samples, so "use parent template" is ambiguous and always disabled for it.
  const parentHasTemplate = !operation?.requiresMultiple && (origin.sample.templateId ?? null) !== null;

  // The base the derived sample name is built from: the origin's own sample name for a single-origin
  // operation, or the operation's label for a multi-origin one (Pool combines several samples, so no
  // single origin name applies - it becomes just "Pool", de-duplicated).
  const sampleNameBase = (op: InventoryOperation): string =>
    op.requiresMultiple ? resolveLabel(op.labelKey) : origin.sample.name;

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
        amountMode: bundle.amountMode ?? resolveDefaultAmountMode(op),
        perSubsampleAmounts: bundle.perSubsampleAmounts ?? {},
        remember: true,
      };
    }
    return {
      values: base,
      templateSelection: templateSelectionFor(undefined),
      documentation: null,
      amountMode: resolveDefaultAmountMode(op),
      perSubsampleAmounts: {},
      remember: false,
    };
  };

  const selectOperation = (op: InventoryOperation) => {
    const initial = buildInitialValues(op, origin);
    // Pre-fill the most-recently-remembered process name for this operation, so a repeat run starts
    // from it (and loads its bundle).
    const savedProcessName = (op.effect.processNameFrom ? (processNameDefaults?.[op.key] ?? "") : "").trim();
    if (op.effect.processNameFrom && savedProcessName) initial[op.effect.processNameFrom] = savedProcessName;
    const s = stateForKey(op, initial);
    // Seed the derived sample name straight away (the dedup effect refines it once names are fetched).
    // A terminal operation (Destroy) creates no sample, so it has no name to derive.
    if (op.effect.nameFrom)
      s.values[op.effect.nameFrom] = derivedSampleName(sampleNameBase(op), resolveProcessName(op, s.values));
    setOperation(op);
    setValues(s.values);
    setTemplateSelection(s.templateSelection);
    setDocumentation(s.documentation);
    setRemember(s.remember);
    setAmountMode(s.amountMode);
    setPerSubsampleAmounts(s.perSubsampleAmounts);
    setReviewing(false);
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
      if (!sampleNameEdited && nameFrom)
        v = { ...v, [nameFrom]: derivedSampleName(sampleNameBase(operation), resolveProcessName(operation, v)) };
      setValues(v);
      setTemplateSelection(s.templateSelection);
      setDocumentation(s.documentation);
      setRemember(s.remember);
      setAmountMode(s.amountMode);
      setPerSubsampleAmounts(s.perSubsampleAmounts);
      return;
    }
    if (nameFrom && next[nameFrom] !== values[nameFrom]) setSampleNameEdited(true);
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
        setAmountMode(bundle.amountMode ?? "same");
        setPerSubsampleAmounts(bundle.perSubsampleAmounts ?? {});
      }
    } else {
      setValues((v) => freshValues(operation, origin, v));
      setTemplateSelection(templateSelectionFor(undefined));
      setDocumentation(null);
      setAmountMode("same");
      setPerSubsampleAmounts({});
    }
  };

  // Auto-derive the sample name "<origin> <process>" and de-duplicate it against existing sample
  // names, keyed on the base name so it re-runs only when the process name changes (not when it sets
  // the sample name). Skipped once the user hand-edits the name.
  const sampleBaseName =
    operation?.effect.nameFrom && !sampleNameEdited
      ? derivedSampleName(sampleNameBase(operation), resolveProcessName(operation, values))
      : "";
  React.useEffect(() => {
    const nameFrom = operation?.effect.nameFrom;
    if (!operation || !nameFrom || sampleNameEdited || sampleBaseName === "") return;
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

  // Whether the amounts step is valid, given the amount mode (adr/0009). "same" (and any operation
  // without amount modes) checks the shared amount against the representative (smallest) origin; "all"
  // is always valid (every origin emptied, never over-removal); "perSubsample" needs a positive,
  // in-range amount for every origin, each checked against its own quantity. The created-sample
  // count/each-amount must be valid in every mode.
  const amountsStepValid = (): boolean => {
    if (!operation) return false;
    const sharedValid =
      detailsValid(operation, values, amountKeys) &&
      !amountTakenExceedsOrigin(operation, values, toOrigin(origin).quantity);
    if (!usesAmountModes(operation)) return sharedValid;
    const createdKeys: ReadonlySet<string> = new Set(
      [operation.effect.countFrom, operation.effect.eachAmountFrom].filter((k): k is string => Boolean(k)),
    );
    if (!detailsValid(operation, values, createdKeys)) return false;
    if (amountMode === "all") return true;
    if (amountMode === "perSubsample") {
      return origins.every((o) => {
        const q = perSubsampleAmounts[o.globalId ?? ""];
        if (!q || !Number.isFinite(q.numericValue) || q.unitId <= 0 || q.numericValue <= 0) return false;
        return !quantityExceedsOrigin(q, toOrigin(o).quantity);
      });
    }
    return sharedValid;
  };

  const stepValidFor = (key: string): boolean => {
    if (!operation) return false;
    // An operation that empties its origin (Destroy) cannot run on an empty subsample. It skips the
    // details step (where other operations gate this), so enforce it for every step here, blocking
    // Perform on the confirmation with the reason shown there.
    if (operation.effect.emptiesOrigin && getValue(origin.quantity) <= 0) return false;
    // An origin with no amount (0, or a quantity never set) cannot be operated on: block the first
    // step (OperationDetailsStep shows the matching error).
    if (key === "details") return detailsValid(operation, values, detailKeys) && getValue(origin.quantity) > 0;
    if (key === "template") return templateStepValid(templateSelection);
    if (key === "amounts") return amountsStepValid();
    return true;
  };

  const stepValid = (): boolean => stepValidFor(stepKeys[activeStep]);
  // Every step complete and valid: the gate for the step-one fast path (adr/0009), only offered when a
  // remembered bundle already makes the whole run performable.
  const allStepsValid = (): boolean => operation !== null && stepKeys.every(stepValidFor);
  // Step one shows the confirmation and Perform (skipping the wizard) when a remembered bundle fully
  // specifies a valid run and the user has not chosen to step through it.
  const fastPath = operation !== null && activeStep === 0 && remember && !reviewing && allStepsValid();

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
      // A terminal operation (Destroy) skips the template step and creates no sample, so it needs no
      // template; resolve one only for producing operations.
      const templateId = operation.noOutput
        ? null
        : resolveTemplateId({
            mode: templateSelection.mode,
            pickedTemplateId: templateSelection.templateId,
            originSampleTemplateId: origin.sample.templateId ?? null,
          });
      // Apply the operation's computed values (adr/0006), e.g. Passage number = parent's + 1, else 1.
      // Computed only for the request, so they never enter the remembered bundle below. Search both the
      // template-defined fields and the sample's ad-hoc extra fields: a "Passage number" the user added
      // as a custom field lives in extraFields, so reading only `fields` would miss it and always fall
      // back to the start value.
      const submitValues: OperationInputs = computed.length
        ? applyComputedValues(operation, {
            parentFields: gatherParentFields(origin.sample),
            values,
            resolveFieldName: resolveLabel,
          })
        : values;
      const request = buildOperationRequest({
        operation,
        values: submitValues,
        origins: origins.map(toOrigin),
        resolveLabel,
        templateId,
        documentationLink: documentation
          ? { fieldName: t("operations.documentation.fieldName"), targetGlobalId: documentation.globalId }
          : undefined,
        amountMode,
        perSubsampleAmounts,
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
          // The amount mode and (for "perSubsample") the per-origin amounts are only meaningful for a
          // multi-origin run, so store them only for such operations (adr/0009); a single-origin bundle
          // stays as before, and an older bundle without them keeps normalising to "same".
          ...(usesAmountModes(operation)
            ? { amountMode, perSubsampleAmounts: amountMode === "perSubsample" ? perSubsampleAmounts : {} }
            : {}),
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
      await Promise.all(origins.map((o) => o.fetchAdditionalInfo()));
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
          // A terminal operation (Destroy) has nothing to remember (no template/amounts/documentation),
          // so passing no handler hides the "remember" checkbox.
          onRememberChange={operation.noOutput ? undefined : onRememberChange}
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
          origins={origins}
          amountMode={amountMode}
          onAmountModeChange={setAmountMode}
          perSubsampleAmounts={perSubsampleAmounts}
          onPerSubsampleAmountsChange={setPerSubsampleAmounts}
        />
      );
    }
    if (key === "documentation") {
      return <DocumentationStep value={documentation} onChange={setDocumentation} />;
    }
    return confirmationStep();
  };

  // The confirmation card, shared by the confirm step and the step-one fast path (adr/0009). Origins
  // are passed as name + global id so a "per subsample" run can break the amounts down per origin.
  const confirmationStep = (): React.ReactNode => {
    if (!operation) return null;
    return (
      <OperationConfirmation
        operation={operation}
        values={values}
        documentation={documentation}
        templateSelection={templateSelection}
        originSampleName={origin.sample.name}
        originName={origin.name ?? ""}
        originHasAmount={getValue(origin.quantity) > 0}
        amountMode={amountMode}
        perSubsampleAmounts={perSubsampleAmounts}
        origins={origins.map((o) => ({ globalId: o.globalId ?? "", name: o.name ?? "" }))}
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
          fastPath ? (
            // A remembered run: show its confirmation on step one so the user can Perform without
            // stepping through the wizard (adr/0009). "Review / edit" (below) drops into the stepper.
            confirmationStep()
          ) : (
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
                    {isViewportSmall ? (
                      <StepContent>{index === activeStep ? stepContent(key) : null}</StepContent>
                    ) : null}
                  </Step>
                ))}
              </Stepper>
              {isViewportSmall ? null : stepContent(stepKeys[activeStep])}
            </>
          )
        ) : (
          <OperationPicker
            onSelect={selectOperation}
            selectionCount={origins.length}
            allSameCategory={allSameCategory}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        {operation ? (
          fastPath ? (
            // Remembered run: perform straight from step one, or drop into the wizard to change it.
            <>
              <Button onClick={() => setReviewing(true)} disabled={submitting}>
                {t("operations.wizard.reviewEdit")}
              </Button>
              <SubmitSpinnerButton
                onClick={() => void submit()}
                loading={submitting}
                disabled={submitting || !allStepsValid()}
                label={t("operations.wizard.perform")}
              />
            </>
          ) : (
            <>
              <Button onClick={back} disabled={submitting}>
                {t("common:actions.back")}
              </Button>
              {isLast ? (
                <SubmitSpinnerButton
                  onClick={() => void submit()}
                  loading={submitting}
                  // Gate Perform on stepValid() too, not just submitting: the confirm step's own
                  // guards (e.g. Destroy's empty-origin block) must actually block submission, not only
                  // show a message. Next is gated the same way below.
                  disabled={submitting || !stepValid()}
                  label={t("operations.wizard.perform")}
                />
              ) : (
                <Button
                  variant="contained"
                  color="callToAction"
                  disableElevation
                  onClick={next}
                  disabled={!stepValid()}
                >
                  {t("common:actions.next")}
                </Button>
              )}
            </>
          )
        ) : null}
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(OperationWizard);
