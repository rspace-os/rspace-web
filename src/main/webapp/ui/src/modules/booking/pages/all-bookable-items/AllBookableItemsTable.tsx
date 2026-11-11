import { Link } from "@tanstack/react-router";
import { CalendarPlusIcon, EyeIcon, PackageCheckIcon, PlusIcon, SettingsIcon } from "lucide-react";
import type { RefObject } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { TableListAlertsApi } from "@/modules/common/table-list/components/TableListAlerts";
import { hasPreset, togglePreset } from "@/modules/common/table-list/presets";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import {
  TableList,
  type TableListFilterButtons,
  type TableListProps,
  type TableListRowActions,
} from "@/modules/common/table-list/TableList";
import type { FilterState } from "@/modules/common/table-list/types";
import { buttonVariants } from "@/modules/common/ui/button";
import { TooltipIconLink } from "@/modules/common/ui/tooltip-icon-link";
import { cn } from "@/modules/common/utils/cn";
import { ArchiveBookableItemDialog } from "../bookable-items/ArchiveBookableItemDialog";
import { BookableItemActionTriggers } from "../bookable-items/BookableItemActionTriggers";
import { bookableItemLifecycleNotice } from "../bookable-items/bookableItemFeedback";
import type { BookingConfigurationRow as AllBookableItem } from "../bookable-items/bookingConfiguration";
import {
  archiveBookingConfiguration,
  permanentlyDeleteBookingConfiguration,
} from "../bookable-items/bookingConfiguration";
import { PermanentDeleteBookableItemDialog } from "../bookable-items/PermanentDeleteBookableItemDialog";
import { ownerPreset } from "../ownerPreset";

type AllBookableItemsTableProps = {
  filters: FilterState<AllBookableItem>;
  hasNoBookableItems: boolean;
  clearSelection: () => void;
  directSysadmin: boolean;
  tableProps: TableListProps<AllBookableItem>;
  resetView: () => void;
  setFilters: (next: FilterState<AllBookableItem>) => void;
  canAdd: boolean;
  onLifecycleChanged: (ids: readonly number[]) => Promise<void>;
  onRestore: (row: AllBookableItem) => Promise<void>;
  alertsRef: RefObject<TableListAlertsApi | null>;
  attachAlerts: (api: TableListAlertsApi | null) => void;
};

export function AllBookableItemsTable({
  filters,
  hasNoBookableItems,
  clearSelection,
  onRestore,
  directSysadmin,
  tableProps,
  resetView,
  setFilters,
  canAdd,
  onLifecycleChanged,

  alertsRef,
  attachAlerts,
}: AllBookableItemsTableProps) {
  const { t } = useTranslation("booking");
  const rowActions = useMemo<TableListRowActions<AllBookableItem>>(
    () => ({
      id: "actions",
      label: t("allBookableItems.fields.actions"),
      width: 224,
      minWidth: 176,
      renderCell: ({ row, activate }) => {
        if (!row.target)
          return (
            <BookableItemActionTriggers
              configuration={row}
              directSysadmin={directSysadmin}
              activate={activate}
              onRestore={onRestore}
              hideDetails
            />
          );
        const detailsLabel = t("allBookableItems.actions.viewDetails");
        const bookLabel = t("allBookableItems.actions.book");
        return (
          <div className="flex items-center gap-1">
            <TooltipIconLink label={detailsLabel} icon={<EyeIcon aria-hidden="true" />}>
              <Link
                className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                data-slot="button"
                to="/booking/bookable-items/$globalId/{-$tab}"
                params={{ globalId: row.target.globalId, tab: undefined }}
              />
            </TooltipIconLink>
            {row.state === "ACTIVE" && row.enabled && row.capabilities?.canCreateBooking ? (
              <TooltipIconLink label={bookLabel} icon={<CalendarPlusIcon aria-hidden="true" />}>
                <Link
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/calendar/bookings/add"
                  search={{ target: row.target.globalId }}
                />
              </TooltipIconLink>
            ) : null}
            {row.state === "ACTIVE" && row.enabled && row.capabilities?.canEditConfiguration ? (
              <TooltipIconLink
                label={t("allBookableItems.actions.settings")}
                icon={<SettingsIcon aria-hidden="true" />}
              >
                <Link
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/bookable-items/$globalId/{-$tab}"
                  params={{ globalId: row.target.globalId, tab: "details" }}
                  search={{ edit: true }}
                />
              </TooltipIconLink>
            ) : null}
            <BookableItemActionTriggers
              configuration={row}
              directSysadmin={directSysadmin}
              activate={activate}
              onRestore={onRestore}
              hideDetails
            />
          </div>
        );
      },
      renderInteraction: ({ actionId, row, close }) =>
        actionId === "archive" ? (
          <ArchiveBookableItemDialog
            configuration={row}
            close={close}
            onArchive={(id, version) => archiveBookingConfiguration(id, version)}
            onArchived={async (id) => {
              alertsRef.current?.push(bookableItemLifecycleNotice("archived", row, t));
              await onLifecycleChanged([id]);
            }}
          />
        ) : actionId === "permanent-delete" ? (
          <PermanentDeleteBookableItemDialog
            configuration={row}
            close={close}
            onDelete={(id, version) => permanentlyDeleteBookingConfiguration(id, version)}
            onDeleted={async () => {
              alertsRef.current?.push(bookableItemLifecycleNotice("deleted", row, t));
              await onLifecycleChanged([row.id]);
            }}
          />
        ) : null,
    }),
    [directSysadmin, onLifecycleChanged, onRestore, t],
  );

  const quickFilters: TableListFilterButtons = {
    legend: t("allBookableItems.legend"),

    align: "end",
    buttons: [
      {
        id: "mine",
        label: t("allBookableItems.ownerPreset"),
        icon: <PackageCheckIcon aria-hidden="true" />,
        pressed: hasPreset(filters.expression, ownerPreset),
        inExpression: true,
        onClick: () => {
          clearSelection();
          setFilters({ ...filters, expression: togglePreset(filters.expression, ownerPreset) });
        },
      },
    ],
    onReset: resetView,
  };

  return (
    <main className="space-y-5 p-4 sm:p-8">
      {tableProps.status === "loading" ? (
        <p role="status" className="sr-only">
          {t("allBookableItems.loading")}
        </p>
      ) : null}
      <TableList
        {...tableProps}
        alertsRef={attachAlerts}
        stickyActions="when-fitted"
        headingClassName="text-2xl font-semibold"
        onReset={resetView}
        filterButtons={quickFilters}
        rowActions={rowActions}
        emptyDescription={
          hasNoBookableItems ? (
            <>
              {t("allBookableItems.primer.description")}
              {canAdd && (
                <>
                  {" "}
                  <Link to="/booking/bookable-items/add">{t("bookableItems.addTitle")}</Link>
                </>
              )}
            </>
          ) : undefined
        }
        createAction={
          <div className="flex items-center gap-2">
            <Link
              to="/booking/calendar"
              search={{
                layout: "availability",
                "calendar-resources.q": filters.search || undefined,
                "calendar-resources.where": filters.expression
                  ? serializeRsqlExpression(filters.expression)
                  : undefined,
              }}
              className={buttonVariants({ variant: "outline" })}
            >
              {t("allBookableItems.actions.viewAvailability")}
            </Link>
            {canAdd ? (
              <Link to="/booking/bookable-items/add" className={cn(buttonVariants(), "rounded-sm")} data-slot="button">
                <PlusIcon aria-hidden="true" data-icon="inline-start" />
                {t("bookableItems.actions.add")}
              </Link>
            ) : undefined}
          </div>
        }
      />
    </main>
  );
}
