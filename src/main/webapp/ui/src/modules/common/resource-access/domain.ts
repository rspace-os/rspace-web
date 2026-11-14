import type { ResourceAccessAssignmentDocument } from "@/modules/common/api/v2/generated/models/resourceAccessAssignmentDocument";
import type { ResourceAccessDocument } from "@/modules/common/api/v2/generated/models/resourceAccessDocument";
import type { ResourceGranteeDirectoryEntry } from "@/modules/common/api/v2/generated/models/resourceGranteeDirectoryEntry";

function required<T>(value: T | undefined, field: string): T {
  if (value === undefined) throw new Error(`Incomplete resource access document: ${field}`);
  return value;
}

export function directoryEntry(entry: ResourceGranteeDirectoryEntry) {
  return {
    ...entry,
    kind: required(entry.kind, "kind"),
    id: required(entry.id, "id"),
    key: required(entry.key, "key"),
    name: required(entry.name, "name"),
  };
}

function assignmentDocument(assignment: ResourceAccessAssignmentDocument) {
  const grantee = required(assignment.grantee, "grantee");
  const kind = required(grantee.kind, "kind");
  const available = required(grantee.available, "available");
  const id = grantee.id;
  if (!(typeof id === "string" || typeof id === "number" || (id === null && !available && kind !== "AUDIENCE"))) {
    throw new Error("Only unavailable users and groups may omit their current identifier.");
  }
  return {
    role: required(assignment.role, "role"),
    grantee: {
      ...grantee,
      id,
      kind,
      available,
      key: required(grantee.key, "key"),
      name: required(grantee.name, "name"),
      roleSources: grantee.roleSources ?? [],
    },
  };
}

/** Establishes the fields needed to edit a complete access document and enforce safe removal. */
export function accessDocument(document: ResourceAccessDocument) {
  const caller = required(document.caller, "caller");
  const capabilities = required(caller.capabilities, "capabilities");
  return {
    ...document,
    scheme: required(document.scheme, "scheme"),
    version: required(document.version, "version"),
    assignments: required(document.assignments, "assignments").map(assignmentDocument),
    inherited: document.inherited ?? false,
    caller: {
      ...caller,
      roleSources: caller.roleSources ?? [],
      capabilities: {
        canManageAssignments: required(capabilities.canManageAssignments, "canManageAssignments"),
        canManageOwners: required(capabilities.canManageOwners, "canManageOwners"),
        canLeave: required(capabilities.canLeave, "canLeave"),
      },
    },
  };
}
export type AccessDocument = ReturnType<typeof accessDocument>;
export type ResourceAccessAssignment = AccessDocument["assignments"][number];
export type ResourceGrantee = ResourceAccessAssignment["grantee"];
export type DirectoryEntry = ReturnType<typeof directoryEntry>;
