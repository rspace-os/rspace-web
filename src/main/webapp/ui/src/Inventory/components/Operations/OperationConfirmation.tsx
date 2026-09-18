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
import {
  type ConfirmSummaryField,
  type InventoryOperation,
  resolveProcessName,
  usesAmountModes,
} from "./operationsConfig";
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
  /** The origin subsample's own name, shown as the card title for a terminal operation (Destroy),
   * which acts on the origin and creates no new sample. */
  originName: string;
  /** Why the origin cannot be operated on, or null when it can. A terminal operation that empties
   * its origin (`emptiesOrigin`) cannot run on a subsample holding nothing, nor on one whose unit is
   * not an amount. Defaults to null (producing operations gate this on their details and amounts
   * steps). */
  originBlocked?: OriginBlockedReason | null;
  amountMode?: AmountMode;
  perSubsampleAmounts?: PerSubsampleAmounts;
  origins?: Array<{ globalId: string; name: string }>;
  remember?: boolean;
  /** When provided, the single "remember" checkbox is shown beneath the summary card; omitted when
   *  there is nothing to remember (a terminal operation). */
  onRememberChange?: (remember: boolean) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const { unitStore } = useStores();
  const resolveLabel = resolveLabelFrom(t);
  const { effect } = operation;
  const unitLabel = (unitId: number): string => unitStore.getUnit(unitId)?.label ?? "";

  // These describe the created sample; a terminal operation (noOutput) has none, so guard each read.
  const count = effect.countFrom ? Number(values[effect.countFrom]) : 0;
  const each = effect.eachAmountFrom ? (values[effect.eachAmountFrom] as OperationQuantity | undefined) : undefined;
  const after = effect.amountTakenFrom ? (values[effect.amountTakenFrom] as OperationQuantity) : null;
  const storageTemp = effect.storageTempFrom ? (values[effect.storageTempFrom] as OperationQuantity) : null;
  const name = effect.nameFrom ? String(values[effect.nameFrom] ?? "") : "";
  const processName = effect.processNameFrom ? String(values[effect.processNameFrom] ?? "").trim() : "";
  // The link field name may interpolate {originName} (Pool's "Pooled from: {originName}"), which is
  // not in `values` - it is injected per origin at build time. For a single-origin operation, supply
  // the origin's name so the preview resolves (without it ICU throws on the missing argument and the
  // raw template string is shown). A multi-origin operation lists every origin in the linkBack row
  // below instead, so this single value is only its single-origin fallback.
  const linkName = effect.links.length ? resolveLabel(effect.links[0].fieldNameKey, { ...values, originName }) : "";
  // Preview the values the operation will compute (DevDocs/adr/0011) so the origin-field rows show real
  // content. Computed here with no parent fields, which is exact for everything this card actually
  // renders: the only computed value it surfaces is an origin field, and the sole operation with
  // those (Destroy) computes `today`, which needs no parent fields. A parent-dependent computed value
  // (Passage's passage number) is a sample textField the card never shows, so its parentless fallback
  // is never displayed. Revisit if an origin field is ever sourced from a parent-dependent computed
  // value: previewing it correctly would need the origin's parent fields loaded here.
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
  // A builder returns null when its value is absent (e.g. no documentation linked), so the row is
  // skipped, or an array to emit several rows (origin fields).
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
      // Pool (multi-origin) links back to every pooled subsample, so list one line per origin to
      // match the links buildOperationRequest actually creates, rather than only the representative
      // origin.
      if (operation.requiresMultiple && origins.length && effect.links.length) {
        const { fieldNameKey, relationType } = effect.links[0];
        // Two origins may share a name, and the server then suffixes each link name with its target;
        // previewing the raw names would show two identical lines for fields stored apart.
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
  const DEFAULT_SUMMARY: Array<ConfirmSummaryField> = [
    "process",
    "template",
    "subsamples",
    "amountTaken",
    "linkBack",
    "documentation",
  ];
  const content: Array<Row> = (operation.confirmSummary ?? DEFAULT_SUMMARY).flatMap(
    (field) => rowBuilders[field]() ?? [],
  );

  // A terminal operation (Destroy) skips the details step, so its description is shown here as an
  // info panel, and its "cannot operate on an empty subsample" guard also moves here.
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
              // resolveProcessName also covers a fixed process name (e.g. Cryopreserve's), which the
              // row-level processName above deliberately leaves blank.
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
