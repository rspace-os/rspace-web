/**
 * A SampleRequest's status changed somewhere in the app (e.g. approved or
 * rejected from the Requests detail pane). Other, unrelated parts of the UI
 * that show request data derived from the backend (the Requests list, the
 * sidebar's pending-count badge) listen for this to refresh themselves,
 * since they don't share any common state with whichever component made
 * the change.
 */
export const SAMPLE_REQUEST_STATUS_CHANGED_EVENT = "inventory:sampleRequestStatusChanged";

export function notifySampleRequestStatusChanged(): void {
  window.dispatchEvent(new Event(SAMPLE_REQUEST_STATUS_CHANGED_EVENT));
}
