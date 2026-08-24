import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Heading } from "@/modules/common/ui/typography";
import { BookingEventList } from "./BookingEventList";
import { fetchBookingConfigurationByTarget } from "./bookingConfiguration";

function Details({ globalId }: { globalId: string }) {
  const { t, i18n } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
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
  const updatedAt =
    data.updatedAt === null || data.updatedAt === undefined ? (
      t("bookableItemDetails.notAvailable")
    ) : (
      <time dateTime={data.updatedAt}>
        {new Intl.DateTimeFormat(i18n.language, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: data.timezone,
        }).format(new Date(data.updatedAt))}
      </time>
    );
  const facts: Array<[string, ReactNode]> = [
    [t("bookableItemDetails.fields.timezone"), data.timezone],
    [t("bookableItemDetails.fields.updatedAt"), updatedAt],
    [t("bookableItemDetails.fields.openingHours"), `${data.openingStart}–${data.openingEnd}`],
    [
      t("bookableItemDetails.fields.granularity"),
      t("bookableItemDetails.minutes", { count: data.slotGranularityMinutes }),
    ],
    [
      t("bookableItemDetails.fields.maximumDuration"),
      data.maxBookingDurationMinutes === 0
        ? t("bookableItemDetails.unlimited")
        : t("bookableItemDetails.minutes", { count: data.maxBookingDurationMinutes }),
    ],
    [
      t("bookableItemDetails.fields.bufferBefore"),
      t("bookableItemDetails.minutes", { count: data.bufferBeforeMinutes }),
    ],
    [t("bookableItemDetails.fields.bufferAfter"), t("bookableItemDetails.minutes", { count: data.bufferAfterMinutes })],
    [
      t("bookableItemDetails.fields.doubleBooking"),
      data.allowDoubleBooking ? t("bookableItemDetails.yes") : t("bookableItemDetails.no"),
    ],
  ];

  return (
    <main className="space-y-8 p-4 sm:p-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-3">
          <Heading level={2} as="h1">
            {t("bookableItemDetails.title")}
          </Heading>
          <InventoryItem
            name={target.value.name}
            globalId={target.globalId}
            href={`/globalId/${target.globalId}`}
            idLinkLabel={t("bookableItemDetails.viewInventory", { name: target.value.name })}
          />
        </div>
        <div className="flex items-center gap-3">
          <Badge variant={data.enabled ? "default" : "secondary"}>
            {data.enabled ? t("bookableItemDetails.enabled") : t("bookableItemDetails.disabled")}
          </Badge>
          {currentUser.hasSysAdminRole ? (
            <Link
              className={buttonVariants({ variant: "outline" })}
              to="/booking/config/bookable-items/$id/edit"
              params={{ id: String(data.id) }}
            >
              {t("bookableItemDetails.edit")}
            </Link>
          ) : null}
        </div>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>{t("bookableItemDetails.rules")}</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-[max-content_1fr]">
            {facts.map(([label, value]) => (
              <div className="grid gap-1 sm:contents" key={label}>
                <dt className="font-medium">{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
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
    </main>
  );
}

export default function BookableItemPage() {
  const { globalId } = useParams({ from: "/booking/bookable-items/$globalId" });
  return <Details globalId={globalId} key={globalId} />;
}
