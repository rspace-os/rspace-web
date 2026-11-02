import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { BookingConfigurationSchema } from "../bookable-items/bookingConfiguration";
import { createAllBookableItemsConfig } from "./allBookableItemsTableConfig";
import { allItemsBaseFilter } from "./allItemsBaseFilter";

export function useAllBookableItemsData({ subjectId }: { subjectId: number }) {
  const { t } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const config = useMemo(
    () => createAllBookableItemsConfig((globalId) => t("tableList.filters.openRecord", { globalId })),
    [t],
  );
  return useApiV2TableList({
    resourceName: "booking-configurations",
    config,
    documentSchema: BookingConfigurationSchema,
    request: {
      token,
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
}
