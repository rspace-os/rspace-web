import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArchiveIcon, EyeIcon, PlusIcon } from "lucide-react";
import { useCallback, useId, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { ApiV2ProblemError, parseApiV2Problem } from "@/modules/booking/domain/booking";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import {
  TableList,
  type TableListFilterButtons,
  type TableListRowActions,
  type TableListSelectionContext,
} from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
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
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import { cn } from "@/modules/common/utils/cn";
import {
  BookingConfigurationActionsMenu,
  type BookingConfigurationLifecycleAction,
} from "./BookingConfigurationActionsMenu";
import {
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "./bookingConfiguration";

const bookableItemsProjection = {
  fixed: [
    "id",
    "configurationVersion",
    "target",
    "enabled",
    "timezone",
    ...schedulingSettingsFieldNames,
    "updatedAt",
    "effectiveRole",
    "roleSources",
    "capabilities",
    "ownerHealth",
    "state",
  ],
} as const;

const maximumBookableItemsSelection = 1000;
const maximumOwnerAttentionCandidates = 1000;

const OwnerAttentionPageSchema = v.object({
  docs: v.array(
    v.object({
      id: v.number(),
      ownerHealth: v.optional(v.object({ hasEffectiveOwner: v.boolean() })),
    }),
  ),
  totalDocs: v.number(),
  totalPages: v.number(),
});

async function fetchOwnerAttentionPage(page: number, token: string, signal: AbortSignal) {
  const search = new URLSearchParams({
    page: String(page),
    limit: "100",
    "fields[booking-configurations]": "id,ownerHealth",
  });
  const response = await fetch(`/api/v2/booking-configurations?${search}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Owner-health request failed with status ${response.status}`);
  return v.parse(OwnerAttentionPageSchema, await response.json());
}

async function fetchOwnerAttentionIds(token: string, signal: AbortSignal): Promise<ReadonlySet<number>> {
  const first = await fetchOwnerAttentionPage(1, token, signal);
  if (first.totalDocs > maximumOwnerAttentionCandidates) {
    throw new Error(`Owner-health filtering supports at most ${maximumOwnerAttentionCandidates} bookable items`);
  }
  const documents = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    documents.push(...(await fetchOwnerAttentionPage(page, token, signal)).docs);
  }
  return new Set(documents.flatMap(({ id, ownerHealth }) => (ownerHealth?.hasEffectiveOwner === false ? [id] : [])));
}

export type BookableItemsBulkAction = "enable" | "disable" | "archive";

type BookableItemsBulkMutation = {
  action: BookableItemsBulkAction;
  selectedRowIds: readonly string[];
};

export async function mutateBookableItems(
  action: BookableItemsBulkAction,
  selectedRowIds: readonly string[],
  token: string,
): Promise<void> {
  if (selectedRowIds.length === 0) throw new Error("A bulk booking action requires at least one row ID");
  if (selectedRowIds.length > maximumBookableItemsSelection) {
    throw new Error(`A bulk booking action cannot contain more than ${maximumBookableItemsSelection} row IDs`);
  }

  const where = serializeRsqlExpression<BookingConfigurationRow>({
    kind: "comparison",
    field: "id",
    operator: "in",
    value: selectedRowIds,
  });
  const search = new URLSearchParams({ where });
  const isDelete = action === "archive";
  const response = await fetch(`/api/v2/booking-configurations?${search}`, {
    method: isDelete ? "DELETE" : "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(isDelete ? {} : { "Content-Type": "application/json" }),
      "X-Requested-With": "XMLHttpRequest",
    },
    ...(isDelete ? {} : { body: JSON.stringify({ enabled: action === "enable" }) }),
  });
  if (!response.ok) throw new Error(`Bulk booking ${action} failed with status ${response.status}`);
}

async function archiveBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
      "If-Match": `"${version}"`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

async function restoreBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "If-Match": `"${version}"`,
      "X-Requested-With": "XMLHttpRequest",
    },
    body: JSON.stringify({ state: "ACTIVE" }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

async function permanentlyDeleteBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}?permanent=true`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
      "If-Match": `"${version}"`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

type BookableItemsLifecycleErrorKey =
  | "bookableItems.lifecycleErrors.restore"
  | "bookableItems.lifecycleErrors.stale"
  | "bookableItems.lifecycleErrors.stateChanged"
  | "bookableItems.permanentDeleteDialog.error";

function lifecycleErrorKey(error: unknown, fallback: BookableItemsLifecycleErrorKey): BookableItemsLifecycleErrorKey {
  if (error instanceof ApiV2ProblemError && error.status === 412) {
    return "bookableItems.lifecycleErrors.stale";
  }
  if (error instanceof ApiV2ProblemError && error.status === 409) {
    return "bookableItems.lifecycleErrors.stateChanged";
  }
  return fallback;
}

function requiredVersion(configuration: BookingConfigurationRow): number {
  if (configuration.configurationVersion === undefined) {
    throw new Error("The booking configuration version is missing from the table projection");
  }
  return configuration.configurationVersion;
}

function BookableItemActionTriggers({
  configuration,
  activate,
  directSysadmin,
  onRestore,
}: {
  configuration: BookingConfigurationRow;
  activate: (actionId: string) => void;
  directSysadmin: boolean;
  onRestore: (configuration: BookingConfigurationRow) => Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  return (
    <div className="flex items-center justify-start gap-1">
      {configuration.target === null ? null : (
        <Link
          to="/booking/bookable-items/$globalId/{-$tab}"
          params={{ globalId: configuration.target.globalId, tab: undefined }}
          aria-label={t("bookableItems.actions.viewDetails", { item: itemName })}
          className={cn(buttonVariants({ variant: "ghost", size: "icon-sm" }), "rounded-sm")}
          data-slot="button"
        >
          <EyeIcon aria-hidden="true" />
        </Link>
      )}
      {configuration.state === "ACTIVE" && !directSysadmin ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="rounded-sm text-destructive"
          aria-label={t("bookableItems.actions.archive")}
          onClick={() => activate("archive")}
        >
          <ArchiveIcon aria-hidden="true" />
        </Button>
      ) : (
        <BookingConfigurationActionsMenu
          configuration={configuration}
          itemName={itemName}
          directSysadmin={directSysadmin}
          compact
          onAction={(action: BookingConfigurationLifecycleAction) => {
            if (action === "restore") void onRestore(configuration);
            else activate(action);
          }}
        />
      )}
    </div>
  );
}

function ArchiveBookableItemDialog({
  configuration,
  close,
  onArchive,
  onArchived,
}: {
  configuration: BookingConfigurationRow;
  close: () => void;
  onArchive: (id: number, version: number) => Promise<void>;
  onArchived: () => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<unknown>(null);
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  const handleDelete = async () => {
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await onArchive(configuration.id, requiredVersion(configuration));
      close();
      onArchived();
    } catch (error) {
      setDeleteError(error);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <AlertDialog open onOpenChange={(open) => !open && close()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("bookableItems.archiveDialog.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("bookableItems.archiveDialog.description", { item: itemName })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {deleteError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.archiveDialog.error", { item: itemName })}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeleting}>{t("common:actions.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={isDeleting}
            aria-busy={isDeleting}
            onClick={() => void handleDelete()}
          >
            {t("bookableItems.actions.archive")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function PermanentDeleteBookableItemDialog({
  configuration,
  close,
  onDelete,
  onDeleted,
}: {
  configuration: BookingConfigurationRow;
  close: () => void;
  onDelete: (id: number, version: number) => Promise<void>;
  onDeleted: () => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [confirmation, setConfirmation] = useState("");
  const confirmationId = useId();
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<unknown>(null);
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  const handleDelete = async () => {
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await onDelete(configuration.id, requiredVersion(configuration));
      close();
      onDeleted();
    } catch (error) {
      setDeleteError(error);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <AlertDialog open onOpenChange={(open) => !open && !isDeleting && close()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("bookableItems.permanentDeleteDialog.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("bookableItems.permanentDeleteDialog.description", { item: itemName })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <label htmlFor={confirmationId} className="space-y-2 text-sm">
          <span>{t("bookableItems.permanentDeleteDialog.confirmationLabel")}</span>
          <Input
            id={confirmationId}
            value={confirmation}
            onChange={(event) => setConfirmation(event.currentTarget.value)}
          />
        </label>
        {deleteError ? (
          <p role="alert" className="text-sm text-destructive">
            {t(lifecycleErrorKey(deleteError, "bookableItems.permanentDeleteDialog.error"))}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeleting}>{t("common:actions.cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={isDeleting || confirmation !== itemName}
            aria-busy={isDeleting}
            onClick={() => void handleDelete()}
          >
            {t("bookableItems.actions.deletePermanently")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function BookableItemsBulkActions({
  selection,
  disabled,
  activeAction,
  failedAction,
  onAction,
}: {
  selection: TableListSelectionContext;
  disabled: boolean;
  activeAction: BookableItemsBulkAction | null;
  failedAction: BookableItemsBulkAction | null;
  onAction: (action: BookableItemsBulkAction, selectedRowIds: ReadonlySet<string>) => Promise<void>;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const error =
    failedAction === "enable"
      ? t("bookableItems.bulk.errors.enable")
      : failedAction === "disable"
        ? t("bookableItems.bulk.errors.disable")
        : failedAction === "archive"
          ? t("bookableItems.bulk.errors.archive")
          : null;

  const runAction = async (action: BookableItemsBulkAction) => {
    try {
      await onAction(action, selection.selectedRowIds);
      if (action === "archive") setArchiveOpen(false);
    } catch {
      // The mutation renders the action-specific error and keeps the selected IDs.
    }
  };

  return (
    <>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "enable"}
        onClick={() => void runAction("enable")}
      >
        {t("bookableItems.bulk.actions.enable")}
      </Button>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "disable"}
        onClick={() => void runAction("disable")}
      >
        {t("bookableItems.bulk.actions.disable")}
      </Button>
      <Button
        type="button"
        variant="destructive"
        size="sm"
        disabled={disabled}
        aria-busy={activeAction === "archive"}
        onClick={() => setArchiveOpen(true)}
      >
        {t("bookableItems.bulk.actions.archive")}
      </Button>
      {error && (!archiveOpen || failedAction !== "archive") ? (
        <p role="alert" className="basis-full text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <AlertDialog open={archiveOpen} onOpenChange={setArchiveOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("bookableItems.bulk.archiveDialog.title", { count: selection.selectedRowIds.size })}
            </AlertDialogTitle>
            <AlertDialogDescription>{t("bookableItems.bulk.archiveDialog.description")}</AlertDialogDescription>
          </AlertDialogHeader>
          {error && failedAction === "archive" ? (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={disabled}>{t("common:actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={disabled}
              aria-busy={activeAction === "archive"}
              onClick={() => void runAction("archive")}
            >
              {t("bookableItems.actions.archive")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

export default function BookableItemsPage() {
  const { t } = useTranslation(["booking", "common"]);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const queryClient = useQueryClient();
  const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const [ownerAttentionOnly, setOwnerAttentionOnly] = useState(false);
  const ownerAttention = useQuery({
    queryKey: ["api-v2", "booking-configurations", "owner-attention"],
    queryFn: ({ signal }) => fetchOwnerAttentionIds(token, signal),
    enabled: ownerAttentionOnly && token.length > 0,
    staleTime: 60_000,
  });
  const ownerAttentionFilter = useMemo<FilterExpression<BookingConfigurationRow> | undefined>(() => {
    if (!ownerAttentionOnly || !ownerAttention.data) return undefined;
    const ids = [...ownerAttention.data];
    return ids.length > 0
      ? { kind: "comparison", field: "id", operator: "in", value: ids }
      : { kind: "comparison", field: "id", operator: "equals", value: -1 };
  }, [ownerAttention.data, ownerAttentionOnly]);
  const request = useMemo(
    () => ({
      token,
      depth: 1,
      projection: bookableItemsProjection,
      ...(ownerAttentionFilter ? { baseFilter: ownerAttentionFilter } : {}),
    }),
    [ownerAttentionFilter, token],
  );
  const table = useApiV2TableList({
    resourceName: "booking-configurations",
    config: bookingConfigurationConfig,
    documentSchema: BookingConfigurationSchema,
    request,
    query: { keepPreviousData: true },
  });
  const onArchive = useCallback(
    (id: number, version: number) => archiveBookingConfiguration(id, version, token),
    [token],
  );
  const onPermanentDelete = useCallback(
    (id: number, version: number) => permanentlyDeleteBookingConfiguration(id, version, token),
    [token],
  );
  const onChanged = useCallback(
    () => void queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
    [queryClient],
  );
  const restoreMutation = useMutation({
    mutationFn: (configuration: BookingConfigurationRow) =>
      restoreBookingConfiguration(configuration.id, requiredVersion(configuration), token),
    onSuccess: onChanged,
  });
  const onRestore = useCallback(
    (configuration: BookingConfigurationRow) => restoreMutation.mutateAsync(configuration),
    [restoreMutation],
  );
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const bulkMutation = useMutation({
    mutationFn: ({ action, selectedRowIds: mutationRowIds }: BookableItemsBulkMutation) =>
      mutateBookableItems(action, mutationRowIds, token),
    onSuccess: async () => {
      setSelectedRowIds(new Set());
      setFailedBulkAction(null);
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
    },
    onError: (_error, variables) => setFailedBulkAction(variables.action),
  });
  const onSelectionChange = useCallback((value: ReadonlySet<string>) => {
    setSelectedRowIds(value);
    setFailedBulkAction(null);
  }, []);
  const onBulkAction = useCallback(
    async (action: BookableItemsBulkAction, actionRowIds: ReadonlySet<string>) => {
      setFailedBulkAction(null);
      await bulkMutation.mutateAsync({ action, selectedRowIds: Array.from(actionRowIds) });
    },
    [bulkMutation],
  );
  const rowActions = useMemo<TableListRowActions<BookingConfigurationRow>>(
    () => ({
      id: "actions",
      label: t("bookableItems.fields.actions"),
      width: 88,
      minWidth: 80,
      renderCell: ({ row, activate }) => (
        <BookableItemActionTriggers
          configuration={row}
          activate={activate}
          directSysadmin={directSysadmin}
          onRestore={onRestore}
        />
      ),
      renderInteraction: ({ actionId, row, close }) =>
        actionId === "archive" ? (
          <ArchiveBookableItemDialog configuration={row} close={close} onArchive={onArchive} onArchived={onChanged} />
        ) : actionId === "permanent-delete" ? (
          <PermanentDeleteBookableItemDialog
            configuration={row}
            close={close}
            onDelete={onPermanentDelete}
            onDeleted={onChanged}
          />
        ) : null,
    }),
    [directSysadmin, onArchive, onChanged, onPermanentDelete, onRestore, t],
  );
  const ownerAttentionButtons: TableListFilterButtons = {
    legend: t("bookableItems.ownerHealth.filters"),
    buttons: [
      {
        id: "owner-attention",
        label: t("bookableItems.ownerHealth.filter"),
        pressed: ownerAttentionOnly,
        count: ownerAttention.data?.size,
        onClick: () => {
          setSelectedRowIds(new Set());
          setOwnerAttentionOnly((current) => !current);
        },
      },
    ],
    onReset: () => setOwnerAttentionOnly(false),
  };
  const displayedRows =
    ownerAttentionOnly && ownerAttention.data
      ? table.tableProps.rows.filter((row) => ownerAttention.data.has(row.id))
      : table.tableProps.rows;

  return (
    <main className="p-4 sm:p-8">
      {ownerAttentionOnly && ownerAttention.isPending ? (
        <p role="status">{t("bookableItems.ownerHealth.loading")}</p>
      ) : null}
      {ownerAttentionOnly && ownerAttention.isError ? (
        <div role="alert" className="mb-3 flex items-center gap-3">
          <span>{t("bookableItems.ownerHealth.error")}</span>
          <Button type="button" variant="outline" onClick={() => void ownerAttention.refetch()}>
            {t("common:actions.retry")}
          </Button>
        </div>
      ) : null}
      {restoreMutation.isError ? (
        <p role="alert" className="mb-3 text-sm text-destructive">
          {t(lifecycleErrorKey(restoreMutation.error, "bookableItems.lifecycleErrors.restore"))}
        </p>
      ) : null}
      <TableList
        {...table.tableProps}
        rows={ownerAttentionOnly && (ownerAttention.isPending || ownerAttention.isError) ? [] : displayedRows}
        rowActions={rowActions}
        filterButtons={ownerAttentionButtons}
        selection={{
          value: selectedRowIds,
          onChange: onSelectionChange,
          disabled: bulkMutation.isPending,
          maximumCount: maximumBookableItemsSelection,
          getRowLabel: (row) => row.target?.value.name ?? t("common:values.unknownItem"),
          renderActions: (selection) => (
            <BookableItemsBulkActions
              selection={selection}
              disabled={bulkMutation.isPending}
              activeAction={bulkMutation.isPending ? (bulkMutation.variables?.action ?? null) : null}
              failedAction={failedBulkAction}
              onAction={onBulkAction}
            />
          ),
        }}
        createAction={
          <Link to="/booking/bookable-items/add" className={cn(buttonVariants(), "rounded-sm")} data-slot="button">
            <PlusIcon aria-hidden="true" data-icon="inline-start" />
            {t("bookableItems.actions.add")}
          </Link>
        }
      />
    </main>
  );
}
