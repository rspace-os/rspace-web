import { Tabs } from "@base-ui/react/tabs";
import { Form, useForm } from "@formisch/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { CalendarClockIcon, ExternalLinkIcon, PencilIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { bookingApiV2JsonHeaders } from "@/modules/booking/domain/apiV2";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
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
import { SchedulingSettingsFields } from "./schedulingSettings";

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

function SpotlightHeader({
  configuration,
  target,
  action,
}: {
  configuration: BookingConfiguration;
  target: NonNullable<BookingConfiguration["target"]>;
  action: ReactNode;
}) {
  const { t } = useTranslation("booking");
  return (
    <section className="rounded-sm border bg-background shadow-md">
      <div className="flex flex-wrap items-center gap-4 p-5">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-sm bg-primary text-primary-foreground">
          <CalendarClockIcon aria-hidden="true" className="size-6" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
            {t("bookableItemDetails.title")}
          </p>
          <Heading level={4} as="h1" className="mt-0.5 truncate">
            {target.value.name}
          </Heading>
          <p className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <Badge
              variant="outline"
              className="font-mono"
              render={
                <a
                  href={`/globalId/${target.globalId}`}
                  aria-label={t("bookableItemDetails.viewInventory", { name: target.value.name })}
                >
                  <ExternalLinkIcon aria-hidden="true" />
                  {target.globalId}
                </a>
              }
            />
            {configuration.timezone}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-3">
          <Badge variant={configuration.enabled ? "default" : "secondary"}>
            {configuration.enabled ? t("bookableItemDetails.enabled") : t("bookableItemDetails.disabled")}
          </Badge>
          {action}
        </div>
      </div>
    </section>
  );
}

function PageTab({ value, children }: { value: string; children: ReactNode }) {
  return (
    <Tabs.Tab
      value={value}
      className="-mb-px cursor-default border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground transition-colors outline-none select-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/30 aria-selected:border-primary aria-selected:text-foreground"
    >
      {children}
    </Tabs.Tab>
  );
}

function RulesReadOut({ configuration }: { configuration: BookingConfiguration }) {
  const { t, i18n } = useTranslation("booking");
  const updatedAt =
    configuration.updatedAt === null || configuration.updatedAt === undefined ? (
      t("bookableItemDetails.notAvailable")
    ) : (
      <time dateTime={configuration.updatedAt}>
        {new Intl.DateTimeFormat(i18n.language, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: configuration.timezone,
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
    <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-[max-content_1fr]">
      {facts.map(([label, value]) => (
        <div className="grid gap-1 sm:contents" key={label}>
          <dt className="font-medium">{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * Mounted only while editing, so `useForm` re-seeds from the saved
 * configuration on every entry and Cancel needs no explicit reset.
 */
function RulesForm({ configuration, onDone }: { configuration: BookingConfiguration; onDone: () => void }) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const form = useForm({
    schema: BookingConfigurationUpdateInputSchema,
    initialInput: {
      enabled: configuration.enabled,
      timezone: configuration.timezone,
      slotGranularityMinutes: configuration.slotGranularityMinutes,
      openingStart: configuration.openingStart,
      openingEnd: configuration.openingEnd,
      bufferBeforeMinutes: configuration.bufferBeforeMinutes,
      bufferAfterMinutes: configuration.bufferAfterMinutes,
      maxBookingDurationMinutes: configuration.maxBookingDurationMinutes,
      allowDoubleBooking: configuration.allowDoubleBooking,
    },
  });
  const updateMutation = useMutation({
    mutationFn: (input: BookingConfigurationUpdateInput) => updateBookingConfiguration(configuration.id, input, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      onDone();
    },
  });

  return (
    <Form of={form} className="max-w-2xl space-y-8" onSubmit={(input) => updateMutation.mutateAsync(input)}>
      <RenderFields
        fields={bookingConfigurationFields.filter((field) => field.name !== "target")}
        form={form}
        disabled={updateMutation.isPending}
      />
      <SchedulingSettingsFields form={form} disabled={updateMutation.isPending} />
      {updateMutation.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t("bookableItems.editError")}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" disabled={updateMutation.isPending} aria-busy={updateMutation.isPending}>
          {t("bookableItems.actions.save")}
        </Button>
        <Button type="button" variant="ghost" disabled={updateMutation.isPending} onClick={onDone}>
          {t("bookableItemDetails.cancelEdit")}
        </Button>
      </div>
    </Form>
  );
}

function Details({ globalId }: { globalId: string }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const { tab = "details", edit = false } = useSearch({ from: "/booking/bookable-items/$globalId" });
  const navigate = useNavigate({ from: "/booking/bookable-items/$globalId" });
  const [cutoff] = useState(() => new Date().toISOString());
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

  const { data } = configuration;
  // The mode is gated on the role, not just the button: a non-administrator who
  // arrives on ?edit=1 still gets the read-only page.
  const canEdit = currentUser.hasSysAdminRole;
  const editing = canEdit && edit;
  // Default values are dropped rather than written, so the tidy state is a bare
  // URL and only a non-default tab or an open editor shows up in the address bar.
  const setSearch = (next: { tab?: "details" | "audit"; edit?: boolean }) =>
    void navigate({
      search: (current) => {
        const merged = { ...current, ...next };
        return {
          ...(merged.tab === "audit" ? { tab: "audit" as const } : {}),
          ...(merged.edit === true ? { edit: true } : {}),
        };
      },
      replace: true,
    });

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Tabs.Root
        value={tab}
        onValueChange={(value) => setSearch({ tab: value === "audit" ? "audit" : "details", edit: false })}
        className="space-y-6"
      >
        <SpotlightHeader
          configuration={data}
          target={target}
          action={
            canEdit && !editing && tab === "details" ? (
              <Button type="button" variant="outline" onClick={() => setSearch({ edit: true })}>
                <PencilIcon aria-hidden="true" />
                {t("bookableItemDetails.edit")}
              </Button>
            ) : null
          }
        />

        <Tabs.List className="flex flex-wrap border-b">
          <PageTab value="details">{t("bookableItemDetails.tabs.details")}</PageTab>
          <PageTab value="audit">{t("bookableItemDetails.tabs.audit")}</PageTab>
        </Tabs.List>

        <Tabs.Panel value="details" className="space-y-8 outline-none">
          <Card>
            <CardHeader>
              <CardTitle>{t("bookableItemDetails.rules")}</CardTitle>
            </CardHeader>
            <CardContent>
              {editing ? (
                <RulesForm configuration={data} onDone={() => setSearch({ edit: false })} />
              ) : (
                <RulesReadOut configuration={data} />
              )}
            </CardContent>
          </Card>

          <section className="space-y-4" aria-labelledby="upcoming-events-heading">
            <Heading level={3} as="h2" id="upcoming-events-heading">
              {t("bookableItemDetails.upcoming")}
            </Heading>
            <BookingEventList globalId={globalId} timezone={data.timezone} period="upcoming" cutoff={cutoff} />
          </section>

          <section className="space-y-4" aria-labelledby="past-events-heading">
            <Heading level={3} as="h2" id="past-events-heading">
              {t("bookableItemDetails.past")}
            </Heading>
            <BookingEventList globalId={globalId} timezone={data.timezone} period="past" cutoff={cutoff} />
          </section>
        </Tabs.Panel>

        <Tabs.Panel value="audit" className="outline-none">
          <BookableItemAuditLog configurationId={data.id} />
        </Tabs.Panel>
      </Tabs.Root>
    </main>
  );
}

export default function BookableItemPage() {
  const { globalId } = useParams({ from: "/booking/bookable-items/$globalId" });
  return <Details globalId={globalId} key={globalId} />;
}
