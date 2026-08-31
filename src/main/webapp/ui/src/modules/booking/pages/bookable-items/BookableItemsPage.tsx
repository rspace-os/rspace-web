import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { EyeIcon, PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import {
  TableList,
  type TableListRowActions,
  type TableListSelectionContext,
} from "@/modules/common/table-list/TableList";
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
import { cn } from "@/modules/common/utils/cn";
import {
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "./bookingConfiguration";

const bookableItemsProjection = {
  fixed: [
    "id",
    "target",
    "enabled",
    "timezone",
    ...schedulingSettingsFieldNames,
    "updatedAt",
    "effectiveRole",
    "roleSources",
    "capabilities",
    "ownerHealth",
  ],
} as const;

const maximumBookableItemsSelection = 1000;
const createdByCurrentUserFilter = {
  kind: "comparison",
  field: "createdBy.value",
  operator: "equals",
  value: "me",
} as const;

export type BookableItemsBulkAction = "enable" | "disable" | "delete";

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
  const isDelete = action === "delete";
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

async function deleteBookingConfiguration(id: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });
  if (!response.ok) throw new Error(`Booking configuration delete failed with status ${response.status}`);
}

function BookableItemActionTriggers({
  configuration,
  activate,
}: {
  configuration: BookingConfigurationRow;
  activate: (actionId: string) => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  return (
    <div className="flex justify-start gap-1">
      {configuration.target === null ? null : (
        <>
          <Link
            to="/booking/bookable-items/$globalId"
            params={{ globalId: configuration.target.globalId }}
            aria-label={t("bookableItems.actions.viewDetails", { item: itemName })}
            className={cn(buttonVariants({ variant: "ghost", size: "icon-xs" }), "rounded-sm")}
            data-slot="button"
          >
            <EyeIcon aria-hidden="true" />
          </Link>
          <Link
            to="/booking/bookable-items/$globalId"
            params={{ globalId: configuration.target.globalId }}
            search={{ tab: "details", edit: true }}
            aria-label={t("bookableItems.actions.edit", { item: itemName })}
            className={cn(buttonVariants({ variant: "ghost", size: "icon-xs" }), "rounded-sm")}
            data-slot="button"
          >
            <PencilIcon aria-hidden="true" />
          </Link>
        </>
      )}
      <Button
        type="button"
        variant="ghost"
        size="icon-xs"
        className="rounded-sm text-destructive"
        aria-label={t("bookableItems.actions.delete", { item: itemName })}
        onClick={() => activate("delete")}
      >
        <Trash2Icon aria-hidden="true" />
      </Button>
    </div>
  );
}

function DeleteBookableItemDialog({
  configuration,
  close,
  onDelete,
  onDeleted,
}: {
  configuration: BookingConfigurationRow;
  close: () => void;
  onDelete: (id: number) => Promise<void>;
  onDeleted: () => void;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteFailed, setDeleteFailed] = useState(false);
  const itemName = configuration.target?.value.name ?? t("common:values.unknownItem");

  const handleDelete = async () => {
    setIsDeleting(true);
    setDeleteFailed(false);
    try {
      await onDelete(configuration.id);
      close();
      onDeleted();
    } catch {
      setDeleteFailed(true);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <AlertDialog open onOpenChange={(open) => !open && close()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("bookableItems.deleteDialog.title")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("bookableItems.deleteDialog.description", { item: itemName })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {deleteFailed ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItems.deleteDialog.error", { item: itemName })}
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
            {t("common:actions.delete")}
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
  const [deleteOpen, setDeleteOpen] = useState(false);
  const error =
    failedAction === "enable"
      ? t("bookableItems.bulk.errors.enable")
      : failedAction === "disable"
        ? t("bookableItems.bulk.errors.disable")
        : failedAction === "delete"
          ? t("bookableItems.bulk.errors.delete")
          : null;

  const runAction = async (action: BookableItemsBulkAction) => {
    try {
      await onAction(action, selection.selectedRowIds);
      if (action === "delete") setDeleteOpen(false);
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
        aria-busy={activeAction === "delete"}
        onClick={() => setDeleteOpen(true)}
      >
        {t("bookableItems.bulk.actions.delete")}
      </Button>
      {error && (!deleteOpen || failedAction !== "delete") ? (
        <p role="alert" className="basis-full text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("bookableItems.bulk.deleteDialog.title", {
                count: selection.selectedRowIds.size,
              })}
            </AlertDialogTitle>
            <AlertDialogDescription>{t("bookableItems.bulk.deleteDialog.description")}</AlertDialogDescription>
          </AlertDialogHeader>
          {error && failedAction === "delete" ? (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={disabled}>{t("common:actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={disabled}
              aria-busy={activeAction === "delete"}
              onClick={() => void runAction("delete")}
            >
              {t("common:actions.delete")}
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
  const queryClient = useQueryClient();
  const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const request = useMemo(
    () => ({ token, depth: 1, projection: bookableItemsProjection, baseFilter: createdByCurrentUserFilter }),
    [token],
  );
  const table = useApiV2TableList({
    resourceName: "booking-configurations",
    config: bookingConfigurationConfig,
    documentSchema: BookingConfigurationSchema,
    request,
    query: { keepPreviousData: true },
  });
  const onDelete = useCallback((id: number) => deleteBookingConfiguration(id, token), [token]);
  const onDeleted = useCallback(
    () => void queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
    [queryClient],
  );
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
      width: 96,
      minWidth: 80,
      renderCell: ({ row, activate }) => <BookableItemActionTriggers configuration={row} activate={activate} />,
      renderInteraction: ({ actionId, row, close }) =>
        actionId === "delete" ? (
          <DeleteBookableItemDialog configuration={row} close={close} onDelete={onDelete} onDeleted={onDeleted} />
        ) : null,
    }),
    [onDelete, onDeleted, t],
  );

  return (
    <main className="p-4 sm:p-8">
      <TableList
        {...table.tableProps}
        rowActions={rowActions}
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
