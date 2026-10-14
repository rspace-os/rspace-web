import { createContext, type Dispatch, type ReactNode, type RefObject, type SetStateAction, useContext } from "react";
import type { BookingDetails, BookingMutation } from "@/modules/booking/domain/booking";
import { type BookingTimeFormat, formatBookingDateTime } from "@/modules/booking/domain/bookingTime";

export type BookingEventContextValue = {
  booking: BookingDetails;
  token: string;
  displayTimeZone: string;
  formId: string;
  editButtonRef: RefObject<HTMLAnchorElement | null>;
  announce: Dispatch<SetStateAction<string>>;
  reportBookingUpdated: (event: BookingMutation, itemName: string) => void;
  setDirty: Dispatch<SetStateAction<boolean>>;
  refreshBooking: () => Promise<void>;
};

export const BookingEventContext = createContext<BookingEventContextValue | null>(null);

export function useBookingEvent(): BookingEventContextValue {
  const value = useContext(BookingEventContext);
  if (!value) throw new Error("Booking event child rendered outside its page");
  return value;
}

export function Panel({
  as: Tag = "section",
  heading,
  headingId,
  headingRef,
  action,
  children,
}: {
  as?: "section" | "aside";
  heading: string;
  headingId: string;
  headingRef?: RefObject<HTMLHeadingElement | null>;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Tag aria-labelledby={headingId} className="min-w-0">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b pb-3">
        <h2 ref={headingRef} id={headingId} className="font-semibold" tabIndex={headingRef ? -1 : undefined}>
          {heading}
        </h2>
        {action}
      </div>
      <div className="pt-4 text-sm">{children}</div>
    </Tag>
  );
}

/** With the UTC offset on a clock-change day, so the two occurrences of a repeated hour read differently. */
export function formatBookingEventDateTime(
  value: string,
  timeZone: string,
  language: string,
  timeFormat: BookingTimeFormat = "AUTOMATIC",
): string {
  return formatBookingDateTime(value, timeZone, language, timeFormat);
}
