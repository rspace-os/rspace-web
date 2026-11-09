import { useCallback, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import { BookingConfigurationsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { dropInvalidMeRules } from "@/modules/common/table-list/presets";
import type { FilterExpression } from "@/modules/common/table-list/types";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { mapBookingConfiguration } from "../bookable-items/bookingConfiguration";
import { createAllBookableItemsConfig } from "./allBookableItemsTableConfig";
import { allItemsBaseFilter } from "./allItemsBaseFilter";

export function useAllBookableItemsData({ subjectId }: { subjectId: number }) {
  const { t } = useTranslation("common");
  const config = useMemo(
    () => createAllBookableItemsConfig((globalId) => t("tableList.filters.openRecord", { globalId })),
    [t],
  );
  const droppedFilterFields = useRef<readonly string[]>([]);
  const normalizeFilterExpression = useCallback((expression: FilterExpression<BookingConfiguration> | null) => {
    const recovered = dropInvalidMeRules(expression, ["targetOwner"]);
    if (recovered.dropped.length > 0) droppedFilterFields.current = recovered.dropped;
    return recovered.expression;
  }, []);
  const table = useApiV2TableList({
    normalizeFilterExpression,
    resource: apiV2Resources["booking-configurations"],
    config,
    documentSchema: BookingConfigurationsReadSchema,
    mapDocument: mapBookingConfiguration,
    request: {
      authScope: subjectId,
      depth: 1,
      baseFilter: allItemsBaseFilter,
      projection: {
        fixed: [
          "id",
          "configurationVersion",
          "target",
          "enabled",
          "timezone",
          ...schedulingSettingsFieldNames,
          "updatedAt",
          "effectiveRole",
          "roleSources",
          "capabilities",
          "state",
        ],
      },
    },
    query: { keepPreviousData: true },
  });
  return { ...table, droppedFilterFields: droppedFilterFields.current };
}
