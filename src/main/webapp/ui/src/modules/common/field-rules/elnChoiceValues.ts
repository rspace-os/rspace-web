/**
 * Parses stored ELN choice field data such as `fieldChoices=a&fieldChoices=b`
 * into `["a", "b"]`, matching the Java `ElnChoiceValues`.
 *
 * Each `&`-separated segment contributes the text between its first and
 * second `=`, which is what the server's `ChoiceBehaviour` displays. Segments
 * without `=` and empty values are skipped rather than throwing, because data
 * written through the forms API is not validated.
 */
export function parseElnChoiceValues(data: string): Array<string> {
  if (data.trim() === "") return [];
  return data.split("&").flatMap((segment) => {
    const start = segment.indexOf("=");
    if (start < 0) return [];
    const end = segment.indexOf("=", start + 1);
    const value = segment.slice(start + 1, end < 0 ? undefined : end);
    return value === "" ? [] : [value];
  });
}
