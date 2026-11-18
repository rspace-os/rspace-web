const MAX_REASON_LENGTH = 200;

/**
 * The message on the server's error page for a search it rejected, as plain
 * text, or "" when it has none. The page is parsed inertly, so nothing in it runs.
 */
export function searchLinkFailureReason(responseText: string | undefined): string {
  const message = new DOMParser().parseFromString(responseText ?? "", "text/html").getElementById("ajaxErrorMsg");
  return (message?.textContent ?? "").replace(/\s+/g, " ").trim().slice(0, MAX_REASON_LENGTH);
}
