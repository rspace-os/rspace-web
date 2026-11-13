import { Link } from "@tanstack/react-router";
import { CalendarClockIcon, EyeIcon, PencilIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import type { CollectionRow } from "@/modules/common/collection/config";
import { buttonVariants } from "@/modules/common/ui/button";
import { TooltipIconLink } from "@/modules/common/ui/tooltip-icon-link";
import { DeleteBookingDialog } from "../bookings/DeleteBookingDialog";

export type BookingRow = CollectionRow<BookingListDocument, "id" | "target" | "canViewConfiguration" | "state">;

const iconButtonClassName = buttonVariants({ size: "icon-lg", variant: "outline" });

export function BookingRowActions({
  row,
  timeZone,
  onCancelled,
}: {
  row: BookingRow;
  timeZone: string;
  onCancelled: () => void | Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const itemCalendarLabel = t("myBookings.actions.itemCalendar");
  const viewDetailsLabel = t("myBookings.actions.viewDetails");
  const editLabel = t("myBookings.actions.edit");
  return (
    <div className="flex flex-wrap gap-1">
      {row.canViewConfiguration ? (
        <TooltipIconLink label={itemCalendarLabel} icon={<CalendarClockIcon aria-hidden="true" />}>
          <Link
            className={iconButtonClassName}
            data-slot="button"
            to="/booking/bookable-items/$globalId/{-$tab}"
            params={{ globalId: row.target.globalId, tab: undefined }}
          />
        </TooltipIconLink>
      ) : null}
      {row.privacy === "full" ? (
        <TooltipIconLink label={viewDetailsLabel} icon={<EyeIcon aria-hidden="true" />}>
          <Link
            className={iconButtonClassName}
            data-slot="button"
            to="/booking/calendar/bookings/$id"
            params={{ id: String(row.id) }}
          />
        </TooltipIconLink>
      ) : null}
      {row.canEdit ? (
        <TooltipIconLink label={editLabel} icon={<PencilIcon aria-hidden="true" />}>
          <Link
            className={iconButtonClassName}
            data-slot="button"
            to="/booking/calendar/bookings/$id/edit"
            params={{ id: String(row.id) }}
          />
        </TooltipIconLink>
      ) : null}
      {row.canCancel ? (
        <DeleteBookingDialog
          bookingId={row.id}
          bookingVersion={row.version ?? 0}
          itemName={row.target.value.name}
          period={formatAgendaPeriod(row.start ?? "", row.end ?? "", timeZone)}
          iconOnly
          onDeleted={onCancelled}
        />
      ) : null}
      {!row.canViewConfiguration && !row.canEdit && !row.canCancel ? (
        <span className="text-sm text-muted-foreground">{t("myBookings.roleLoss.readOnly")}</span>
      ) : null}
    </div>
  );
}
