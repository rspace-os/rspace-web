import { type BookingEventListProps, BookingEventTable } from "./BookingEventTable";

export type { BookingEventListProps } from "./BookingEventTable";

export function BookingEventList(props: BookingEventListProps) {
  return <BookingEventTable {...props} key={`${props.globalId}:${props.period}:${props.cutoff}`} />;
}
