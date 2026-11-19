import { useTranslation } from "react-i18next";
import type { FieldDisplayRule } from "@/modules/common/field-rules/displayRule";
import { formatList } from "@/modules/common/i18n/listFormat";

/**
 * Wording for a rule's condition: `summary` is the one-line "Shown when..."
 * shown on a field. A null source name means the source field no longer exists.
 */
export function useDescribeRule(): {
  summary: (rule: FieldDisplayRule, sourceName: string | null) => string;
} {
  const { t, i18n } = useTranslation("common");
  const options = (rule: FieldDisplayRule) =>
    formatList(rule.values, i18n.resolvedLanguage ?? i18n.language, { type: "disjunction" });

  return {
    summary: (rule, sourceName) => {
      if (sourceName === null) return t("fieldEditor.summary.sourceMissing");
      switch (rule.operator) {
        case "IS_ANY_OF":
          return t("fieldEditor.summary.isAnyOf", { source: sourceName, options: options(rule) });
        case "IS_NONE_OF":
          return t("fieldEditor.summary.isNoneOf", { source: sourceName, options: options(rule) });
        case "HAS_VALUE":
          return t("fieldEditor.summary.hasValue", { source: sourceName });
        case "HAS_NO_VALUE":
          return t("fieldEditor.summary.hasNoValue", { source: sourceName });
        case "EQUALS":
          return t("fieldEditor.summary.equals", { source: sourceName, value: rule.values[0] ?? "" });
        case "NOT_EQUALS":
          return t("fieldEditor.summary.notEquals", { source: sourceName, value: rule.values[0] ?? "" });
        case "CONTAINS":
          return t("fieldEditor.summary.contains", { source: sourceName, value: rule.values[0] ?? "" });
        case "LESS_THAN":
          return t("fieldEditor.summary.lessThan", { source: sourceName, value: rule.values[0] ?? "" });
        case "AT_MOST":
          return t("fieldEditor.summary.atMost", { source: sourceName, value: rule.values[0] ?? "" });
        case "GREATER_THAN":
          return t("fieldEditor.summary.greaterThan", { source: sourceName, value: rule.values[0] ?? "" });
        case "AT_LEAST":
          return t("fieldEditor.summary.atLeast", { source: sourceName, value: rule.values[0] ?? "" });
      }
    },
  };
}
