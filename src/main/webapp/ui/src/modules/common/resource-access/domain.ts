import type { ResourceAccessAssignmentDocument } from "@/modules/common/api/v2/generated/models/resourceAccessAssignmentDocument";
import type { ResourceAccessDocument } from "@/modules/common/api/v2/generated/models/resourceAccessDocument";
import type { ResourceGranteeDirectoryEntry } from "@/modules/common/api/v2/generated/models/resourceGranteeDirectoryEntry";
import type { ResourceGranteeKind } from "@/modules/common/api/v2/generated/models/resourceGranteeKind";

function assignmentDocument({ role, grantee }: ResourceAccessAssignmentDocument) {
  const { id, available, kind } = grantee;
  if (!(typeof id === "string" || typeof id === "number" || (id === null && !available && kind !== "AUDIENCE"))) {
    throw new Error("Only unavailable users and groups may omit their current identifier.");
  }
  return { role, grantee: { ...grantee, id } };
}

/** Establishes the grantee identifiers needed to edit an access document and enforce safe removal. */
export function accessDocument(document: ResourceAccessDocument) {
  return { ...document, assignments: document.assignments.map(assignmentDocument) };
}
export type AccessDocument = ReturnType<typeof accessDocument>;
export type ResourceAccessAssignment = AccessDocument["assignments"][number];
export type ResourceGrantee = ResourceAccessAssignment["grantee"];
export type DirectoryEntry = ResourceGranteeDirectoryEntry;
export type GranteeKind = ResourceGranteeKind;
