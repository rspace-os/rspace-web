import { Link } from "@tanstack/react-router";
import type { TFunction } from "i18next";
import { useEffect, useRef } from "react";
import type { TableListAlert } from "@/modules/common/table-list/components/TableListAlerts";
import { buttonVariants } from "@/modules/common/ui/button";
import type { BookableItemsBulkAction } from "./bookableItemLifecycleHelpers";
import type { BookingConfigurationRow } from "./bookingConfiguration";

const bulkFeedbackKeys = {
  archive: { count: "bulkArchived", unknown: "bulkArchivedUnknown" },
  enable: { count: "bulkEnabled", unknown: "bulkEnabledUnknown" },
  disable: { count: "bulkDisabled", unknown: "bulkDisabledUnknown" },
} as const;

const lifecycleFeedbackKeys = {
  archived: "archived",
  restored: "restored",
  deleted: "deleted",
} as const;

function bookableItemNoticeId(identifier: string | number): string {
  return `bookable-item-${identifier}`;
}

export function bookableItemBulkNotice(
  action: BookableItemsBulkAction,
  count: number | null,
  t: TFunction<["booking", "common"]>,
): TableListAlert {
  const keys = bulkFeedbackKeys[action];
  const message =
    count === null
      ? t(`booking:bookableItems.feedback.${keys.unknown}`, {})
      : t(`booking:bookableItems.feedback.${keys.count}`, { count });
  return { id: `bulk-${action}`, tone: action === "archive" ? "warning" : "success", message };
}

export function bookableItemLifecycleNotice(
  action: "archived" | "restored" | "deleted",
  row: BookingConfigurationRow,
  t: TFunction<["booking", "common"]>,
): TableListAlert {
  const item = row.target?.value.name ?? t("common:values.unknownItem");
  const message = t(`booking:bookableItems.feedback.${lifecycleFeedbackKeys[action]}`, { item });
  return {
    id: bookableItemNoticeId(row.target?.globalId ?? row.id),
    tone: action === "archived" ? "warning" : "success",
    message,
    actions:
      action !== "deleted" && row.target ? (
        <Link
          to="/booking/bookable-items/$globalId/{-$tab}"
          params={{ globalId: row.target.globalId, tab: undefined }}
          className={buttonVariants({ variant: "outline", size: "sm" })}
        >
          {t("booking:bookableItems.feedback.viewDetails")}
        </Link>
      ) : undefined,
  };
}

/** Retains target IDs across paging so bulk changes can remove earlier item actions. */
export function useBookableItemNoticeIds(rows: readonly BookingConfigurationRow[]) {
  const ids = useRef(new Map<number, string>());
  useEffect(() => {
    for (const row of rows) ids.current.set(row.id, bookableItemNoticeId(row.target?.globalId ?? row.id));
  }, [rows]);
  return ids.current;
}
