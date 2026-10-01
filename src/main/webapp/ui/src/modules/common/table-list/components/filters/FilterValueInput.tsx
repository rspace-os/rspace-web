import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import type { FilterOperator, ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { selectOptionText } from "@/modules/common/collection/collectionConfig";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { type RelationshipSource, relationshipSources } from "@/modules/common/relationship-picker/relationshipSources";
import { Input } from "@/modules/common/ui/input";
import { MultiSelect } from "@/modules/common/ui/multi-select";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { FilterSelect } from "./FilterSelect";

type FilterValue = string | readonly string[];

function scalarValue(value: FilterValue): string {
  return typeof value === "string" ? value : (value[0] ?? "");
}

function listValue(value: FilterValue): readonly string[] {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? [] : [trimmed];
}

function identitySourceFor<TDocument>(
  field: ResolvedFieldConfig<TDocument>,
  sources: Readonly<Record<string, RelationshipSource>>,
): RelationshipSource | undefined {
  if (field.filterPicker) {
    const source = sources[field.filterPicker.resource];
    return source?.globalIdPrefix === field.filterPicker.globalIdPrefix ? source : undefined;
  }
  return field.type === "relationship" ? sources[field.relationTo] : undefined;
}

export function FilterValueInput<TDocument>({
  field,
  sources,
  authScope,
  operator,
  value,
  number,
  onChange,
}: {
  field: ResolvedFieldConfig<TDocument>;
  sources?: Readonly<Record<string, RelationshipSource>>;
  authScope?: string | number;
  operator: FilterOperator;
  value: FilterValue;
  number: number;
  onChange: (value: FilterValue) => void;
}) {
  const { t } = useTranslation("common");
  const ariaLabel = t("tableList.filters.value", { number });
  const multiple = operator === "in" || operator === "notIn";
  const availableSources = { ...relationshipSources, ...sources };
  const identitySource = identitySourceFor(field, availableSources);
  const selectLabels = {
    placeholder: t("tableList.filters.placeholders.value"),
    noMatch: t("tableList.filters.valueSearch.noMatch"),
    clear: t("tableList.filters.valueSearch.clear"),
    trigger: t("tableList.filters.valueSearch.trigger"),
  };

  if (operator !== "exists" && identitySource) {
    return (
      <Suspense fallback={<Skeleton className="h-8 rounded-sm" />}>
        <RelationshipPicker
          source={identitySource}
          authScope={authScope}
          ariaLabel={ariaLabel}
          className="rounded-sm"
          compact
          browseWhenEmpty={identitySource.browsable === true}
          multiple={multiple}
          value={multiple ? listValue(value).join(",") : scalarValue(value)}
          onChange={(next) => onChange(multiple ? next.split(",").filter(Boolean) : next)}
        />
      </Suspense>
    );
  }

  if (multiple) {
    const select = field.type === "select";
    return (
      <MultiSelect
        options={
          select
            ? field.options.map((option) => ({
                label: selectOptionText(option),
                value: typeof option === "string" ? option : option.value,
              }))
            : []
        }
        value={listValue(value)}
        onValueChange={onChange}
        allowCustomValues={!select}
        ariaLabel={ariaLabel}
        placeholder={select ? "" : t("tableList.filters.multiSelect.customPlaceholder")}
        emptyMessage={select ? t("tableList.filters.multiSelect.empty") : t("tableList.filters.multiSelect.enterValue")}
        removeLabel={(item) => t("tableList.filters.multiSelect.remove", { value: item })}
        className="min-h-8 rounded-sm py-1 text-xs"
      />
    );
  }

  if (operator !== "exists" && field.type === "select") {
    return (
      <FilterSelect
        ariaLabel={ariaLabel}
        options={field.options.map((option) => ({
          value: typeof option === "string" ? option : option.value,
          label: selectOptionText(option),
          groupLabelKey: null,
        }))}
        value={scalarValue(value)}
        labels={selectLabels}
        onChange={onChange}
      />
    );
  }

  if (operator === "exists" || field.type === "boolean") {
    return (
      <FilterSelect
        ariaLabel={ariaLabel}
        options={
          operator === "exists"
            ? [
                { value: "true", label: t("tableList.filters.present"), groupLabelKey: null },
                { value: "false", label: t("tableList.filters.missing"), groupLabelKey: null },
              ]
            : [
                { value: "true", label: t("actions.yes"), groupLabelKey: null },
                { value: "false", label: t("actions.no"), groupLabelKey: null },
              ]
        }
        value={scalarValue(value)}
        labels={selectLabels}
        onChange={onChange}
      />
    );
  }

  return (
    <Input
      aria-label={ariaLabel}
      className="h-8 rounded-sm text-xs"
      type={
        field.type === "number"
          ? "number"
          : field.origin?.runtimeValueType === "date"
            ? "date"
            : field.origin?.runtimeValueType === "time"
              ? "time"
              : field.type === "dateTime"
                ? "datetime-local"
                : "text"
      }
      placeholder={t(
        operator === "matches" ? "tableList.filters.placeholders.pattern" : "tableList.filters.placeholders.value",
      )}
      value={scalarValue(value)}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}
