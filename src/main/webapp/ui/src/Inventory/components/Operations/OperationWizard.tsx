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
import { buildOperationRequest } from "./buildOperationRequest";
import type { DocumentationSelection } from "./DocumentationStep";
import DocumentationStep from "./DocumentationStep";
import OperationConfirmation from "./OperationConfirmation";
import OperationDetailsStep from "./OperationDetailsStep";
import OperationPicker from "./OperationPicker";
import { createTemplateFromOriginSample, performOperation } from "./operationsApi";
import type { InventoryOperation } from "./operationsConfig";
import { detailsValid } from "./operationValidation";
import TemplateStep, { type TemplateSelection } from "./TemplateStep";
import {
  resolveTemplateId,
  selectionToRemember,
  type TemplateDefault,
  templateDefaultsAfterPerform,
} from "./templateResolution";
import type { OperationInputs, OperationOrigin } from "./types";

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
  const [activeStep, setActiveStep] = React.useState(0);
  const [submitting, setSubmitting] = React.useState(false);
  const [templateSelection, setTemplateSelection] = React.useState<TemplateSelection>({
    mode: "none",
    templateId: null,
    remember: false,
  });
  const [templateDefaults, setTemplateDefaults] = useUiPreference<Record<string, TemplateDefault>>(
    PREFERENCES.INVENTORY_OPERATION_TEMPLATE_DEFAULTS,
    { defaultValue: {} },
  );

  const stepKeys = operation
    ? ["details", "template", ...(operation.documentationStep ? ["documentation"] : []), "confirm"]
    : [];
  const isLast = activeStep === stepKeys.length - 1;

  const selectOperation = (op: InventoryOperation) => {
    setOperation(op);
    setValues(buildInitialValues(op, origin));
    setDocumentation(null);
    const remembered = templateDefaults?.[op.key];
    // A remembered specific template is shown as a banner with no radio selected ("remembered");
    // a remembered "none"/"fromSample" is applied as that radio directly.
    const isSpecific = remembered?.mode === "pick" && remembered.templateId !== null;
    setTemplateSelection(
      remembered
        ? {
            mode: isSpecific ? "remembered" : remembered.mode,
            templateId: remembered.templateId,
            templateName: remembered.templateName,
            remember: true,
          }
        : { mode: "none", templateId: null, remember: false },
    );
    setActiveStep(0);
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
    if (key === "template") {
      return templateSelection.mode !== "pick" || templateSelection.templateId !== null;
    }
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
      // Persist the per-operation "remember" choice only now that Perform has succeeded. When it is
      // unticked, any previously-remembered choice for this operation is dropped (so unticking
      // sticks). Cancelling at any step never reaches here, so it never persists (adr/0003). A
      // template created from a template-less parent is remembered as that specific template, so it
      // is reused rather than recreated (the backend cannot link it to the parent sample).
      const toRemember = selectionToRemember({
        selection: templateSelection,
        resolvedTemplateId: templateId,
        originHadTemplate,
        createdTemplateName: derivedTemplateName,
      });
      setTemplateDefaults(templateDefaultsAfterPerform(templateDefaults ?? {}, operation.key, toRemember));
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
      return <OperationDetailsStep operation={operation} origin={origin} values={values} onChange={setValues} />;
    }
    if (key === "template") {
      return (
        <TemplateStep value={templateSelection} onChange={setTemplateSelection} originSampleName={origin.sample.name} />
      );
    }
    if (key === "documentation") {
      return <DocumentationStep operationKey={operation.key} value={documentation} onChange={setDocumentation} />;
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

  return (
    <ContextDialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t("operations.wizard.title")}</DialogTitle>
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
              <Button
                variant="contained"
                color="callToAction"
                disableElevation
                onClick={() => setActiveStep((s) => s + 1)}
                disabled={!stepValid()}
              >
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
