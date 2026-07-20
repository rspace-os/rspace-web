import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import { useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import DescriptionList from "@/components/DescriptionList";
import useStores from "@/stores/use-stores";
import type { DocumentationSelection } from "./DocumentationStep";
import type { ConfirmSummaryField, InventoryOperation } from "./operationsConfig";
import type { TemplateSelection } from "./TemplateStep";
import type { OperationInputs, OperationQuantity } from "./types";

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
}: {
  operation: InventoryOperation;
  values: OperationInputs;
  documentation: DocumentationSelection;
  templateSelection: TemplateSelection;
  originSampleName: string;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const { unitStore } = useStores();
  const resolveLabel = t as unknown as (key: string, params?: Record<string, unknown>) => string;
  const { effect } = operation;
  const unitLabel = (unitId: number): string => unitStore.getUnit(unitId)?.label ?? "";

  const count = Number(values[effect.countFrom]);
  const each = values[effect.eachAmountFrom] as OperationQuantity;
  const after = effect.amountTakenFrom ? (values[effect.amountTakenFrom] as OperationQuantity) : null;
  const storageTemp = effect.storageTempFrom ? (values[effect.storageTempFrom] as OperationQuantity) : null;
  const name = String(values[effect.nameFrom]);
  const processName = effect.processNameFrom ? String(values[effect.processNameFrom] ?? "").trim() : "";
  const linkName = resolveLabel(effect.links[0].fieldNameKey, values);
  const templateValue =
    templateSelection.mode === "none"
      ? t("operations.template.valueNone")
      : templateSelection.mode === "fromSample"
        ? t("operations.template.valueFromSample", { name: originSampleName })
        : (templateSelection.templateName ?? "");

  type Row = { label: string; value: React.ReactNode };
  // One builder per configurable summary field; a builder returns null when its value is absent
  // (e.g. no documentation linked), so the row is skipped. The operation's confirmSummary picks and
  // orders which of these appear (see operations_config.json).
  const rowBuilders: Record<ConfirmSummaryField, () => Row | null> = {
    process: () => (processName ? { label: t("operations.confirm.labels.process"), value: processName } : null),
    template: () => ({ label: t("operations.confirm.labels.template"), value: templateValue }),
    subsamples: () => ({
      label: t("operations.confirm.labels.subsamples"),
      value: t("operations.confirm.values.subsamples", {
        count,
        amount: each.numericValue,
        unit: unitLabel(each.unitId),
      }),
    }),
    amountTaken: () =>
      after
        ? {
            label: t("operations.confirm.labels.amountTaken"),
            value: t("operations.confirm.values.amountTaken", {
              amount: after.numericValue,
              unit: unitLabel(after.unitId),
            }),
          }
        : null,
    storageTemp: () =>
      storageTemp
        ? {
            label: t("operations.confirm.labels.storageTemp"),
            value: t("operations.confirm.values.storageTemp", { temp: storageTemp.numericValue }),
          }
        : null,
    linkBack: () => ({ label: t("operations.confirm.labels.linkBack"), value: linkName }),
    documentation: () =>
      documentation ? { label: t("operations.confirm.labels.documentation"), value: documentation.name } : null,
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
    .map((field) => rowBuilders[field]())
    .filter((row): row is Row => row !== null);

  return (
    <Card variant="outlined">
      <CardHeader
        title={name}
        subheader={t("operations.confirm.cardSubheader", { operation: resolveLabel(operation.labelKey) })}
        sx={{
          backgroundColor: theme.palette.record.sample.lighter,
          paddingBottom: "4px",
        }}
      />
      <CardContent>
        <DescriptionList content={content} dividers />
      </CardContent>
    </Card>
  );
}

export default observer(OperationConfirmation);
