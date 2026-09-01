// PROTOTYPE ONLY (plan 012). One access-editor layout on a recognizable bookable-item page: a
// principal-first table of users and groups, ordered by grantee, reflowing into cards on narrow
// screens. Earlier rounds compared role-first, guided, compact-row, master-detail and
// grouped-by-grant layouts; all were discarded, so there is no variant switcher left.
// A separate scenario selector exercises mixed inheritance, Manager limits, sysadmin access,
// last-Owner and unavailable holders, leave, stale-save conflict, a fake second role scheme,
// and the surrounding flows (global default, All Items, ownership transfer, departed My
// Bookings). All state is in-memory React state; nothing persists and no network runs. The
// editor layouts are scheme-generic: every role name and consequence string comes from the
// selected scheme fixture, never from a Booking literal.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Form, getInput, reset, useField, useForm } from "@formisch/react";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import {
  ArchiveIcon,
  CalendarPlusIcon,
  CheckIcon,
  ChevronDownIcon,
  GlobeIcon,
  InfoIcon,
  LockIcon,
  LogOutIcon,
  PencilIcon,
  PlusIcon,
  RotateCcwIcon,
  Trash2Icon,
  TriangleAlertIcon,
  Undo2Icon,
  UserIcon,
  UsersIcon,
} from "lucide-react";
import * as React from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import type { RelationshipOptions } from "@/modules/common/collection-form/RenderFields.types";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Avatar, AvatarFallback } from "@/modules/common/ui/avatar";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/modules/common/ui/dialog";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { Heading } from "@/modules/common/ui/typography";
import { cn } from "@/modules/common/utils/cn";
import {
  applyDraft,
  assignableRankCeiling,
  DIRECTORY,
  diffAssignments,
  effectiveRole,
  GRANTEES,
  type PrototypeAccessState,
  type PrototypeAssignment,
  type PrototypeGrantee,
  type PrototypeRole,
  type PrototypeRoleSource,
  type PrototypeScenario,
  type PrototypeScheme,
  roleOf,
  rolesByRankDesc,
  SCENARIOS,
  SCHEMES,
  scenarioByKey,
  schemeByKey,
  simulateRemoteChange,
  topRole,
  topRoleHolders,
} from "./resourceAccessPrototypeFixtures";

// --- What each scenario shows for the surrounding item page --------------------------------

const RESOURCE_BY_SCHEME: Record<string, { name: string; globalId: string; location: string }> = {
  booking: { name: "Confocal microscope", globalId: "IN123", location: "Imaging suite 1.02" },
  archive: { name: "Coral reef survey archive", globalId: "DA871", location: "Marine data store" },
};

// --- Editor draft state ---------------------------------------------------------------------

type EditorState = {
  saved: PrototypeAccessState;
  draft: PrototypeAssignment[];
  pendingSave: boolean;
  /** The newer server copy revealed by a stale save, until the caller adopts it. */
  conflict: PrototypeAccessState | null;
  /** A stale save already happened once; the next save succeeds. */
  resolvedConflict: boolean;
  /** A simulated request failure already happened once; the next save succeeds. */
  resolvedRequestFailure: boolean;
  announcement: string;
};

type EditorAction =
  | { type: "reset"; state: EditorState }
  | { type: "cancel" }
  | { type: "stage-add"; grantee: PrototypeGrantee; role: string }
  | { type: "stage-role"; granteeId: string; role: string }
  | { type: "stage-remove"; granteeId: string; isSelf: boolean }
  | { type: "restore"; granteeId: string }
  | { type: "save-start" }
  | { type: "save-finish"; scheme: PrototypeScheme; conflictOnSave: boolean; requestFailureOnSave: boolean }
  | { type: "adopt-latest" }
  | { type: "announce"; message: string };

function initEditorState(access: PrototypeAccessState): EditorState {
  return {
    saved: access,
    draft: [...access.assignments],
    pendingSave: false,
    conflict: null,
    resolvedConflict: false,
    resolvedRequestFailure: false,
    announcement: "",
  };
}

function editorReducer(state: EditorState, action: EditorAction): EditorState {
  if (action.type === "reset") return action.state;
  if (action.type === "cancel") {
    return {
      ...state,
      draft: [...state.saved.assignments],
      pendingSave: false,
      conflict: null,
      resolvedConflict: false,
      resolvedRequestFailure: false,
      announcement: "Changes discarded; saved access is unchanged.",
    };
  }
  if (action.type === "stage-add") {
    if (state.draft.some((assignment) => assignment.grantee.id === action.grantee.id)) return state;
    return {
      ...state,
      draft: [...state.draft, { grantee: action.grantee, role: action.role }],
      announcement: `Unsaved changes: ${action.grantee.name} added.`,
    };
  }
  if (action.type === "stage-role") {
    return {
      ...state,
      draft: state.draft.map((assignment) =>
        assignment.grantee.id === action.granteeId ? { ...assignment, role: action.role } : assignment,
      ),
      announcement: "Unsaved changes: role updated.",
    };
  }
  if (action.type === "stage-remove") {
    return {
      ...state,
      draft: state.draft.filter((assignment) => assignment.grantee.id !== action.granteeId),
      announcement: action.isSelf
        ? "Unsaved changes: your direct assignment will be removed."
        : "Unsaved changes: assignment removed.",
    };
  }
  if (action.type === "restore") {
    const original = state.saved.assignments.find((assignment) => assignment.grantee.id === action.granteeId);
    if (!original || state.draft.some((assignment) => assignment.grantee.id === action.granteeId)) return state;
    // Reinsert at the saved position so restoring does not shuffle the list.
    const draft = state.saved.assignments
      .map((assignment) =>
        assignment.grantee.id === action.granteeId
          ? original
          : state.draft.find((candidate) => candidate.grantee.id === assignment.grantee.id),
      )
      .filter((assignment): assignment is PrototypeAssignment => assignment !== undefined);
    const additions = state.draft.filter(
      (assignment) => !state.saved.assignments.some((saved) => saved.grantee.id === assignment.grantee.id),
    );
    return { ...state, draft: [...draft, ...additions], announcement: "Unsaved changes: assignment restored." };
  }
  if (action.type === "save-start") return { ...state, pendingSave: true, announcement: "Saving…" };
  if (action.type === "save-finish") {
    if (action.requestFailureOnSave && !state.resolvedRequestFailure) {
      return {
        ...state,
        pendingSave: false,
        resolvedRequestFailure: true,
        announcement: "Save failed: the service could not be reached. Your draft is preserved; retry when ready.",
      };
    }
    if (action.conflictOnSave && !state.resolvedConflict) {
      return {
        ...state,
        pendingSave: false,
        conflict: simulateRemoteChange(action.scheme, state.saved),
        announcement: "Save rejected: access changed elsewhere. Your draft is preserved.",
      };
    }
    const saved = applyDraft(state.saved, state.draft);
    return {
      ...state,
      saved,
      draft: [...saved.assignments],
      pendingSave: false,
      conflict: null,
      resolvedConflict: false,
      resolvedRequestFailure: false,
      announcement: `Saved. Access is now version ${saved.version}.`,
    };
  }
  if (action.type === "adopt-latest") {
    if (!state.conflict) return state;
    return {
      ...state,
      saved: state.conflict,
      conflict: null,
      resolvedConflict: true,
      announcement: "Loaded the latest access. Review your staged changes, then press Save changes again.",
    };
  }
  if (action.type === "announce") return { ...state, announcement: action.message };
  return state;
}

/** Everything a variant needs. Variants are pure views over this; all state lives above them. */
type EditorContext = {
  scheme: PrototypeScheme;
  state: EditorState;
  inherited: Record<string, PrototypeRoleSource[]>;
  /** Highest role rank the caller may assign or change; 0 means read-only. */
  ceiling: number;
  /** The scenario simulates a stale save on the first attempt. */
  conflictOnSave: boolean;
  /** The scenario simulates a request failure on the first attempt. */
  requestFailureOnSave: boolean;
  /** Forces the acceptance-test 320 px layout without relying on Storybook chrome. */
  narrow: boolean;
  dispatch: React.Dispatch<EditorAction>;
};

// --- Row model shared by all variants -------------------------------------------------------

type RowStatus = "unchanged" | "added" | "changed" | "removed";

type RowModel = {
  grantee: PrototypeGrantee;
  role: string;
  fromRole: string | null;
  status: RowStatus;
  sources: PrototypeRoleSource[];
  effective: PrototypeRole | null;
  isSelf: boolean;
  canEdit: boolean;
  lockReason: string | null;
  lastHolderReason: string | null;
};

function rowModels(ctx: EditorContext): RowModel[] {
  const { scheme, state, inherited } = ctx;
  const top = topRole(scheme);
  const draftById = new Map(state.draft.map((assignment) => [assignment.grantee.id, assignment]));
  const savedIds = new Set(state.saved.assignments.map((assignment) => assignment.grantee.id));
  const holders = topRoleHolders(scheme, state.draft);

  const build = (assignment: PrototypeAssignment, status: RowStatus, fromRole: string | null): RowModel => {
    const inheritedSources = inherited[assignment.grantee.id] ?? [];
    const directRole = status === "removed" ? null : assignment.role;
    const relevantRank = roleOf(scheme, status === "removed" ? (fromRole ?? assignment.role) : assignment.role).rank;
    const canEdit = ctx.ceiling > 0 && relevantRank <= ctx.ceiling;
    const lastHolder =
      status !== "removed" && assignment.role === top.key && holders.length === 1 && holders[0] === assignment;
    return {
      grantee: assignment.grantee,
      role: assignment.role,
      fromRole,
      status,
      sources: [
        ...(directRole === null ? [] : [{ kind: "direct", label: "Direct assignment", role: directRole } as const]),
        ...inheritedSources,
      ],
      effective: effectiveRole(scheme, directRole, inheritedSources),
      isSelf: assignment.grantee.id === state.saved.caller.granteeId,
      canEdit,
      lockReason: canEdit ? null : ctx.ceiling === 0 ? "View only." : `Only an ${top.label} can change this.`,
      lastHolderReason: lastHolder ? `Add another ${top.label} first.` : null,
    };
  };

  return [
    ...state.saved.assignments.map((saved) => {
      const draft = draftById.get(saved.grantee.id);
      if (!draft) return build(saved, "removed", saved.role);
      return build(draft, draft.role === saved.role ? "unchanged" : "changed", saved.role);
    }),
    ...state.draft
      .filter((assignment) => !savedIds.has(assignment.grantee.id))
      .map((assignment) => build(assignment, "added", null)),
  ];
}

// --- Small semantic pieces shared by the variants (identity, sources, role picking) ---------

function GranteeGlyph({ kind }: { kind: PrototypeGrantee["kind"] }) {
  if (kind === "user") return <UserIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />;
  if (kind === "group") return <UsersIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />;
  return <GlobeIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />;
}

/** The audience is not a kind worth labelling; only real principals get a type badge. */
/** Only a group needs a line under its name; a user badge already says who the user is. */
function granteeDetail(grantee: PrototypeGrantee): string | null {
  return grantee.kind === "group" && grantee.detail !== "" ? grantee.detail : null;
}

function granteeKindLabel(grantee: PrototypeGrantee): string | null {
  if (grantee.kind === "user") return "User";
  if (grantee.kind === "group") return "Group";
  return null;
}

function stagedLabel(scheme: PrototypeScheme, row: RowModel): string | null {
  if (row.status === "added") return `Staged: added as ${roleOf(scheme, row.role).label}`;
  if (row.status === "changed")
    return `Staged: ${roleOf(scheme, row.fromRole ?? row.role).label} \u2192 ${roleOf(scheme, row.role).label}`;
  if (row.status === "removed") return "Staged: removal";
  return null;
}

/**
 * The change marker, sitting left of the grantee glyph. The dot carries the colour; its title and
 * screen-reader text carry the same meaning, so the state is never colour-only.
 */
function StatusDot({ status, label }: { status: RowStatus; label: string | null }) {
  if (label === null) return <span className="size-2 shrink-0" aria-hidden="true" />;
  return (
    <span className="shrink-0 leading-none" title={label}>
      <span
        className={cn(
          "block size-2 rounded-full",
          status === "added" && "bg-primary",
          status === "changed" && "bg-amber-500",
          status === "removed" && "bg-destructive",
        )}
        aria-hidden="true"
      />
      <span className="sr-only">{label}</span>
    </span>
  );
}

function PrincipalLabel({ grantee }: { grantee: PrototypeGrantee }) {
  if (grantee.kind === "user") {
    const initials = grantee.name
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0])
      .join("");
    return (
      <span className="inline-flex min-w-0 items-center gap-1.5">
        <Avatar size="sm" aria-hidden="true">
          <AvatarFallback>{initials}</AvatarFallback>
        </Avatar>
        <span className="truncate font-medium">{grantee.name}</span>
      </span>
    );
  }
  return (
    <span className="inline-flex min-w-0 items-center gap-1.5">
      <GranteeGlyph kind={grantee.kind} />
      <span className="truncate font-medium">{grantee.name}</span>
    </span>
  );
}

function GranteeIdentity({
  grantee,
  isSelf,
  status = "unchanged",
  stagedText = null,
}: {
  grantee: PrototypeGrantee;
  isSelf: boolean;
  status?: RowStatus;
  stagedText?: string | null;
}) {
  return (
    // Two grid rows: the dot shares row 1 with the name line and is centred on it, while a group's
    // detail line sits in row 2 under the name. A margin could not do this, because the first
    // line's height varies with the user badge's avatar.
    <span className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] items-center gap-x-2">
      <StatusDot status={status} label={stagedText} />
      <span className="flex min-w-0 flex-wrap items-center gap-1.5 font-medium">
        <PrincipalLabel grantee={grantee} />
        {isSelf ? <Badge variant="outline">You</Badge> : null}
        {granteeKindLabel(grantee) === null ? null : <Badge variant="secondary">{granteeKindLabel(grantee)}</Badge>}
        {grantee.unavailable ? <Badge variant="destructive">Unavailable</Badge> : null}
      </span>
      {granteeDetail(grantee) === null ? null : (
        <span className="col-start-2 truncate text-xs text-muted-foreground">{granteeDetail(grantee)}</span>
      )}
    </span>
  );
}

/**
 * The explicit role control. A Menu rather than a native select: options are
 * ordinary markup, so each role can carry an icon and its description while remaining keyboard operable.
 */
function RoleSelect({ ctx, row, className }: { ctx: EditorContext; row: RowModel; className?: string }) {
  const options = rolesByRankDesc(ctx.scheme).filter(
    (role) =>
      role.rank <= ctx.ceiling &&
      (role.allowedGranteeKinds === undefined || role.allowedGranteeKinds.includes(row.grantee.kind)),
  );
  if (!row.canEdit) {
    return (
      <span className="flex items-center gap-1 text-sm">
        <LockIcon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
        {roleOf(ctx.scheme, row.role).label}
      </span>
    );
  }
  const current = roleOf(ctx.scheme, row.role);
  return (
    <Menu>
      <MenuTrigger
        render={
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="w-56 justify-between rounded-sm max-sm:w-auto max-sm:min-w-0 max-sm:flex-1"
          />
        }
        disabled={row.lastHolderReason !== null || row.status === "removed"}
        aria-label={`Role for ${row.grantee.name}`}
        className={className}
      >
        {current.label}
        <ChevronDownIcon aria-hidden="true" />
      </MenuTrigger>
      {/* Matches the trigger: with the descriptions gone there is nothing to justify a wider popup. */}
      <MenuContent align="start" className="w-56 rounded-sm">
        {options.map((role) => (
          <MenuItem
            key={role.key}
            onClick={() => ctx.dispatch({ type: "stage-role", granteeId: row.grantee.id, role: role.key })}
          >
            {/* Icon slot: a production scheme drops its per-role icon here. */}
            <CheckIcon className={cn("size-4 shrink-0", role.key !== row.role && "invisible")} aria-hidden="true" />
            {role.label}
          </MenuItem>
        ))}
      </MenuContent>
    </Menu>
  );
}

function RowRemoveAction({ ctx, row }: { ctx: EditorContext; row: RowModel }) {
  const reasonId = React.useId();
  // The audience is not a membership anyone can revoke from here.
  if (row.grantee.kind === "audience") return null;

  const restoring = row.status === "removed";
  const verb = restoring ? "Restore" : row.isSelf ? "Leave" : "Remove";
  const label = restoring
    ? `Restore ${row.grantee.name}`
    : row.isSelf
      ? "Leave: remove your own direct assignment"
      : `Remove ${row.grantee.name}`;
  // The last-holder rule only blocks removal; a restore is blocked solely by the caller's reach.
  const blockedReason = (restoring ? null : row.lastHolderReason) ?? (row.canEdit ? null : row.lockReason);
  const Icon = restoring ? Undo2Icon : row.isSelf ? LogOutIcon : Trash2Icon;

  return (
    <Tooltip>
      {/* aria-disabled, not disabled: a blocked icon button keeps focus and hover, so both its name
          and the reason still reach pointer and keyboard users. */}
      <TooltipTrigger
        render={
          <Button
            type="button"
            size="icon-sm"
            variant={restoring ? "outline" : "ghost"}
            aria-label={label}
            aria-disabled={blockedReason === null ? undefined : true}
            aria-describedby={blockedReason === null ? undefined : reasonId}
            className={cn(
              !restoring && "text-destructive hover:text-destructive",
              blockedReason !== null && "cursor-not-allowed opacity-50",
            )}
            onClick={(event) => {
              if (blockedReason !== null) {
                event.preventDefault();
                ctx.dispatch({ type: "announce", message: `Change blocked: ${blockedReason}` });
                return;
              }
              ctx.dispatch(
                restoring
                  ? { type: "restore", granteeId: row.grantee.id }
                  : { type: "stage-remove", granteeId: row.grantee.id, isSelf: row.isSelf },
              );
            }}
          />
        }
      >
        <Icon aria-hidden="true" />
      </TooltipTrigger>
      {/* The hover text replaces the label the button used to carry. */}
      <TooltipContent>{blockedReason ?? verb}</TooltipContent>
      {/* Base UI does not point aria-describedby at the popup, so a blocked reason also lives here:
          a screen reader hears it on focus, a sighted user sees it on hover. */}
      {blockedReason === null ? null : (
        <span id={reasonId} className="sr-only">
          {blockedReason}
        </span>
      )}
    </Tooltip>
  );
}

function RowLockNotes({ row }: { row: RowModel }) {
  if (row.lockReason === null) return null;
  return <p className="mt-1 text-xs text-muted-foreground">{row.lockReason}</p>;
}

// --- Grantee search -------------------------------------------------------------------------

/**
 * The finder, rendered through RenderFields as a `relationship` field so it is the same control the
 * rest of the app uses for picking a record. `relationTo: "principals"` has no entry in
 * relationshipSources, so RelationshipField takes its static-options path and nothing hits the
 * network. The audience is deliberately absent from the options: All users is not something you
 * add here.
 */
const FINDER_CONFIG = resolveCollectionConfig<{ principal: unknown }>({
  slug: "access-principals",
  idField: "principal",
  labels: { singularKey: "Users and groups", pluralKey: "Users and groups" },
  useAsTitle: "principal",
  defaultColumns: ["principal"],
  fields: [
    {
      name: "principal",
      type: "relationship",
      labelKey: "Add user or group",
      relationTo: "principals",
      hasMany: false,
      nullable: true,
    },
  ],
});

const FINDER_SCHEMA = v.object({ principal: v.nullable(v.any()) });

function GranteeSearch({
  excluded,
  onPick,
}: {
  excluded: ReadonlySet<string>;
  onPick: (grantee: PrototypeGrantee) => void;
}) {
  const form = useForm({ schema: FINDER_SCHEMA, initialInput: { principal: null } });
  useField(form, { path: ["principal"] });
  const picked = (getInput(form) as { principal?: { value?: unknown } | null }).principal?.value;

  React.useEffect(() => {
    if (typeof picked !== "string") return;
    const grantee = DIRECTORY.find((candidate) => candidate.id === picked);
    if (grantee) onPick(grantee);
    reset(form, { initialInput: { principal: null } });
  }, [picked, onPick, form]);

  const options: RelationshipOptions = {
    principals: DIRECTORY.filter((grantee) => grantee.kind !== "audience" && !excluded.has(grantee.id)).map(
      (grantee) => ({
        value: grantee.id,
        label: grantee.name,
        content: (
          <>
            <span className="min-w-0">
              <span className="flex flex-wrap items-center gap-1.5">
                <PrincipalLabel grantee={grantee} />
                <Badge variant="secondary">{granteeKindLabel(grantee)}</Badge>
                {grantee.unavailable ? <Badge variant="destructive">Unavailable</Badge> : null}
              </span>
              {granteeDetail(grantee) === null ? null : (
                <span className="block text-xs font-normal text-muted-foreground">{granteeDetail(grantee)}</span>
              )}
            </span>
          </>
        ),
      }),
    ),
  };

  return (
    <Form of={form} onSubmit={() => undefined}>
      <RenderFields fields={FINDER_CONFIG.fields} form={form} relationshipOptions={options} />
    </Form>
  );
}

// --- Shared editor chrome: leave consequence, conflict recovery, footer ---------------------

function LeaveConsequence({ ctx }: { ctx: EditorContext }) {
  const caller = ctx.state.saved.caller;
  if (caller.granteeId === null) return null;
  const diff = diffAssignments(ctx.state.saved.assignments, ctx.state.draft);
  if (!diff.removed.some((assignment) => assignment.grantee.id === caller.granteeId)) return null;
  const remaining = (ctx.inherited[caller.granteeId] ?? []).filter((source) => source.kind !== "direct");
  if (remaining.length > 0) {
    const strongest = effectiveRole(ctx.scheme, null, remaining);
    return (
      <Alert>
        <InfoIcon aria-hidden="true" />
        <AlertTitle>You are leaving, but inherited access remains</AlertTitle>
        <AlertDescription>
          Only your direct assignment is removed. You keep {strongest?.label ?? ""} through{" "}
          {remaining.map((source) => source.label).join(", ")}.
        </AlertDescription>
      </Alert>
    );
  }
  return (
    <Alert variant="destructive">
      <TriangleAlertIcon aria-hidden="true" />
      <AlertTitle>You will lose all access to this {ctx.scheme.resourceNoun.toLowerCase()}</AlertTitle>
      <AlertDescription>{ctx.scheme.leaveWarning}</AlertDescription>
    </Alert>
  );
}

function ConflictAlert({ ctx }: { ctx: EditorContext }) {
  const { conflict, saved } = ctx.state;
  if (conflict === null) return null;
  const remoteDiff = diffAssignments(saved.assignments, conflict.assignments);
  return (
    <Alert variant="destructive">
      <TriangleAlertIcon aria-hidden="true" />
      <AlertTitle>Access changed elsewhere while you were editing</AlertTitle>
      <AlertDescription>
        <p>
          The saved access is now version {conflict.version}; your draft was based on version {saved.version}. Nothing
          was overwritten and your staged changes are preserved.
        </p>
        <ul className="list-disc pl-4">
          {remoteDiff.changed.map((change) => (
            <li key={change.grantee.id}>
              {change.grantee.name}: {roleOf(ctx.scheme, change.from).label} → {roleOf(ctx.scheme, change.to).label}
            </li>
          ))}
          {remoteDiff.added.map((assignment) => (
            <li key={assignment.grantee.id}>
              {assignment.grantee.name} added as {roleOf(ctx.scheme, assignment.role).label}
            </li>
          ))}
          {remoteDiff.removed.map((assignment) => (
            <li key={assignment.grantee.id}>{assignment.grantee.name} removed</li>
          ))}
        </ul>
        <Button type="button" size="sm" variant="outline" onClick={() => ctx.dispatch({ type: "adopt-latest" })}>
          Review latest and retry
        </Button>
      </AlertDescription>
    </Alert>
  );
}

function EditorFooter({ ctx }: { ctx: EditorContext }) {
  const diff = diffAssignments(ctx.state.saved.assignments, ctx.state.draft);
  const dirty = diff.added.length + diff.removed.length + diff.changed.length > 0;
  const invariantBroken = topRoleHolders(ctx.scheme, ctx.state.draft).length === 0;
  const statusRef = React.useRef<HTMLParagraphElement>(null);
  const previousVersion = React.useRef(ctx.state.saved.version);
  React.useEffect(() => {
    if (ctx.state.saved.version !== previousVersion.current) {
      previousVersion.current = ctx.state.saved.version;
      statusRef.current?.focus();
    }
  }, [ctx.state.saved.version]);
  return (
    <div className="space-y-3">
      <LeaveConsequence ctx={ctx} />
      <ConflictAlert ctx={ctx} />
      {invariantBroken ? (
        <Alert variant="destructive">
          <TriangleAlertIcon aria-hidden="true" />
          <AlertTitle>
            Every {ctx.scheme.resourceNoun.toLowerCase()} needs at least one persisted {topRole(ctx.scheme).label}
          </AlertTitle>
        </Alert>
      ) : null}
      {ctx.state.announcement !== "" ? (
        <p
          ref={statusRef}
          role={ctx.state.announcement.startsWith("Save failed") ? "alert" : "status"}
          tabIndex={-1}
          className="text-xs text-muted-foreground"
        >
          {ctx.state.announcement}
        </p>
      ) : null}
      <div className="flex flex-wrap items-center justify-end gap-2">
        {/* Not a live region: the user just made the change, and the row dots already mark it. */}
        {dirty ? (
          <p className="mr-auto flex items-center gap-1.5 text-xs text-amber-700 dark:text-amber-400">
            <TriangleAlertIcon className="size-3.5 shrink-0" aria-hidden="true" />
            Unsaved changes
          </p>
        ) : null}
        <Button type="button" variant="outline" onClick={() => ctx.dispatch({ type: "cancel" })}>
          Cancel
        </Button>
        <Button
          type="button"
          disabled={!dirty || ctx.state.pendingSave || invariantBroken || ctx.state.conflict !== null}
          aria-busy={ctx.state.pendingSave}
          onClick={() => {
            ctx.dispatch({ type: "save-start" });
            // ponytail: fixed 450ms fake latency; a real save reports server state.
            window.setTimeout(
              () =>
                ctx.dispatch({
                  type: "save-finish",
                  scheme: ctx.scheme,
                  conflictOnSave: ctx.conflictOnSave,
                  requestFailureOnSave: ctx.requestFailureOnSave,
                }),
              450,
            );
          }}
        >
          {ctx.state.pendingSave ? "Saving…" : "Save changes"}
        </Button>
      </div>
    </div>
  );
}

// --- The access table --------------------------------------------------------

function AccessTable({ ctx }: { ctx: EditorContext }) {
  const rows = rowModels(ctx);
  const lowest = rolesByRankDesc(ctx.scheme).at(-1) ?? ctx.scheme.roles[0];
  const excluded = new Set(rows.filter((row) => row.status !== "removed").map((row) => row.grantee.id));
  return (
    <div className="space-y-4">
      {ctx.ceiling > 0 ? (
        <div className="px-1">
          <GranteeSearch
            excluded={excluded}
            onPick={(grantee) => ctx.dispatch({ type: "stage-add", grantee, role: lowest.key })}
          />
        </div>
      ) : null}
      {/* Wide screens: one table ordered by grantee. */}
      <div className={cn(ctx.narrow ? "hidden" : "max-sm:hidden")}>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>User/Group</TableHead>
              <TableHead>Direct role</TableHead>
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.grantee.id} className={cn(row.status === "removed" && "opacity-60")}>
                <TableCell className="align-middle">
                  <GranteeIdentity
                    grantee={row.grantee}
                    isSelf={row.isSelf}
                    status={row.status}
                    stagedText={stagedLabel(ctx.scheme, row)}
                  />
                </TableCell>
                <TableCell className="align-middle">
                  <RoleSelect ctx={ctx} row={row} />
                  <RowLockNotes row={row} />
                </TableCell>
                <TableCell className="align-middle text-right">
                  <RowRemoveAction ctx={ctx} row={row} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {/* Narrow screens: the same rows as labelled cards, no horizontal scrolling. */}
      <ul className={cn("space-y-2", ctx.narrow ? "block" : "sm:hidden")}>
        {rows.map((row) => (
          <li key={row.grantee.id} className={cn("rounded-sm border p-3", row.status === "removed" && "opacity-60")}>
            <GranteeIdentity
              grantee={row.grantee}
              isSelf={row.isSelf}
              status={row.status}
              stagedText={stagedLabel(ctx.scheme, row)}
            />
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <RoleSelect ctx={ctx} row={row} />
              <RowRemoveAction ctx={ctx} row={row} />
            </div>
            <RowLockNotes row={row} />
          </li>
        ))}
      </ul>
    </div>
  );
}

// --- Access editor content --------------------------------------------------------------------

type ShellContext = EditorContext;

function AccessEditorBody({ ctx }: { ctx: ShellContext }) {
  return (
    <TooltipProvider>
      <div className="space-y-4">
        <AccessTable ctx={ctx} />
        <EditorFooter ctx={ctx} />
      </div>
    </TooltipProvider>
  );
}

// --- Item page shell (recognizable, but nothing network-backed is mounted) --------------------

function ItemTabs({ tabs }: { tabs: readonly { label: string; content: React.ReactNode }[] }) {
  const [active, setActive] = React.useState(0);
  const base = React.useId();
  const tabRefs = React.useRef<Array<HTMLButtonElement | null>>([]);
  const selectTab = (index: number) => {
    setActive(index);
    tabRefs.current[index]?.focus();
  };
  return (
    <div>
      <div role="tablist" aria-label="Item sections" className="flex flex-wrap border-b">
        {tabs.map((tab, index) => (
          <button
            key={tab.label}
            ref={(element) => {
              tabRefs.current[index] = element;
            }}
            type="button"
            role="tab"
            id={`${base}-tab-${index}`}
            aria-selected={index === active}
            aria-controls={`${base}-panel-${index}`}
            tabIndex={index === active ? 0 : -1}
            onClick={() => setActive(index)}
            onKeyDown={(event) => {
              let next: number | null = null;
              if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
              if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
              if (event.key === "Home") next = 0;
              if (event.key === "End") next = tabs.length - 1;
              if (next !== null) {
                event.preventDefault();
                selectTab(next);
              }
            }}
            className="-mb-px border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/30 aria-selected:border-primary aria-selected:text-foreground"
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${base}-panel-${active}`} aria-labelledby={`${base}-tab-${active}`} className="py-6">
        {tabs[active].content}
      </div>
    </div>
  );
}

function ItemPageShell({ ctx }: { ctx: ShellContext }) {
  const caller = ctx.state.saved.caller;
  const capabilities = new Set(caller.capabilities);
  const resource = RESOURCE_BY_SCHEME[ctx.scheme.key] ?? RESOURCE_BY_SCHEME.booking;
  const implicit = caller.sources.every((source) => source.kind === "implicit");
  const roleLabel = roleOf(ctx.scheme, caller.effectiveRole).label;
  const primaryTabs = (
    ctx.scheme.key === "booking" ? ["Bookings", "Details", "Audit log"] : ["Datasets", "Details", "Audit log"]
  ).map((label) => ({
    label,
    content: (
      <p className="text-sm text-muted-foreground">Static {label} content — not part of this prototype's question.</p>
    ),
  }));
  return (
    <main className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <section className="flex flex-wrap items-center gap-4">
        <InventoryItem
          name={resource.name}
          nameAs="h1"
          globalId={resource.globalId}
          className="min-w-full flex-1 p-0 sm:min-w-0"
        >
          <span>Europe/London</span>
          <InventoryLocationLink name={resource.location} globalId={`${resource.globalId}-LOC`} />
        </InventoryItem>
        <div className="flex min-w-0 flex-wrap items-center gap-3">
          <Badge>Enabled</Badge>
          {implicit ? <Badge variant="outline">{`${roleLabel} (system administrator)`}</Badge> : null}
          {capabilities.has("create-booking") ? (
            <Button type="button" size="sm">
              <CalendarPlusIcon aria-hidden="true" /> Create booking
            </Button>
          ) : null}
          {capabilities.has("subscribe") ? (
            <Button type="button" size="sm" variant="outline">
              Calendar subscription
            </Button>
          ) : null}
          {capabilities.has("edit-configuration") ? (
            <Button type="button" size="sm" variant="ghost">
              <PencilIcon aria-hidden="true" /> Edit details
            </Button>
          ) : null}
          {capabilities.has("archive") ? (
            <Button type="button" size="sm" variant="ghost">
              <ArchiveIcon aria-hidden="true" /> Archive
            </Button>
          ) : null}
        </div>
      </section>
      <ItemTabs
        tabs={[
          ...primaryTabs,
          {
            label: "Access",
            content: <AccessEditorBody ctx={ctx} />,
          },
        ]}
      />
    </main>
  );
}

// --- Surrounding-flow scenes -------------------------------------------------------------------

function GlobalDefaultScene() {
  const [mode, setMode] = React.useState("all-users");
  const [selected, setSelected] = React.useState<PrototypeGrantee[]>([GRANTEES.imaging, GRANTEES.ada]);
  const excluded = new Set(selected.map((grantee) => grantee.id));
  return (
    <main className="mx-auto max-w-2xl space-y-4 p-4 sm:p-8">
      <Card>
        <CardHeader>
          <CardTitle>Booking settings — Default shared with</CardTitle>
          <CardDescription>
            Instance-wide default applied when a new Booking configuration is created. It grants initial Booker access;
            the creator is always a persisted Owner.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <RadioGroup value={mode} onValueChange={(value) => setMode(String(value))} className="gap-3">
            <Label className="flex items-start gap-2 font-normal">
              <RadioGroupItem value="all-users" className="mt-0.5" />
              <span>
                <span className="font-medium">All users</span>
                <span className="block text-xs text-muted-foreground">
                  A dynamic audience: every current account and every future account gets Booker on new configurations.
                </span>
              </span>
            </Label>
            <Label className="flex items-start gap-2 font-normal">
              <RadioGroupItem value="selected" className="mt-0.5" />
              <span>
                <span className="font-medium">Selected users and groups</span>
                <span className="block text-xs text-muted-foreground">
                  Exactly the grantees listed below get Booker on new configurations.
                </span>
              </span>
            </Label>
            <Label className="flex items-start gap-2 font-normal">
              <RadioGroupItem value="only-me" className="mt-0.5" />
              <span>
                <span className="font-medium">Only me</span>
                <span className="block text-xs text-muted-foreground">
                  New configurations start with only the creator's Owner assignment.
                </span>
              </span>
            </Label>
          </RadioGroup>
          {mode === "selected" ? (
            <div className="space-y-2 rounded-sm border p-3">
              <p className="text-sm font-medium">These grantees receive Booker on each new configuration:</p>
              <ul className="space-y-1">
                {selected.map((grantee) => (
                  <li key={grantee.id} className="flex items-center justify-between gap-2">
                    <GranteeIdentity grantee={grantee} isSelf={false} />
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      aria-label={`Remove ${grantee.name} from the default list`}
                      onClick={() => setSelected((current) => current.filter((entry) => entry.id !== grantee.id))}
                    >
                      Remove
                    </Button>
                  </li>
                ))}
              </ul>
              <GranteeSearch
                excluded={excluded}
                onPick={(grantee) => {
                  if (grantee.kind !== "audience") setSelected((current) => [...current, grantee]);
                }}
              />
            </div>
          ) : null}
        </CardContent>
      </Card>
    </main>
  );
}

const ALL_ITEMS_ROWS = [
  { name: "Confocal microscope", globalId: "IN123", role: "Owner", actions: "manage" },
  { name: "Electron microscope", globalId: "IN124", role: "Manager", actions: "manage" },
  { name: "Flow cytometer", globalId: "IN125", role: "Booker", actions: "book" },
  { name: "PCR thermocycler", globalId: "IN126", role: "Viewer", actions: "view" },
];

function AllItemsScene() {
  return (
    <main className="mx-auto max-w-4xl space-y-4 p-4 sm:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Heading level={2} as="h1">
          All Items
        </Heading>
        <div className="text-right">
          <Button type="button" size="sm">
            <PlusIcon aria-hidden="true" /> Add item
          </Button>
          <p className="mt-1 text-xs text-muted-foreground">
            Shown because you own Plate reader (IN130), an eligible Instrument with no configuration yet.
          </p>
        </div>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Item</TableHead>
            <TableHead>Your role</TableHead>
            <TableHead>Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {ALL_ITEMS_ROWS.map((row) => (
            <TableRow key={row.globalId}>
              <TableCell>
                {row.name} <span className="text-xs text-muted-foreground">{row.globalId}</span>
              </TableCell>
              <TableCell>
                <Badge variant="secondary">{row.role}</Badge>
              </TableCell>
              <TableCell>
                {row.actions === "manage" ? (
                  <span className="flex gap-2">
                    <Button type="button" size="sm" variant="outline" aria-label={`Settings for ${row.name}`}>
                      Settings
                    </Button>
                    <Button type="button" size="sm" variant="outline" aria-label={`Access for ${row.name}`}>
                      <LockIcon aria-hidden="true" /> Access
                    </Button>
                  </span>
                ) : row.actions === "book" ? (
                  <span className="flex items-center gap-2">
                    <Button type="button" size="sm">
                      Book
                    </Button>
                    <Button type="button" size="sm" variant="ghost">
                      View calendar
                    </Button>
                  </span>
                ) : (
                  <span className="flex items-center gap-2">
                    <Button type="button" size="sm" variant="ghost">
                      View calendar
                    </Button>
                    <span className="text-xs text-muted-foreground">Read-only</span>
                  </span>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <p className="text-xs text-muted-foreground">
        Bulk actions on the sysadmin All bookable items page cover settings, enabled, and archive. No bulk action ever
        modifies access.
      </p>
    </main>
  );
}

const TRANSFER_ITEMS = [
  {
    name: "Plate reader (IN130)",
    state: "No Booking configuration exists.",
    effect: "Nothing to transfer — only Inventory ownership moves.",
    eligible: false,
  },
  {
    name: "Confocal microscope (IN123)",
    state: "You are a Booking Owner and may change Owners.",
    effect:
      "Adds Grace Hopper (incoming Instrument owner) as Booking Owner and removes only your direct Owner assignment. Every other assignment is preserved.",
    eligible: true,
  },
  {
    name: "Electron microscope (IN124)",
    state: "You cannot change this configuration's Owners.",
    effect: "Booking access is left completely unchanged; only Inventory ownership moves.",
    eligible: false,
  },
];

function OwnershipTransferScene() {
  const [alsoTransfer, setAlsoTransfer] = React.useState(true);
  const checkboxId = React.useId();
  return (
    <main className="mx-auto max-w-2xl space-y-4 p-4 sm:p-8">
      <Card>
        <CardHeader>
          <CardTitle>Transfer ownership to Grace Hopper</CardTitle>
          <CardDescription>3 Inventory items selected.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-start gap-2">
            <Checkbox
              id={checkboxId}
              checked={alsoTransfer}
              onCheckedChange={(checked) => setAlsoTransfer(checked === true)}
            />
            <Label htmlFor={checkboxId} className="font-normal">
              <span className="font-medium">Also transfer Booking configuration ownership</span>
              <span className="block text-xs text-muted-foreground">
                Applies per item, and only where you may change Booking Owners.
              </span>
            </Label>
          </div>
          <ul className="space-y-2">
            {TRANSFER_ITEMS.map((item) => (
              <li key={item.name} className="rounded-sm border p-3">
                <p className="font-medium">{item.name}</p>
                <p className="text-xs text-muted-foreground">{item.state}</p>
                <p className="mt-1 text-sm">
                  {alsoTransfer && item.eligible ? (
                    item.effect
                  ) : alsoTransfer ? (
                    <span className="flex items-start gap-1">
                      <LockIcon className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                      {item.effect}
                    </span>
                  ) : (
                    "Booking access is left unchanged."
                  )}
                </p>
              </li>
            ))}
          </ul>
          <Alert>
            <InfoIcon aria-hidden="true" />
            <AlertTitle>Each item transfers atomically</AlertTitle>
            <AlertDescription>
              If the Booking part fails for one item, that item's Booking access is not partially changed, and the other
              items still transfer.
            </AlertDescription>
          </Alert>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline">
              Cancel
            </Button>
            <Button type="button">Transfer 3 items</Button>
          </div>
        </CardContent>
      </Card>
    </main>
  );
}

const DEPARTED_BOOKINGS = [
  { when: "Mon 24 Aug 2026, 09:00–11:00", item: "Confocal microscope", status: "Upcoming" },
  { when: "Fri 21 Aug 2026, 14:00–15:30", item: "Confocal microscope", status: "Past" },
];

function DepartedScene() {
  const [lossMode, setLossMode] = React.useState("voluntary");
  return (
    <main className="mx-auto max-w-2xl space-y-4 p-4 sm:p-8">
      <Heading level={2} as="h1">
        My Bookings
      </Heading>
      <RadioGroup value={lossMode} onValueChange={(value) => setLossMode(String(value))} className="flex gap-4">
        <Label className="flex items-center gap-2 font-normal">
          <RadioGroupItem value="voluntary" /> Voluntary leave
        </Label>
        <Label className="flex items-center gap-2 font-normal">
          <RadioGroupItem value="involuntary" /> Assignment removed by an Owner
        </Label>
      </RadioGroup>
      <Alert>
        <InfoIcon aria-hidden="true" />
        <AlertTitle>
          {lossMode === "voluntary"
            ? "You left Confocal microscope's booking configuration"
            : "An Owner removed your final role on Confocal microscope"}
        </AlertTitle>
        <AlertDescription>
          Your own past and future bookings remain visible here, read-only. You can no longer open the configuration,
          its calendar, or edit or cancel these bookings.
        </AlertDescription>
      </Alert>
      <ul className="space-y-2">
        {DEPARTED_BOOKINGS.map((booking) => (
          <li key={booking.when} className="flex flex-wrap items-center justify-between gap-2 rounded-sm border p-3">
            <span>
              <span className="block font-medium">{booking.item}</span>
              <span className="block text-sm text-muted-foreground">{booking.when}</span>
            </span>
            <span className="flex items-center gap-2">
              <Badge variant="secondary">{booking.status}</Badge>
              <Badge variant="outline">Read-only</Badge>
            </span>
          </li>
        ))}
      </ul>
    </main>
  );
}

function OwnerRepairScene() {
  const [open, setOpen] = React.useState(false);
  const [repaired, setRepaired] = React.useState(false);
  return (
    <main className="mx-auto max-w-3xl space-y-4 p-4 sm:p-8">
      <Heading level={2} as="h1">
        All bookable items
      </Heading>
      <p className="text-sm text-muted-foreground">
        You have assumed Owner access as a system administrator. No system-administrator assignment is persisted.
      </p>
      <Card>
        <CardHeader>
          <CardTitle>Confocal microscope</CardTitle>
          <CardDescription>Persisted Owner: Leo Szilard (disabled)</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center justify-between gap-3">
          {repaired ? (
            <Badge>Effective Owner restored: Mary Anning</Badge>
          ) : (
            <Badge variant="destructive">No effective Owner</Badge>
          )}
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger render={<Button type="button" variant="outline" />}>
              <LockIcon aria-hidden="true" /> Repair access
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Repair Owner access for Confocal microscope</DialogTitle>
                <DialogDescription>
                  Leo's persisted row satisfies the structural invariant but supplies no effective access. Add a real
                  enabled user or group Owner; your implicit sysadmin access will not be saved as a row.
                </DialogDescription>
              </DialogHeader>
              <div className="rounded-sm border p-3">
                <GranteeIdentity grantee={GRANTEES.mary} isSelf={false} />
                <p className="mt-1 text-xs text-muted-foreground">Will be added as Owner.</p>
              </div>
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                  Cancel
                </Button>
                <Button
                  type="button"
                  onClick={() => {
                    setRepaired(true);
                    setOpen(false);
                  }}
                >
                  Save Owner assignment
                </Button>
              </div>
            </DialogContent>
          </Dialog>
        </CardContent>
      </Card>
      {repaired ? (
        <p role="status" className="text-sm text-muted-foreground">
          Owner access repaired. Mary Anning is now a persisted effective Owner.
        </p>
      ) : null}
    </main>
  );
}

// --- Scheme remapping (roles are matched by rank when the scheme selector overrides) ----------

function remapRoleKey(from: PrototypeScheme, to: PrototypeScheme, key: string): string {
  const rank = roleOf(from, key).rank;
  return to.roles.find((role) => role.rank === rank)?.key ?? to.roles[0].key;
}

function remapAccess(access: PrototypeAccessState, from: PrototypeScheme, to: PrototypeScheme): PrototypeAccessState {
  if (from.key === to.key) return access;
  return {
    ...access,
    assignments: access.assignments.map((assignment) => ({
      ...assignment,
      role: remapRoleKey(from, to, assignment.role),
    })),
    caller: {
      ...access.caller,
      effectiveRole: remapRoleKey(from, to, access.caller.effectiveRole),
      sources: access.caller.sources.map((source) => ({ ...source, role: remapRoleKey(from, to, source.role) })),
    },
  };
}

function remapInherited(
  inherited: Record<string, PrototypeRoleSource[]>,
  from: PrototypeScheme,
  to: PrototypeScheme,
): Record<string, PrototypeRoleSource[]> {
  if (from.key === to.key) return inherited;
  return Object.fromEntries(
    Object.entries(inherited).map(([granteeId, sources]) => [
      granteeId,
      sources.map((source) => ({ ...source, role: remapRoleKey(from, to, source.role) })),
    ]),
  );
}

// --- Story shell --------------------------------------------------------------------------------

function DebugPanel({
  scenario,
  scheme,
  editor,
}: {
  scenario: PrototypeScenario;
  scheme: PrototypeScheme;
  editor: EditorState | null;
}) {
  const debug = editor
    ? {
        scenario: scenario.key,
        scheme: scheme.key,
        version: editor.saved.version,
        callerCapabilities: editor.saved.caller.capabilities,
        saved: editor.saved.assignments.map((assignment) => `${assignment.grantee.name}=${assignment.role}`),
        draft: editor.draft.map((assignment) => `${assignment.grantee.name}=${assignment.role}`),
        diff: diffAssignments(editor.saved.assignments, editor.draft),
        pendingSave: editor.pendingSave,
        conflictPending: editor.conflict !== null,
      }
    : { scenario: scenario.key, scheme: scheme.key, note: "flow scenario — no editor state" };
  return (
    <Collapsible className="mx-auto max-w-5xl px-4 pb-24">
      <CollapsibleTrigger render={<Button type="button" size="sm" variant="ghost" />}>
        Prototype state
      </CollapsibleTrigger>
      <CollapsibleContent>
        <pre className="overflow-x-auto rounded-sm border bg-muted/30 p-3 text-xs">
          {JSON.stringify(debug, null, 2)}
        </pre>
      </CollapsibleContent>
    </Collapsible>
  );
}

function ResourceAccessPrototype() {
  const [scenarioKey, setScenarioKey] = React.useState<string>(SCENARIOS[0].key);
  const [schemeKey, setSchemeKey] = React.useState<string>(SCENARIOS[0].scheme);
  const [narrow, setNarrow] = React.useState(false);
  const scenario = scenarioByKey(scenarioKey);
  const scenarioScheme = schemeByKey(scenario.scheme);
  const scheme = schemeByKey(schemeKey);
  const scenarioSelectId = React.useId();
  const schemeSelectId = React.useId();

  const initFor = React.useCallback(
    (nextScenario: PrototypeScenario, nextScheme: PrototypeScheme): EditorState =>
      initEditorState(
        remapAccess(
          nextScenario.access ?? {
            version: 0,
            assignments: [],
            caller: {
              granteeId: null,
              name: "",
              effectiveRole: nextScheme.roles[0].key,
              capabilities: [],
              sources: [],
            },
          },
          schemeByKey(nextScenario.scheme),
          nextScheme,
        ),
      ),
    [],
  );

  const [editor, dispatch] = React.useReducer(editorReducer, initFor(scenario, scheme));

  const resetTo = (nextScenarioKey: string, nextSchemeKey: string) => {
    const nextScenario = scenarioByKey(nextScenarioKey);
    const nextScheme = schemeByKey(nextSchemeKey);
    setScenarioKey(nextScenario.key);
    setSchemeKey(nextScheme.key);
    dispatch({ type: "reset", state: initFor(nextScenario, nextScheme) });
  };

  const ctx: ShellContext = {
    scheme,
    state: editor,
    inherited: remapInherited(scenario.inherited ?? {}, scenarioScheme, scheme),
    ceiling:
      editor.saved.caller.capabilities.length === 0
        ? 0
        : assignableRankCeiling(scheme, editor.saved.caller.effectiveRole),
    dispatch,
    conflictOnSave: scenario.conflictOnSave === true,
    requestFailureOnSave: scenario.requestFailureOnSave === true,
    narrow,
  };
  // Callers whose role sits below the second rank cannot assign anything.
  if (
    ctx.ceiling > 0 &&
    !editor.saved.caller.capabilities.some(
      (capability) => capability === "manage-all-roles" || capability === "manage-lower-roles",
    )
  ) {
    ctx.ceiling = 0;
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <section aria-label="Prototype controls" className="border-b bg-muted/30">
        <div className="mx-auto flex max-w-5xl flex-wrap items-end gap-4 px-4 py-3">
          <div className="space-y-1">
            <Label htmlFor={scenarioSelectId}>Scenario</Label>
            <select
              id={scenarioSelectId}
              value={scenarioKey}
              onChange={(event) => resetTo(event.target.value, scenarioByKey(event.target.value).scheme)}
              className="block h-8 max-w-64 rounded-sm border border-input bg-background px-2 text-sm"
            >
              {SCENARIOS.map((entry) => (
                <option key={entry.key} value={entry.key}>
                  {entry.label}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor={schemeSelectId}>Role scheme</Label>
            <select
              id={schemeSelectId}
              value={schemeKey}
              onChange={(event) => resetTo(scenarioKey, event.target.value)}
              className="block h-8 w-full max-w-64 rounded-sm border border-input bg-background px-2 text-sm"
            >
              {SCHEMES.map((entry) => (
                <option key={entry.key} value={entry.key}>
                  {entry.resourceNoun} ({entry.roles.map((role) => role.label).join(", ")})
                </option>
              ))}
            </select>
          </div>
          <Button type="button" size="sm" variant="outline" onClick={() => resetTo(scenarioKey, schemeKey)}>
            <RotateCcwIcon aria-hidden="true" /> Reset scenario
          </Button>
          <Label className="flex items-center gap-2 pb-1 text-sm font-normal">
            <Checkbox checked={narrow} onCheckedChange={(checked) => setNarrow(checked === true)} />
            Constrain page to 320 px (use the viewport toolbar to test the Access tab)
          </Label>
        </div>
        <p className="mx-auto max-w-5xl px-4 pb-3 text-sm text-muted-foreground">{scenario.brief}</p>
      </section>

      <div className={cn(narrow && "max-w-[320px] border-r")}>
        {scenario.kind === "editor" ? (
          <ItemPageShell key={`${scenarioKey}:${schemeKey}`} ctx={ctx} />
        ) : scenario.key === "global-default" ? (
          <GlobalDefaultScene />
        ) : scenario.key === "all-items" ? (
          <AllItemsScene />
        ) : scenario.key === "ownership-transfer" ? (
          <OwnershipTransferScene />
        ) : scenario.key === "owner-repair" ? (
          <OwnerRepairScene />
        ) : (
          <DepartedScene />
        )}
      </div>

      <DebugPanel scenario={scenario} scheme={scheme} editor={scenario.kind === "editor" ? editor : null} />
    </div>
  );
}

const meta = {
  title: "Booking/Prototypes/Resource Access Editor",
  component: ResourceAccessPrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof ResourceAccessPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

const runAcceptance: NonNullable<Story["play"]> = async ({ canvasElement, step }) => {
  const body = within(canvasElement.ownerDocument.body);
  // Dialog/combobox primitives portal into body, and this fullscreen story's decorator also
  // mounts outside Storybook's otherwise-empty canvas wrapper. One semantic root avoids mixing
  // canvas and portal queries.
  const canvas = body;
  const scenario = await canvas.findByRole("combobox", { name: "Scenario" });
  const accessTab = () => canvas.getByRole("tab", { name: "Access" });
  const chooseScenario = async (key: string) => {
    await userEvent.selectOptions(scenario, key);
    await waitFor(() => expect(scenario).toHaveValue(key));
  };
  const openEditor = async () => {
    const trigger = accessTab();
    trigger.focus();
    await userEvent.keyboard("{Enter}");
    const panel = await body.findByRole("tabpanel", { name: "Access" });
    expect(trigger).toHaveAttribute("aria-selected", "true");
    return { panel, trigger };
  };
  const tabTo = async (target: HTMLElement, limit = 80) => {
    for (let index = 0; index < limit && canvasElement.ownerDocument.activeElement !== target; index += 1) {
      await userEvent.tab();
    }
    expect(canvasElement.ownerDocument.activeElement).toBe(target);
  };
  const chooseRoleByKeyboard = async (trigger: HTMLElement, roleName: string) => {
    await tabTo(trigger);
    await userEvent.keyboard("{Enter}");
    const option = await body.findByRole("menuitem", { name: roleName });
    await userEvent.keyboard(roleName[0].toLowerCase());
    await waitFor(() => expect(canvasElement.ownerDocument.activeElement).toBe(option));
    await userEvent.keyboard("{Enter}");
  };

  await step("keyboard tab entry, add, role change, remove, save, and cancel", async () => {
    await chooseScenario("add-change");
    let { panel, trigger } = await openEditor();
    let editorView = within(panel);
    const finder = editorView.getByRole("combobox", { name: "Add user or group" });
    await tabTo(finder);
    await userEvent.type(finder, "Mary{ArrowDown}{Enter}");
    const maryRole = await editorView.findByRole("button", { name: "Role for Mary Anning" });
    await chooseRoleByKeyboard(maryRole, "Booker");
    await waitFor(() => expect(maryRole).toHaveTextContent("Booker"));

    const graceRole = editorView.getByRole("button", { name: "Role for Grace Hopper" });
    await chooseRoleByKeyboard(graceRole, "Booker");
    await waitFor(() => expect(graceRole).toHaveTextContent("Booker"));

    const removeImaging = editorView.getByRole("button", { name: "Remove Imaging Lab" });
    await tabTo(removeImaging);
    await userEvent.keyboard("{Enter}");
    expect(await editorView.findByText("Unsaved changes")).toBeInTheDocument();
    expect(editorView.getByRole("status")).toHaveTextContent("assignment removed");

    const save = editorView.getByRole("button", { name: "Save changes" });
    await tabTo(save);
    await userEvent.keyboard("{Enter}");
    const savedStatus = editorView.getByRole("status");
    await waitFor(() => expect(savedStatus).toHaveTextContent("Saved. Access is now version 13."));
    expect(canvasElement.ownerDocument.activeElement).toBe(savedStatus);

    trigger.focus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(canvas.getByRole("tab", { name: "Audit log" })).toHaveAttribute("aria-selected", "true");
    expect(canvas.queryByRole("tabpanel", { name: "Access" })).not.toBeInTheDocument();
    await userEvent.keyboard("{ArrowRight}");
    expect(trigger).toHaveAttribute("aria-selected", "true");
    panel = canvas.getByRole("tabpanel", { name: "Access" });
    editorView = within(panel);
    const removeGrace = editorView.getByRole("button", { name: "Remove Grace Hopper" });
    await tabTo(removeGrace);
    await userEvent.keyboard("{Enter}{Escape}");
    expect(editorView.getByRole("button", { name: "Restore Grace Hopper" })).toBeInTheDocument();
    const cancel = editorView.getByRole("button", { name: "Cancel" });
    await tabTo(cancel);
    await userEvent.keyboard("{Enter}");
    expect(editorView.getByRole("status")).toHaveTextContent("Changes discarded");
    expect(canvasElement.ownerDocument.activeElement).toBe(cancel);
  });

  await step("role constraints, validation announcement, and 320 px reflow", async () => {
    await chooseScenario("owner-mixed");
    const narrow = canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ });
    await userEvent.click(narrow);
    let { panel } = await openEditor();
    expect(panel.getBoundingClientRect().width).toBeLessThanOrEqual(320);
    await waitFor(() => expect(panel.scrollWidth).toBeLessThanOrEqual(panel.clientWidth));
    const allUsersRole = within(panel).getByRole("button", { name: "Role for All users" });
    await userEvent.click(allUsersRole);
    expect(await body.findByRole("menuitem", { name: "Booker" })).toBeInTheDocument();
    expect(body.queryByRole("menuitem", { name: "Owner" })).not.toBeInTheDocument();
    expect(body.queryByRole("menuitem", { name: "Manager" })).not.toBeInTheDocument();
    await userEvent.keyboard("{Escape}{Escape}");

    await chooseScenario("last-owner");
    ({ panel } = await openEditor());
    const lastOwnerRole = within(panel).getByRole("button", { name: "Role for Microscopy Core" });
    expect(lastOwnerRole).toBeDisabled();
    const blockedRemove = within(panel).getByRole("button", { name: "Remove Microscopy Core" });
    await userEvent.click(blockedRemove);
    expect(within(panel).getByRole("status")).toHaveTextContent("Change blocked: Add another Owner first.");
    await userEvent.keyboard("{Escape}");
  });

  await step("request failure and stale-version recovery preserve the draft", async () => {
    await chooseScenario("request-failure");
    let { panel } = await openEditor();
    let editor = within(panel);
    await userEvent.click(editor.getByRole("button", { name: "Remove Ada Lovelace" }));
    await userEvent.click(editor.getByRole("button", { name: "Save changes" }));
    expect(await editor.findByRole("alert")).toHaveTextContent("Save failed");
    expect(editor.getByRole("button", { name: "Restore Ada Lovelace" })).toBeInTheDocument();
    await userEvent.click(editor.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(editor.getByRole("status")).toHaveTextContent("version 19"));

    await chooseScenario("conflict");
    ({ panel } = await openEditor());
    editor = within(panel);
    await userEvent.click(editor.getByRole("button", { name: "Remove Ada Lovelace" }));
    await userEvent.click(editor.getByRole("button", { name: "Save changes" }));
    expect(await editor.findByText("Access changed elsewhere while you were editing")).toBeInTheDocument();
    expect(editor.getByRole("button", { name: "Restore Ada Lovelace" })).toBeInTheDocument();
    await userEvent.click(editor.getByRole("button", { name: "Review latest and retry" }));
    await userEvent.click(editor.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(editor.getByRole("status")).toHaveTextContent("Saved. Access is now version"));
  });

  await step("generic scheme and every surrounding permission flow", async () => {
    await chooseScenario("second-scheme");
    let { panel } = await openEditor();
    const archiveEditor = within(panel);
    expect(archiveEditor.getByRole("button", { name: "Role for Mary Anning" })).toHaveTextContent("Contributor");
    expect(archiveEditor.queryByText("Booker")).not.toBeInTheDocument();
    expect(archiveEditor.queryByText("Viewer")).not.toBeInTheDocument();
    await userEvent.keyboard("{Escape}");

    await chooseScenario("global-default");
    expect(canvas.getByRole("radio", { name: /All users/ })).toBeChecked();
    await userEvent.click(canvas.getByRole("radio", { name: /Selected users and groups/ }));
    expect(canvas.getByText(/These grantees receive Booker/)).toBeInTheDocument();

    await chooseScenario("all-items");
    expect(canvas.getByRole("button", { name: "Book" })).toBeInTheDocument();
    expect(canvas.getAllByRole("button", { name: /Access for/ })).toHaveLength(2);
    expect(canvas.queryByRole("button", { name: /bulk.*access/i })).not.toBeInTheDocument();

    await chooseScenario("ownership-transfer");
    expect(canvas.getByRole("checkbox", { name: /Also transfer Booking configuration ownership/ })).toBeChecked();
    expect(canvas.getByText("Each item transfers atomically")).toBeInTheDocument();

    await chooseScenario("role-lost");
    expect(canvas.getAllByText("Read-only")).toHaveLength(2);
    await userEvent.click(canvas.getByRole("radio", { name: /Assignment removed by an Owner/ }));
    expect(canvas.getByText(/An Owner removed your final role/)).toBeInTheDocument();
    expect(canvas.queryByRole("button", { name: /Edit|Cancel booking/ })).not.toBeInTheDocument();

    await chooseScenario("owner-repair");
    expect(canvas.getByText("No effective Owner")).toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Repair access" }));
    const dialog = await body.findByRole("dialog");
    expect(within(dialog).getByText(/implicit sysadmin access will not be saved/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save Owner assignment" }));
    expect(await canvas.findByRole("status")).toHaveTextContent("Mary Anning is now a persisted effective Owner");

    // Leave the selected Access tab active at 320 px so Storybook's post-play axe scan covers
    // the editor and narrow card reflow rather than only a surrounding scene.
    await chooseScenario("owner-mixed");
    ({ panel } = await openEditor());
    expect(panel.getBoundingClientRect().width).toBeLessThanOrEqual(320);
  });
};

export const Prototype: Story = {
  // Keep the human review canvas pristine. Vitest builds Storybook in test mode and runs the
  // exhaustive play function; development and static-review builds render only the prototype.
  play: import.meta.env.MODE === "test" ? runAcceptance : undefined,
};
