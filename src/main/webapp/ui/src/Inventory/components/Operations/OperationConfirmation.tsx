import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import useStores from "@/stores/use-stores";
import type { DocumentationSelection } from "./DocumentationStep";
import type { InventoryOperation } from "./operationsConfig";
import type { TemplateSelection } from "./TemplateStep";
import type { OperationInputs, OperationQuantity } from "./types";

/** Final step: a plain-language summary of what the operation will do before it is performed. */
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
  const { unitStore } = useStores();
  const resolveLabel = t as unknown as (key: string, params?: Record<string, unknown>) => string;
  const { effect } = operation;
  const unitLabel = (unitId: number): string => unitStore.getUnit(unitId)?.label ?? "";

  const count = Number(values[effect.countFrom]);
  const each = values[effect.eachAmountFrom] as OperationQuantity;
  const after = effect.amountTakenFrom ? (values[effect.amountTakenFrom] as OperationQuantity) : null;
  const name = String(values[effect.nameFrom]);
  const linkName = resolveLabel(effect.links[0].fieldNameKey, values);
  const templateSummary =
    templateSelection.mode === "none"
      ? t("operations.template.summaryNone")
      : templateSelection.mode === "fromSample"
        ? t("operations.template.summaryFromSample", { name: originSampleName })
        : t("operations.template.summaryPick", { name: templateSelection.templateName ?? "" });

  return (
    <Stack spacing={0.5}>
      <Typography variant="body2">{t("operations.confirm.newSample", { name })}</Typography>
      <Typography variant="body2">{templateSummary}</Typography>
      <Typography variant="body2">
        {t("operations.confirm.created", {
          count,
          amount: each.numericValue,
          unit: unitLabel(each.unitId),
        })}
      </Typography>
      {after ? (
        <Typography variant="body2">
          {t("operations.confirm.amountTaken", {
            amount: after.numericValue,
            unit: unitLabel(after.unitId),
          })}
        </Typography>
      ) : null}
      <Typography variant="body2">{t("operations.confirm.link", { name: linkName })}</Typography>
      {documentation ? (
        <Typography variant="body2">{t("operations.confirm.documentation", { name: documentation.name })}</Typography>
      ) : null}
    </Stack>
  );
}

export default observer(OperationConfirmation);
