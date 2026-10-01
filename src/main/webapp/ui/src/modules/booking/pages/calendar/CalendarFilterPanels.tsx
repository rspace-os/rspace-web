import { XIcon } from "lucide-react";
import type * as React from "react";
import { useTranslation } from "react-i18next";
import { RestoredViewIssue } from "@/modules/common/table-list/components/RestoredViewIssue";
import { Button } from "@/modules/common/ui/button";
import { Spinner } from "@/modules/common/ui/spinner";

export type CalendarFilterPanelKind = "items" | "events";

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

export type CalendarTargetFilterState = {
  globalId: string;
  /** The item name once resolved; the global ID is shown alone until then. */
  name?: string;
  /** The name is still resolving, so the global ID is shown with a spinner, as a restoring relationship filter is. */
  loading?: boolean;
  /** Shown in place of the global ID when the name could not be resolved, e.g. "IN123 (unavailable)". */
  unresolvedLabel?: string;
  onRemove: () => void;
};

export type CalendarQuickFilterChipState = {
  id: string;
  label: string;
  onRemove: () => void;
};

function FilterChip({
  label,
  removeLabel,
  loading = false,
  onRemove,
  ...props
}: {
  label: string;
  removeLabel: string;
  loading?: boolean;
  onRemove: () => void;
} & Omit<React.ComponentProps<"span">, "children">) {
  const { t: commonT } = useTranslation("common");
  return (
    <span
      {...props}
      aria-busy={loading || undefined}
      className="inline-flex max-w-full min-w-0 items-center gap-1 rounded-sm border bg-secondary py-0.5 pr-0.5 pl-2 text-xs font-medium text-secondary-foreground"
    >
      <span className="min-w-0 truncate" title={label}>
        {label}
      </span>
      {loading ? (
        <>
          <span className="sr-only">{commonT("loading")}</span>
          <Spinner aria-hidden="true" className="size-3 shrink-0" />
        </>
      ) : null}
      <Button
        type="button"
        variant="ghost"
        size="icon-xs"
        aria-label={removeLabel}
        onClick={(event) => {
          // The chip unmounts with this button, so keep keyboard focus on the table's Filters control.
          const filters = event.currentTarget
            .closest("[data-table-list]")
            ?.querySelector<HTMLElement>("[data-table-list-filters]");
          onRemove();
          filters?.focus();
        }}
      >
        <XIcon aria-hidden="true" />
      </Button>
    </span>
  );
}

/** The bookable item that the route focuses the calendar on, e.g. after creating a booking. */
function CalendarTargetFilterChip({ globalId, name, loading, unresolvedLabel, onRemove }: CalendarTargetFilterState) {
  const { t } = useTranslation("booking");
  const label = name
    ? t("calendar.targetFilter.label", { name, globalId })
    : t("calendar.targetFilter.labelWithoutName", { globalId: unresolvedLabel ?? globalId });
  return (
    <FilterChip
      data-calendar-target-filter
      label={label}
      removeLabel={t("calendar.targetFilter.remove")}
      loading={loading}
      onRemove={onRemove}
    />
  );
}

/**
 * The route's item focus and the quick filters that are on. The quick filters live in the Filters popover,
 * so these chips are the only place the toolbar shows them.
 */
export function CalendarAppliedFilters({
  targetFilter,
  quickFilters,
}: {
  targetFilter?: CalendarTargetFilterState;
  quickFilters: readonly CalendarQuickFilterChipState[];
}) {
  const { t } = useTranslation("booking");
  if (!targetFilter && quickFilters.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2 pt-3">
      {targetFilter ? <CalendarTargetFilterChip {...targetFilter} /> : null}
      {quickFilters.map((filter) => (
        <FilterChip
          key={filter.id}
          label={filter.label}
          removeLabel={t("calendar.quickFilters.remove", { filter: filter.label })}
          onRemove={filter.onRemove}
        />
      ))}
    </div>
  );
}
