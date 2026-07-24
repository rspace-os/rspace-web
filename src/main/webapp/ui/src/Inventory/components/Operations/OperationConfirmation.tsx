import Alert from "@mui/material/Alert";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Stack from "@mui/material/Stack";
import { useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import DescriptionList from "@/components/DescriptionList";
import useStores from "@/stores/use-stores";
import { applyComputedValues } from "./computedValues";
import type { DocumentationSelection } from "./DocumentationStep";
import { type ConfirmSummaryField, type InventoryOperation, usesAmountModes } from "./operationsConfig";
import type { TemplateSelection } from "./TemplateStep";
import type { AmountMode, OperationInputs, OperationQuantity, PerSubsampleAmounts } from "./types";

/**
 * Final step: a preview card of the sample the operation will create, before it is performed. The
 * card header names the new sample and the operation; the body is a DescriptionList (label:value),
 * so field names read as bold, secondary-coloured <dt> labels distinct from their values — the same
 * label:value paradigm Inventory uses to preview records elsewhere (InfoCard/RecordDetails).
 */
function OperationConfirmation({
  operation,
  values,
  documentation,
  templateSelection,
  originSampleName,
  originName,
  originHasAmount = true,
  amountMode = "same",
  perSubsampleAmounts = {},
  origins = [],
}: {
  operation: InventoryOperation;
  values: OperationInputs;
  documentation: DocumentationSelection;
  templateSelection: TemplateSelection;
  originSampleName: string;
  /** The origin subsample's own name, shown as the card title for a terminal operation (Destroy),
   * which acts on the origin and creates no new sample. */
  originName: string;
  /** Whether the origin holds any material. A terminal operation that empties its origin
   * (`emptiesOrigin`) cannot run on an empty subsample, so the confirmation shows why and the wizard
   * disables Perform. Defaults to true (producing operations gate emptiness on their amounts step). */
  originHasAmount?: boolean;
  /** The amount mode for a multi-origin operation (adr/0009); drives how the amount-taken row reads. */
  amountMode?: AmountMode;
  /** Per-origin amounts (by origin global id) for "perSubsample" mode. */
  perSubsampleAmounts?: PerSubsampleAmounts;
  /** Every selected origin (name + global id), for the "per subsample" amount breakdown. */
  origins?: Array<{ globalId: string; name: string }>;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const { unitStore } = useStores();
  const resolveLabel = t as unknown as (key: string, params?: Record<string, unknown>) => string;
  const { effect } = operation;
  const unitLabel = (unitId: number): string => unitStore.getUnit(unitId)?.label ?? "";

  // These describe the created sample; a terminal operation (noOutput) has none, so guard each read.
  const count = effect.countFrom ? Number(values[effect.countFrom]) : 0;
  const each = effect.eachAmountFrom ? (values[effect.eachAmountFrom] as OperationQuantity | undefined) : undefined;
  const after = effect.amountTakenFrom ? (values[effect.amountTakenFrom] as OperationQuantity) : null;
  const storageTemp = effect.storageTempFrom ? (values[effect.storageTempFrom] as OperationQuantity) : null;
  const name = effect.nameFrom ? String(values[effect.nameFrom]) : "";
  const processName = effect.processNameFrom ? String(values[effect.processNameFrom] ?? "").trim() : "";
  // The link field name may interpolate {originName} (Pool's "Pooled from: {originName}"), which is
  // not in `values` - it is injected per origin at build time. For a single-origin operation, supply
  // the origin's name so the preview resolves (without it ICU throws on the missing argument and the
  // raw template string is shown). A multi-origin operation lists every origin in the linkBack row
  // below instead, so this single value is only its single-origin fallback.
  const linkName = effect.links.length ? resolveLabel(effect.links[0].fieldNameKey, { ...values, originName }) : "";
  // Preview the values the operation will compute (adr/0006) so the origin-field rows show real
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
  // One builder per configurable summary field; a builder returns null when its value is absent
  // (e.g. no documentation linked), so the row is skipped, or an array to emit several rows (origin
  // fields). The operation's confirmSummary picks and orders which of these appear (see
  // operations_config.json).
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
      // An operation with per-origin amount modes (Pool) takes from each subsample, so its row is
      // labelled "Amount taken from each subsample"; a single-origin operation keeps "Amount taken".
      const amountTakenLabel = usesAmountModes(operation)
        ? t("operations.confirm.labels.amountTakenEach")
        : t("operations.confirm.labels.amountTaken");
      // Multi-origin amount modes (adr/0009): "take all" reads as emptied; "per subsample" lists each
      // origin's chosen amount; "same" (and single-origin) shows the one shared amount as before.
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
                return <div key={o.globalId}>{`${o.name}: ${amount}`}</div>;
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
      // Pool (multi-origin) links back to every pooled subsample; its link field name interpolates each
      // origin's own name ("Pooled from: {originName}"), so list one line per origin to match the links
      // buildOperationRequest creates, rather than only the representative origin (adr/0007). A
      // single-origin operation shows its one link name.
      if (operation.requiresMultiple && origins.length && effect.links.length) {
        const fieldNameKey = effect.links[0].fieldNameKey;
        return {
          label: t("operations.confirm.labels.linkBack"),
          value: (
            <>
              {origins.map((o) => (
                <div key={o.globalId}>{resolveLabel(fieldNameKey, { ...values, originName: o.name })}</div>
              ))}
            </>
          ),
        };
      }
      return { label: t("operations.confirm.labels.linkBack"), value: linkName };
    },
    documentation: () =>
      documentation ? { label: t("operations.confirm.labels.documentation"), value: documentation.name } : null,
    // Terminal operations (Destroy): the origin's volume is set to zero, and each custom field the
    // operation adds to the origin (e.g. the disposal date) is previewed as its own row.
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
  // Default order preserves the pre-config behaviour for operations that do not declare confirmSummary.
  const DEFAULT_SUMMARY: Array<ConfirmSummaryField> = [
    "process",
    "template",
    "subsamples",
    "amountTaken",
    "linkBack",
    "documentation",
  ];
  const content: Array<Row> = (operation.confirmSummary ?? DEFAULT_SUMMARY)
    .flatMap((field) => rowBuilders[field]() ?? [])
    .filter((row): row is Row => row !== null);

  // A terminal operation (Destroy) skips the details step, so its description is shown here as an info
  // panel, and its "cannot operate on an empty subsample" guard also moves here: when it would empty
  // the origin but the origin holds nothing, the confirmation explains why and the wizard blocks Perform.
  const infoText = operation.noOutput && operation.descriptionKey ? resolveLabel(operation.descriptionKey) : null;
  const originEmptyBlocked = Boolean(effect.emptiesOrigin) && !originHasAmount;

  return (
    <Stack spacing={1}>
      {infoText ? <Alert severity="info">{infoText}</Alert> : null}
      {originEmptyBlocked ? <Alert severity="error">{t("operations.fields.originAmountZero")}</Alert> : null}
      <Card variant="outlined">
        <CardHeader
          // A terminal operation (Destroy) creates no sample, so the card names the origin subsample it
          // acts on rather than a new sample name, and its subheader must not claim a "New sample".
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
    </Stack>
  );
}

export default observer(OperationConfirmation);
