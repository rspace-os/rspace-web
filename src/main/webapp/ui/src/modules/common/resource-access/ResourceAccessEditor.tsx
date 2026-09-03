import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { TriangleAlertIcon } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/modules/common/ui/alert-dialog";
import { Button } from "@/modules/common/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { TooltipProvider } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import { GranteeIdentity, RoleMenu, RowActions } from "./AccessRowParts";
import {
  type AccessMergeConflict,
  type AccessRow,
  buildAccessRows,
  callerKeyOf,
  draftWithout,
  draftWithRestored,
  draftWithRole,
  mergeAccessDraft,
} from "./accessRows";
import { GranteePicker } from "./GranteePicker";
import {
  fetchResourceAccess,
  leaveResource,
  ResourceAccessRequestError,
  replaceResourceAccess,
} from "./resourceAccess";
import type { GranteeKind, ResourceAccessAssignment, ResourceGranteeDirectoryEntry } from "./schemas";

export type ResourceAccessRole = {
  key: string;
  label: string;
  description: string;
  allowedGranteeKinds: readonly GranteeKind[];
};

export type ResourceAccessAdapter = {
  roles: readonly ResourceAccessRole[];
  ownerRole: string;
  defaultRole: string;
  allUsersRole: string;
  allUsersLabel: string;
  leaveLabel: string;
};

type Props = {
  resource: string;
  resourceId: number;
  token: string;
  adapter: ResourceAccessAdapter;
  onLeave?: () => void;
  readOnly?: boolean;
};

function sameDraft(left: readonly ResourceAccessAssignment[], right: readonly ResourceAccessAssignment[]) {
  const rightByKey = new Map(right.map((assignment) => [assignment.grantee.key, assignment.role]));
  return (
    left.length === right.length &&
    left.every((assignment) => rightByKey.get(assignment.grantee.key) === assignment.role)
  );
}

export function ResourceAccessEditor({ resource, resourceId, token, adapter, onLeave, readOnly = false }: Props) {
  const { t, i18n } = useTranslation("common");
  const queryClient = useQueryClient();
  const queryKey = ["api-v2", resource, resourceId, "access"] as const;
  const access = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchResourceAccess(resource, resourceId, token, signal),
  });
  const [draft, setDraft] = useState<readonly ResourceAccessAssignment[]>([]);
  const [conflicts, setConflicts] = useState<readonly AccessMergeConflict[]>([]);
  const [recoveringConflict, setRecoveringConflict] = useState(false);
  const [conflictRefreshError, setConflictRefreshError] = useState(false);
  const [assignmentSearch, setAssignmentSearch] = useState("");
  const [announcement, setAnnouncement] = useState("");
  const [leaveOpen, setLeaveOpen] = useState(false);
  const statusRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (access.data && !recoveringConflict) setDraft(access.data.assignments);
  }, [access.data, recoveringConflict]);

  const dirty = !readOnly && access.data ? !sameDraft(draft, access.data.assignments) : false;
  const ownerCount = draft.filter(({ role }) => role === adapter.ownerRole).length;
  const ownerLabel = adapter.roles.find(({ key }) => key === adapter.ownerRole)?.label ?? adapter.ownerRole;

  const refreshAfterConflict = async (base = access.data?.assignments ?? [], local = draft) => {
    setRecoveringConflict(true);
    setConflictRefreshError(false);
    const latest = await access.refetch();
    if (latest.isSuccess && latest.data) {
      const merged = mergeAccessDraft(base, local, latest.data.assignments);
      setDraft(merged.draft);
      setConflicts(merged.conflicts);
      setAnnouncement(
        merged.conflicts.length > 0
          ? t("resourceAccess.conflictCount", { count: merged.conflicts.length })
          : t("resourceAccess.conflictMerged"),
      );
    } else {
      setConflictRefreshError(true);
      setAnnouncement(t("resourceAccess.conflictRefreshError"));
    }
  };

  const save = useMutation({
    mutationFn: () =>
      replaceResourceAccess(
        resource,
        resourceId,
        access.data?.version ?? -1,
        draft.map(({ grantee, role }) => ({ granteeKey: grantee.key, role })),
        token,
      ),
    onSuccess: (document) => {
      queryClient.setQueryData(queryKey, document);
      setDraft(document.assignments);
      setConflicts([]);
      setRecoveringConflict(false);
      setAnnouncement(t("resourceAccess.saved"));
      requestAnimationFrame(() => statusRef.current?.focus());
    },
    onError: async (error) => {
      if (error instanceof ResourceAccessRequestError && error.status === 412) {
        await refreshAfterConflict();
      } else if (
        error instanceof ResourceAccessRequestError &&
        error.code === "errors.api.v2.resourceAccess.assignmentLimit"
      ) {
        setAnnouncement(t("resourceAccess.assignmentLimitError"));
      } else {
        setAnnouncement(t("resourceAccess.saveError"));
      }
    },
  });

  const leave = useMutation({
    mutationFn: () => leaveResource(resource, resourceId, token),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["api-v2", resource] });
      onLeave?.();
    },
    onError: () => setAnnouncement(t("resourceAccess.leaveError")),
  });

  useEffect(() => {
    if (!dirty || !save.isSuccess) return;
    save.reset();
    setAnnouncement(t("resourceAccess.unsavedChanges"));
  }, [dirty, save.isSuccess, save.reset, t]);

  const assignedKeys = useMemo(() => new Set(draft.map(({ grantee }) => grantee.key)), [draft]);
  const namedAssignmentCount = draft.filter(({ grantee }) => grantee.kind !== "AUDIENCE").length;
  const canManage = !readOnly && access.data?.caller.capabilities.canManageAssignments === true;

  const add = (grantee: ResourceGranteeDirectoryEntry, roleKey = adapter.defaultRole) => {
    if (grantee.kind !== "AUDIENCE" && namedAssignmentCount >= 100) {
      setAnnouncement(t("resourceAccess.assignmentLimitError"));
      return;
    }
    const role = adapter.roles.find(
      ({ key, allowedGranteeKinds }) => key === roleKey && allowedGranteeKinds.includes(grantee.kind),
    );
    if (!role) return;
    setDraft((current) => [
      ...current,
      { grantee: { ...grantee, available: true, effectiveRole: null, roleSources: [] }, role: role.key },
    ]);
    setAnnouncement(t("resourceAccess.stagedAdd", { name: grantee.name }));
  };

  if (access.isPending) return <p role="status">{t("resourceAccess.loading")}</p>;
  if (access.isError || !access.data) return <p role="alert">{t("resourceAccess.loadError")}</p>;

  const saved = access.data;
  const rows = buildAccessRows(saved.assignments, draft, callerKeyOf(saved), i18n.resolvedLanguage);
  const normalizedSearch = assignmentSearch.trim().toLocaleLowerCase(i18n.resolvedLanguage);
  const visibleRows = normalizedSearch
    ? rows.filter(({ assignment }) =>
        [assignment.grantee.name, assignment.grantee.detail, assignment.grantee.key].some((value) =>
          value?.toLocaleLowerCase(i18n.resolvedLanguage).includes(normalizedSearch),
        ),
      )
    : rows;
  const invariantBroken = ownerCount === 0;

  const stagedLabelFor = (row: AccessRow): string | null => {
    const roleLabel = (key: string) => adapter.roles.find((role) => role.key === key)?.label ?? key;
    if (row.status === "added") return t("resourceAccess.staged.added", { role: roleLabel(row.assignment.role) });
    if (row.status === "changed")
      return t("resourceAccess.staged.changed", {
        from: roleLabel(row.fromRole ?? row.assignment.role),
        to: roleLabel(row.assignment.role),
      });
    if (row.status === "removed") return t("resourceAccess.staged.removed");
    return null;
  };

  /** Why this row's role cannot be changed, or null when it can. Mirrors the previous `mutable` rule. */
  const manageBlockedReason = (row: AccessRow): string | null => {
    if (!canManage) return t("resourceAccess.viewOnly");
    if (row.assignment.role === adapter.ownerRole) {
      if (!saved.caller.capabilities.canManageOwners) return t("resourceAccess.ownerOnly", { role: ownerLabel });
      if (ownerCount === 1 && row.status !== "removed") return t("resourceAccess.lastOwner", { role: ownerLabel });
    }
    return null;
  };

  /**
   * Why this row's action is blocked. Leaving is its own server-computed capability: a caller may
   * leave a resource they cannot otherwise administer, and the server already accounts for the
   * scheme's required roles, so it is not gated on managing assignments.
   */
  const actionBlockedReason = (row: AccessRow): string | null => {
    if (row.isSelf) {
      return saved.caller.capabilities.canLeave ? null : t("resourceAccess.lastOwner", { role: ownerLabel });
    }
    return manageBlockedReason(row);
  };

  const rolesFor = (row: AccessRow) =>
    adapter.roles.filter(
      ({ key, allowedGranteeKinds }) =>
        allowedGranteeKinds.includes(row.assignment.grantee.kind) &&
        (key !== adapter.ownerRole || saved.caller.capabilities.canManageOwners),
    );

  const resolveConflict = (item: AccessMergeConflict, choice: "local" | "latest") => {
    const assignment = choice === "local" ? item.local : item.latest;
    setDraft((current) => {
      const without = current.filter(({ grantee }) => grantee.key !== item.key);
      return assignment ? [...without, assignment] : without;
    });
    setConflicts((current) => current.filter(({ key }) => key !== item.key));
  };

  const roleMenuFor = (row: AccessRow) => {
    if (readOnly) {
      return <span>{adapter.roles.find(({ key }) => key === row.assignment.role)?.label ?? row.assignment.role}</span>;
    }
    return (
      <RoleMenu
        row={row}
        roles={rolesFor(row)}
        // A row staged for removal keeps its role visible but frozen until it is restored.
        disabled={row.status === "removed" || !row.assignment.grantee.available}
        lockReason={manageBlockedReason(row)}
        onChange={(role) => setDraft((current) => draftWithRole(current, row.key, role))}
      />
    );
  };

  const actionsFor = (row: AccessRow) =>
    readOnly ? null : (
      <RowActions
        row={row}
        leaveLabel={adapter.leaveLabel}
        blockedReason={actionBlockedReason(row)}
        onRemove={() => setDraft((current) => draftWithout(current, row.key))}
        onRestore={() => setDraft((current) => draftWithRestored(saved.assignments, current, row.assignment))}
        onLeave={() => setLeaveOpen(true)}
      />
    );

  return (
    <TooltipProvider>
      <div className="min-w-0 space-y-4 overflow-x-clip">
        <DirtyNavigationGuard dirty={dirty} />
        {canManage ? (
          <div className="flex flex-wrap items-end gap-2">
            <div className="min-w-56 flex-1">
              <GranteePicker
                resource={resource}
                resourceId={resourceId}
                token={token}
                assignedKeys={assignedKeys}
                disabled={namedAssignmentCount >= 100}
                onPick={(grantee) => add(grantee)}
              />
            </div>
            <p className="text-sm text-muted-foreground">
              {t("resourceAccess.assignmentCount", { count: namedAssignmentCount, limit: 100 })}
            </p>
          </div>
        ) : null}

        <label className="block max-w-sm space-y-1 text-sm">
          <span>{t("resourceAccess.searchAssigned")}</span>
          <input
            type="search"
            className="h-9 w-full rounded-sm border bg-background px-3"
            value={assignmentSearch}
            onChange={(event) => setAssignmentSearch(event.currentTarget.value)}
          />
        </label>

        {/* Wide screens: one table ordered by grantee. */}
        <div className="max-sm:hidden">
          <Table aria-label={t("resourceAccess.assignments")}>
            <TableHeader>
              <TableRow>
                <TableHead>{t("resourceAccess.userOrGroup")}</TableHead>
                <TableHead>{t("resourceAccess.directRole")}</TableHead>
                <TableHead>
                  <span className="sr-only">{t("resourceAccess.actions")}</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visibleRows.map((row) => (
                <TableRow key={row.key} className={cn(row.status === "removed" && "opacity-60")}>
                  <TableCell className="align-middle">
                    <GranteeIdentity row={row} stagedLabel={stagedLabelFor(row)} />
                  </TableCell>
                  <TableCell className="align-middle">{roleMenuFor(row)}</TableCell>
                  <TableCell className="align-middle text-right">{actionsFor(row)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>

        {/* Narrow screens: the same rows as cards, identity then controls, no horizontal scrolling. */}
        <ul aria-label={t("resourceAccess.assignments")} className="space-y-2 sm:hidden">
          {visibleRows.map((row) => (
            <li key={row.key} className={cn("rounded-sm border p-3", row.status === "removed" && "opacity-60")}>
              <GranteeIdentity row={row} stagedLabel={stagedLabelFor(row)} />
              <div className="mt-2 flex flex-wrap items-center gap-2">
                {roleMenuFor(row)}
                {actionsFor(row)}
              </div>
            </li>
          ))}
        </ul>

        {invariantBroken ? (
          <Alert variant="destructive" className="rounded-sm">
            <TriangleAlertIcon aria-hidden="true" />
            <AlertTitle>{t("resourceAccess.ownerInvariant", { role: ownerLabel })}</AlertTitle>
          </Alert>
        ) : null}

        {recoveringConflict && (access.isFetching || conflictRefreshError || conflicts.length > 0) ? (
          <Alert variant="destructive" className="rounded-sm">
            <AlertTitle>{t("resourceAccess.conflictTitle")}</AlertTitle>
            <AlertDescription className="space-y-2">
              {access.isFetching ? <p role="status">{t("resourceAccess.refreshingLatest")}</p> : null}
              {conflictRefreshError ? (
                <Button type="button" variant="outline" onClick={() => void refreshAfterConflict()}>
                  {t("resourceAccess.retryLatest")}
                </Button>
              ) : null}
              {conflicts.map((item) => (
                <div key={item.key} className="rounded-sm border p-3">
                  <p className="font-medium">{item.local?.grantee.name ?? item.latest?.grantee.name ?? item.key}</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <Button type="button" variant="outline" onClick={() => resolveConflict(item, "local")}>
                      {t("resourceAccess.keepMine")}
                    </Button>
                    <Button type="button" variant="outline" onClick={() => resolveConflict(item, "latest")}>
                      {t("resourceAccess.useLatest")}
                    </Button>
                  </div>
                </div>
              ))}
            </AlertDescription>
          </Alert>
        ) : null}

        {canManage ? (
          <div className="flex flex-wrap items-center justify-end gap-2">
            {/* Not a live region: the user just made the change, and the row dots already mark it. */}
            {dirty ? (
              <p className="mr-auto flex items-center gap-1.5 text-xs text-amber-700 dark:text-amber-400">
                <TriangleAlertIcon className="size-3.5 shrink-0" aria-hidden="true" />
                {t("resourceAccess.unsavedChanges")}
              </p>
            ) : null}
            <Button
              type="button"
              variant="outline"
              className="rounded-sm"
              disabled={!dirty || save.isPending}
              onClick={() => {
                setDraft(saved.assignments);
                setConflicts([]);
                setRecoveringConflict(false);
                setConflictRefreshError(false);
                setAnnouncement(t("resourceAccess.cancelled"));
              }}
            >
              {t("actions.cancel")}
            </Button>
            <Button
              type="button"
              className="rounded-sm"
              aria-busy={save.isPending}
              disabled={!dirty || save.isPending || invariantBroken || conflicts.length > 0 || conflictRefreshError}
              onClick={() => save.mutate()}
            >
              {t("resourceAccess.saveChanges")}
            </Button>
          </div>
        ) : null}

        <p
          ref={statusRef}
          role="status"
          aria-live="polite"
          tabIndex={-1}
          className="text-sm text-muted-foreground outline-none"
        >
          {save.isPending ? t("resourceAccess.saving") : announcement}
        </p>

        <AlertDialog open={leaveOpen} onOpenChange={(open) => !leave.isPending && setLeaveOpen(open)}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{adapter.leaveLabel}</AlertDialogTitle>
              <AlertDialogDescription>{t("resourceAccess.leaveConfirm")}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel disabled={leave.isPending}>{t("actions.cancel")}</AlertDialogCancel>
              <AlertDialogAction variant="destructive" disabled={leave.isPending} onClick={() => leave.mutate()}>
                {adapter.leaveLabel}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </TooltipProvider>
  );
}
