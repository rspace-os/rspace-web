import Typography from "@mui/material/Typography";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { FieldDisplayRule, SourceKind } from "@/modules/common/field-rules/displayRule";
import { useDescribeRule } from "./useDescribeRule";

type DisplayRuleSummaryProps = {
  rule: FieldDisplayRule | null;
  /** Name of the rule's source field; null when that field no longer exists. */
  sourceName: string | null;
  /** True when the source field comes after this field, which reads less naturally. */
  sourceIsBelow?: boolean;
  /** The source field's kind, which decides whether its value is quoted. */
  sourceKind?: SourceKind;
};

/** The one-line "Shown when..." summary on a field that has a display rule. */
export default function DisplayRuleSummary({
  rule,
  sourceName,
  sourceIsBelow = false,
  sourceKind,
}: DisplayRuleSummaryProps): ReactNode {
  const { t } = useTranslation("common");
  const { summary } = useDescribeRule();
  if (rule === null) return null;
  return (
    <Typography variant="body2" color="text.secondary">
      {summary(rule, sourceName, sourceKind)}
      {sourceIsBelow && sourceName !== null && <> {t("fieldEditor.summary.sourceBelowHint", { source: sourceName })}</>}
    </Typography>
  );
}
