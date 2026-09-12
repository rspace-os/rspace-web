import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Step from "@mui/material/Step";
import StepContent from "@mui/material/StepContent";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import { useQuery } from "@tanstack/react-query";
import { omit } from "es-toolkit";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinnerButton from "@/components/SubmitSpinnerButton";
import useUiPreference, { PREFERENCES } from "@/hooks/api/useUiPreference";
import useViewportDimensions from "@/hooks/browser/useViewportDimensions";
import { mkAlert } from "@/stores/contexts/Alert";
import { CELSIUS, categoryOfUnit, toCommonUnit } from "@/stores/definitions/Units";
import AlwaysNewFactory from "@/stores/models/Factory/AlwaysNewFactory";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import getRootStore from "@/stores/stores/getRootStore";
import type { UnitCategory } from "@/stores/stores/UnitStore";
import { showToastWhilstPending } from "@/util/alerts";
import { getErrorMessage } from "@/util/error";
import ContextDialog from "../ContextMenu/ContextDialog";
import { buildOperationInputsRequest } from "./buildOperationRequest";
import type { DocumentationSelection } from "./DocumentationStep";
import DocumentationStep from "./DocumentationStep";
import OperationConfirmation from "./OperationConfirmation";
import OperationDetailsStep from "./OperationDetailsStep";
import OperationPicker from "./OperationPicker";
import { describeOperationError, fetchOperationsConfig, performOperation, sampleNameAvailable } from "./operationsApi";
import {
  amountKeysFor,
  type InventoryOperation,
  resolveDefaultAmountMode,
  resolveProcessName,
  usesAmountModes,
} from "./operationsConfig";
import {
  amountIsStorable,
  amountTakenExceedsOrigin,
  detailsValid,
  quantityExceedsOrigin,
  reconcileRestoredQuantities,
} from "./operationValidation";
import { addProcessName, processNameDefaultAfterPerform, rememberKey } from "./processNames";
import { normalizeProcessValues, type ProcessValues, processValuesAfterPerform } from "./processValues";
import { derivedSampleName, firstAvailableName } from "./sampleNaming";
import TemplateStep, { type TemplateSelection } from "./TemplateStep";
import {
  initialTemplateSelection,
  resolveTemplateId,
  templateBlockReason,
  templateSelectionFor,
  templateSelectionToDefault,
  templateStepValid,
} from "./templateResolution";
import type { AmountMode, OperationInputs, OperationOrigin, OperationQuantity, PerSubsampleAmounts } from "./types";
import { resolveLabelFrom, UNSET_UNIT } from "./types";

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
 * units are prefilled from the origin subsample's own unit (the overwhelmingly common choice), which
 * the user may change to any unit in the amount's category. A template picked in a different
 * measurement category resets the created amount's unit (see onTemplateSelectionChange).
 */
function blankAmounts(operation: InventoryOperation, origin: SubSampleModel): OperationInputs {
  const out: OperationInputs = {};
  const unitId = getUnitId(origin.quantity);
  // A terminal operation (Destroy) declares no count/each-amount; only set those that exist.
  if (operation.effect.countFrom) out[operation.effect.countFrom] = 1;
  if (operation.effect.eachAmountFrom) out[operation.effect.eachAmountFrom] = { numericValue: 1, unitId };
  if (operation.effect.amountTakenFrom) out[operation.effect.amountTakenFrom] = { numericValue: 1, unitId };
  return out;
}

/**
 * A fresh set of values for the "defaults" state (nothing remembered, or the user unticked remember):
 * every input at its config default, amounts reset to 1 with the origin's own unit prefilled,
 * keeping only the current names.
 */
function freshValues(operation: InventoryOperation, origin: SubSampleModel, current: OperationInputs): OperationInputs {
  const values = { ...buildInitialValues(operation, origin), ...blankAmounts(operation, origin) };
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

/**
 * A subsample's quantity in its category's atomic unit, or 0 when it has no quantity or holds one
 * this module cannot convert.
 *
 * toCommonUnit THROWS ("Unknown unit: N") for any unit outside volume/mass/dimensionless, and
 * molarity (ids 11-14) and concentration (15-17) are real, server-supported inventory units that a
 * subsample can genuinely hold. Because this runs during render (representativeOrigin) and
 * ProcessAction mounts the wizard as soon as the selection is processable, that throw took out the
 * whole context menu - the same failure the categoryOfUnit comment below describes, reached through
 * a different helper. categoryOfUnit is the total unit-to-category test, so gate on it.
 *
 * 0 is the safe answer: an unconvertible origin sorts as smallest and becomes representative, and
 * allSameCategory is already false for those units, so Pool stays disabled either way.
 */
function commonQuantity(origin: SubSampleModel): number {
  if (!origin.quantity) return 0;
  const unitId = getUnitId(origin.quantity);
  return categoryOfUnit(unitId) === null ? 0 : toCommonUnit(getValue(origin.quantity), unitId);
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
 * The operation wizard: pick operation -> details (process name, derived sample name) -> template
 * -> amounts -> (optional documentation) -> confirm (summary + the remember checkbox) -> perform.
 * The origin subsample is pre-selected (launched from its detail pane). The whole effect is one
 * atomic backend call (DevDocs/adr/0007).
 *
 * A single "remember" checkbox governs everything kept for a process name: the template, the
 * documentation, and the collected amounts (DevDocs/adr/0007). Ticking it loads the saved bundle; unticking
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
  const { t, i18n } = useTranslation(["inventory", "common"]);
  const resolveLabel = resolveLabelFrom(t);
  // The representative origin (the smallest; see representativeOrigin) drives the single-origin wizard
  // logic - units, the derived name base, over-removal - unchanged. Multi-origin specifics (all the
  // origins, the shared amount, the per-origin links) are threaded in only where they differ.
  const origin = representativeOrigin(origins);
  // The measurement category of an origin, derived from its unit id through the static unit table
  // rather than read off SubSampleModel.quantityCategory. That getter goes through the unit store
  // and THROWS ("Could not get unit category") whenever the store holds no entry for the id, which
  // is every id on a fresh profile: UnitStore seeds itself from localStorage, so it stays empty
  // until GET /units has resolved once. ProcessAction mounts this wizard as soon as the selection
  // is processable, not only while the dialog is open, so that throw took out the whole context
  // menu before the operation picker had appeared (Copilot review, PR #1090). categoryOfUnit needs
  // no store, so it is right immediately, and it answers null rather than throwing for an unset or
  // unrecognised unit - which is also the honest answer for an origin holding no quantity at all.
  const categoryOf = (subSample: SubSampleModel): UnitCategory | null => categoryOfUnit(getUnitId(subSample.quantity));
  const originCategory = categoryOf(origin);
  // Pool requires every selected origin to share one measurement category (the shared amount is in one
  // unit); the picker only enables Pool when this holds, but keep it here for the picker's own gating.
  // An undeterminable category is not a match: it must leave Pool disabled rather than enable it on
  // the accident of two nulls comparing equal.
  const allSameCategory = originCategory !== null && origins.every((o) => categoryOf(o) === originCategory);
  // On small viewports (phones/tablets) the horizontal label row gets cramped, so fall back to the
  // classic vertical stepper (labels stacked, active step's content inline beneath its label).
  const { isViewportSmall } = useViewportDimensions();
  // The operation definitions come from the backend's single authoritative operations_config.json
  // (DevDocs/adr/0007). The config only changes on deployment, so cache it for the session.
  const {
    data: availableOperations,
    isError: operationsLoadFailed,
    error: operationsLoadError,
  } = useQuery({
    queryKey: ["inventory", "operationsConfig"],
    queryFn: fetchOperationsConfig,
    staleTime: Infinity,
  });
  React.useEffect(() => {
    if (operationsLoadError) {
      console.error("Could not load the operation definitions", operationsLoadError);
    }
  }, [operationsLoadError]);
  const [operation, setOperation] = React.useState<InventoryOperation | null>(null);
  const [values, setValues] = React.useState<OperationInputs>({});
  const [documentation, setDocumentation] = React.useState<DocumentationSelection>(null);
  const [remember, setRemember] = React.useState(false);
  // The multi-origin amount mode (DevDocs/adr/0007) and, for "perSubsample", the per-origin amounts by global
  // id. Single-origin operations stay on "same".
  const [amountMode, setAmountMode] = React.useState<AmountMode>("same");
  const [perSubsampleAmounts, setPerSubsampleAmounts] = React.useState<PerSubsampleAmounts>({});
  // When a complete remembered bundle loads, step one offers Perform straight away; "reviewing" is set
  // once the user chooses to step through the wizard instead (DevDocs/adr/0007).
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
  const amountKeys: ReadonlySet<string> = operation ? amountKeysFor(operation) : new Set();
  const detailKeys: ReadonlySet<string> = operation
    ? new Set(operation.inputs.map((i) => i.key).filter((k) => !amountKeys.has(k)))
    : new Set();
  // The amounts step offers the chosen template's units, or the origin subsample's when no specific
  // template is picked.
  const amountCategory = templateSelection.quantityCategory ?? originCategory;
  // Only "use parent template" needs the parent's template; when the parent has none, that option is
  // disabled and the user must pick an existing template or none. A multi-origin operation (Pool) has
  // several parent samples, so "use parent template" is ambiguous and always disabled for it.
  const parentHasTemplate = !operation?.requiresMultiple && (origin.sample.templateId ?? null) !== null;

  // Loads the origin sample's own template so the template step can hold "use parent template" to
  // the same mandatory-without-default check a picked template goes through (F5). GET
  // /sampleTemplates/{id} returns the fields the check reads, so no second fetch is needed.
  // Referentially stable per parent template id: TemplateStep's mount effect keys on this callback,
  // so a fresh identity each render would re-run the lookup continuously.
  const parentTemplateId = origin.sample.templateId ?? null;

  // The "use parent template" check lives HERE, not in TemplateStep, because the gate it feeds
  // (templateStepValid, via allStepsValid) is evaluated on every step including step one, while the
  // wizard renders only the ACTIVE step. A check owned by the step therefore never ran for the
  // one-click fast path, leaving Perform permanently disabled for the most common template mode
  // (parallel review, C2). The step now only displays this status.
  const [parentTemplateError, setParentTemplateError] = React.useState<string | null>(null);
  const [parentTemplateChecking, setParentTemplateChecking] = React.useState(false);
  // Monotonic, not a fixed sentinel: two overlapping lookups previously carried the same token, so
  // a late failure could overwrite an earlier success with a "lookup failed" message on a selection
  // that had actually passed (parallel review, I8).
  const parentCheckIdRef = React.useRef(0);

  const needsParentTemplateCheck =
    parentHasTemplate &&
    parentTemplateId !== null &&
    templateSelection.mode === "fromSample" &&
    templateSelection.templateId === null;

  React.useEffect(() => {
    if (!needsParentTemplateCheck || parentTemplateId === null) {
      // The check is no longer wanted, typically because the user switched to a picked template
      // while a lookup was outstanding. Advance the token so that lookup's result is discarded, and
      // release the status it owned: otherwise a late rejection reported lookupFailed against the
      // mode just switched TO, and "checking" stayed true until a request whose answer no longer
      // matters happened to settle (Copilot review, PR #1090).
      parentCheckIdRef.current++;
      setParentTemplateError(null);
      setParentTemplateChecking(false);
      return;
    }
    const checkId = ++parentCheckIdRef.current;
    setParentTemplateError(null);
    setParentTemplateChecking(true);
    void (async () => {
      try {
        const template = await getRootStore().searchStore.getTemplate(parentTemplateId, null, new AlwaysNewFactory());
        if (checkId !== parentCheckIdRef.current) return;
        const reason = templateBlockReason(template, i18n.resolvedLanguage ?? i18n.language);
        if (reason.blocked) {
          setParentTemplateError(
            t("operations.template.mandatoryFieldsError", {
              count: reason.count,
              fields: reason.fields,
            }),
          );
          return;
        }
        // Only a PASSING check writes the id, which is what makes templateStepValid's id test the
        // signal that this step is done. Functional update, so a concurrent change to another part
        // of the selection is not clobbered by a stale snapshot (parallel review, I7).
        setTemplateSelection((previous) =>
          previous.mode === "fromSample"
            ? { ...previous, templateId: parentTemplateId, quantityCategory: template.quantityCategory }
            : previous,
        );
      } catch {
        if (checkId !== parentCheckIdRef.current) return;
        setParentTemplateError(t("operations.template.lookupFailed"));
      } finally {
        if (checkId === parentCheckIdRef.current) setParentTemplateChecking(false);
      }
    })();
  }, [needsParentTemplateCheck, parentTemplateId, t, i18n.language]);

  // The base the derived sample name is built from: the origin's own sample name for a single-origin
  // operation, or the operation's label for a multi-origin one (Pool combines several samples, so no
  // single origin name applies - it becomes just "Pool", de-duplicated).
  const sampleNameBase = (op: InventoryOperation): string =>
    op.requiresMultiple ? resolveLabel(op.labelKey) : origin.sample.name;

  /**
   * A restored template selection, with "use parent template" dropped when this run has no parent
   * template to use.
   *
   * That combination is reachable whenever a bundle is reused on a different origin, or on a Pool
   * (where "the parent" is ambiguous, so parentHasTemplate is forced false). Left as it was, the
   * step demanded a validated template id that nothing could ever supply, and the radio is disabled
   * in that state, so Next stayed disabled with no spinner, no message and no way for the user to
   * change anything (parallel review, C3). Falling back to "unselected" asks for the one thing that
   * does resolve it: an explicit choice.
   */
  const restoredTemplateSelection = (remembered: TemplateSelection): TemplateSelection =>
    remembered.mode === "fromSample" && !parentHasTemplate
      ? { ...initialTemplateSelection(parentHasTemplate), remember: remembered.remember }
      : remembered;

  // Binds reconcileRestoredQuantities to this run's origins. The amount taken is checked against
  // the representative origin (the same one the amounts step validates against); the created
  // amount against the restored template's category when a template came back, else the origin's;
  // and each per-origin amount against that origin's own unit.
  const reconcileForOrigins = (
    op: InventoryOperation,
    restoredTemplate: TemplateSelection,
    vals: OperationInputs,
    perOrigin: PerSubsampleAmounts,
  ) =>
    reconcileRestoredQuantities({
      values: vals,
      perSubsampleAmounts: perOrigin,
      amountTakenFrom: op.effect.amountTakenFrom,
      eachAmountFrom: op.effect.eachAmountFrom,
      originUnitId: getUnitId(origin.quantity),
      // originCategory, never origin.quantityCategory: the store-backed getter throws for a unit the
      // store has no entry for, which is every unit on a fresh profile and any origin holding no
      // quantity at all (unit id 0). That threw inside the operation-select click handler, before
      // the "origin holds nothing" guard downstream ever ran (parallel review, I10). An
      // undeterminable category means "leave the amounts alone", which reconcileRestoredQuantities
      // already treats as such.
      createdCategory: restoredTemplate.quantityCategory ?? originCategory,
      perOriginUnitIds: Object.fromEntries(origins.map((o) => [o.globalId ?? "", getUnitId(o.quantity)])),
    });

  // The saved bundle for a given values set, and the wizard state (values + template + documentation +
  // whether remember is on) that a process name resolves to: its saved bundle if one exists, else the
  // defaults. Used on operation select and whenever the process name changes.
  const stateForKey = (op: InventoryOperation, vals: OperationInputs) => {
    const bundle = normalizeProcessValues(processValues?.[rememberKey(op, vals)]);
    const base = freshValues(op, origin, vals);
    if (bundle) {
      const restoredTemplate = restoredTemplateSelection(templateSelectionFor(bundle.template));
      // A bundle is keyed by operation + process name only, so a bundle saved on a volume origin is
      // offered on a mass one. Repair any restored amount whose category no longer fits before it
      // reaches the form, or the wizard offers one-click Perform on a request the endpoint rejects.
      const reconciled = reconcileForOrigins(
        op,
        restoredTemplate,
        {
          ...base,
          ...bundle.values,
        },
        bundle.perSubsampleAmounts ?? {},
      );
      return {
        values: reconciled.values,
        templateSelection: restoredTemplate,
        documentation: bundle.documentation,
        amountMode: bundle.amountMode ?? resolveDefaultAmountMode(op),
        perSubsampleAmounts: reconciled.perSubsampleAmounts,
        remember: true,
      };
    }
    return {
      values: base,
      // First-time run: preselect the parent's own template when it has one (DevDocs/adr/0007).
      templateSelection: initialTemplateSelection(parentHasTemplate),
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
        // Same reconciliation as the load path: ticking the box restores the same bundle, so it can
        // carry the same cross-category amounts.
        const restoredTemplate = restoredTemplateSelection(templateSelectionFor(bundle.template));
        const reconciled = reconcileForOrigins(
          operation,
          restoredTemplate,
          { ...values, ...bundle.values },
          bundle.perSubsampleAmounts ?? {},
        );
        setValues(reconciled.values);
        setTemplateSelection(restoredTemplate);
        setDocumentation(bundle.documentation);
        setAmountMode(bundle.amountMode ?? resolveDefaultAmountMode(operation));
        setPerSubsampleAmounts(reconciled.perSubsampleAmounts);
      }
    } else {
      setValues((v) => freshValues(operation, origin, v));
      setTemplateSelection(initialTemplateSelection(parentHasTemplate));
      setDocumentation(null);
      // Back to the operation's own configured default (Pool: "all"), like every other reset path.
      setAmountMode(resolveDefaultAmountMode(operation));
      setPerSubsampleAmounts({});
    }
  };

  // Template-step edits flow through here so a template picked in a DIFFERENT measurement category
  // resets the created amount's prefilled unit (the amounts step will offer the new category's units,
  // and a stale unit from the old category must not survive into the request). The amount taken FROM
  // the origin always stays in the origin's own category, so its unit is kept.
  const onTemplateSelectionChange = (next: React.SetStateAction<TemplateSelection>) => {
    const eachAmountFrom = operation?.effect.eachAmountFrom;
    // The step may send an updater rather than a value, because its post-lookup write crosses an
    // await (FE6). Resolving it here decides the category reset; the WRITE below resolves it again
    // against the freshest state, which is the point. Updaters are pure, so running one twice is
    // safe.
    const resolved = typeof next === "function" ? next(templateSelection) : next;
    const previousCategory = templateSelection.quantityCategory ?? originCategory;
    const nextCategory = resolved.quantityCategory ?? originCategory;
    if (eachAmountFrom && nextCategory !== previousCategory) {
      setValues((v) => {
        const each = v[eachAmountFrom] as OperationQuantity | undefined;
        return { ...v, [eachAmountFrom]: { numericValue: each?.numericValue ?? 1, unitId: UNSET_UNIT } };
      });
    }
    setTemplateSelection((previous) => (typeof next === "function" ? next(previous) : next));
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

  // Whether the amounts step is valid, given the amount mode (DevDocs/adr/0007). "same" (and any operation
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
        // Same three-decimal rule as the shared amount: the endpoint rejects a finer value.
        if (!amountIsStorable(q.numericValue)) return false;
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
    if (operation.effect.emptiesOrigin && origins.some((o) => commonQuantity(o) <= 0)) return false;
    // An origin with no amount (0, or a quantity never set) cannot be operated on - the backend
    // rejects it (DevDocs/adr/0007) - so block the first step (OperationDetailsStep shows the
    // matching error). Checked on EVERY origin explicitly: the representative is the smallest, which
    // makes the two equivalent today, but the backend rule is per-origin so the gate is too.
    if (key === "details")
      return detailsValid(operation, values, detailKeys) && origins.every((o) => commonQuantity(o) > 0);
    if (key === "template") return templateStepValid(templateSelection);
    if (key === "amounts") return amountsStepValid();
    return true;
  };

  const stepValid = (): boolean => stepValidFor(stepKeys[activeStep]);
  // Every step complete and valid: the gate for the step-one fast path (DevDocs/adr/0007), only offered when a
  // remembered bundle already makes the whole run performable.
  const allStepsValid = (): boolean => operation !== null && stepKeys.every(stepValidFor);
  // Step one shows the confirmation and Perform (skipping the wizard) when a remembered bundle fully
  // specifies a valid run and the user has not chosen to step through it.
  const fastPath = operation !== null && activeStep === 0 && remember && !reviewing && allStepsValid();

  // Closing does not cancel the in-flight POST, so allowing it (Cancel, or Escape via the dialog)
  // would let the user reopen the wizard and submit a second operation while the first is still
  // decrementing the same origins (Copilot review, PR #1090).
  const closeUnlessSubmitting = () => {
    if (!submitting) onClose();
  };

  const submit = async (): Promise<void> => {
    if (!operation) return;
    setSubmitting(true);
    try {
      // "fromSample" reads the origin sample's own template, so the parent must be loaded first.
      if (templateSelection.mode === "fromSample") await origin.sample.fetchAdditionalInfo();
      // A terminal operation (Destroy) skips the template step and creates no sample, so it needs no
      // template; resolve one only for producing operations.
      const templateId = operation.noOutput
        ? null
        : resolveTemplateId({
            mode: templateSelection.mode,
            pickedTemplateId: templateSelection.templateId,
            originSampleTemplateId: origin.sample.templateId ?? null,
          });
      // The server builds the sample from the definition and these inputs (plan-operations-server-
      // builds.md, M4), including the computed values (Passage number = parent's + 1, the disposed
      // date in the session's timezone), so none of that is assembled here any more.
      const request = buildOperationInputsRequest({
        operation,
        values,
        origins: origins.map(toOrigin),
        templateId,
        documentedByGlobalId: documentation?.globalId ?? null,
        amountMode,
        perSubsampleAmounts,
      });
      await showToastWhilstPending(t("operations.wizard.inProgress"), performOperation(request));
    } catch (error) {
      // Never fail silently: surface the reason (e.g. a backend rejection) instead of leaving the
      // Perform button looking dead. The wizard stays open so the user can retry. The reason comes
      // from the field-scoped errors array, not `message`, which for a rejected request is only
      // "Errors detected: 1" (code review, finding 4); an error on a declared input is worded with
      // the input's label rather than its bare key.
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: t("operations.wizard.failed"),
          message: describeOperationError(error, operation, resolveLabel, t("operations.wizard.failed")),
          variant: "error",
        }),
      );
      // Re-read the origins: the usual rejection is "you asked for more than it holds", and the
      // amounts step validates against origin.quantity, so without this the user can only fail the
      // same way again. Best-effort - the alert above already names the real problem.
      try {
        await Promise.all(origins.map((o) => o.fetchAdditionalInfo()));
      } catch (refreshError) {
        console.warn("Could not refresh the origins after a rejected operation", refreshError);
      }
      setSubmitting(false);
      return;
    }
    // From here the operation has committed (output created, origins decremented), so nothing below
    // may report it as failed or leave the wizard open for a retry that would charge the origins
    // again (code review, finding 2). Bookkeeping errors are warnings, and the wizard closes.
    try {
      // Persist the "remember" bundle only now that Perform has succeeded, and only when the box is
      // ticked. Keyed per operation + process name; unticking never deletes a prior bundle (DevDocs/adr/0007).
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
          // multi-origin run, so store them only for such operations (DevDocs/adr/0007); a single-origin bundle
          // stays as before, and an older bundle without them keeps normalising to "same".
          ...(usesAmountModes(operation)
            ? { amountMode, perSubsampleAmounts: amountMode === "perSubsample" ? perSubsampleAmounts : {} }
            : {}),
        };
        setProcessValues(processValuesAfterPerform(processValues ?? {}, key, bundle));
        if (operation.effect.processNameFrom) {
          const name = String(values[operation.effect.processNameFrom] ?? "");
          const list = processNames?.[operation.key] ?? [];
          const updated = addProcessName(list, name);
          if (updated !== list) setProcessNames({ ...(processNames ?? {}), [operation.key]: updated });
          setProcessNameDefaults(processNameDefaultAfterPerform(processNameDefaults ?? {}, operation.key, name));
        }
      }
      onClose();
      await Promise.all(origins.map((o) => o.fetchAdditionalInfo()));
      // Re-run the main search so the newly created sample appears without a manual re-search.
      getRootStore().searchStore.search.performSearch();
    } catch (error) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: t("operations.wizard.refreshFailed"),
          message: getErrorMessage(error, t("operations.wizard.refreshFailed")),
          variant: "warning",
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
        />
      );
    }
    if (key === "template") {
      return (
        <TemplateStep
          value={templateSelection}
          onChange={onTemplateSelectionChange}
          originSampleName={origin.sample.name}
          parentHasTemplate={parentHasTemplate}
          parentTemplateChecking={parentTemplateChecking}
          parentTemplateError={parentTemplateError}
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
          unitCategories={amountCategory ? [amountCategory] : []}
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

  // The confirmation card, shared by the confirm step and the step-one fast path (DevDocs/adr/0007). Origins
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
        remember={remember}
        // The single "remember" checkbox sits with the summary (this card), on both the confirm step
        // and the step-one fast path. A terminal operation (Destroy) has nothing to remember (no
        // template/amounts/documentation), so passing no handler hides it.
        onRememberChange={operation.noOutput ? undefined : onRememberChange}
      />
    );
  };

  // The dialog heading names the operation on every step, appending a user-entered process name where
  // the operation has one (e.g. "Derive: dna extraction"); operations with a fixed process name
  // (Cryopreserve) show just the operation name.
  const headingProcessName = operation?.effect.processNameFrom ? resolveProcessName(operation, values) : "";
  const heading = operation
    ? headingProcessName
      ? t("operations.wizard.headingWithProcess", {
          operation: resolveLabel(operation.labelKey),
          process: headingProcessName,
        })
      : resolveLabel(operation.labelKey)
    : t("operations.wizard.title");

  return (
    <ContextDialog open={open} onClose={closeUnlessSubmitting} maxWidth="sm" fullWidth disableBackdropClick>
      <DialogTitle>{heading}</DialogTitle>
      <DialogContent dividers sx={{ minHeight: operation ? "60vh" : undefined }}>
        {operation ? (
          fastPath ? (
            // A remembered run: show its confirmation on step one so the user can Perform without
            // stepping through the wizard (DevDocs/adr/0007). "Review / edit" (below) drops into the stepper.
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
        ) : operationsLoadFailed ? (
          <Alert severity="error">{t("operations.picker.loadFailed")}</Alert>
        ) : availableOperations ? (
          <OperationPicker
            operations={availableOperations}
            onSelect={selectOperation}
            selectionCount={origins.length}
            allSameCategory={allSameCategory}
          />
        ) : (
          <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
            <CircularProgress aria-label={t("operations.picker.loading")} />
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={closeUnlessSubmitting} disabled={submitting}>
          {t("common:actions.cancel")}
        </Button>
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
                // fastPath itself requires allStepsValid() in the same render, so only submitting
                // can disable Perform here.
                disabled={submitting}
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
                  // Gate Perform on EVERY step, not just the confirm step's own guards (e.g.
                  // Destroy's empty-origin block). Un-ticking "remember" here resets the earlier
                  // steps' values without leaving this step, so checking only this one could submit
                  // a run an earlier step would have blocked (Copilot review, PR #1090).
                  disabled={submitting || !allStepsValid()}
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
