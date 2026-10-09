/** True for a URL path segment made only of digits, such as a record id. */
export function isWholeNumberSegment(segment: string | undefined): boolean {
  return !!segment && [...segment].every((c) => c >= "0" && c <= "9");
}
