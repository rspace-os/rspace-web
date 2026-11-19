import { truncateIsoTimestamp } from "../../../stores/definitions/Units";
import { isJavaUri } from "../../../util/javaUri";
import { isBlankToServer } from "../../../util/StringUtils";

/**
 * The content of a template date field for what its picker holds: the date as
 * `yyyy-MM-dd`, empty when the picker is empty, or the invalid date itself,
 * which the field's error marks and which is never sent (see
 * `FieldModel.backendParams`).
 */
export function dateContent({ value, valid }: { value: Date | null; valid: boolean }): string | Date {
  if (!value) return "";
  if (!valid) return value;
  return truncateIsoTimestamp(value, "date").orElse("");
}

/** Whether the server rejects a URI field's value: one that is not blank and that `java.net.URI` cannot parse (`InventoryUriField.validate`). */
export function isInvalidUri(value: string): boolean {
  return !isBlankToServer(value) && !isJavaUri(value);
}
