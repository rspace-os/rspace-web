import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { bookingCatalogueQuery, catalogueItemAsConfiguration } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { useAlignedMinute } from "@/modules/common/hooks/useAlignedMinute";
import { useQueryIdentityScope } from "@/modules/common/queries/queryIdentityScope";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { TableListProps } from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/types";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendarAvailability";
import {
  type AllBookableItem,
  keepAcrossMinutes,
  serverAvailabilityFilter,
  todayAvailabilityWindow,
  useAvailabilityCounts,
  withAvailability,
} from "./availabilityQuickFilters";
import { createAvailabilityTableConfig } from "./availabilityTableConfig";

const clock = () => new Date();

export function useCalendarAvailabilityLayout({
  expression,
  q,
  date,

  subjectId,
  enabled,
}: {
  expression: FilterExpression<AllBookableItem> | null;
  q: string;
  date: string;
  subjectId: number;
  enabled: boolean;
}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const rowConfig = useMemo(
    () =>
      createAvailabilityTableConfig(
        t("availabilityLayout.quickFilters.availableNow"),
        t("availabilityLayout.quickFilters.freeLaterToday"),
        (globalId) => commonT("tableList.filters.openRecord", { globalId }),
      ),
    [t, commonT],
  );
  const preferences = useBookingDisplayPreferences();
  const minute = useAlignedMinute(clock);
  const userToday = todayInTimeZone(preferences.timeZone, new Date(minute));
  const today = todayAvailabilityWindow(
    new Date(minute),
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
  );
  const split = serverAvailabilityFilter(expression);
  const quickMode = split.supported ? split.availability : undefined;
  const countsFilter = withAvailability(expression, undefined);
  const counts = useAvailabilityCounts(
    subjectId,
    { q, where: countsFilter ? serializeRsqlExpression(countsFilter) : undefined },
    today,
    enabled && split.supported,
  );
  const scope = useQueryIdentityScope(subjectId);
  const signature = JSON.stringify([q, expression, date]);
  const [paging, setPaging] = useState({ signature, pageIndex: 0, pageSize: 20 });
  const page = paging.signature === signature ? paging : { signature, pageIndex: 0, pageSize: paging.pageSize };
  const options = bookingCatalogueQuery(scope, {
    q,
    where: split.supported && split.where ? serializeRsqlExpression(split.where) : undefined,
    availability: quickMode,
    availabilityWindow: today,
    page: page.pageIndex + 1,
    pageSize: page.pageSize,
    totals: true,
  });
  const catalogue = useQuery({
    ...options,
    enabled: enabled && split.supported,
    placeholderData: quickMode ? keepAcrossMinutes(options.queryKey) : undefined,
  });
  const rows = enabled && split.supported ? (catalogue.data?.items ?? []).map(catalogueItemAsConfiguration) : [];
  const tableProps: TableListProps<AllBookableItem> = {
    config: rowConfig,
    rows,
    getRowId: (row) => String(row.id),
    clientSide: false,
    queryString: false,
    status: !split.supported
      ? "idle"
      : catalogue.isError
        ? "error"
        : catalogue.isPending
          ? "loading"
          : catalogue.isFetching
            ? "refreshing"
            : "idle",
    error: catalogue.error,
    features: {
      filtering: false,
      sorting: false,
      columns: false,
      pagination: {
        value: page,
        rowCount: catalogue.data?.total ?? 0,
        onChange: (next) => setPaging({ ...next, signature }),
      },
    },
  };
  const rowsDate = quickMode ? today.date : date;
  const rowsBounds = quickMode
    ? today.bounds
    : displayInterval(
        date,
        preferences.timeZone,
        preferences.availabilityWindow.start,
        preferences.availabilityWindow.end,
      );
  const availabilityRows = enabled
    ? rows.flatMap((row) => {
        if (!row.target) return [];
        const item = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
        return item ? [item] : [];
      })
    : [];
  const availability = useCalendarAvailability(availabilityRows, rowsBounds, subjectId);
  return {
    tableProps,
    counts,
    quickMode,
    rowsDate,
    rowsBounds,
    userToday,
    minute,
    preferences,
    availability,
    supported: split.supported,
    queryReady: enabled && split.supported,
    refetch: () => (enabled && split.supported ? catalogue.refetch() : Promise.resolve(catalogue)),
  };
}
