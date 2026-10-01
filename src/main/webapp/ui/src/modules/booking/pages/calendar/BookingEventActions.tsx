import { Link } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingCalendarFileButton } from "@/modules/booking/components/BookingCalendarFileButton";
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { InlineBookingEditor } from "./InlineBookingEditor";

const ACTION_COLUMNS = ["grid-cols-1", "grid-cols-1", "grid-cols-2", "grid-cols-3"];

export function isEditableBooking(event: BookingListDocument): event is BookingListDocument & EditableBooking {
  return event.privacy === "full" && event.canEdit && event.state === "CONFIRMED";
}

export function BookingActions({
  event,
  timezone,
  timelineDate,
}: {
  event: BookingListDocument;
  timezone: string;
  timelineDate?: string;
}) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [editing, setEditing] = React.useState(false);
  const editable = isEditableBooking(event);
  if (editing && editable) {
    return (
      <InlineBookingEditor
        event={event}
        timezone={timezone}
        timelineDate={timelineDate}
        token={token}
        onClose={() => setEditing(false)}
      />
    );
  }
  // The same pair of conditions the download endpoint itself enforces, so the action never 404s.
  const canDownload = event.canViewConfiguration && event.state === "CONFIRMED";
  const canViewDetails = event.privacy === "full";
  const actionCount = (canViewDetails ? 1 : 0) + (editable ? 1 : 0) + (canDownload ? 1 : 0);
  if (actionCount === 0) return null;
  return (
    <div
      className={cn(
        "grid border-border border-t text-xs",
        ACTION_COLUMNS[actionCount],
        actionCount > 1 && "divide-x divide-border",
      )}
    >
      {canViewDetails ? (
        <Link
          className={cn(buttonVariants({ variant: "link", size: "xs" }), "h-auto rounded-none py-2")}
          to="/booking/calendar/bookings/$id"
          params={{ id: String(event.id) }}
        >
          {t("calendar.actions.viewDetails")}
        </Link>
      ) : null}
      {editable ? (
        <button
          type="button"
          className={cn(buttonVariants({ variant: "link", size: "xs" }), "h-auto rounded-none py-2")}
          onClick={() => setEditing(true)}
        >
          {t("calendar.actions.edit")}
        </button>
      ) : null}
      {canDownload ? (
        <BookingCalendarFileButton
          bookingId={event.id}
          itemName={event.target.value.name}
          period={formatAgendaPeriod(event.start, event.end, timezone)}
          token={token}
          size="xs"
          variant="link"
          className="h-auto rounded-none py-2"
        />
      ) : null}
    </div>
  );
}
