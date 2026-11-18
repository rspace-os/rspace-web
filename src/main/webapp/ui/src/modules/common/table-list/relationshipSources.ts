import type { RelationshipSource } from "@/modules/common/relationship-picker/sources";
import { instrumentRelationshipSources } from "@/modules/inventory/instrumentRelationshipSource";

/**
 * Relationship filters get a picker from published metadata without page wiring. Pages can add or
 * replace sources by resource name through the collection config.
 */
export function tableListRelationshipSources(
  sources?: Readonly<Record<string, RelationshipSource>>,
): Readonly<Record<string, RelationshipSource>> {
  return { ...instrumentRelationshipSources, ...sources };
}
