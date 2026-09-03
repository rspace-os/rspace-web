import { Tabs } from "@base-ui/react/tabs";
import { Form, isDirty, reset, useForm } from "@formisch/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { PencilIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { SchedulingSettingsFields } from "@/modules/booking/configuration/schedulingSettings";
import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { bookingApiV2JsonHeaders } from "@/modules/booking/domain/apiV2";
import { ApiV2ProblemError, parseApiV2Problem } from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { DirtyNavigationGuard } from "@/modules/common/navigation/DirtyNavigationGuard";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { ResourceAccessEditor } from "@/modules/common/resource-access/ResourceAccessEditor";
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
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { Input } from "@/modules/common/ui/input";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Heading } from "@/modules/common/ui/typography";
import { BookableItemAuditLog } from "./BookableItemAuditLog";
import {
  BookingConfigurationActionsMenu,
  type BookingConfigurationLifecycleAction,
} from "./BookingConfigurationActionsMenu";
import { BookingEventList } from "./BookingEventList";
import {
  BOOKING_CONFIGURATION_READ_FIELDS,
  type BookingConfiguration,
  type BookingConfigurationUpdateInput,
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationFields,
  fetchBookingConfigurationByTarget,
} from "./bookingConfiguration";
import { bookingResourceAccessAdapter } from "./bookingResourceAccess";
import { CalendarSubscriptionPopover } from "./CalendarSubscriptionPopover";

type BookableItemTab = "bookings" | "details" | "audit" | "access";

function bookableItemTab(tab: string | undefined): BookableItemTab {
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

function configurationInput(configuration: BookingConfiguration): BookingConfigurationUpdateInput {
  return {
    enabled: configuration.enabled,
    slotGranularityMinutes: configuration.slotGranularityMinutes,
    openingStart: configuration.openingStart,
    openingEnd: configuration.openingEnd,
    bufferBeforeMinutes: configuration.bufferBeforeMinutes,
    bufferAfterMinutes: configuration.bufferAfterMinutes,
    maxBookingDurationMinutes: configuration.maxBookingDurationMinutes,
    allowDoubleBooking: configuration.allowDoubleBooking,
  };
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

function SpotlightHeader({
  configuration,
  target,
  action,
}: {
  configuration: BookingConfiguration;
  target: NonNullable<BookingConfiguration["target"]>;
  action?: ReactNode;
}) {
  const { t } = useTranslation("booking");
  return (
    <section className="flex flex-wrap items-center gap-4">
      <InventoryItem
        name={target.value.name}
        nameAs="h1"
        globalId={target.globalId}
        idPlacement="title"
        className="min-w-full flex-1 p-0 sm:min-w-0"
      >
        <span>{configuration.timezone}</span>
      </InventoryItem>
      <div
        data-slot="bookable-item-header-actions"
        className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0 [&_[data-slot=badge]]:h-[30px] [&_button]:h-[30px] [&_button]:min-h-[30px]"
      >
        <Badge variant={configuration.enabled ? "default" : "secondary"}>
          {configuration.enabled ? t("bookableItemDetails.enabled") : t("bookableItemDetails.disabled")}
        </Badge>
        {configuration.state === "ARCHIVED" ? (
          <Badge variant="secondary">{t("bookableItemDetails.archived")}</Badge>
        ) : null}
        {action}
      </div>
    </section>
  );
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

function RulesReadOut({
  configuration,
  displayTimeZone,
}: {
  configuration: BookingConfiguration;
  displayTimeZone: string;
}) {
  const { t, i18n } = useTranslation("booking");
  const updatedAt =
    configuration.updatedAt === null || configuration.updatedAt === undefined ? (
      t("bookableItemDetails.notAvailable")
    ) : (
      <time dateTime={configuration.updatedAt}>
        {new Intl.DateTimeFormat(i18n.language, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: displayTimeZone,
        }).format(new Date(configuration.updatedAt))}
      </time>
    );
  const facts: Array<[string, ReactNode]> = [
    [t("bookableItemDetails.fields.timezone"), configuration.timezone],
    [t("bookableItemDetails.fields.updatedAt"), updatedAt],
    [t("bookableItemDetails.fields.openingHours"), `${configuration.openingStart}–${configuration.openingEnd}`],
    [
      t("bookableItemDetails.fields.granularity"),
      t("bookableItemDetails.minutes", { count: configuration.slotGranularityMinutes }),
    ],
    [
      t("bookableItemDetails.fields.maximumDuration"),
      configuration.maxBookingDurationMinutes === 0
        ? t("bookableItemDetails.unlimited")
        : t("bookableItemDetails.minutes", { count: configuration.maxBookingDurationMinutes }),
    ],
    [
      t("bookableItemDetails.fields.bufferBefore"),
      t("bookableItemDetails.minutes", { count: configuration.bufferBeforeMinutes }),
    ],
    [
      t("bookableItemDetails.fields.bufferAfter"),
      t("bookableItemDetails.minutes", { count: configuration.bufferAfterMinutes }),
    ],
    [
      t("bookableItemDetails.fields.doubleBooking"),
      configuration.allowDoubleBooking ? t("bookableItemDetails.yes") : t("bookableItemDetails.no"),
    ],
  ];

  return (
    <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
      <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
        {facts.map(([label, value]) => (
          <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME} key={label}>
            <dt className="font-medium">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function LoadedBookableItemPage({
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
  const [staleBase, setStaleBase] = useState<BookingConfigurationUpdateInput | null>(null);
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
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: configurationInput(configuration),
  });

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
      ignoreBlocker: true,
    });

  const updateMutation = useMutation({
    mutationFn: (input: BookingConfigurationUpdateInput) =>
      updateBookingConfiguration(configuration.id, configuration.configurationVersion, input, token),
    onMutate: () => setSaveAnnouncement(null),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      setSaveAnnouncement("saved");
      setStaleBase(null);
      setEdit(false);
    },
    onError: async (error) => {
      if (typeof error === "object" && error !== null && "status" in error && error.status === 412) {
        setStaleBase(configurationInput(configuration));
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const archiveMutation = useMutation({
    mutationFn: () => archiveBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      setArchiveOpen(false);
      setSaveAnnouncement("archived");
      requestAnimationFrame(() => actionsButtonRef.current?.focus());
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
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      setSaveAnnouncement("restored");
      requestAnimationFrame(() => actionsButtonRef.current?.focus());
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
    if (!editing) reset(form, { initialInput: configurationInput(configuration) });
  }, [configuration, editing, form]);

  useEffect(() => {
    if (wasEditing.current && !editing) editButtonRef.current?.focus();
    wasEditing.current = editing;
  }, [editing]);

  useEffect(() => {
    if (updateMutation.isError && !updateMutation.isPending) saveButtonRef.current?.focus();
  }, [updateMutation.isError, updateMutation.isPending]);

  useEffect(() => {
    if (!active && edit) setEdit(false);
  }, [active, edit]);

  useEffect(() => {
    if (
      (tab === "audit" && !configuration.capabilities.canViewAudit) ||
      (tab === "access" && !configuration.capabilities.canViewAccess)
    ) {
      setTab("bookings");
    }
  }, [configuration.capabilities.canViewAccess, configuration.capabilities.canViewAudit, tab]);

  if (target === null) return null;

  const cancelEdit = () => {
    reset(form, { initialInput: configurationInput(configuration) });
    setSaveAnnouncement(null);
    setStaleBase(null);
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
    <main className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <DirtyNavigationGuard dirty={editing && isDirty(form)} />
      <Tabs.Root
        value={tab}
        onValueChange={(value) => {
          if (updateMutation.isPending) return;
          const nextTab = value === "details" || value === "audit" || value === "access" ? value : "bookings";
          setTab(nextTab);
        }}
        className="space-y-6"
      >
        {restoreMutation.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t(lifecycleErrorKey(restoreMutation.error, "bookableItemDetails.lifecycleErrors.restore"))}
          </p>
        ) : null}
        <SpotlightHeader
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
          {configuration.capabilities.canViewAccess ? (
            <PageTab value="access" disabled={updateMutation.isPending}>
              {t("bookableItemDetails.tabs.access")}
            </PageTab>
          ) : null}
        </Tabs.List>

        <Tabs.Panel value="bookings" className="space-y-8 outline-none">
          <section className="space-y-4" aria-labelledby="upcoming-events-heading">
            <Heading level={3} as="h2" id="upcoming-events-heading">
              {t("bookableItemDetails.upcoming")}
            </Heading>
            <BookingEventList globalId={globalId} timezone={preferences.timeZone} period="upcoming" cutoff={cutoff} />
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
                <Form
                  id={formId}
                  of={form}
                  className="min-w-0 space-y-4"
                  onSubmit={(input) => updateMutation.mutateAsync(input)}
                >
                  <RenderFields
                    fields={bookingConfigurationFields.filter((field) => field.name !== "target")}
                    form={form}
                    disabled={updateMutation.isPending}
                    layout="inline"
                  />
                  <SchedulingSettingsFields form={form} disabled={updateMutation.isPending} layout="inline" />
                  {staleBase !== null ? (
                    <div role="alert" className="space-y-1 text-sm text-destructive">
                      <p>{t("bookableItems.staleEdit")}</p>
                      <ul className="list-inside list-disc">
                        {Object.entries(configurationInput(configuration)).flatMap(([field, value]) =>
                          staleBase[field as keyof BookingConfigurationUpdateInput] === value
                            ? []
                            : [<li key={field}>{field}</li>],
                        )}
                      </ul>
                    </div>
                  ) : updateMutation.isError ? (
                    <p role="alert" className="text-sm text-destructive">
                      {t("bookableItems.editError")}
                    </p>
                  ) : null}
                </Form>
              ) : (
                <RulesReadOut configuration={configuration} displayTimeZone={preferences.timeZone} />
              )}
            </CardContent>
          </Card>
        </Tabs.Panel>

        {configuration.capabilities.canViewAudit ? (
          <Tabs.Panel value="audit" className="outline-none">
            <BookableItemAuditLog configurationId={configuration.id} />
          </Tabs.Panel>
        ) : null}

        {configuration.capabilities.canViewAccess ? (
          <Tabs.Panel value="access" className="outline-none">
            <Card>
              <CardContent className="pt-6">
                <ResourceAccessEditor
                  resource="booking-configurations"
                  resourceId={configuration.id}
                  token={token}
                  adapter={bookingResourceAccessAdapter(t)}
                  readOnly={!active}
                  onLeave={() => void navigate({ to: "/booking", ignoreBlocker: true })}
                />
              </CardContent>
            </Card>
          </Tabs.Panel>
        ) : null}
      </Tabs.Root>

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

function Details({ globalId, tab }: { globalId: string; tab: BookableItemTab }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const configuration = useQuery({
    queryKey: ["api-v2", "booking-configurations", "target", globalId],
    queryFn: ({ signal }) => fetchBookingConfigurationByTarget(globalId, token, signal),
  });

  if (configuration.isPending) {
    return (
      <main className="p-4 sm:p-8">
        <p role="status">{t("bookableItemDetails.loading")}</p>
      </main>
    );
  }

  const target = configuration.data?.target;
  if (configuration.isError || target === null || target === undefined || target.globalId !== globalId) {
    return (
      <main className="p-4 sm:p-8">
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>{t("bookableItemDetails.error.title")}</EmptyTitle>
            <EmptyDescription>{t("bookableItemDetails.error.description")}</EmptyDescription>
          </EmptyHeader>
          <Button type="button" variant="outline" onClick={() => void configuration.refetch()}>
            {commonT("actions.retry")}
          </Button>
        </Empty>
      </main>
    );
  }

  return <LoadedBookableItemPage configuration={configuration.data} globalId={globalId} tab={tab} token={token} />;
}

export default function BookableItemPage() {
  const { globalId, tab } = useParams({ from: "/booking/bookable-items/$globalId/{-$tab}" });
  return <Details globalId={globalId} tab={bookableItemTab(tab)} key={globalId} />;
}
