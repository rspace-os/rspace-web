// PROTOTYPE ONLY (RPD-183 issues 20 and 27). Static fixtures for the effective-access and
// role-description prototypes, typed directly against the production resource-access schemas so
// the fixtures double as a proof of API sufficiency: whatever typechecks here needs no backend
// change. The one deliberate extension is INHERITED_ONLY_GRANTEES — grantees whose access is
// entirely group- or audience-derived have no direct assignment, so today's assignments-only
// document cannot list them. That gap is recorded in the decision record, not papered over.

import type {
  ResourceAccessAssignment,
  ResourceAccessDocument,
  ResourceGrantee,
} from "@/modules/common/resource-access/schemas";

export type PrototypeRoleSource = ResourceGrantee["roleSources"][number];

// --- Role ranking (mirrors the booking adapter's role order) --------------------------------

export const ROLE_RANK: Record<string, number> = { OWNER: 4, MANAGER: 3, BOOKER: 2, VIEWER: 1 };

export function strongerRole(a: string | null, b: string | null): string | null {
  if (a === null) return b;
  if (b === null) return a;
  return (ROLE_RANK[a] ?? 0) >= (ROLE_RANK[b] ?? 0) ? a : b;
}

/** Highest-role resolution across a direct role and inherited sources. */
export function effectiveRoleOf(direct: string | null, sources: readonly PrototypeRoleSource[]): string | null {
  return sources
    .filter((source) => source.kind !== "DIRECT")
    .reduce<string | null>((best, source) => strongerRole(best, source.role), direct);
}

/** What remains when the direct assignment goes away: the remove-preview the prototype shows. */
export function effectiveAfterDirectRemoval(sources: readonly PrototypeRoleSource[]): string | null {
  return effectiveRoleOf(null, sources);
}

export function nonDirectSources(sources: readonly PrototypeRoleSource[]): PrototypeRoleSource[] {
  return sources.filter((source) => source.kind !== "DIRECT");
}

// --- Grantee directory -----------------------------------------------------------------------

const directSource = (role: string): PrototypeRoleSource => ({ kind: "DIRECT", role, grantee: null });

const groupSource = (role: string, id: number, name: string): PrototypeRoleSource => ({
  kind: "GROUP",
  role,
  grantee: { kind: "GROUP", id, key: `group:${id}`, name },
});

/** A retained snapshot whose granting group has since been deleted: no source identity remains. */
const unavailableGroupSource = (role: string): PrototypeRoleSource => ({ kind: "GROUP", role, grantee: null });

const audienceSource = (role: string): PrototypeRoleSource => ({
  kind: "AUDIENCE",
  role,
  grantee: { kind: "AUDIENCE", id: "all-users", key: "audience:all-users", name: "All users" },
});

const user = (
  id: number,
  name: string,
  sources: readonly PrototypeRoleSource[],
  options: { available?: boolean; detail?: string } = {},
): ResourceGrantee => ({
  kind: "USER",
  id,
  key: `user:${id}`,
  name,
  detail: options.detail ?? null,
  available: options.available ?? true,
  effectiveRole: effectiveRoleOf(sources.find((source) => source.kind === "DIRECT")?.role ?? null, sources),
  roleSources: [...sources],
});

const group = (id: number, name: string, detail: string, sources: readonly PrototypeRoleSource[]): ResourceGrantee => ({
  kind: "GROUP",
  id,
  key: `group:${id}`,
  name,
  detail,
  available: true,
  effectiveRole: effectiveRoleOf(sources.find((source) => source.kind === "DIRECT")?.role ?? null, sources),
  roleSources: [...sources],
});

const LONG_GROUP_NAME =
  "Interdisciplinary Quantitative Biosciences Imaging Consortium — Northern Hemisphere Confocal Working Group";

export const GRANTEES = {
  /** The current caller: direct Owner, final-owner invariant visible. */
  you: user(10, "Priya Sharma", [directSource("OWNER")]),
  /** Direct only. */
  ada: user(11, "Ada Lovelace", [directSource("BOOKER")]),
  /** Stronger inherited role: direct Viewer beaten by a Manager group. */
  charles: user(13, "Charles Babbage", [directSource("VIEWER"), groupSource("MANAGER", 91, "Engineering Support")]),
  /** Audience plus direct: All users grants Booker over a direct Viewer. */
  noor: user(14, "Noor Patel", [directSource("VIEWER"), audienceSource("BOOKER")]),
  /** Deleted principal: the assignment snapshot is retained, the account is gone. */
  leo: user(15, "Leo Szilard", [directSource("BOOKER"), unavailableGroupSource("VIEWER")], { available: false }),
  /** A group grantee whose name must wrap rather than overflow at 320 px. */
  longGroup: group(92, LONG_GROUP_NAME, "Collaboration group · 34 members", [directSource("VIEWER")]),
  /** Ten sources: the readability stress case. */
  sam: user(16, "Sam Okafor", [
    directSource("VIEWER"),
    groupSource("BOOKER", 101, "Optics Training Cohort"),
    groupSource("BOOKER", 102, "Microscopy Users"),
    groupSource("VIEWER", 103, "Departmental Seminar Series"),
    groupSource("MANAGER", 104, "Facility Stewards"),
    groupSource("BOOKER", 105, "Live-cell Imaging Project"),
    groupSource("VIEWER", 106, "Grant Reporting Working Group"),
    groupSource("BOOKER", 107, "Night-shift Access Pilot"),
    groupSource("VIEWER", 108, "Visiting Researchers 2026"),
    audienceSource("BOOKER"),
  ]),
  allUsers: {
    kind: "AUDIENCE",
    id: "all-users",
    key: "audience:all-users",
    name: "All users",
    detail: null,
    available: true,
    effectiveRole: "BOOKER",
    roleSources: [directSource("BOOKER")],
  } satisfies ResourceGrantee,
  /** Group only, one group. */
  grace: user(12, "Grace Hopper", [groupSource("BOOKER", 90, "Cell Biology Lab")]),
  /** Group only, two groups with different roles. */
  mary: user(17, "Mary Anning", [
    groupSource("BOOKER", 93, "Field Research"),
    groupSource("MANAGER", 94, "Genomics Consortium"),
  ]),
  /** Duplicate display names for the issue-27 cases. */
  alexOne: user(18, "Alex Chen", [directSource("BOOKER")]),
  alexTwo: user(19, "Alex Chen", [directSource("VIEWER")]),
} as const;

// --- The saved access document ---------------------------------------------------------------

const assignment = (grantee: ResourceGrantee): ResourceAccessAssignment => {
  const direct = grantee.roleSources.find((source) => source.kind === "DIRECT");
  if (!direct) throw new Error(`${grantee.name} has no direct assignment`);
  return { grantee, role: direct.role };
};

/** Grantees with only inherited access: today's assignments-only API cannot list them. */
export const INHERITED_ONLY_GRANTEES: readonly ResourceGrantee[] = [GRANTEES.grace, GRANTEES.mary];

export const SAVED_DOCUMENT: ResourceAccessDocument = {
  scheme: "booking",
  version: 12,
  assignments: [
    assignment(GRANTEES.you),
    assignment(GRANTEES.ada),
    assignment(GRANTEES.charles),
    assignment(GRANTEES.noor),
    assignment(GRANTEES.leo),
    assignment(GRANTEES.longGroup),
    assignment(GRANTEES.sam),
    assignment(GRANTEES.allUsers),
  ],
  caller: {
    effectiveRole: "OWNER",
    roleSources: [directSource("OWNER")],
    granteeKey: GRANTEES.you.key,
    capabilities: { canManageAssignments: true, canManageOwners: true, canLeave: true },
  },
};

/** The issue-27 Manager scenario: the caller may not grant or remove Owner. */
export const MANAGER_DOCUMENT: ResourceAccessDocument = {
  scheme: "booking",
  version: 7,
  assignments: [
    assignment(GRANTEES.you),
    assignment(GRANTEES.ada),
    assignment(GRANTEES.alexOne),
    assignment(GRANTEES.alexTwo),
    assignment(GRANTEES.longGroup),
    assignment(GRANTEES.allUsers),
  ],
  caller: {
    effectiveRole: "MANAGER",
    roleSources: [groupSource("MANAGER", 91, "Engineering Support")],
    granteeKey: null,
    capabilities: { canManageAssignments: true, canManageOwners: false, canLeave: false },
  },
};
