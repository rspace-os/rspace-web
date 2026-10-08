import { Link } from "@tanstack/react-router";
import type { TFunction } from "i18next";
import { useEffect, useRef } from "react";
import type { TableListAlert } from "@/modules/common/table-list/components/TableListAlerts";
import { buttonVariants } from "@/modules/common/ui/button";
import type { BookableItemsBulkAction } from "./bookableItemLifecycleHelpers";
import type { BookingConfigurationRow } from "./bookingConfiguration";

export function bookableItemBulkNotice(
  action: BookableItemsBulkAction,
  count: number | null,
  t: TFunction<["booking", "common"]>,
): TableListAlert {
  const message =
    count === null
      ? action === "archive"
        ? t("booking:bookableItems.feedback.bulkArchivedUnknown", {})
        : action === "enable"
          ? t("booking:bookableItems.feedback.bulkEnabledUnknown", {})
          : t("booking:bookableItems.feedback.bulkDisabledUnknown", {})
      : action === "archive"
        ? t("booking:bookableItems.feedback.bulkArchived", {
            count,
          })
        : action === "enable"
          ? t("booking:bookableItems.feedback.bulkEnabled", {
              count,
            })
          : t("booking:bookableItems.feedback.bulkDisabled", {
              count,
            });
  return { id: `bulk-${action}`, tone: action === "archive" ? "warning" : "success", message };
}

export function bookableItemLifecycleNotice(
  action: "archived" | "restored" | "deleted",
  row: BookingConfigurationRow,
  t: TFunction<["booking", "common"]>,
): TableListAlert {
  const item = row.target?.value.name ?? t("common:values.unknownItem");
  const message =
    action === "archived"
      ? t("booking:bookableItems.feedback.archived", {
          item,
        })
      : action === "restored"
        ? t("booking:bookableItems.feedback.restored", {
            item,
          })
        : t("booking:bookableItems.feedback.deleted", { item });
  return {
    id: `bookable-item-${row.target?.globalId ?? row.id}`,
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
    for (const row of rows) ids.current.set(row.id, `bookable-item-${row.target?.globalId ?? row.id}`);
  }, [rows]);
  return ids.current;
}
