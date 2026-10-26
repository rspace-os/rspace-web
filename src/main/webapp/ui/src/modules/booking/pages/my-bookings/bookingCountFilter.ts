export function upcomingBookingWhere(requesterId: number, asOf: string): string {
  return `requesterId==${requesterId};kind==BOOKING;state==CONFIRMED;end=gt=${asOf}`;
}
