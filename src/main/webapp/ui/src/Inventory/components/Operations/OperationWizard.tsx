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
import useUiPreference, { PREFERENCES, readUiPreference, useRawUiPreferences } from "@/hooks/api/useUiPreference";
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
import { buildFacadeRequest } from "./buildOperationRequest";
import type { DocumentationSelection } from "./DocumentationStep";
import DocumentationStep from "./DocumentationStep";
import OperationConfirmation from "./OperationConfirmation";
import OperationDetailsStep from "./OperationDetailsStep";
import OperationPicker from "./OperationPicker";
import { describeOperationError, performOperation, sampleNameAvailable } from "./operationsApi";
import {
  amountKeysFor,
  type InventoryOperation,
  operations,
  resolveDefaultAmountMode,
  resolveProcessName,
  usesAmountModes,
} from "./operationsConfig";
import {
  amountIsStorable,
  amountTakenExceedsOrigin,
  detailsValid,
  originBlockedReason,
  quantityExceedsOrigin,
  reconcileRestoredQuantities,
} from "./operationValidation";
import {
  addProcessName,
  processNameDefaultAfterPerform,
  processValuesPreferenceFor,
  rememberKey,
} from "./processNames";
import { normalizeProcessValues, type ProcessValues } from "./processValues";
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

function blankAmounts(operation: InventoryOperation, origin: SubSampleModel): OperationInputs {
  const out: OperationInputs = {};
  const unitId = getUnitId(origin.quantity);
  // A terminal operation (Destroy) declares no count/each-amount; only set those that exist.
  if (operation.effect.countFrom) out[operation.effect.countFrom] = 1;
  if (operation.effect.eachAmountFrom) out[operation.effect.eachAmountFrom] = { numericValue: 1, unitId };
  if (operation.effect.amountTakenFrom) out[operation.effect.amountTakenFrom] = { numericValue: 1, unitId };
  return out;
}

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
 * toCommonUnit throws for any unit outside volume/mass/dimensionless, and molarity/concentration
 * units are real, server-supported units a subsample can hold - so gate with categoryOfUnit first.
 * 0 is the safe fallback: an unconvertible origin sorts as smallest, and allSameCategory is already
 * false for those units, so Pool stays disabled either way.
 */
function commonQuantity(origin: SubSampleModel): number {
  if (!origin.quantity) return 0;
  const unitId = getUnitId(origin.quantity);
  return categoryOfUnit(unitId) === null ? 0 : toCommonUnit(getValue(origin.quantity), unitId);
}

/**
 * The origin the wizard treats as representative for a (possibly multi-origin) run: the one holding
 * the *least* material. Because a Pool takes the same shared amount from every origin, the smallest
 * origin is the binding constraint, so checking against it is equivalent to checking every origin.
 */
function representativeOrigin(origins: Array<SubSampleModel>): SubSampleModel {
  return origins.reduce((smallest, o) => (commonQuantity(o) < commonQuantity(smallest) ? o : smallest));
}

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
  const origin = representativeOrigin(origins);
  // Derived via categoryOfUnit rather than SubSampleModel.quantityCategory: that getter throws when
  // the (localStorage-backed) unit store has no entry for the id, which is every id before GET
  // /units first resolves, crashing the wizard before it renders. categoryOfUnit needs no store and
  // returns null instead for an unset or unrecognised unit.
  const categoryOf = (subSample: SubSampleModel): UnitCategory | null => categoryOfUnit(getUnitId(subSample.quantity));
  const originCategory = categoryOf(origin);
  // Pool requires every selected origin to share one measurement category. An undeterminable
  // category is not a match: it must leave Pool disabled rather than enable it on two nulls
  // comparing equal.
  const allSameCategory = originCategory !== null && origins.every((o) => categoryOf(o) === originCategory);
  const { isViewportSmall } = useViewportDimensions();
  const [operation, setOperation] = React.useState<InventoryOperation | null>(null);
  const [values, setValues] = React.useState<OperationInputs>({});
  const [documentation, setDocumentation] = React.useState<DocumentationSelection>(null);
  const [remember, setRemember] = React.useState(false);
  const [amountMode, setAmountMode] = React.useState<AmountMode>("same");
  const [perSubsampleAmounts, setPerSubsampleAmounts] = React.useState<PerSubsampleAmounts>({});
  // When a complete remembered bundle loads, step one offers Perform straight away; "reviewing" is set
  // once the user chooses to step through the wizard instead (DevDocs/adr/0011).
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
  // Each operation type has its own collection: useUiPreference derives its value fresh from
  // context every render rather than caching it in a useState, so passing a DIFFERENT preference
  // key as `operation` changes reads that operation's data immediately rather than a stale mirror
  // of whichever operation was selected first.
  const [processValues, setProcessValues] = useUiPreference<Record<string, ProcessValues>>(
    processValuesPreferenceFor(operation?.key ?? ""),
    { defaultValue: {} },
  );
  // The raw context, for reading a bundle for an operation OTHER than the one `processValues` above
  // is currently bound to: `processValues` only starts reading a new operation's collection on the
  // render AFTER `operation` state actually changes.
  const uiPreferences = useRawUiPreferences();
  const [processNames, setProcessNames] = useUiPreference<Record<string, Array<string>>>(
    PREFERENCES.INVENTORY_OPERATION_PROCESS_NAMES,
    { defaultValue: {} },
  );
  const [processNameDefaults, setProcessNameDefaults] = useUiPreference<Record<string, string>>(
    PREFERENCES.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS,
    { defaultValue: {} },
  );

  const stepKeys: ReadonlyArray<string> = operation
    ? (operation.steps ?? [
        "details",
        "template",
        "amounts",
        ...(operation.documentationStep ? ["documentation"] : []),
        "confirm",
      ])
    : [];
  const isLast = activeStep === stepKeys.length - 1;

  const amountKeys: ReadonlySet<string> = operation ? amountKeysFor(operation) : new Set();
  const detailKeys: ReadonlySet<string> = operation
    ? new Set(operation.inputs.map((i) => i.key).filter((k) => !amountKeys.has(k)))
    : new Set();
  const amountCategory = templateSelection.quantityCategory ?? originCategory;
  // A multi-origin operation (Pool) has several parent samples, so "use parent template" is
  // ambiguous and always disabled for it.
  const parentHasTemplate = !operation?.requiresMultiple && (origin.sample.templateId ?? null) !== null;

  const parentTemplateId = origin.sample.templateId ?? null;

  // This check lives HERE, not in TemplateStep, because the gate it feeds (templateStepValid) is
  // evaluated on every step including step one, while the wizard renders only the active step - a
  // check owned by the step would never run for the one-click fast path. The step only displays
  // this status.
  const [parentTemplateError, setParentTemplateError] = React.useState<string | null>(null);
  const [parentTemplateChecking, setParentTemplateChecking] = React.useState(false);
  const parentCheckIdRef = React.useRef(0);

  const needsParentTemplateCheck =
    parentHasTemplate &&
    parentTemplateId !== null &&
    templateSelection.mode === "fromSample" &&
    templateSelection.templateId === null;

  React.useEffect(() => {
    if (!needsParentTemplateCheck || parentTemplateId === null) {
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
        // A trashed template is still readable (soft deletion) and its mandatory fields stay intact,
        // so it would otherwise pass here and be offered as "From <sample name>", only to be
        // refused by the server at Perform.
        if (template.deleted) {
          setParentTemplateError(t("operations.template.templateDeleted", { name: template.name }));
          return;
        }
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
        // signal that this step is done.
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

  // A remembered template's id and name come from the stored bundle, not a check, so by now it may
  // have been renamed or moved to trash. One lookup settles both: the name is refreshed from the
  // server, and a template that is gone, trashed, or no longer usable drops the selection so the
  // user must choose another.
  const [rememberedTemplateError, setRememberedTemplateError] = React.useState<string | null>(null);
  const rememberedCheckIdRef = React.useRef(0);
  const rememberedTemplateId = templateSelection.mode === "remembered" ? templateSelection.templateId : null;
  const needsRememberedTemplateCheck = rememberedTemplateId !== null && templateSelection.pendingCheck === true;

  React.useEffect(() => {
    if (!needsRememberedTemplateCheck || rememberedTemplateId === null) {
      // Deliberately does NOT clear the error message here: rejecting a remembered template resets
      // the mode, which lands in this branch on the very next render and would wipe the explanation
      // the user still needs to see. It's cleared instead when the user makes their own choice, or
      // switches operation.
      rememberedCheckIdRef.current++;
      return;
    }
    const checkId = ++rememberedCheckIdRef.current;
    setRememberedTemplateError(null);
    void (async () => {
      // Always resets to "unselected", never initialTemplateSelection: the parent sample usually has
      // a template, so that fallback would resolve to "fromSample" and hand back one-click Perform
      // against a template the user never chose. The step must stay incomplete until they make an
      // explicit choice.
      const reject = (message: string) => {
        setRememberedTemplateError(message);
        setTemplateSelection((previous) =>
          previous.mode === "remembered" && previous.templateId === rememberedTemplateId
            ? { mode: "unselected", templateId: null, remember: previous.remember }
            : previous,
        );
      };
      try {
        const template = await getRootStore().searchStore.getTemplate(
          rememberedTemplateId,
          null,
          new AlwaysNewFactory(),
        );
        if (checkId !== rememberedCheckIdRef.current) return;
        // A trashed template still resolves (soft deletion), so the flag is the only signal.
        if (template.deleted) {
          reject(t("operations.template.rememberedDeleted", { name: template.name }));
          return;
        }
        const reason = templateBlockReason(template, i18n.resolvedLanguage ?? i18n.language);
        if (reason.blocked) {
          reject(t("operations.template.mandatoryFieldsError", { count: reason.count, fields: reason.fields }));
          return;
        }
        // Passed: take the template's CURRENT name and category, and clear pendingCheck, which is
        // what makes the step valid and re-offers the fast path.
        setTemplateSelection((previous) =>
          previous.mode === "remembered" && previous.templateId === rememberedTemplateId
            ? {
                ...previous,
                pendingCheck: false,
                templateName: template.name,
                quantityCategory: template.quantityCategory,
              }
            : previous,
        );
      } catch {
        if (checkId !== rememberedCheckIdRef.current) return;
        reject(t("operations.template.lookupFailed"));
      }
    })();
  }, [needsRememberedTemplateCheck, rememberedTemplateId, parentHasTemplate, t, i18n.language]);

  const sampleNameBase = (op: InventoryOperation): string =>
    op.requiresMultiple ? resolveLabel(op.labelKey) : origin.sample.name;

  /**
   * A restored template selection, with "use parent template" dropped when this run has no parent
   * template to use (reachable when a bundle is reused on a different origin, or on a Pool where
   * "the parent" is ambiguous). Left as "fromSample", the step would demand a validated template id
   * that nothing could ever supply, leaving Next permanently disabled with no way for the user to
   * fix it.
   */
  const restoredTemplateSelection = (remembered: TemplateSelection): TemplateSelection =>
    remembered.mode === "fromSample" && !parentHasTemplate
      ? { ...initialTemplateSelection(parentHasTemplate), remember: remembered.remember }
      : remembered;

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
      createdCategory: restoredTemplate.quantityCategory ?? originCategory,
      perOriginUnitIds: Object.fromEntries(origins.map((o) => [o.globalId ?? "", getUnitId(o.quantity)])),
    });

  const bundleFor = (op: InventoryOperation, key: string): ProcessValues | null => {
    const own = readUiPreference<Record<string, ProcessValues>>(uiPreferences, processValuesPreferenceFor(op.key), {});
    return normalizeProcessValues(own[key]);
  };

  const stateForKey = (op: InventoryOperation, vals: OperationInputs) => {
    const key = rememberKey(op, vals);
    const bundle = bundleFor(op, key);
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
      templateSelection: initialTemplateSelection(parentHasTemplate),
      documentation: null,
      amountMode: resolveDefaultAmountMode(op),
      perSubsampleAmounts: {},
      remember: false,
    };
  };

  const selectOperation = (op: InventoryOperation) => {
    const initial = buildInitialValues(op, origin);
    const savedProcessName = (op.effect.processNameFrom ? (processNameDefaults?.[op.key] ?? "") : "").trim();
    if (op.effect.processNameFrom && savedProcessName) initial[op.effect.processNameFrom] = savedProcessName;
    const s = stateForKey(op, initial);
    // Seed the derived sample name immediately; the dedup effect (below) refines it once existing
    // names are fetched. A terminal operation (Destroy) has no name to derive, hence the guard.
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
    setRememberedTemplateError(null);
    setActiveStep(0);
  };

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

  // Ticking only marks the current form for saving on Perform. It must not reload the stored
  // bundle: the user may have unticked, changed the values, and ticked again to save the new ones.
  const onRememberChange = (checked: boolean) => {
    if (!operation) return;
    setRemember(checked);
    if (!checked) {
      setValues((v) => freshValues(operation, origin, v));
      setTemplateSelection(initialTemplateSelection(parentHasTemplate));
      setDocumentation(null);
      setAmountMode(resolveDefaultAmountMode(operation));
      setPerSubsampleAmounts({});
    }
  };

  // A template picked in a DIFFERENT measurement category resets the created amount's prefilled
  // unit, so a stale unit from the old category can't survive into the request. The amount taken
  // FROM the origin stays in the origin's own category, so its unit is untouched.
  const onTemplateSelectionChange = (next: React.SetStateAction<TemplateSelection>) => {
    // The user is making their own choice, which answers the "choose another template" the dropped
    // remembered template asked for.
    setRememberedTemplateError(null);
    const eachAmountFrom = operation?.effect.eachAmountFrom;
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

  // Keyed on the derived base name (not `values` itself) so this re-runs only when the process name
  // changes, not when it sets the sample name. Skipped once the user hand-edits the name.
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

  /*
   * Renewal is a POST that recreates a five-minute lock, so it is ordered against the release the
   * caller performs on close: closing while one is in flight lets the DELETE go first, and the late
   * POST then re-locks an origin whose wizard is already gone, with nothing left to release it.
   * The latest renewal is kept here and closeAfterRenewals waits for it (RSDEV-1231).
   */
  const renewals = React.useRef<Promise<unknown>>(Promise.resolve());

  // Set the moment a close begins. Close waits for the renewals outstanding at that instant, so a
  // batch started afterwards would not be waited for and could land behind the caller's release.
  // A closing wizard has no lock left to keep alive, so it simply stops renewing.
  const closing = React.useRef(false);

  /** Extends the edit-session lock on each origin as the wizard's steps complete (RSDEV-1231). */
  const extendOriginLocks = () => {
    if (closing.current) return;
    const batch = Promise.allSettled(
      origins.map((o) =>
        o.acquireEditLock().catch((error: unknown) => {
          console.warn("Could not extend the edit lock on an operation origin", error);
        }),
      ),
    );
    // Folded in, not replaced: stepping again while a batch is still in flight would otherwise drop
    // the older one, and it could then land after the release. Batches are not chained to each
    // other, so a slow renewal never delays a later one; only close waits for them all.
    renewals.current = Promise.allSettled([renewals.current, batch]);
  };

  /** Hands back to the caller only once no renewal can still land behind its release. */
  const closeAfterRenewals = () => {
    if (closing.current) return;
    closing.current = true;
    void renewals.current.then(onClose);
  };

  const locksLapsed = (): boolean =>
    origins.some((o) => o.lockExpired || (Boolean(o.lockExpiry) && Date.now() >= o.lockExpiry.getTime()));

  const next = () => {
    extendOriginLocks();
    setActiveStep((s) => s + 1);
  };

  const back = () => {
    extendOriginLocks();
    if (activeStep === 0) {
      setOperation(null);
      return;
    }
    setActiveStep((s) => s - 1);
  };

  // "all" is always valid: every origin is fully emptied, so over-removal cannot occur. "same" and
  // "perSubsample" are checked against the origin(s)' actual quantity.
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
    // An operation that empties its origin (Destroy) skips the details step, where other operations
    // gate this, so enforce it for every step here instead.
    if (operation.effect.emptiesOrigin && origins.some((o) => commonQuantity(o) <= 0)) return false;
    // Checked on EVERY origin explicitly, not just the representative: the two are equivalent today
    // (representative is the smallest), but the backend rule is per-origin, so the gate matches it.
    if (key === "details")
      return detailsValid(operation, values, detailKeys) && origins.every((o) => commonQuantity(o) > 0);
    if (key === "template") return templateStepValid(templateSelection);
    if (key === "amounts") return amountsStepValid();
    return true;
  };

  const stepValid = (): boolean => stepValidFor(stepKeys[activeStep]);
  const allStepsValid = (): boolean => operation !== null && stepKeys.every(stepValidFor);
  const fastPath = operation !== null && activeStep === 0 && remember && !reviewing && allStepsValid();

  const closeUnlessSubmitting = () => {
    if (!submitting) closeAfterRenewals();
  };

  const submit = async (): Promise<void> => {
    if (!operation) return;
    if (locksLapsed()) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: t("operations.wizard.originsLocked"),
          message: t("operations.wizard.lockExpired"),
          variant: "error",
        }),
      );
      return;
    }
    setSubmitting(true);
    extendOriginLocks();
    try {
      // "fromSample" reads the origin sample's own template, so the parent must be loaded first.
      if (templateSelection.mode === "fromSample") await origin.sample.fetchAdditionalInfo();
      const templateId = operation.noOutput
        ? null
        : resolveTemplateId({
            mode: templateSelection.mode,
            pickedTemplateId: templateSelection.templateId,
            originSampleTemplateId: origin.sample.templateId ?? null,
          });
      const request = buildFacadeRequest({
        operation,
        values,
        origins: origins.map(toOrigin),
        templateId,
        documentedByGlobalId: documentation?.globalId ?? null,
        amountMode,
        perSubsampleAmounts,
      });
      await showToastWhilstPending(t("operations.wizard.inProgress"), performOperation(operation, request));
    } catch (error) {
      // The error's `message` for a rejected request is just "Errors detected: 1"; the actual reason
      // lives in the field-scoped errors array, which describeOperationError reads instead.
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: t("operations.wizard.failed"),
          message: describeOperationError(error, operation, resolveLabel, t("operations.wizard.failed")),
          variant: "error",
        }),
      );
      // Re-read the origins: the usual rejection is "you asked for more than it holds", and the
      // amounts step validates against origin.quantity, so without a refresh the user could only
      // fail the same way again.
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
    // again. Bookkeeping errors are warnings, and the wizard closes.
    try {
      if (remember) {
        const key = rememberKey(operation, values);
        const bundle: ProcessValues = {
          values: omit(
            values,
            [operation.effect.nameFrom, operation.effect.processNameFrom].filter((k): k is string => Boolean(k)),
          ),
          template: templateSelectionToDefault(templateSelection),
          documentation,
          // Amount mode and per-origin amounts are only meaningful for a multi-origin run, so store
          // them only then; an older bundle without them keeps normalising to "same".
          ...(usesAmountModes(operation)
            ? { amountMode, perSubsampleAmounts: amountMode === "perSubsample" ? perSubsampleAmounts : {} }
            : {}),
        };
        setProcessValues({ ...(processValues ?? {}), [key]: bundle });
        if (operation.effect.processNameFrom) {
          const name = String(values[operation.effect.processNameFrom] ?? "");
          const list = processNames?.[operation.key] ?? [];
          const updated = addProcessName(list, name);
          if (updated !== list) setProcessNames({ ...(processNames ?? {}), [operation.key]: updated });
          setProcessNameDefaults(processNameDefaultAfterPerform(processNameDefaults ?? {}, operation.key, name));
        }
      }
      closing.current = true;
      await renewals.current;
      onClose();
      await Promise.all(origins.map((o) => o.fetchAdditionalInfo()));
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
          rememberedTemplateError={rememberedTemplateError}
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

  // Origins are passed as name + global id so a "per subsample" run can break the amounts down per
  // origin.
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
        originBlocked={originBlockedReason(origin.quantity)}
        amountMode={amountMode}
        perSubsampleAmounts={perSubsampleAmounts}
        origins={origins.map((o) => ({ globalId: o.globalId ?? "", name: o.name ?? "" }))}
        remember={remember}
        // A terminal operation (Destroy) has nothing to remember (no template/amounts/documentation),
        // so passing no handler hides the checkbox.
        onRememberChange={operation.noOutput ? undefined : onRememberChange}
      />
    );
  };

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
            confirmationStep()
          ) : (
            <>
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
            operations={operations}
            onSelect={selectOperation}
            selectionCount={origins.length}
            allSameCategory={allSameCategory}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={closeUnlessSubmitting} disabled={submitting}>
          {t("common:actions.cancel")}
        </Button>
        {operation ? (
          fastPath ? (
            <>
              <Button onClick={() => setReviewing(true)} disabled={submitting}>
                {t("operations.wizard.reviewEdit")}
              </Button>
              <SubmitSpinnerButton
                onClick={() => void submit()}
                loading={submitting}
                disabled={submitting || locksLapsed()}
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
                  // a run an earlier step would have blocked.
                  disabled={submitting || !allStepsValid() || locksLapsed()}
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
