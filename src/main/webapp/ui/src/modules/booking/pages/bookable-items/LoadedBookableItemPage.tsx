import { Tabs } from "@base-ui/react/tabs";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { PencilIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { bookingApiV2JsonHeaders } from "@/modules/booking/domain/apiV2";
import { ApiV2ProblemError, parseApiV2Problem } from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { leaveResource } from "@/modules/common/resource-access/resourceAccess";
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
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Input } from "@/modules/common/ui/input";
import { Heading } from "@/modules/common/ui/typography";
import { detailColumnsClassName, detailPageClassName } from "../DetailPageShell";
import { BookableItemAuditLog } from "./BookableItemAuditLog";
import { BookableItemConfigurationForm } from "./BookableItemConfigurationForm";
import { BookableItemFactsAside } from "./BookableItemFactsAside";
import { BookableItemRulesReadOut } from "./BookableItemRulesReadOut";
import { BookableItemSpotlightHeader } from "./BookableItemSpotlightHeader";
import {
  BookingConfigurationActionsMenu,
  type BookingConfigurationLifecycleAction,
} from "./BookingConfigurationActionsMenu";
import { BookingEventList } from "./BookingEventList";
import { calendarSubscriptionQueryKey } from "./bookableItemCalendarSubscription";
import {
  BOOKING_CONFIGURATION_READ_FIELDS,
  type BookingConfiguration,
  type BookingConfigurationUpdateInput,
} from "./bookingConfiguration";
import { CalendarSubscriptionPopover } from "./CalendarSubscriptionPopover";
import { InventoryAccessReadOnly } from "./InventoryAccessReadOnly";

export { BookableItemSkeleton } from "./BookableItemSkeleton";

export type BookableItemTab = "bookings" | "details" | "audit" | "access";

const itemPageClassName = detailPageClassName;
const itemColumnsClassName = detailColumnsClassName;

export function bookableItemTab(tab: string | undefined): BookableItemTab {
  return tab === "details" || tab === "audit" || tab === "access" ? tab : "bookings";
}

async function updateBookingConfiguration(
  id: number,
  version: number,
  input: BookingConfigurationUpdateInput,
  token: string,
): Promise<void> {
  const search = new URLSearchParams({
    depth: "1",
    "fields[booking-configurations]": BOOKING_CONFIGURATION_READ_FIELDS,
  });
  const response = await fetch(`/api/v2/booking-configurations/${id}?${search}`, {
    method: "PATCH",
    headers: bookingApiV2JsonHeaders(token, { "If-Match": `"${version}"` }),
    body: JSON.stringify(input),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
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
    headers: bookingApiV2JsonHeaders(token, { "If-Match": `"${version}"` }),
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

type BookableItemLifecycleErrorKey =
  | "bookableItemDetails.lifecycleErrors.restore"
  | "bookableItemDetails.lifecycleErrors.stale"
  | "bookableItemDetails.lifecycleErrors.stateChanged"
  | "bookableItemDetails.permanentDeleteDialog.error";

function lifecycleErrorKey(error: unknown, fallback: BookableItemLifecycleErrorKey): BookableItemLifecycleErrorKey {
  if (error instanceof ApiV2ProblemError && error.status === 412) {
    return "bookableItemDetails.lifecycleErrors.stale";
  }
  if (error instanceof ApiV2ProblemError && error.status === 409) {
    return "bookableItemDetails.lifecycleErrors.stateChanged";
  }
  return fallback;
}

function PageTab({ value, disabled, children }: { value: BookableItemTab; disabled: boolean; children: ReactNode }) {
  return (
    <Tabs.Tab
      value={value}
      disabled={disabled}
      className="-mb-px cursor-default border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground transition-colors outline-none select-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/30 aria-selected:border-primary aria-selected:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
    >
      {children}
    </Tabs.Tab>
  );
}

export function LoadedBookableItemPage({
  configuration,
  globalId,
  tab,
  token,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  tab: BookableItemTab;
  token: string;
}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: currentUser } = useCurrentUserQuery();
  const preferences = useBookingDisplayPreferences();
  const { edit = false } = useSearch({ from: "/booking/bookable-items/$globalId/{-$tab}" });
  const navigate = useNavigate({ from: "/booking/bookable-items/$globalId/{-$tab}" });
  const queryClient = useQueryClient();
  const [cutoff] = useState(() => new Date().toISOString());
  const [saveAnnouncement, setSaveAnnouncement] = useState<"saved" | "archived" | "restored" | null>(null);
  const [staleEdit, setStaleEdit] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [permanentDeleteOpen, setPermanentDeleteOpen] = useState(false);
  const [permanentDeleteConfirmation, setPermanentDeleteConfirmation] = useState("");
  const [leaveOpen, setLeaveOpen] = useState(false);
  const formId = `bookable-item-details-${useId()}`;
  const permanentConfirmationId = `${formId}-permanent-delete-confirmation`;
  const editButtonRef = useRef<HTMLButtonElement>(null);
  const saveButtonRef = useRef<HTMLButtonElement>(null);
  const actionsButtonRef = useRef<HTMLButtonElement>(null);
  const wasEditing = useRef(false);
  const target = configuration.target;
  const canEdit = configuration.capabilities.canEditConfiguration;
  const active = configuration.state === "ACTIVE";
  const editing = active && canEdit && edit;
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const setEdit = (next: boolean) =>
    void navigate({
      search: next ? { edit: true } : {},
      replace: true,
    });

  const setTab = (next: BookableItemTab) =>
    void navigate({
      to: "/booking/bookable-items/$globalId/{-$tab}",
      params: { globalId, tab: next === "bookings" ? undefined : next },
      search: edit ? { edit: true } : {},
      replace: true,
      resetScroll: false,
    });

  const updateMutation = useMutation({
    mutationFn: ({ input, version }: { input: BookingConfigurationUpdateInput; version: number }) =>
      updateBookingConfiguration(configuration.id, version, input, token),
    onMutate: () => setSaveAnnouncement(null),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      setSaveAnnouncement("saved");
      setStaleEdit(false);
      setEdit(false);
    },
    onError: async (error) => {
      if (typeof error === "object" && error !== null && "status" in error && error.status === 412) {
        setStaleEdit(true);
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const archiveMutation = useMutation({
    mutationFn: () => archiveBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configuration.id) }),
      ]);
      setArchiveOpen(false);
      setSaveAnnouncement("archived");
    },
    onError: async (error) => {
      if (typeof error === "object" && error !== null && "status" in error && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const restoreMutation = useMutation({
    mutationFn: () => restoreBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configuration.id) }),
      ]);
      setSaveAnnouncement("restored");
    },
    onError: async (error) => {
      if (typeof error === "object" && error !== null && "status" in error && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const permanentDeleteMutation = useMutation({
    mutationFn: () =>
      permanentlyDeleteBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      setPermanentDeleteOpen(false);
      void navigate({ to: "/booking/config/bookable-items", ignoreBlocker: true });
    },
    onError: async (error) => {
      if (
        typeof error === "object" &&
        error !== null &&
        "status" in error &&
        (error.status === 409 || error.status === 412)
      ) {
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const leaveMutation = useMutation({
    mutationFn: () => leaveResource("booking-configurations", configuration.id, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      void navigate({ to: "/booking", ignoreBlocker: true });
    },
  });

  useEffect(() => {
    if (wasEditing.current && !editing) editButtonRef.current?.focus();
    wasEditing.current = editing;
  }, [editing]);

  useEffect(() => {
    if (updateMutation.isError && !updateMutation.isPending) saveButtonRef.current?.focus();
  }, [updateMutation.isError, updateMutation.isPending]);

  useEffect(() => {
    // onSuccess runs before the mutation clears pending and re-enables this button.
    if (
      archiveMutation.isPending ||
      restoreMutation.isPending ||
      (!archiveMutation.isSuccess && !restoreMutation.isSuccess)
    ) {
      return;
    }
    const frame = requestAnimationFrame(() => actionsButtonRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [archiveMutation.isPending, archiveMutation.isSuccess, restoreMutation.isPending, restoreMutation.isSuccess]);

  useEffect(() => {
    if (!active && edit) setEdit(false);
  }, [active, edit]);

  useEffect(() => {
    if (tab === "audit" && !configuration.capabilities.canViewAudit) {
      setTab("bookings");
    }
  }, [configuration.capabilities.canViewAudit, tab]);

  if (target === null) return null;

  const cancelEdit = () => {
    setSaveAnnouncement(null);
    setStaleEdit(false);
    setEdit(false);
  };

  const handleLifecycleAction = (action: BookingConfigurationLifecycleAction) => {
    if (action === "archive") setArchiveOpen(true);
    if (action === "restore") restoreMutation.mutate();
    if (action === "permanent-delete") {
      setPermanentDeleteConfirmation("");
      setPermanentDeleteOpen(true);
    }
  };

  return (
    <main className={itemPageClassName}>
      <div className="@container">
        <Tabs.Root
          value={tab}
          onValueChange={(value) => {
            if (updateMutation.isPending) return;
            const nextTab = value === "details" || value === "audit" || value === "access" ? value : "bookings";
            setTab(nextTab);
          }}
          className="min-w-0 space-y-6"
        >
          {restoreMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t(lifecycleErrorKey(restoreMutation.error, "bookableItemDetails.lifecycleErrors.restore"))}
            </p>
          ) : null}
          <BookableItemSpotlightHeader
            configuration={configuration}
            target={target}
            action={
              <>
                {active &&
                (configuration.capabilities.canCreateBooking || configuration.capabilities.canCreateBlockout) ? (
                  <BookingCreationButtonGroup
                    ownerId={`bookable-item-${configuration.id}`}
                    target={bookableItemOption({ ...configuration, target })}
                    lockTarget
                    disabled={!configuration.enabled}
                  />
                ) : null}
                {configuration.capabilities.canSubscribeCalendar ? (
                  <CalendarSubscriptionPopover configurationId={configuration.id} token={token} archived={!active} />
                ) : null}
                {active && configuration.capabilities.canLeaveConfiguration ? (
                  <Button type="button" variant="outline" onClick={() => setLeaveOpen(true)}>
                    {t("bookableItemDetails.actions.leave")}
                  </Button>
                ) : null}
                <BookingConfigurationActionsMenu
                  configuration={configuration}
                  itemName={target.value.name}
                  directSysadmin={directSysadmin}
                  disabled={archiveMutation.isPending || restoreMutation.isPending || permanentDeleteMutation.isPending}
                  triggerRef={actionsButtonRef}
                  onAction={handleLifecycleAction}
                />
              </>
            }
          />

          <Tabs.List className="flex flex-wrap border-b">
            <PageTab value="bookings" disabled={updateMutation.isPending}>
              {t("bookableItemDetails.tabs.bookings")}
            </PageTab>
            <PageTab value="details" disabled={updateMutation.isPending}>
              {t("bookableItemDetails.tabs.details")}
            </PageTab>
            {configuration.capabilities.canViewAudit ? (
              <PageTab value="audit" disabled={updateMutation.isPending}>
                {t("bookableItemDetails.tabs.audit")}
              </PageTab>
            ) : null}
            <PageTab value="access" disabled={updateMutation.isPending}>
              {t("bookableItemDetails.tabs.access")}
            </PageTab>
          </Tabs.List>

          <div className={itemColumnsClassName}>
            <div className="min-w-0">
              <Tabs.Panel value="bookings" className="space-y-8 outline-none">
                <section className="space-y-4" aria-labelledby="upcoming-events-heading">
                  <Heading level={3} as="h2" id="upcoming-events-heading">
                    {t("bookableItemDetails.upcoming")}
                  </Heading>
                  <BookingEventList
                    globalId={globalId}
                    timezone={preferences.timeZone}
                    period="upcoming"
                    cutoff={cutoff}
                  />
                </section>

                <section className="space-y-4" aria-labelledby="past-events-heading">
                  <Heading level={3} as="h2" id="past-events-heading">
                    {t("bookableItemDetails.past")}
                  </Heading>
                  <BookingEventList globalId={globalId} timezone={preferences.timeZone} period="past" cutoff={cutoff} />
                </section>
              </Tabs.Panel>

              <Tabs.Panel value="details" keepMounted className="outline-none">
                <Card>
                  <CardHeader>
                    <CardTitle>{t("bookableItemDetails.rules")}</CardTitle>
                    {active && canEdit ? (
                      <CardAction className="flex gap-3">
                        {editing ? (
                          <>
                            <Button
                              key="save"
                              ref={saveButtonRef}
                              type="submit"
                              size="sm"
                              form={formId}
                              disabled={updateMutation.isPending}
                              aria-busy={updateMutation.isPending}
                            >
                              {t("bookableItems.actions.save")}
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              disabled={updateMutation.isPending}
                              onClick={cancelEdit}
                            >
                              {t("bookableItemDetails.cancelEdit")}
                            </Button>
                          </>
                        ) : (
                          <Button
                            key="edit"
                            ref={editButtonRef}
                            type="button"
                            size="sm"
                            variant="ghost"
                            onClick={() => setEdit(true)}
                          >
                            <PencilIcon aria-hidden="true" />
                            {t("bookableItemDetails.edit")}
                          </Button>
                        )}
                      </CardAction>
                    ) : null}
                  </CardHeader>
                  <CardContent>
                    {editing ? (
                      <BookableItemConfigurationForm
                        configuration={configuration}
                        globalId={globalId}
                        formId={formId}
                        pending={updateMutation.isPending}
                        staleEdit={staleEdit}
                        failed={updateMutation.isError}
                        onSubmit={(input, version) => updateMutation.mutateAsync({ input, version })}
                      />
                    ) : (
                      <BookableItemRulesReadOut configuration={configuration} />
                    )}
                  </CardContent>
                </Card>
              </Tabs.Panel>

              {configuration.capabilities.canViewAudit ? (
                <Tabs.Panel value="audit" className="outline-none">
                  <BookableItemAuditLog configurationId={configuration.id} />
                </Tabs.Panel>
              ) : null}

              <Tabs.Panel value="access" className="outline-none">
                <Card>
                  <CardContent className="pt-0">
                    <InventoryAccessReadOnly instrumentId={target.value.id} />
                  </CardContent>
                </Card>
              </Tabs.Panel>
            </div>
            <BookableItemFactsAside configuration={configuration} displayTimeZone={preferences.timeZone} />
          </div>
        </Tabs.Root>
      </div>

      <AlertDialog open={archiveOpen} onOpenChange={(open) => !archiveMutation.isPending && setArchiveOpen(open)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.archiveDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookableItemDetails.archiveDialog.description", { item: target.value.name })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          {archiveMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t("bookableItemDetails.archiveDialog.error")}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={archiveMutation.isPending}>{commonT("actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={archiveMutation.isPending}
              aria-busy={archiveMutation.isPending}
              onClick={() => archiveMutation.mutate()}
            >
              {t("bookableItemDetails.archiveDialog.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog
        open={permanentDeleteOpen}
        onOpenChange={(open) => {
          if (permanentDeleteMutation.isPending) return;
          setPermanentDeleteOpen(open);
          if (!open) requestAnimationFrame(() => actionsButtonRef.current?.focus());
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.permanentDeleteDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("bookableItemDetails.permanentDeleteDialog.description", { item: target.value.name })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <label htmlFor={permanentConfirmationId} className="space-y-2 text-sm">
            <span>{t("bookableItemDetails.permanentDeleteDialog.confirmationLabel")}</span>
            <Input
              id={permanentConfirmationId}
              value={permanentDeleteConfirmation}
              onChange={(event) => setPermanentDeleteConfirmation(event.currentTarget.value)}
              autoComplete="off"
            />
          </label>
          {permanentDeleteMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t(lifecycleErrorKey(permanentDeleteMutation.error, "bookableItemDetails.permanentDeleteDialog.error"))}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={permanentDeleteMutation.isPending}>
              {commonT("actions.cancel")}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={permanentDeleteMutation.isPending || permanentDeleteConfirmation !== target.value.name}
              aria-busy={permanentDeleteMutation.isPending}
              onClick={() => permanentDeleteMutation.mutate()}
            >
              {t("bookableItemDetails.permanentDeleteDialog.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog open={leaveOpen} onOpenChange={(open) => !leaveMutation.isPending && setLeaveOpen(open)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("bookableItemDetails.leaveDialog.title")}</AlertDialogTitle>
            <AlertDialogDescription>{t("bookableItemDetails.leaveDialog.description")}</AlertDialogDescription>
          </AlertDialogHeader>
          {leaveMutation.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {t("bookableItemDetails.leaveDialog.error")}
            </p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={leaveMutation.isPending}>{commonT("actions.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={leaveMutation.isPending}
              aria-busy={leaveMutation.isPending}
              onClick={() => leaveMutation.mutate()}
            >
              {t("bookableItemDetails.actions.leave")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <p role="status" aria-live="polite" className="sr-only">
        {updateMutation.isPending
          ? t("bookableItemDetails.update.pending")
          : saveAnnouncement === "saved"
            ? t("bookableItemDetails.update.saved")
            : saveAnnouncement === "archived"
              ? t("bookableItemDetails.update.archived")
              : saveAnnouncement === "restored"
                ? t("bookableItemDetails.update.restored")
                : null}
      </p>
    </main>
  );
}
