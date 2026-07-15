import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Step from "@mui/material/Step";
import StepContent from "@mui/material/StepContent";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinnerButton from "@/components/SubmitSpinnerButton";
import useUiPreference, { PREFERENCES } from "@/hooks/api/useUiPreference";
import { mkAlert } from "@/stores/contexts/Alert";
import { CELSIUS } from "@/stores/definitions/Units";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import getRootStore from "@/stores/stores/getRootStore";
import { showToastWhilstPending } from "@/util/alerts";
import { getErrorMessage } from "@/util/error";
import ContextDialog from "../ContextMenu/ContextDialog";
import { type AmountDefault, amountDefaultsAfterPerform, normalizeAmountDefault } from "./amountDefaults";
import { buildOperationRequest } from "./buildOperationRequest";
import type { DocumentationSelection } from "./DocumentationStep";
import DocumentationStep from "./DocumentationStep";
import { docDefaultsAfterPerform, normalizeDocumentation } from "./documentationResolution";
import OperationConfirmation from "./OperationConfirmation";
import OperationDetailsStep from "./OperationDetailsStep";
import OperationPicker from "./OperationPicker";
import { createTemplateFromOriginSample, performOperation } from "./operationsApi";
import type { InventoryOperation } from "./operationsConfig";
import { detailsValid } from "./operationValidation";
import { addProcessName, processNameDefaultAfterPerform, rememberKey } from "./processNames";
import TemplateStep, { type TemplateSelection } from "./TemplateStep";
import {
  resolveTemplateId,
  selectionToRemember,
  type TemplateDefault,
  templateDefaultsAfterPerform,
  templateSelectionFor,
  templateStepValid,
} from "./templateResolution";
import type { OperationInputs, OperationOrigin, OperationQuantity } from "./types";

function buildInitialValues(operation: InventoryOperation, origin: SubSampleModel): OperationInputs {
  const unitId = getUnitId(origin.quantity);
  const values: OperationInputs = {};
  for (const input of operation.inputs) {
    if (input.type === "text") {
      values[input.key] = typeof input.default === "string" ? input.default : "";
    } else if (input.type === "integer") {
      values[input.key] = typeof input.default === "number" ? input.default : (input.min ?? 1);
    } else if (input.type === "quantity") {
      // Amounts (each-amount, amount-taken) default to 0; the user must enter a positive amount.
      values[input.key] = { numericValue: 0, unitId };
    } else {
      values[input.key] = { numericValue: -80, unitId: CELSIUS };
    }
  }
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
 * The operation wizard: pick operation -> details -> (optional documentation) -> confirm -> perform.
 * The origin subsample is pre-selected (launched from its detail pane). The whole effect is one
 * atomic backend call (adr/0001).
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
  const [operation, setOperation] = React.useState<InventoryOperation | null>(null);
  const [values, setValues] = React.useState<OperationInputs>({});
  const [documentation, setDocumentation] = React.useState<DocumentationSelection>(null);
  const [rememberDoc, setRememberDoc] = React.useState(false);
  const [activeStep, setActiveStep] = React.useState(0);
  const [submitting, setSubmitting] = React.useState(false);
  const [templateSelection, setTemplateSelection] = React.useState<TemplateSelection>({
    mode: "unselected",
    templateId: null,
    remember: false,
  });
  const [templateDefaults, setTemplateDefaults] = useUiPreference<Record<string, TemplateDefault>>(
    PREFERENCES.INVENTORY_OPERATION_TEMPLATE_DEFAULTS,
    { defaultValue: {} },
  );
  const [docDefaults, setDocDefaults] = useUiPreference<Record<string, DocumentationSelection>>(
    PREFERENCES.INVENTORY_OPERATION_DOC_DEFAULTS,
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
  const [amountDefaults, setAmountDefaults] = useUiPreference<Record<string, AmountDefault>>(
    PREFERENCES.INVENTORY_OPERATION_AMOUNT_DEFAULTS,
    { defaultValue: {} },
  );
  // Whether the user has manually edited an amount this session. Saved amounts for a process name are
  // only auto-filled while the amounts are untouched, so choosing a process name never overwrites an
  // amount the user typed by hand.
  const [amountsTouched, setAmountsTouched] = React.useState(false);
  // Whether the chosen amounts should be remembered, and whether the user has toggled that box by
  // hand. The box reflects whether amounts are already saved for the current key: unchecked when
  // nothing is saved (e.g. a fresh Derive run before a process name is entered), checked once a saved
  // key is active (a remembered process name, or a repeat Cryopreserve) - unless the user overrides it.
  const [rememberAmounts, setRememberAmounts] = React.useState(false);
  const [rememberAmountsTouched, setRememberAmountsTouched] = React.useState(false);
  // Whether the entered process name should become this operation's default on future runs. Unlike
  // the template/doc defaults (keyed per process name), the process-name default is keyed by the
  // operation alone, so it is resolved and applied as soon as the operation is chosen.
  const [rememberProcessName, setRememberProcessName] = React.useState(false);
  // Whether the user has edited the template / documentation steps during this wizard session.
  // Remembered defaults only pre-fill steps the user has not yet touched, so navigating back and
  // forth (or editing the process name) never discards a choice made during this session; everything
  // is reset on the next open, and only Perform persists across sessions (adr/0003).
  const [templateTouched, setTemplateTouched] = React.useState(false);
  const [docTouched, setDocTouched] = React.useState(false);

  const stepKeys = operation
    ? ["details", "template", ...(operation.documentationStep ? ["documentation"] : []), "confirm"]
    : [];
  const isLast = activeStep === stepKeys.length - 1;

  // The remembered amounts for the current remember-scope key (process-name-scoped for Derive,
  // operation-scoped for Cryopreserve), or null if nothing is saved.
  const savedAmountsFor = (op: InventoryOperation, vals: OperationInputs): AmountDefault | null =>
    normalizeAmountDefault(amountDefaults?.[rememberKey(op.key, op.effect.processNameFrom, vals)]);

  // Details-step change handler. Marks the amounts as touched once the user edits one (so they are no
  // longer auto-filled), and when the process name changes to one with saved amounts, pre-fills those
  // amounts - unless the user has already edited them by hand.
  const onDetailsChange = (next: OperationInputs) => {
    const pnFrom = operation?.effect.processNameFrom;
    if (operation && pnFrom) {
      const eachFrom = operation.effect.eachAmountFrom;
      const takenFrom = operation.effect.amountTakenFrom;
      const amountChanged =
        next[eachFrom] !== values[eachFrom] || (takenFrom ? next[takenFrom] !== values[takenFrom] : false);
      if (amountChanged) setAmountsTouched(true);
      if (next[pnFrom] !== values[pnFrom]) {
        // The process name changed: reflect that name's saved amounts - tick the box and pre-fill the
        // count + amounts when the name has saved amounts; untick and CLEAR any previously-loaded
        // count + amounts when it does not (a new name starts blank). Both are skipped once the user
        // has overridden the box, or edited the amounts, by hand.
        const countFrom = operation.effect.countFrom;
        const saved = savedAmountsFor(operation, next);
        if (!rememberAmountsTouched) setRememberAmounts(saved !== null);
        if (!amountsTouched) {
          if (saved) {
            next = {
              ...next,
              [countFrom]: saved.count,
              [eachFrom]: saved.eachAmount,
              ...(takenFrom ? { [takenFrom]: saved.amountTaken } : {}),
            };
          } else {
            const blank = buildInitialValues(operation, origin);
            next = {
              ...next,
              [countFrom]: blank[countFrom],
              [eachFrom]: blank[eachFrom],
              ...(takenFrom ? { [takenFrom]: blank[takenFrom] } : {}),
            };
          }
        }
      }
    }
    setValues(next);
  };

  const selectOperation = (op: InventoryOperation) => {
    setOperation(op);
    // The process-name default is keyed by operation, so it can be applied immediately: pre-fill the
    // process-name field and tick its remember box when this operation has a saved default.
    const initial = buildInitialValues(op, origin);
    // Trim defensively: a blank/whitespace-only remembered name is treated as none (never pre-filled,
    // never ticks the remember box). Blanks are not stored in the first place (processNames.ts).
    const savedProcessName = (op.effect.processNameFrom ? (processNameDefaults?.[op.key] ?? "") : "").trim();
    if (op.effect.processNameFrom && savedProcessName) initial[op.effect.processNameFrom] = savedProcessName;
    // Pre-fill remembered amounts for the resolved key: operation-scoped for Cryopreserve, and for
    // Derive when a remembered process name is present (so a repeated process starts from its amounts).
    const savedAmounts = savedAmountsFor(op, initial);
    if (savedAmounts) {
      initial[op.effect.countFrom] = savedAmounts.count;
      initial[op.effect.eachAmountFrom] = savedAmounts.eachAmount;
      if (op.effect.amountTakenFrom) initial[op.effect.amountTakenFrom] = savedAmounts.amountTaken;
    }
    setValues(initial);
    setRememberProcessName(savedProcessName !== "");
    // The amounts box starts checked only when amounts are already saved for the resolved key (a
    // remembered process name that carries amounts, or a repeat Cryopreserve); otherwise unchecked.
    setRememberAmounts(savedAmounts !== null);
    setAmountsTouched(false);
    setRememberAmountsTouched(false);
    // Template/documentation defaults are keyed by process name for operations that have one, so they
    // can only be resolved once the process name is known. Start blank and apply on leaving Details.
    setDocumentation(null);
    setRememberDoc(false);
    setTemplateSelection({ mode: "unselected", templateId: null, remember: false });
    setTemplateTouched(false);
    setDocTouched(false);
    setActiveStep(0);
  };

  // On leaving the Details step, pre-fill the template + documentation steps from the remembered
  // defaults for this operation and (for Derive) the chosen process name. Only steps the user has not
  // yet touched are pre-filled, so their in-session choices are never overwritten - including when
  // they step back and change the process name before ever visiting those steps (adr/0003).
  const applyRememberedDefaults = (op: InventoryOperation, vals: OperationInputs) => {
    const key = rememberKey(op.key, op.effect.processNameFrom, vals);
    if (!templateTouched) setTemplateSelection(templateSelectionFor(templateDefaults?.[key]));
    if (!docTouched) {
      const doc = normalizeDocumentation(docDefaults?.[key]);
      setDocumentation(doc);
      setRememberDoc(doc !== null);
    }
  };

  const next = () => {
    if (operation && stepKeys[activeStep] === "details") applyRememberedDefaults(operation, values);
    setActiveStep((s) => s + 1);
  };

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
    if (key === "details") return detailsValid(operation, values);
    if (key === "template") return templateStepValid(templateSelection);
    return true;
  };

  const submit = async (): Promise<void> => {
    if (!operation) return;
    setSubmitting(true);
    try {
      // Resolve the template choice to a single templateId before building the request. Option
      // "fromSample" reuses the origin sample's own template when it has one, and only creates a
      // new template when the sample is template-less (adr/0003).
      if (templateSelection.mode === "fromSample") {
        await origin.sample.fetchAdditionalInfo();
      }
      const originHadTemplate = (origin.sample.templateId ?? null) !== null;
      const derivedTemplateName = t("operations.template.derivedName", { name: origin.sample.name });
      const templateId = await resolveTemplateId({
        mode: templateSelection.mode,
        pickedTemplateId: templateSelection.templateId,
        originSampleTemplateId: origin.sample.templateId ?? null,
        createTemplate: () => createTemplateFromOriginSample(origin.sample, derivedTemplateName),
      });
      const request = buildOperationRequest({
        operation,
        values,
        origin: toOrigin(origin),
        resolveLabel,
        templateId,
        documentationLink: documentation
          ? { fieldName: t("operations.documentation.fieldName"), targetGlobalId: documentation.globalId }
          : undefined,
      });
      await showToastWhilstPending(t("operations.wizard.inProgress"), performOperation(request));
      // Persist the "remember" choices only now that Perform has succeeded. They are keyed per
      // operation, and additionally per process name for operations that have one (Derive): deriving
      // as "dna extraction" remembers separately from "boil" (see processNames.ts). Cancelling at any
      // step never reaches here, so nothing persists (adr/0003).
      const key = rememberKey(operation.key, operation.effect.processNameFrom, values);
      // A template created from a template-less parent is remembered as that specific template, so it
      // is reused rather than recreated (the backend cannot link it to the parent sample).
      const toRemember = selectionToRemember({
        selection: templateSelection,
        resolvedTemplateId: templateId,
        originHadTemplate,
        createdTemplateName: derivedTemplateName,
      });
      setTemplateDefaults(templateDefaultsAfterPerform(templateDefaults ?? {}, key, toRemember));
      // Remember (or, when unticked, forget) the documentation choice for this key.
      setDocDefaults(docDefaultsAfterPerform(docDefaults ?? {}, key, documentation, rememberDoc));
      // Add the chosen process name to this operation's saved list, so it appears in the autocomplete
      // next time (operations without a process name contribute nothing).
      if (operation.effect.processNameFrom) {
        const name = String(values[operation.effect.processNameFrom] ?? "");
        const list = processNames?.[operation.key] ?? [];
        const updated = addProcessName(list, name);
        if (updated !== list) setProcessNames({ ...(processNames ?? {}), [operation.key]: updated });
        // Remember (or, when unticked, forget) this operation's default process name.
        setProcessNameDefaults(
          processNameDefaultAfterPerform(processNameDefaults ?? {}, operation.key, name, rememberProcessName),
        );
      }
      // Remember (or, when the "remember amounts" box is unticked, forget) the chosen amounts for this
      // key: operation-scoped for Cryopreserve, process-name-scoped for Derive.
      const eachFrom = operation.effect.eachAmountFrom;
      const takenFrom = operation.effect.amountTakenFrom;
      setAmountDefaults(
        amountDefaultsAfterPerform(
          amountDefaults ?? {},
          key,
          Number(values[operation.effect.countFrom]),
          values[eachFrom] as OperationQuantity | undefined,
          takenFrom ? (values[takenFrom] as OperationQuantity | undefined) : undefined,
          rememberAmounts,
        ),
      );
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
          processNameOptions={
            operation.effect.processNameFrom ? (processNames?.[operation.key] ?? []).filter((n) => n.trim() !== "") : []
          }
          rememberProcessName={rememberProcessName}
          onRememberProcessNameChange={operation.effect.processNameFrom ? setRememberProcessName : undefined}
          rememberAmounts={rememberAmounts}
          onRememberAmountsChange={(r) => {
            setRememberAmountsTouched(true);
            setRememberAmounts(r);
          }}
        />
      );
    }
    if (key === "template") {
      return (
        <TemplateStep
          value={templateSelection}
          onChange={(sel) => {
            setTemplateTouched(true);
            setTemplateSelection(sel);
          }}
          originSampleName={origin.sample.name}
          processName={processNameValue || undefined}
        />
      );
    }
    if (key === "documentation") {
      return (
        <DocumentationStep
          value={documentation}
          onChange={(doc) => {
            setDocTouched(true);
            setDocumentation(doc);
          }}
          remember={rememberDoc}
          onRememberChange={(r) => {
            setDocTouched(true);
            setRememberDoc(r);
          }}
          processName={processNameValue || undefined}
        />
      );
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

  // Once an operation is chosen, the dialog heading names it on every step, appending the chosen
  // process name where the operation has one (e.g. "Derive: dna extraction"); operations without a
  // process name (Cryopreserve) show just the operation name.
  const processNameValue = operation?.effect.processNameFrom
    ? String(values[operation.effect.processNameFrom] ?? "").trim()
    : "";
  const heading = operation
    ? `${resolveLabel(operation.labelKey)}${processNameValue ? `: ${processNameValue}` : ""}`
    : t("operations.wizard.title");

  return (
    <ContextDialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{heading}</DialogTitle>
      <DialogContent dividers>
        {operation ? (
          <Stepper activeStep={activeStep} orientation="vertical">
            {stepKeys.map((key, index) => (
              <Step key={key}>
                <StepLabel>{stepLabel(key)}</StepLabel>
                <StepContent>{index === activeStep ? stepContent(key) : null}</StepContent>
              </Step>
            ))}
          </Stepper>
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
