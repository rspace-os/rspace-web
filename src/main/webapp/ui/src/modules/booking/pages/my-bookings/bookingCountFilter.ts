export function upcomingBookingWhere(requesterId: number, asOf: string): string {
  return `requester.value==${requesterId};kind==BOOKING;state==CONFIRMED;end=gt=${asOf}`;
}
