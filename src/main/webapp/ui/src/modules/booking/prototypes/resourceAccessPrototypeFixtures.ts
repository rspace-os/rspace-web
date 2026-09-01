// PROTOTYPE ONLY. In-memory fixtures and helpers for the resource-access editor prototype
// (plan 012). No production authorization lives here: the helpers only need to keep the
// prototype UI internally consistent. Everything is scheme-generic — the Booking role names
// exist only inside the two scheme fixtures, never in helper logic.

export type PrototypeRole = {
  key: string;
  label: string;
  description: string;
  /** Higher rank includes every lower-ranked role's permissions. */
  rank: number;
  /** Optional scheme rule limiting which principal kinds may hold this role. */
  allowedGranteeKinds?: readonly PrototypeGrantee["kind"][];
};

export type PrototypeScheme = {
  key: string;
  /** What the resource is called in copy, e.g. "Booking configuration". */
  resourceNoun: string;
  roles: readonly PrototypeRole[];
  /** Scheme-specific capability notice shown near the role choices. */
  capabilityNotice: string;
  /** What losing the final effective role means; supplied by the scheme so the editor stays generic. */
  leaveWarning: string;
};

export type PrototypeGrantee =
  | { kind: "user"; id: string; name: string; detail: string; unavailable: boolean }
  | { kind: "group"; id: string; name: string; detail: string; unavailable: boolean }
  | { kind: "audience"; id: "all-users"; name: string; detail: string; unavailable: false };

export type PrototypeAssignment = {
  grantee: PrototypeGrantee;
  /** A role key of the active scheme. */
  role: string;
};

export type PrototypeRoleSource = {
  kind: "direct" | "group" | "audience" | "implicit";
  label: string;
  role: string;
};

export type PrototypeCaller = {
  /** Grantee id of the caller when they appear in the assignment list; null for sysadmins. */
  granteeId: string | null;
  name: string;
  effectiveRole: string;
  capabilities: string[];
  sources: PrototypeRoleSource[];
};

export type PrototypeAccessState = {
  version: number;
  assignments: PrototypeAssignment[];
  caller: PrototypeCaller;
};

export type PrototypeScenario = {
  key: string;
  label: string;
  /** What the reviewer should look at, shown above the page. */
  brief: string;
  kind: "editor" | "flow";
  /** Which scheme the scenario starts on; the scheme selector can still override it. */
  scheme: string;
  /** Editor scenarios only. */
  access?: PrototypeAccessState;
  /** Inherited (non-direct) sources per user grantee id, editor scenarios only. */
  inherited?: Record<string, PrototypeRoleSource[]>;
  /** The first Save attempt fails with a simulated stale version. */
  conflictOnSave?: boolean;
  /** The first Save attempt fails with a simulated request error. */
  requestFailureOnSave?: boolean;
};

// --- Schemes -----------------------------------------------------------------------------

export const BOOKING_SCHEME: PrototypeScheme = {
  key: "booking",
  resourceNoun: "Booking configuration",
  capabilityNotice: "All roles may create a personal calendar subscription.",
  leaveWarning:
    "You lose this configuration and its calendar. Your own past and future bookings stay visible, read-only, in My Bookings.",
  roles: [
    {
      key: "owner",
      label: "Owner",
      rank: 4,
      allowedGranteeKinds: ["user", "group"],
      description: "Edits everything, manages all role assignments, and archives the configuration.",
    },
    {
      key: "manager",
      label: "Manager",
      rank: 3,
      allowedGranteeKinds: ["user", "group"],
      description: "Edits configuration and all calendar events; assigns Manager, Booker, and Viewer.",
    },
    {
      key: "booker",
      label: "Booker",
      rank: 2,
      allowedGranteeKinds: ["user", "group", "audience"],
      description: "Creates bookings and edits or cancels only their own bookings.",
    },
    {
      key: "viewer",
      label: "Viewer",
      rank: 1,
      allowedGranteeKinds: ["user", "group", "audience"],
      description: "Reads the configuration, schedule, and full authorized event details.",
    },
  ],
};

/** A deliberately different scheme to prove the editor has no Booking-specific branches. */
export const ARCHIVE_SCHEME: PrototypeScheme = {
  key: "archive",
  resourceNoun: "Dataset archive",
  capabilityNotice: "Readers may request an export; exports are logged.",
  leaveWarning:
    "You lose this archive and its catalogue. Your own submissions stay listed, read-only, in My Submissions.",
  roles: [
    {
      key: "owner",
      label: "Owner",
      rank: 4,
      description: "Controls retention policy, all role assignments, and deletion review.",
    },
    {
      key: "manager",
      label: "Manager",
      rank: 3,
      description: "Curates metadata and approves incoming submissions.",
    },
    {
      key: "contributor",
      label: "Contributor",
      rank: 2,
      description: "Uploads datasets and edits or withdraws only their own submissions.",
    },
    {
      key: "reader",
      label: "Reader",
      rank: 1,
      description: "Browses the catalogue and downloads published datasets.",
    },
  ],
};

export const SCHEMES: readonly PrototypeScheme[] = [BOOKING_SCHEME, ARCHIVE_SCHEME];

// --- Grantee directory -------------------------------------------------------------------

export const ALL_USERS: PrototypeGrantee = {
  kind: "audience",
  id: "all-users",
  name: "All users",
  detail: "",
  unavailable: false,
};

const user = (id: string, name: string, detail: string, unavailable = false): PrototypeGrantee => ({
  kind: "user",
  id,
  name,
  detail,
  unavailable,
});

const group = (id: string, name: string, detail: string, unavailable = false): PrototypeGrantee => ({
  kind: "group",
  id,
  name,
  detail,
  unavailable,
});

export const GRANTEES = {
  you: user("u-you", "Priya Sharma", "Imaging facility"),
  ada: user("u-ada", "Ada Lovelace", "Imaging facility"),
  grace: user("u-grace", "Grace Hopper", "Cell biology"),
  charles: user("u-charles", "Charles Babbage", "Engineering support"),
  mary: user("u-mary", "Mary Anning", "Field research"),
  leo: user("u-leo", "Leo Szilard", "Account disabled", true),
  imaging: group("g-imaging", "Imaging Lab", "Lab group · 8 members"),
  genomics: group("g-genomics", "Genomics Consortium", "Collaboration group · 21 members"),
  alpha: group("g-alpha", "Project Alpha", "Project group · 5 members"),
  emptyLab: group("g-empty", "Microscopy Core", "Lab group · 0 active members"),
  legacy: group("g-legacy", "Legacy Screening", "Project group · deleted", true),
} as const;

/** Everything the add-grantee search can return, including grantees already assigned. */
export const DIRECTORY: readonly PrototypeGrantee[] = [...Object.values(GRANTEES), ALL_USERS];

// --- Scheme helpers ----------------------------------------------------------------------

export function rolesByRankDesc(scheme: PrototypeScheme): readonly PrototypeRole[] {
  return [...scheme.roles].sort((a, b) => b.rank - a.rank);
}

export function topRole(scheme: PrototypeScheme): PrototypeRole {
  return rolesByRankDesc(scheme)[0];
}

export function roleOf(scheme: PrototypeScheme, key: string): PrototypeRole {
  return scheme.roles.find((role) => role.key === key) ?? scheme.roles[0];
}

export function roleAllowsGrantee(role: PrototypeRole, grantee: PrototypeGrantee): boolean {
  return role.allowedGranteeKinds?.includes(grantee.kind) ?? true;
}

/**
 * The highest rank the caller may assign or change. The top role manages everything; the
 * second role manages everything except the top role; everyone else manages nothing.
 */
export function assignableRankCeiling(scheme: PrototypeScheme, callerEffectiveRole: string): number {
  const ranks = rolesByRankDesc(scheme);
  const callerRank = roleOf(scheme, callerEffectiveRole).rank;
  if (callerRank === ranks[0].rank) return ranks[0].rank;
  if (callerRank === ranks[1].rank) return ranks[1].rank;
  return 0;
}

/** Highest-role resolution: a direct role plus inherited sources yield the strongest role. */
export function effectiveRole(
  scheme: PrototypeScheme,
  direct: string | null,
  inherited: readonly PrototypeRoleSource[],
): PrototypeRole | null {
  const candidates = [
    ...(direct === null ? [] : [roleOf(scheme, direct)]),
    ...inherited.map((source) => roleOf(scheme, source.role)),
  ];
  if (candidates.length === 0) return null;
  return candidates.reduce((best, role) => (role.rank > best.rank ? role : best));
}

/**
 * Owner-invariant presentation: the persisted assignments that hold the top role. Unavailable
 * holders and empty groups still count; implicit sysadmin access never does.
 */
export function topRoleHolders(scheme: PrototypeScheme, assignments: readonly PrototypeAssignment[]) {
  return assignments.filter((assignment) => assignment.role === topRole(scheme).key);
}

export function isLastTopRoleHolder(
  scheme: PrototypeScheme,
  assignments: readonly PrototypeAssignment[],
  granteeId: string,
): boolean {
  const holders = topRoleHolders(scheme, assignments);
  return holders.length === 1 && holders[0].grantee.id === granteeId;
}

// --- Draft staging -----------------------------------------------------------------------

export type PrototypeDraftDiff = {
  added: PrototypeAssignment[];
  removed: PrototypeAssignment[];
  changed: { grantee: PrototypeGrantee; from: string; to: string }[];
};

export function diffAssignments(
  saved: readonly PrototypeAssignment[],
  draft: readonly PrototypeAssignment[],
): PrototypeDraftDiff {
  const savedById = new Map(saved.map((assignment) => [assignment.grantee.id, assignment]));
  const draftById = new Map(draft.map((assignment) => [assignment.grantee.id, assignment]));
  return {
    added: draft.filter((assignment) => !savedById.has(assignment.grantee.id)),
    removed: saved.filter((assignment) => !draftById.has(assignment.grantee.id)),
    changed: draft.flatMap((assignment) => {
      const before = savedById.get(assignment.grantee.id);
      return before && before.role !== assignment.role
        ? [{ grantee: assignment.grantee, from: before.role, to: assignment.role }]
        : [];
    }),
  };
}

/** Successful Save: atomically replace the assignment list and bump the version. */
export function applyDraft(state: PrototypeAccessState, draft: readonly PrototypeAssignment[]): PrototypeAccessState {
  return { ...state, version: state.version + 1, assignments: [...draft] };
}

/**
 * Conflict simulation: what "someone else saved first" did to the server copy. Promotes the
 * first non-top-role assignment it finds so the reviewer can see a concrete remote change.
 */
export function simulateRemoteChange(scheme: PrototypeScheme, state: PrototypeAccessState): PrototypeAccessState {
  const ranks = rolesByRankDesc(scheme);
  const target = state.assignments.find((assignment) => assignment.role !== ranks[0].key);
  return {
    ...state,
    version: state.version + 1,
    assignments: state.assignments.map((assignment) =>
      assignment === target ? { ...assignment, role: ranks[1].key } : assignment,
    ),
  };
}

// --- Scenario fixtures -------------------------------------------------------------------

const ownerCaller: PrototypeCaller = {
  granteeId: GRANTEES.you.id,
  name: "Priya Sharma",
  effectiveRole: "owner",
  capabilities: ["edit-configuration", "manage-all-roles", "archive", "create-booking", "subscribe"],
  sources: [{ kind: "direct", label: "Direct assignment", role: "owner" }],
};

const OWNER_MIXED_ASSIGNMENTS: PrototypeAssignment[] = [
  { grantee: GRANTEES.you, role: "owner" },
  { grantee: GRANTEES.ada, role: "viewer" },
  { grantee: GRANTEES.grace, role: "manager" },
  { grantee: GRANTEES.imaging, role: "manager" },
  { grantee: GRANTEES.genomics, role: "viewer" },
  { grantee: ALL_USERS, role: "booker" },
];

/**
 * Ada holds direct Viewer but inherits Manager through Imaging Lab plus Booker from All users.
 * Charles and Mary have no direct assignment at all: their access exists only through a group or
 * the audience, which is the case the grouped layout puts in its own section.
 */
const OWNER_MIXED_INHERITED: Record<string, PrototypeRoleSource[]> = {
  [GRANTEES.ada.id]: [
    { kind: "group", label: "Imaging Lab", role: "manager" },
    { kind: "group", label: "Genomics Consortium", role: "viewer" },
    { kind: "audience", label: "All users", role: "booker" },
  ],
  [GRANTEES.charles.id]: [
    { kind: "group", label: "Imaging Lab", role: "manager" },
    { kind: "audience", label: "All users", role: "booker" },
  ],
  [GRANTEES.mary.id]: [{ kind: "audience", label: "All users", role: "booker" }],
  [GRANTEES.grace.id]: [{ kind: "audience", label: "All users", role: "booker" }],
  [GRANTEES.you.id]: [{ kind: "audience", label: "All users", role: "booker" }],
};

export const SCENARIOS: readonly PrototypeScenario[] = [
  {
    key: "owner-mixed",
    label: "Owner, mixed sources",
    brief:
      "You are a direct Owner. Ada Lovelace has direct Viewer but effective Manager through the Imaging Lab group; All users supplies Booker. Expand a row's sources to see why.",
    kind: "editor",
    scheme: "booking",
    access: { version: 7, assignments: OWNER_MIXED_ASSIGNMENTS, caller: ownerCaller },
    inherited: OWNER_MIXED_INHERITED,
  },
  {
    key: "manager",
    label: "Manager",
    brief:
      "You are a Manager. Owner rows are visible but immutable, with an explanation. Archive is absent from the page. You can add, change, and remove Manager, Booker, and Viewer only.",
    kind: "editor",
    scheme: "booking",
    access: {
      version: 4,
      assignments: OWNER_MIXED_ASSIGNMENTS.map((assignment) =>
        assignment.grantee.id === GRANTEES.you.id ? { ...assignment, role: "manager" } : assignment,
      ),
      caller: {
        granteeId: GRANTEES.you.id,
        name: "Priya Sharma",
        effectiveRole: "manager",
        capabilities: ["edit-configuration", "manage-lower-roles", "create-booking", "subscribe"],
        sources: [{ kind: "direct", label: "Direct assignment", role: "manager" }],
      },
    },
    inherited: OWNER_MIXED_INHERITED,
  },
  {
    key: "sysadmin",
    label: "System administrator",
    brief:
      "You are a system administrator with no assignment row. Access is assumed, not persisted: the banner says Owner (system administrator) and there is no fake row for you. Charles has an explicit, distinguishable Owner assignment.",
    kind: "editor",
    scheme: "booking",
    access: {
      version: 3,
      assignments: [
        { grantee: GRANTEES.charles, role: "owner" },
        { grantee: GRANTEES.imaging, role: "booker" },
      ],
      caller: {
        granteeId: null,
        name: "Sam Rivera",
        effectiveRole: "owner",
        capabilities: ["edit-configuration", "manage-all-roles", "archive", "create-booking", "subscribe"],
        sources: [{ kind: "implicit", label: "System administrator", role: "owner" }],
      },
    },
    inherited: {},
  },
  {
    key: "add-change",
    label: "Add and change",
    brief:
      "As Owner: add Mary Anning as Booker, promote Grace Hopper, demote a Manager, and watch the staged review before Save changes.",
    kind: "editor",
    scheme: "booking",
    access: {
      version: 12,
      assignments: [
        { grantee: GRANTEES.you, role: "owner" },
        { grantee: GRANTEES.grace, role: "viewer" },
        { grantee: GRANTEES.imaging, role: "manager" },
        { grantee: ALL_USERS, role: "booker" },
      ],
      caller: ownerCaller,
    },
    inherited: { [GRANTEES.grace.id]: [{ kind: "audience", label: "All users", role: "booker" }] },
  },
  {
    key: "remove-leave",
    label: "Remove and leave (access remains)",
    brief:
      "You are a direct Booker who also inherits Viewer via Imaging Lab. Remove another grantee, then stage Leave on your own row: the editor must say inherited Viewer access remains.",
    kind: "editor",
    scheme: "booking",
    access: {
      version: 9,
      assignments: [
        { grantee: GRANTEES.charles, role: "owner" },
        { grantee: GRANTEES.you, role: "booker" },
        { grantee: GRANTEES.mary, role: "viewer" },
        { grantee: GRANTEES.imaging, role: "viewer" },
      ],
      caller: {
        granteeId: GRANTEES.you.id,
        name: "Priya Sharma",
        effectiveRole: "booker",
        capabilities: ["create-booking", "subscribe"],
        sources: [
          { kind: "direct", label: "Direct assignment", role: "booker" },
          { kind: "group", label: "Imaging Lab", role: "viewer" },
        ],
      },
    },
    inherited: { [GRANTEES.you.id]: [{ kind: "group", label: "Imaging Lab", role: "viewer" }] },
  },
  {
    key: "leave-final",
    label: "Leave (final access lost)",
    brief:
      "Your only access is a direct Booker assignment. Staging Leave must show the departed-requester warning: you lose the configuration and calendar but keep a read-only view of your own bookings.",
    kind: "editor",
    scheme: "booking",
    access: {
      version: 5,
      assignments: [
        { grantee: GRANTEES.charles, role: "owner" },
        { grantee: GRANTEES.you, role: "booker" },
      ],
      caller: {
        granteeId: GRANTEES.you.id,
        name: "Priya Sharma",
        effectiveRole: "booker",
        capabilities: ["create-booking", "subscribe"],
        sources: [{ kind: "direct", label: "Direct assignment", role: "booker" }],
      },
    },
    inherited: {},
  },
  {
    key: "last-owner",
    label: "Last Owner",
    brief:
      "Microscopy Core (a group with zero active members) is the only persisted Owner. It still satisfies the requirement, is labelled, and cannot be removed or demoted until another Owner is added.",
    kind: "editor",
    scheme: "booking",
    access: {
      version: 6,
      assignments: [
        { grantee: GRANTEES.emptyLab, role: "owner" },
        { grantee: GRANTEES.you, role: "manager" },
        { grantee: GRANTEES.ada, role: "booker" },
      ],
      caller: {
        ...ownerCaller,
        effectiveRole: "owner",
        sources: [{ kind: "implicit", label: "System administrator", role: "owner" }],
        granteeId: null,
        name: "Sam Rivera",
      },
    },
    inherited: {},
  },
  {
    key: "unavailable",
    label: "Unavailable holder",
    brief:
      "Leo Szilard is disabled and Legacy Screening is deleted. Both stay visible, are labelled unavailable, remain removable when you may edit them, and still count toward the Owner requirement.",
    kind: "editor",
    scheme: "booking",
    access: {
      version: 8,
      assignments: [
        { grantee: GRANTEES.you, role: "owner" },
        { grantee: GRANTEES.leo, role: "manager" },
        { grantee: GRANTEES.legacy, role: "owner" },
        { grantee: GRANTEES.ada, role: "booker" },
      ],
      caller: ownerCaller,
    },
    inherited: {},
  },
  {
    key: "request-failure",
    label: "Request failure on save",
    brief:
      "Stage a change and press Save changes: the first request fails. The error is announced, the draft remains intact, and a retry succeeds without rebuilding it.",
    kind: "editor",
    scheme: "booking",
    requestFailureOnSave: true,
    access: {
      version: 18,
      assignments: [
        { grantee: GRANTEES.you, role: "owner" },
        { grantee: GRANTEES.ada, role: "viewer" },
        { grantee: ALL_USERS, role: "booker" },
      ],
      caller: ownerCaller,
    },
    inherited: {},
  },
  {
    key: "conflict",
    label: "Conflict on save",
    brief:
      "Stage any change and press Save changes: the first attempt hits a stale version. Your draft is preserved, the remote change is shown, and Review latest and retry never silently overwrites.",
    kind: "editor",
    scheme: "booking",
    conflictOnSave: true,
    access: {
      version: 15,
      assignments: [
        { grantee: GRANTEES.you, role: "owner" },
        { grantee: GRANTEES.ada, role: "viewer" },
        { grantee: GRANTEES.grace, role: "booker" },
        { grantee: ALL_USERS, role: "booker" },
      ],
      caller: ownerCaller,
    },
    inherited: { [GRANTEES.ada.id]: [{ kind: "audience", label: "All users", role: "booker" }] },
  },
  {
    key: "second-scheme",
    label: "Second role scheme",
    brief:
      "The same editor rendering a Dataset archive with Owner, Manager, Contributor, and Reader. If any Booking wording leaks into the editor layout here, the layout is not generic.",
    kind: "editor",
    scheme: "archive",
    access: {
      version: 2,
      assignments: [
        { grantee: GRANTEES.you, role: "owner" },
        { grantee: GRANTEES.mary, role: "contributor" },
        { grantee: GRANTEES.genomics, role: "reader" },
      ],
      caller: {
        granteeId: GRANTEES.you.id,
        name: "Priya Sharma",
        effectiveRole: "owner",
        capabilities: ["manage-all-roles"],
        sources: [{ kind: "direct", label: "Direct assignment", role: "owner" }],
      },
    },
    inherited: { [GRANTEES.mary.id]: [{ kind: "group", label: "Genomics Consortium", role: "reader" }] },
  },
  {
    key: "global-default",
    label: "Global default (Default shared with)",
    brief:
      "The instance-wide default for new configurations: All users, Selected users and groups, or Only me. Selected mode reveals the exact grantee list and the Booker grant it produces.",
    kind: "flow",
    scheme: "booking",
  },
  {
    key: "all-items",
    label: "All Items",
    brief:
      "Every configuration you can see. Owner and Manager rows expose Settings and Access; Booker rows expose Book and View; Viewer rows expose View. Add item appears because you own an eligible unconfigured Instrument. No bulk access action exists.",
    kind: "flow",
    scheme: "booking",
  },
  {
    key: "ownership-transfer",
    label: "Ownership transfer",
    brief:
      "Inventory transfer with the optional Booking checkbox across a mixed selection: no configuration, an eligible configuration, and one whose Owners you cannot change. Each item is atomic.",
    kind: "flow",
    scheme: "booking",
  },
  {
    key: "role-lost",
    label: "Role-lost My Bookings",
    brief:
      "After either voluntarily leaving or involuntarily losing the final role, your own past and future bookings stay visible and explicitly read-only. No configuration or calendar links, no edit or cancel actions.",
    kind: "flow",
    scheme: "booking",
  },
  {
    key: "owner-repair",
    label: "System administrator Owner repair",
    brief:
      "A system administrator repairs a configuration with no effective Owner. Implicit sysadmin access is never stored as a fake assignment; repair adds a real user or group Owner before other access changes.",
    kind: "flow",
    scheme: "booking",
  },
];

export function scenarioByKey(key: string): PrototypeScenario {
  return SCENARIOS.find((scenario) => scenario.key === key) ?? SCENARIOS[0];
}

export function schemeByKey(key: string): PrototypeScheme {
  return SCHEMES.find((scheme) => scheme.key === key) ?? BOOKING_SCHEME;
}
