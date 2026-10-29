import type { RelationshipFilterPicker } from "@/modules/common/collection/relationshipFilterPicker";
import type { RelationshipSource } from "./sources";

export function relationshipSourceForPicker(
  picker: RelationshipFilterPicker,
  sources: Readonly<Record<string, RelationshipSource>>,
): RelationshipSource | undefined {
  const source = sources[picker.resource];
  return source?.globalIdPrefix === picker.globalIdPrefix ? source : undefined;
}
