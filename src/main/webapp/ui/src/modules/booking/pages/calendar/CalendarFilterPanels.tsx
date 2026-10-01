import { XIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { RestoredViewIssue } from "@/modules/common/table-list/components/RestoredViewIssue";
import { Button } from "@/modules/common/ui/button";

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
  /** The item name once the catalogue or events have loaded; the global ID is shown alone until then. */
  name?: string;
  onRemove: () => void;
};

/** The bookable item that the route focuses the calendar on, e.g. after creating a booking. */
export function CalendarTargetFilterChip({ globalId, name, onRemove }: CalendarTargetFilterState) {
  const { t } = useTranslation("booking");
  const label = name
    ? t("calendar.targetFilter.label", { name, globalId })
    : t("calendar.targetFilter.labelWithoutName", { globalId });
  return (
    <div className="flex pt-3">
      <span
        data-calendar-target-filter
        className="inline-flex max-w-full min-w-0 items-center gap-1 rounded-sm border bg-secondary py-0.5 pr-0.5 pl-2 text-xs font-medium text-secondary-foreground"
      >
        <span className="min-w-0 truncate" title={label}>
          {label}
        </span>
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          aria-label={t("calendar.targetFilter.remove")}
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
    </div>
  );
}
