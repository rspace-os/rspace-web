import { useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import {
  CalendarArrowDownIcon,
  CalendarClockIcon,
  CalendarX2Icon,
  EllipsisIcon,
  EyeIcon,
  PencilIcon,
} from "lucide-react";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useBookingCalendarFileDownload } from "@/modules/booking/components/BookingCalendarFileButton";
import { bookingProblemMessage } from "@/modules/booking/creation/useCreateBooking";
import { type BookingListDocument, restoreBooking } from "@/modules/booking/domain/booking";
import type { BookingTimeFormat } from "@/modules/booking/domain/bookingTime";
import { formatAgendaPeriod, formatBookingPeriod } from "@/modules/booking/domain/bookingTime";
import type { CollectionRow } from "@/modules/common/collection/collectionConfig";
import { useTableListAlerts } from "@/modules/common/table-list/TableList";
import { buttonVariants } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import { DeleteBookingDialog } from "../bookings/DeleteBookingDialog";

export type BookingRow = CollectionRow<BookingListDocument, "id" | "target" | "canViewConfiguration" | "state">;

const iconButtonClassName = cn(buttonVariants({ size: "icon-lg", variant: "outline" }));

/**
 * The frequent actions stay as icon buttons; the calendar file and the destructive cancel sit
 * behind "More actions" with text labels, so the two calendar icons are never side by side.
 */
export function BookingRowActions({
  row,
  token,
  timeZone,
  timeFormat = "AUTOMATIC",
}: {
  row: BookingRow;
  token: string;
  timeZone: string;
  timeFormat?: BookingTimeFormat;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const { t: bookingT } = useTranslation("booking");
  const alerts = useTableListAlerts();
  const queryClient = useQueryClient();
  const moreActionsRef = useRef<HTMLButtonElement>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const itemName = row.target?.value.name ?? t("common:values.unknownItem");
  const period = formatAgendaPeriod(row.start ?? "", row.end ?? "", timeZone, undefined, timeFormat);
  // The cancel confirmation and its announcement name the date too, since rows on other days can share the times.
  const datedPeriod =
    row.start && row.end ? formatBookingPeriod(row.start, row.end, timeZone, undefined, timeFormat) : "";
  const calendarFile = useBookingCalendarFileDownload({ bookingId: row.id, itemName, period, token });
  const canDownload = row.canViewConfiguration && row.target !== null && row.state === "CONFIRMED";
  const itemCalendarLabel = t("myBookings.actions.itemCalendar");
  const viewDetailsLabel = t("myBookings.actions.viewDetails");
  const editLabel = t("myBookings.actions.edit");
  const moreActionsLabel = t("myBookings.actions.more");

  return (
    <div className="flex flex-wrap gap-1">
      {row.canViewConfiguration && row.target ? (
        <Tooltip>
          <TooltipTrigger
            render={
              <Link
                aria-label={itemCalendarLabel}
                className={iconButtonClassName}
                data-slot="button"
                to="/booking/bookable-items/$globalId/{-$tab}"
                params={{ globalId: row.target.globalId, tab: undefined }}
              />
            }
          >
            <CalendarClockIcon aria-hidden="true" />
          </TooltipTrigger>
          <TooltipContent role="tooltip">{itemCalendarLabel}</TooltipContent>
        </Tooltip>
      ) : null}
      {row.privacy === "full" ? (
        <Tooltip>
          <TooltipTrigger
            render={
              <Link
                aria-label={viewDetailsLabel}
                className={iconButtonClassName}
                data-slot="button"
                to="/booking/calendar/bookings/$id"
                params={{ id: String(row.id) }}
              />
            }
          >
            <EyeIcon aria-hidden="true" />
          </TooltipTrigger>
          <TooltipContent role="tooltip">{viewDetailsLabel}</TooltipContent>
        </Tooltip>
      ) : null}
      {row.canEdit ? (
        <Tooltip>
          <TooltipTrigger
            render={
              <Link
                aria-label={editLabel}
                className={iconButtonClassName}
                data-slot="button"
                to="/booking/calendar/bookings/$id/edit"
                params={{ id: String(row.id) }}
              />
            }
          >
            <PencilIcon aria-hidden="true" />
          </TooltipTrigger>
          <TooltipContent role="tooltip">{editLabel}</TooltipContent>
        </Tooltip>
      ) : null}
      {canDownload || row.canCancel ? (
        <Menu>
          <Tooltip>
            <TooltipTrigger
              render={
                <MenuTrigger
                  ref={moreActionsRef}
                  className={iconButtonClassName}
                  data-slot="button"
                  aria-label={moreActionsLabel}
                />
              }
            >
              <EllipsisIcon aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent role="tooltip">{moreActionsLabel}</TooltipContent>
          </Tooltip>
          <MenuContent className="w-56">
            {canDownload ? (
              <MenuItem disabled={calendarFile.pending} onClick={() => void calendarFile.download()}>
                <CalendarArrowDownIcon aria-hidden="true" />
                {t("myBookings.actions.downloadCalendarFile")}
              </MenuItem>
            ) : null}
            {row.canCancel ? (
              <MenuItem className="text-destructive" onClick={() => setCancelOpen(true)}>
                <CalendarX2Icon aria-hidden="true" />
                {t("bookings.actions.cancel")}
              </MenuItem>
            ) : null}
          </MenuContent>
        </Menu>
      ) : null}
      {/* Outside the menu: a menu item unmounts when its menu closes, taking a dialog or live region with it. */}
      {row.canCancel ? (
        <DeleteBookingDialog
          open={cancelOpen}
          onOpenChange={setCancelOpen}
          finalFocus={moreActionsRef}
          bookingId={row.id}
          bookingVersion={row.version ?? 0}
          itemName={itemName}
          period={datedPeriod}
          token={token}
          onDeleted={(cancelled) =>
            // The row leaves this list once the bookings refetch; the TableList alert outlives it.
            alerts.push({
              id: `booking-cancelled-${row.id}`,
              tone: "warning",
              icon: <CalendarX2Icon aria-hidden="true" />,
              message: t("myBookings.cancelled.alert", { itemName, period: datedPeriod }),
              undo: {
                run: async () => {
                  await restoreBooking(row.id, cancelled.version ?? 0, token);
                  await queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] });
                },
                focusRowId: String(row.id),
                describeError: (error) =>
                  t("myBookings.cancelled.undoFailed", { reason: bookingProblemMessage(error, bookingT) }),
              },
            })
          }
        />
      ) : null}
      {canDownload ? calendarFile.announcements : null}
      {!row.canViewConfiguration && !row.canEdit && !row.canCancel ? (
        <span className="text-sm text-muted-foreground">{t("myBookings.roleLoss.readOnly")}</span>
      ) : null}
    </div>
  );
}
