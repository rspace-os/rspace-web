import { Link } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingCalendarFileButton } from "@/modules/booking/components/BookingCalendarFileButton";
import type { ExpandedEventEditController } from "@/modules/booking/components/ExpandedEventCard";
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatAgendaPeriod } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { InlineBookingEditor } from "./InlineBookingEditor";

const ACTION_COLUMNS = ["grid-cols-1", "grid-cols-1", "grid-cols-2", "grid-cols-3"];

export function isEditableBooking(event: BookingListDocument): event is BookingListDocument & EditableBooking {
  return event.privacy === "full" && event.canEdit && event.state === "CONFIRMED" && event.target !== null;
}

export function BookingActions({
  event,
  timezone,
  timelineDate,
  timelineEventElement,
  editController,
}: {
  event: BookingListDocument;
  timezone: string;
  timelineDate?: string;
  timelineEventElement?: HTMLElement | null;
  editController?: ExpandedEventEditController;
}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const timeFormat = useBookingTimeFormat();
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const [localEditing, setLocalEditing] = React.useState(false);
  const editing = editController?.editing ?? localEditing;
  const setEditing = editController?.onEditingChange ?? setLocalEditing;
  const editable = isEditableBooking(event);
  const editButtonRef = React.useRef<HTMLButtonElement>(null);
  const viewDetailsRef = React.useRef<HTMLAnchorElement>(null);
  const previousEditing = React.useRef(editing);
  React.useLayoutEffect(() => {
    if (previousEditing.current && !editing) {
      (editable ? editButtonRef.current : viewDetailsRef.current)?.focus();
    }
    previousEditing.current = editing;
  }, [editable, editing]);
  React.useEffect(() => {
    if (!editable && editing) setEditing(false);
  }, [editable, editing, setEditing]);
  if (editing && editable) {
    return (
      <InlineBookingEditor
        event={event}
        timezone={timezone}
        timelineDate={timelineDate}
        timelineEventElement={timelineEventElement}
        token={token}
        onClose={() => setEditing(false)}
      />
    );
  }
  // The same pair of conditions the download endpoint itself enforces, so the action never 404s.
  const canDownload = event.canViewConfiguration && event.target !== null && event.state === "CONFIRMED";
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
          ref={viewDetailsRef}
          className={cn(buttonVariants({ variant: "link", size: "xs" }), "h-auto rounded-none py-2")}
          to="/booking/calendar/bookings/$id"
          params={{ id: String(event.id) }}
        >
          {t("calendar.actions.viewDetails")}
        </Link>
      ) : null}
      {editable ? (
        <button
          ref={editButtonRef}
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
          itemName={event.target?.value.name ?? commonT("values.unknownItem")}
          period={formatAgendaPeriod(event.start, event.end, timezone, undefined, timeFormat)}
          token={token}
          size="xs"
          variant="link"
          className="h-auto rounded-none py-2"
        />
      ) : null}
    </div>
  );
}
