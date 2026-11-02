import { Link } from "@tanstack/react-router";
import { CalendarPlusIcon, EyeIcon, PackageCheckIcon, PlusIcon, SettingsIcon } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
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
import { BookingConfigurationActionsMenu } from "../bookable-items/BookingConfigurationActionsMenu";
import { bookableItemLifecycleNotice } from "../bookable-items/bookableItemFeedback";
import {
  archiveBookingConfiguration,
  permanentlyDeleteBookingConfiguration,
} from "../bookable-items/bookingConfiguration";
import { PermanentDeleteBookableItemDialog } from "../bookable-items/PermanentDeleteBookableItemDialog";
import type { AllBookableItem } from "../calendar/availability/availabilityQuickFilters";
import { ownerPreset } from "../ownerPreset";
import type { useAllBookableItemsData } from "./useAllBookableItemsData";

type AllBookableItemsTableProps = Omit<
  ReturnType<typeof useAllBookableItemsData>,
  | "config"
  | "invalidFilter"
  | "catalogue"
  | "runtimeFieldState"
  | "bulkMutation"
  | "failedBulkAction"
  | "rows"
  | "selectedRowIds"
  | "selectionPending"
  | "setFailedBulkAction"
> & {
  types?: readonly string[];
  directSysadmin: boolean;
  tableProps: TableListProps<AllBookableItem>;
  resetView: () => void;
  setFilters: (next: FilterState<AllBookableItem>) => void;
};

export function AllBookableItemsTable({
  filters,
  hasNoBookableItems,
  clearSelection,
  directSysadmin,
  tableProps,
  resetView,
  setFilters,
  canAdd,
  onLifecycleChanged,
  token,
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
        if (!row.target) return null;
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
            {row.capabilities?.canCreateBooking ? (
              <TooltipIconLink label={bookLabel} icon={<CalendarPlusIcon aria-hidden="true" />}>
                <Link
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/calendar/bookings/add"
                  search={{ target: row.target.globalId }}
                />
              </TooltipIconLink>
            ) : null}
            {row.capabilities?.canEditConfiguration ? (
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
            {/* The Administration lifecycle actions; listed items are always active, so never Restore. */}
            <BookingConfigurationActionsMenu
              configuration={row}
              itemName={row.target.value.name}
              directSysadmin={directSysadmin}
              // The neighbouring links render borderless at 40 px.
              triggerClassName="min-h-10 min-w-10 border-transparent"
              onAction={activate}
            />
          </div>
        );
      },
      renderInteraction: ({ actionId, row, close }) =>
        actionId === "archive" ? (
          <ArchiveBookableItemDialog
            configuration={row}
            close={close}
            onArchive={(id, version) => archiveBookingConfiguration(id, version, token)}
            onArchived={async (id) => {
              alertsRef.current?.push(bookableItemLifecycleNotice("archived", row, t));
              await onLifecycleChanged([id]);
            }}
          />
        ) : actionId === "permanent-delete" ? (
          <PermanentDeleteBookableItemDialog
            configuration={row}
            close={close}
            onDelete={(id, version) => permanentlyDeleteBookingConfiguration(id, version, token)}
            onDeleted={async () => {
              alertsRef.current?.push(bookableItemLifecycleNotice("deleted", row, t));
              await onLifecycleChanged([row.id]);
            }}
          />
        ) : null,
    }),
    [directSysadmin, onLifecycleChanged, t, token],
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
        presentations={{ table: "wide", cards: "narrow" }}
        rowActions={rowActions}
        emptyDescription={
          hasNoBookableItems ? (
            <>
              {t("bookableItems.primer.description")}
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
