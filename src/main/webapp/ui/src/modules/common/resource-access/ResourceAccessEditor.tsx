import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import {
  fetchResourceAccess,
  leaveResource,
  ResourceAccessRequestError,
  replaceResourceAccess,
  searchResourceGrantees,
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
};

function sameDraft(left: readonly ResourceAccessAssignment[], right: readonly ResourceAccessAssignment[]) {
  return (
    left.length === right.length &&
    left.every((assignment, index) => {
      const other = right[index];
      return other?.grantee.key === assignment.grantee.key && other.role === assignment.role;
    })
  );
}

export function ResourceAccessEditor({ resource, resourceId, token, adapter, onLeave }: Props) {
  const { t } = useTranslation("common");
  const queryClient = useQueryClient();
  const queryKey = ["api-v2", resource, resourceId, "access"] as const;
  const access = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchResourceAccess(resource, resourceId, token, signal),
  });
  const [draft, setDraft] = useState<readonly ResourceAccessAssignment[]>([]);
  const [searchText, setSearchText] = useState("");
  const [submittedSearch, setSubmittedSearch] = useState("");
  const [conflict, setConflict] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const statusRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (access.data && !conflict) setDraft(access.data.assignments);
  }, [access.data, conflict]);

  const directory = useQuery({
    queryKey: ["api-v2", resource, resourceId, "access-grantees", submittedSearch],
    queryFn: ({ signal }) => searchResourceGrantees(resource, resourceId, submittedSearch, token, signal),
    enabled: submittedSearch.length >= 2,
  });
  const dirty = access.data ? !sameDraft(draft, access.data.assignments) : false;
  const ownerCount = draft.filter(({ role }) => role === adapter.ownerRole).length;

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
      setConflict(false);
      setAnnouncement(t("resourceAccess.saved"));
      requestAnimationFrame(() => statusRef.current?.focus());
    },
    onError: async (error) => {
      if (error instanceof ResourceAccessRequestError && error.status === 412) {
        setConflict(true);
        await access.refetch();
        setAnnouncement(t("resourceAccess.conflict"));
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

  const assignedKeys = useMemo(() => new Set(draft.map(({ grantee }) => grantee.key)), [draft]);
  const canManage = access.data?.caller.capabilities.canManageAssignments === true;
  const availableResults = directory.data?.filter(({ key }) => !assignedKeys.has(key)) ?? [];

  const add = (grantee: ResourceGranteeDirectoryEntry, roleKey = adapter.defaultRole) => {
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

  const allUsersAssigned = assignedKeys.has("audience:all-users");

  return (
    <div className="min-w-0 space-y-5 overflow-x-clip">
      {canManage ? (
        <form
          className="space-y-2"
          onSubmit={(event) => {
            event.preventDefault();
            setSubmittedSearch(searchText.trim());
          }}
        >
          <label htmlFor={`resource-access-search-${resourceId}`} className="text-sm font-medium">
            {t("resourceAccess.addUserOrGroup")}
          </label>
          <div className="flex flex-wrap gap-2">
            <Input
              id={`resource-access-search-${resourceId}`}
              value={searchText}
              onChange={(event) => setSearchText(event.currentTarget.value)}
              minLength={2}
              className="min-w-48 flex-1"
            />
            <Button type="submit" variant="outline" disabled={searchText.trim().length < 2 || directory.isFetching}>
              {t("resourceAccess.search")}
            </Button>
            {!allUsersAssigned ? (
              <Button
                type="button"
                variant="outline"
                onClick={() =>
                  add(
                    {
                      kind: "AUDIENCE",
                      id: 0,
                      key: "audience:all-users",
                      name: adapter.allUsersLabel,
                      detail: null,
                    },
                    adapter.allUsersRole,
                  )
                }
              >
                {t("resourceAccess.addNamed", { name: adapter.allUsersLabel })}
              </Button>
            ) : null}
          </div>
          {directory.isError ? <p role="alert">{t("resourceAccess.searchError")}</p> : null}
          {availableResults.length > 0 ? (
            <ul aria-label={t("resourceAccess.searchResults")} className="grid gap-2 rounded-2xl border p-2">
              {availableResults.map((grantee) => (
                <li key={grantee.key} className="flex flex-wrap items-center justify-between gap-2">
                  <span className="min-w-0">
                    <span className="block font-medium">{grantee.name}</span>
                    {grantee.detail ? (
                      <span className="block text-sm text-muted-foreground">{grantee.detail}</span>
                    ) : null}
                  </span>
                  <Button type="button" size="sm" variant="outline" onClick={() => add(grantee)}>
                    {t("resourceAccess.addNamed", { name: grantee.name })}
                  </Button>
                </li>
              ))}
            </ul>
          ) : null}
        </form>
      ) : null}

      <ul aria-label={t("resourceAccess.assignments")} className="grid gap-3">
        {draft.map((assignment) => {
          const isOwner = assignment.role === adapter.ownerRole;
          const ownerLocked = isOwner && (!access.data.caller.capabilities.canManageOwners || ownerCount === 1);
          const mutable = canManage && assignment.grantee.available && !ownerLocked;
          const roles = adapter.roles.filter(({ allowedGranteeKinds }) =>
            allowedGranteeKinds.includes(assignment.grantee.kind),
          );
          return (
            <li
              key={assignment.grantee.key}
              className="grid gap-3 rounded-2xl border p-4 sm:grid-cols-[1fr_minmax(10rem,14rem)_auto] sm:items-center"
            >
              <div className="min-w-0">
                <span className="block font-medium">{assignment.grantee.name}</span>
                {assignment.grantee.detail ? (
                  <span className="block text-sm text-muted-foreground">{assignment.grantee.detail}</span>
                ) : null}
                {!assignment.grantee.available ? (
                  <Badge variant="destructive">{t("resourceAccess.unavailable")}</Badge>
                ) : null}
              </div>
              <label className="grid gap-1 text-sm">
                <span>{t("resourceAccess.directRole")}</span>
                <select
                  className="h-9 rounded-3xl bg-input/50 px-3 outline-none focus-visible:ring-3 focus-visible:ring-ring/30 forced-colors:border"
                  value={assignment.role}
                  disabled={!mutable}
                  aria-label={t("resourceAccess.roleFor", { name: assignment.grantee.name })}
                  onChange={(event) =>
                    setDraft((current) =>
                      current.map((row) =>
                        row.grantee.key === assignment.grantee.key ? { ...row, role: event.target.value } : row,
                      ),
                    )
                  }
                >
                  {roles.map((role) => (
                    <option key={role.key} value={role.key}>
                      {role.label}
                    </option>
                  ))}
                </select>
              </label>
              <Button
                type="button"
                variant="ghost"
                disabled={!canManage || ownerLocked}
                onClick={() =>
                  setDraft((current) => current.filter((row) => row.grantee.key !== assignment.grantee.key))
                }
              >
                {t("resourceAccess.removeNamed", { name: assignment.grantee.name })}
              </Button>
            </li>
          );
        })}
      </ul>

      {conflict ? (
        <Alert variant="destructive">
          <AlertTitle>{t("resourceAccess.conflictTitle")}</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>{t("resourceAccess.conflict")}</p>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                if (access.data) setDraft(access.data.assignments);
                setConflict(false);
              }}
            >
              {t("resourceAccess.reviewLatest")}
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {canManage ? (
          <>
            <Button type="button" disabled={!dirty || save.isPending || conflict} onClick={() => save.mutate()}>
              {t("resourceAccess.saveChanges")}
            </Button>
            <Button
              type="button"
              variant="ghost"
              disabled={!dirty || save.isPending}
              onClick={() => {
                setDraft(access.data.assignments);
                setConflict(false);
                setAnnouncement(t("resourceAccess.cancelled"));
              }}
            >
              {t("actions.cancel")}
            </Button>
          </>
        ) : null}
        {access.data.caller.capabilities.canLeave ? (
          <Button type="button" variant="destructive" disabled={leave.isPending} onClick={() => leave.mutate()}>
            {adapter.leaveLabel}
          </Button>
        ) : null}
      </div>
      <p
        ref={statusRef}
        role="status"
        aria-live="polite"
        tabIndex={-1}
        className="text-sm text-muted-foreground outline-none"
      >
        {save.isPending ? t("resourceAccess.saving") : dirty ? t("resourceAccess.unsaved") : announcement}
      </p>
    </div>
  );
}
