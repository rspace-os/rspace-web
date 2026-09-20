import Alert from "@mui/material/Alert";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Checkbox from "@mui/material/Checkbox";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormHelperText from "@mui/material/FormHelperText";
import Stack from "@mui/material/Stack";
import { useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import DescriptionList from "@/components/DescriptionList";
import useStores from "@/stores/use-stores";
import { withUniqueFieldNames } from "./buildOperationRequest";
import { applyComputedValues } from "./computedValues";
import type { DocumentationSelection } from "./DocumentationStep";
import { type ConfirmSummaryField, type InventoryOperation, resolveProcessName, usesAmountModes } from "./operations";
import type { OriginBlockedReason } from "./operationValidation";
import type { TemplateSelection } from "./TemplateStep";
import type { AmountMode, OperationInputs, OperationQuantity, PerSubsampleAmounts } from "./types";
import { resolveLabelFrom } from "./types";

function OperationConfirmation({
  operation,
  values,
  documentation,
  templateSelection,
  originSampleName,
  originName,
  originBlocked = null,
  amountMode = "same",
  perSubsampleAmounts = {},
  origins = [],
  remember = false,
  onRememberChange,
}: {
  operation: InventoryOperation;
  values: OperationInputs;
  documentation: DocumentationSelection;
  templateSelection: TemplateSelection;
  originSampleName: string;
  originName: string;
  originBlocked?: OriginBlockedReason | null;
  amountMode?: AmountMode;
  perSubsampleAmounts?: PerSubsampleAmounts;
  origins?: Array<{ globalId: string; name: string }>;
  remember?: boolean;
  onRememberChange?: (remember: boolean) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const { unitStore } = useStores();
  const resolveLabel = resolveLabelFrom(t);
  const { effect } = operation;
  const unitLabel = (unitId: number): string => unitStore.getUnit(unitId)?.label ?? "";

  const count = effect.countFrom ? Number(values[effect.countFrom]) : 0;
  const each = effect.eachAmountFrom ? (values[effect.eachAmountFrom] as OperationQuantity | undefined) : undefined;
  const after = effect.amountTakenFrom ? (values[effect.amountTakenFrom] as OperationQuantity) : null;
  const storageTemp = effect.storageTempFrom ? (values[effect.storageTempFrom] as OperationQuantity) : null;
  const name = effect.nameFrom ? String(values[effect.nameFrom] ?? "") : "";
  const processName = effect.processNameFrom ? String(values[effect.processNameFrom] ?? "").trim() : "";
  // The link field name may interpolate {originName}, which is not in `values` - it is injected per
  // origin at build time - and ICU throws on the missing argument, so supply the origin's name.
  const linkName = effect.links.length ? resolveLabel(effect.links[0].fieldNameKey, { ...values, originName }) : "";
  // Computed with no parent fields, which is exact only because the sole computed value this card
  // surfaces is an origin field needing none. An origin field sourced from a parent-dependent
  // computed value would need the origin's parent fields loaded here.
  const displayValues = operation.effect.computed?.length
    ? applyComputedValues(operation, { parentFields: [], values, resolveFieldName: resolveLabel })
    : values;
  const templateValue =
    templateSelection.mode === "none"
      ? t("operations.template.valueNone")
      : templateSelection.mode === "fromSample"
        ? t("operations.template.valueFromSample", { name: originSampleName })
        : (templateSelection.templateName ?? "");

  type Row = { label: string; value: React.ReactNode };
  const rowBuilders: Record<ConfirmSummaryField, () => Row | Array<Row> | null> = {
    process: () => (processName ? { label: t("operations.confirm.labels.process"), value: processName } : null),
    template: () => ({ label: t("operations.confirm.labels.template"), value: templateValue }),
    subsamples: () =>
      each
        ? {
            label: t("operations.confirm.labels.subsamples"),
            value: t("operations.confirm.values.subsamples", {
              count,
              amount: each.numericValue,
              unit: unitLabel(each.unitId),
            }),
          }
        : null,
    amountTaken: () => {
      const amountTakenLabel = usesAmountModes(operation)
        ? t("operations.confirm.labels.amountTakenEach")
        : t("operations.confirm.labels.amountTaken");
      if (usesAmountModes(operation) && amountMode === "all") {
        return { label: amountTakenLabel, value: t("operations.confirm.values.takeAll") };
      }
      if (usesAmountModes(operation) && amountMode === "perSubsample") {
        return {
          label: amountTakenLabel,
          value: (
            <>
              {origins.map((o) => {
                const q = perSubsampleAmounts[o.globalId];
                const amount = q
                  ? t("operations.confirm.values.amountTaken", { amount: q.numericValue, unit: unitLabel(q.unitId) })
                  : "";
                return (
                  <div key={o.globalId}>{t("operations.confirm.values.originAmount", { origin: o.name, amount })}</div>
                );
              })}
            </>
          ),
        };
      }
      return after
        ? {
            label: amountTakenLabel,
            value: t("operations.confirm.values.amountTaken", {
              amount: after.numericValue,
              unit: unitLabel(after.unitId),
            }),
          }
        : null;
    },
    storageTemp: () =>
      storageTemp
        ? {
            label: t("operations.confirm.labels.storageTemp"),
            value: t("operations.confirm.values.storageTemp", { temp: storageTemp.numericValue }),
          }
        : null,
    linkBack: () => {
      if (operation.requiresMultiple && origins.length && effect.links.length) {
        const { fieldNameKey, relationType } = effect.links[0];
        const names = withUniqueFieldNames(
          origins.map((o) => ({
            name: resolveLabel(fieldNameKey, { ...values, originName: o.name }),
            type: "link" as const,
            newFieldRequest: true as const,
            operationFieldKey: fieldNameKey,
            link: { relationType, targetGlobalId: o.globalId, versionPin: null },
          })),
        ).map((field) => field.name);
        return {
          label: t("operations.confirm.labels.linkBack"),
          value: (
            <>
              {origins.map((o, index) => (
                <div key={o.globalId}>{names[index]}</div>
              ))}
            </>
          ),
        };
      }
      return { label: t("operations.confirm.labels.linkBack"), value: linkName };
    },
    documentation: () =>
      documentation ? { label: t("operations.confirm.labels.documentation"), value: documentation.name } : null,
    originEmptied: () =>
      effect.emptiesOrigin
        ? { label: t("operations.confirm.labels.originEmptied"), value: t("operations.confirm.values.emptied") }
        : null,
    originFields: () =>
      (effect.originFields ?? []).map((spec) => ({
        label: resolveLabel(spec.nameKey),
        value: String(displayValues[spec.contentFrom] ?? ""),
      })),
  };
  const content: Array<Row> = operation.confirmSummary.flatMap((field) => rowBuilders[field]() ?? []);

  const infoText = operation.noOutput && operation.descriptionKey ? resolveLabel(operation.descriptionKey) : null;
  const originEmptyBlocked = Boolean(effect.emptiesOrigin) && originBlocked !== null;

  return (
    <Stack spacing={1}>
      {infoText ? <Alert severity="info">{infoText}</Alert> : null}
      {originEmptyBlocked ? (
        <Alert severity="error">
          {t(
            originBlocked === "unsupportedCategory"
              ? "operations.fields.originCategoryUnsupported"
              : "operations.fields.originAmountZero",
          )}
        </Alert>
      ) : null}
      <Card variant="outlined">
        <CardHeader
          title={operation.noOutput ? originName : name}
          subheader={t(
            operation.noOutput ? "operations.confirm.cardSubheaderTerminal" : "operations.confirm.cardSubheader",
            { operation: resolveLabel(operation.labelKey) },
          )}
          sx={{
            backgroundColor: theme.palette.record.sample.lighter,
            paddingBottom: "4px",
          }}
        />
        <CardContent>
          <DescriptionList content={content} dividers />
        </CardContent>
      </Card>
      {onRememberChange ? (
        <FormControl>
          <FormControlLabel
            control={<Checkbox checked={remember} onChange={(e) => onRememberChange(e.target.checked)} />}
            label={resolveLabel("operations.fields.rememberProcessValues", {
              name: resolveProcessName(operation, values),
            })}
          />
          <FormHelperText sx={{ mt: 0, ml: "34px" }}>
            {resolveLabel("operations.fields.rememberProcessValuesHelp")}
          </FormHelperText>
        </FormControl>
      ) : null}
    </Stack>
  );
}

export default observer(OperationConfirmation);
