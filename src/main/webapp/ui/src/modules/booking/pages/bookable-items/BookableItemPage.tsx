import { Tabs } from "@base-ui/react/tabs";
import { Form, reset, useForm } from "@formisch/react";
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
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Heading } from "@/modules/common/ui/typography";
import { BookableItemAuditLog } from "./BookableItemAuditLog";
import { BookingEventList } from "./BookingEventList";
import {
  BOOKING_CONFIGURATION_READ_FIELDS,
  type BookingConfiguration,
  type BookingConfigurationUpdateInput,
  BookingConfigurationUpdateInputSchema,
  bookingConfigurationFields,
  fetchBookingConfigurationByTarget,
} from "./bookingConfiguration";
import { CalendarSubscriptionPopover } from "./CalendarSubscriptionPopover";

type BookableItemTab = "bookings" | "details" | "audit";

async function updateBookingConfiguration(
  id: number,
  input: BookingConfigurationUpdateInput,
  token: string,
): Promise<void> {
  const search = new URLSearchParams({
    depth: "1",
    "fields[booking-configurations]": BOOKING_CONFIGURATION_READ_FIELDS,
  });
  const response = await fetch(`/api/v2/booking-configurations/${id}?${search}`, {
    method: "PATCH",
    headers: bookingApiV2JsonHeaders(token),
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error(`Booking configuration update failed with status ${response.status}`);
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
  const hasLocation = target.value.parentContainerName != null && target.value.parentContainerGlobalId != null;

  return (
    <section className="flex flex-wrap items-center gap-4">
      <InventoryItem
        name={target.value.name}
        nameAs="h1"
        globalId={target.globalId}
        href={`/globalId/${target.globalId}`}
        idLinkLabel={t("bookableItemDetails.viewInventory", { name: target.value.name })}
        idPlacement="title"
        className="min-w-full flex-1 p-0 sm:min-w-0"
      >
        <span>{configuration.timezone}</span>
        {hasLocation ? (
          <InventoryLocationLink
            name={target.value.parentContainerName}
            globalId={target.value.parentContainerGlobalId}
          />
        ) : null}
      </InventoryItem>
      <div className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0">
        <Badge variant={configuration.enabled ? "default" : "secondary"}>
          {configuration.enabled ? t("bookableItemDetails.enabled") : t("bookableItemDetails.disabled")}
        </Badge>
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
  token,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  token: string;
}) {
  const { t } = useTranslation("booking");
  const preferences = useBookingDisplayPreferences();
  const { data: currentUser } = useCurrentUserQuery();
  const { tab = "bookings", edit = false } = useSearch({ from: "/booking/bookable-items/$globalId" });
  const navigate = useNavigate({ from: "/booking/bookable-items/$globalId" });
  const queryClient = useQueryClient();
  const [cutoff] = useState(() => new Date().toISOString());
  const [saveAnnouncement, setSaveAnnouncement] = useState<"saved" | null>(null);
  const formId = `bookable-item-details-${useId()}`;
  const editButtonRef = useRef<HTMLButtonElement>(null);
  const saveButtonRef = useRef<HTMLButtonElement>(null);
  const wasEditing = useRef(false);
  const target = configuration.target;
  const canEdit = currentUser.hasSysAdminRole;
  const editing = canEdit && edit;
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: configurationInput(configuration),
  });

  const setSearch = (next: { tab?: BookableItemTab; edit?: boolean }) =>
    void navigate({
      search: (current) => {
        const merged = { ...current, ...next };
        return {
          ...(merged.tab === "details" || merged.tab === "audit" ? { tab: merged.tab } : {}),
          ...(merged.edit === true ? { edit: true } : {}),
        };
      },
      replace: true,
    });

  const updateMutation = useMutation({
    mutationFn: (input: BookingConfigurationUpdateInput) => updateBookingConfiguration(configuration.id, input, token),
    onMutate: () => setSaveAnnouncement(null),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      setSaveAnnouncement("saved");
      setSearch({ edit: false });
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

  if (target === null) return null;

  const cancelEdit = () => {
    reset(form, { initialInput: configurationInput(configuration) });
    setSaveAnnouncement(null);
    setSearch({ edit: false });
  };

  return (
    <main className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">
      <Tabs.Root
        value={tab}
        onValueChange={(value) => {
          if (updateMutation.isPending) return;
          const nextTab = value === "details" || value === "audit" ? value : "bookings";
          setSearch({ tab: nextTab });
        }}
        className="space-y-6"
      >
        <SpotlightHeader
          configuration={configuration}
          target={target}
          action={
            <>
              <BookingCreationButtonGroup
                ownerId={`bookable-item-${configuration.id}`}
                target={bookableItemOption({ ...configuration, target })}
                lockTarget
                disabled={!configuration.enabled}
              />
              <CalendarSubscriptionPopover configurationId={configuration.id} token={token} />
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
          <PageTab value="audit" disabled={updateMutation.isPending}>
            {t("bookableItemDetails.tabs.audit")}
          </PageTab>
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
              {canEdit ? (
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
                      onClick={() => setSearch({ edit: true })}
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
                    fields={bookingConfigurationFields.filter(
                      (field) => field.name !== "target" && field.name !== "enabled",
                    )}
                    form={form}
                    disabled={updateMutation.isPending}
                    layout="inline"
                  />
                  <SchedulingSettingsFields form={form} disabled={updateMutation.isPending} layout="inline" />
                  {updateMutation.isError ? (
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

        <Tabs.Panel value="audit" className="outline-none">
          <BookableItemAuditLog configurationId={configuration.id} />
        </Tabs.Panel>
      </Tabs.Root>

      <p role="status" aria-live="polite" className="sr-only">
        {updateMutation.isPending
          ? t("bookableItemDetails.update.pending")
          : saveAnnouncement === "saved"
            ? t("bookableItemDetails.update.saved")
            : null}
      </p>
    </main>
  );
}

function Details({ globalId }: { globalId: string }) {
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

  return <LoadedBookableItemPage configuration={configuration.data} globalId={globalId} token={token} />;
}

export default function BookableItemPage() {
  const { globalId } = useParams({ from: "/booking/bookable-items/$globalId" });
  return <Details globalId={globalId} key={globalId} />;
}
