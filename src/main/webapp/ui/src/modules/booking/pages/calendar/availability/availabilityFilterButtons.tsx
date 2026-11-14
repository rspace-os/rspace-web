import { CalendarClockIcon, Clock3Icon, PackageCheckIcon } from "lucide-react";
import type { useTranslation } from "react-i18next";
import { hasPreset, togglePreset } from "@/modules/common/table-list/presets";
import type { TableListFilterButtons } from "@/modules/common/table-list/TableList";
import type { FilterState } from "@/modules/common/table-list/types";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { ownerPreset } from "../../ownerPreset";
import { type AllBookableItem, withAvailability } from "./availabilityQuickFilters";
import type { useCalendarAvailabilityLayout } from "./useCalendarAvailabilityLayout";

export function availabilityFilterButtons(
  t: ReturnType<typeof useTranslation<"booking">>["t"],
  data: ReturnType<typeof useCalendarAvailabilityLayout>,
  filters: FilterState<AllBookableItem>,
  onReset: () => void,
  setFilters: (next: FilterState<AllBookableItem>) => void,
): TableListFilterButtons {
  const { counts, quickMode } = data;
  const quickFilterText = {
    "available-now": {
      label: t("availabilityLayout.quickFilters.availableNow"),
      description: t("availabilityLayout.quickFilters.availableNowDescription"),
    },
    "free-later-today": {
      label: t("availabilityLayout.quickFilters.freeLaterToday"),
      description: t("availabilityLayout.quickFilters.freeLaterTodayDescription"),
    },
  };
  return {
    onReset,
    legend: t("availabilityLayout.quickFilters.legend"),
    align: "end",
    controlsOnSeparateRow: true,
    buttons: [
      {
        id: "mine",
        label: t("availabilityLayout.quickFilters.myItems"),
        icon: <PackageCheckIcon aria-hidden="true" />,
        pressed: hasPreset(filters.expression, ownerPreset),
        inExpression: true,
        onClick: () => {
          setFilters({ ...filters, expression: togglePreset(filters.expression, ownerPreset) });
        },
      },
      ...(["available-now", "free-later-today"] as const).map((mode) => {
        // The two categories are mutually exclusive, so each chip explains which items it counts.
        const text = quickFilterText[mode];
        return {
          id: mode,
          label: (
            <>
              {text.label}
              <span
                aria-hidden="true"
                className="ml-0.5 min-w-5 rounded-sm bg-foreground px-1 text-[10px] text-background"
              >
                {counts.isError ? (
                  "—"
                ) : counts.data ? (
                  mode === "available-now" ? (
                    counts.data.availableNow
                  ) : (
                    counts.data.freeLaterToday
                  )
                ) : (
                  <Skeleton className="h-3 w-3" />
                )}
              </span>
            </>
          ),
          description: text.description,
          icon: mode === "available-now" ? <Clock3Icon aria-hidden="true" /> : <CalendarClockIcon aria-hidden="true" />,
          pressed: quickMode === mode,
          onClick: () =>
            setFilters({
              ...filters,
              expression: withAvailability(filters.expression, quickMode === mode ? undefined : mode),
            }),
        };
      }),
    ],
  };
}
