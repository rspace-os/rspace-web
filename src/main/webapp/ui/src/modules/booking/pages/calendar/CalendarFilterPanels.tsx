import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import type { RuntimeFieldDefinition } from "@/modules/common/table-list/adapters/apiV2/runtimeFieldCatalog";
import { TableListFilters, topLevelFilterCount } from "@/modules/common/table-list/components/filters/TableListFilters";
import { RestoredViewIssue } from "@/modules/common/table-list/components/RestoredViewIssue";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { Button } from "@/modules/common/ui/button";

export type CalendarFilterPanelKind = "items" | "events";

type CalendarFilterPanelProps<TDocument> = {
  kind: CalendarFilterPanelKind;
  config: ResolvedCollectionConfig<TDocument>;
  expression: FilterExpression<TDocument> | null;
  onApply: (expression: FilterExpression<TDocument> | null) => void;
  onSelectRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  runtimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  runtimeFieldAuthScope?: string | number;
  onClose: () => void;
};

export function CalendarFilterPanel<TDocument>({
  kind,
  config,
  expression,
  onApply,
  onSelectRuntimeField,
  runtimeFieldDefinitions,
  runtimeFieldAuthScope,
  onClose,
}: CalendarFilterPanelProps<TDocument>) {
  const { t } = useTranslation("booking");
  const title = kind === "items" ? t("calendar.filterGroups.items") : t("calendar.filterGroups.events");
  return (
    <section aria-label={title} className="mt-2 rounded-sm border bg-card p-3">
      <h2 className="sr-only">{title}</h2>
      <TableListFilters
        config={config}
        expression={expression}
        onApply={onApply}
        onSelectRuntimeField={onSelectRuntimeField}
        runtimeFieldDefinitions={runtimeFieldDefinitions}
        runtimeFieldAuthScope={runtimeFieldAuthScope}
        onClose={onClose}
      />
    </section>
  );
}

export function CalendarFilterButtons<TDocument>({
  kind,
  expression,
  active,
  onClick,
}: {
  kind: CalendarFilterPanelKind;
  expression: FilterExpression<TDocument> | null;
  active: boolean;
  onClick: () => void;
}) {
  const { t } = useTranslation("booking");
  const title = kind === "items" ? t("calendar.filterGroups.items") : t("calendar.filterGroups.events");
  const count = topLevelFilterCount(expression);
  return (
    <Button
      type="button"
      aria-expanded={active}
      aria-label={count > 0 ? t("calendar.filterGroups.applied", { count, group: title }) : title}
      variant={active || count > 0 ? "secondary" : "outline"}
      onClick={onClick}
    >
      {title}
      {count > 0 ? (
        <span aria-hidden="true" className="ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background">
          {count}
        </span>
      ) : null}
    </Button>
  );
}

export function CalendarFilterIssue({
  kind,
  encoded,
  network,
  onRetry,
  onReset,
}: {
  kind: CalendarFilterPanelKind;
  encoded: string;
  network: boolean;
  onRetry?: () => void;
  onReset: () => void;
}) {
  const { t: bookingT } = useTranslation("booking");
  const title = kind === "items" ? bookingT("calendar.filterGroups.items") : bookingT("calendar.filterGroups.events");
  return (
    <RestoredViewIssue
      className="mt-2 rounded-sm border border-destructive/30 bg-destructive/5 p-3"
      label={title}
      issue={{
        kind: network ? "network" : "invalid",
        encoded,
        retry: onRetry,
        remove: onReset,
      }}
    />
  );
}

export type CalendarFilterIssueState = {
  kind: CalendarFilterPanelKind;
  encoded: string;
  network: boolean;
  onRetry?: () => void;
  onReset: () => void;
};
