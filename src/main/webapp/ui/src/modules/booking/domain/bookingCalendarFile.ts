import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";

/**
 * A one-off calendar file for a single booking. Nothing is stored and no subscription is created:
 * the file is generated per request and never updates itself once saved.
 */
export async function fetchBookingCalendarFile(
  bookingId: number,
  token: string,
  signal?: AbortSignal,
): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch(`/api/v2/bookings/${bookingId}/calendar-file`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  return {
    blob: await response.blob(),
    // The server owns the naming contract; the fallback only covers a stripped header.
    filename: filenameFrom(response.headers.get("Content-Disposition")) ?? `booking-${bookingId}.ics`,
  };
}

/** Reads the plain `filename="…"` form the download endpoint sends. */
export function filenameFrom(contentDisposition: string | null): string | null {
  const match = /filename="([^"]+)"/.exec(contentDisposition ?? "");
  return match ? match[1] : null;
}

/** Hands a fetched blob to the browser's own download machinery. */
export function saveBlob(blob: Blob, filename: string, target: Document = document): void {
  const url = URL.createObjectURL(blob);
  const anchor = target.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  target.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export async function downloadBookingCalendarFile(bookingId: number, token: string): Promise<string> {
  const { blob, filename } = await fetchBookingCalendarFile(bookingId, token);
  saveBlob(blob, filename);
  return filename;
}
