/**
 * Parses stored ELN choice field data such as `fieldChoices=a&fieldChoices=b`
 * into `["a", "b"]`, matching the Java `ElnChoiceValues`.
 *
 * Each `&`-separated segment contributes the text between its first and
 * second `=`, which is what the server's `ChoiceBehaviour` displays. Segments
 * without `=` and empty values are skipped rather than throwing, because data
 * written through the forms API is not validated. This is not URL encoding, so
 * `URLSearchParams` would decode values the server shows literally.
 */
export function parseElnChoiceValues(data: string): Array<string> {
  if (data.trim() === "") return [];
  return data.split("&").flatMap((segment) => {
    // With a limit of 2, JavaScript keeps only the first two pieces: name and value.
    const value = segment.split("=", 2)[1] ?? "";
    return value === "" ? [] : [value];
  });
}
