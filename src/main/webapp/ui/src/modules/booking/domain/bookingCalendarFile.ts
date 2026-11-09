import { downloadBookingCalendarFile as generatedDownloadBookingCalendarFile } from "@/modules/common/api/v2/generated/bookings/bookings";

/**
 * A one-off calendar file for a single booking. Nothing is stored and no subscription is created:
 * the file is generated per request and never updates itself once saved.
 */
async function fetchBookingCalendarFile(
  bookingId: number,

  signal?: AbortSignal,
): Promise<{ blob: Blob; filename: string }> {
  const response = await generatedDownloadBookingCalendarFile(bookingId, { signal });
  return {
    blob: response.body,
    // The server owns the naming contract; the fallback only covers a stripped header.
    filename: filenameFrom(response.headers.get("Content-Disposition")) ?? `booking-${bookingId}.ics`,
  };
}

/** Reads the plain `filename="…"` form the download endpoint sends. */
function filenameFrom(contentDisposition: string | null): string | null {
  const match = /filename="([^"]+)"/.exec(contentDisposition ?? "");
  return match ? match[1] : null;
}

/** Hands a fetched blob to the browser's own download machinery. */
function saveBlob(blob: Blob, filename: string, target: Document = document): void {
  const url = URL.createObjectURL(blob);
  const anchor = target.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  target.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export async function downloadBookingCalendarFile(bookingId: number): Promise<string> {
  const { blob, filename } = await fetchBookingCalendarFile(bookingId);
  saveBlob(blob, filename);
  return filename;
}
