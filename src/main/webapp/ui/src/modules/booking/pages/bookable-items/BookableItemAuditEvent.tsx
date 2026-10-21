import { Link } from "@tanstack/react-router";
import { ChevronRightIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { UserBadge } from "@/modules/common/ui/user-badge";
import type { AuditRow } from "./bookableItemAudit";
import { recordedValues } from "./bookableItemAudit";

function RecordedValues({ row }: { row: AuditRow }) {
  const { t, i18n } = useTranslation("booking");
  const labels: Record<string, string> = {
    start: t("bookableItemDetails.audit.values.start"),
    end: t("bookableItemDetails.audit.values.end"),
    kind: t("bookableItemDetails.audit.values.kind"),
    purpose: t("bookableItemDetails.audit.values.purpose"),
    state: t("bookableItemDetails.audit.values.state"),
    target: t("bookableItemDetails.audit.values.target"),
    bookingConfigurationId: t("bookableItemDetails.audit.values.configuration"),
    enabled: t("bookableItemDetails.audit.values.enabled"),
    timezone: t("bookableItemDetails.audit.values.timezone"),
    openingHours: t("bookableItemDetails.audit.values.openingHours"),
    openingStart: t("bookableItemDetails.audit.values.openingStart"),
    openingEnd: t("bookableItemDetails.audit.values.openingEnd"),
    slotGranularityMinutes: t("bookableItemDetails.audit.values.increment"),
    maxBookingDurationMinutes: t("bookableItemDetails.audit.values.maximumDuration"),
    bufferBeforeMinutes: t("bookableItemDetails.audit.values.bufferBefore"),
    bufferAfterMinutes: t("bookableItemDetails.audit.values.bufferAfter"),
    allowDoubleBooking: t("bookableItemDetails.audit.values.allowDoubleBooking"),
  };
  return (
    <dl className="grid min-w-0 grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
      {recordedValues(row.payload, i18n.language).map(([label, value]) => (
        <div className="contents" key={label}>
          <dt className="break-words text-muted-foreground">{labels[label] ?? label}</dt>
          <dd className="break-words">{value === "{}" ? t("bookableItemDetails.audit.values.empty") : value}</dd>
        </div>
      ))}
    </dl>
  );
}

function bookingId(target: string | null | undefined): string | null {
  const match = target?.match(/^bookings:(\d+)$/);
  return match?.[1] ?? null;
}

function AuditTarget({ target }: { target: AuditRow["target"] }) {
  const id = bookingId(target);
  return id === null ? (
    <span>{target ?? "—"}</span>
  ) : (
    <Link className="underline" to="/booking/calendar/bookings/$id" params={{ id }}>
      {target}
    </Link>
  );
}

function AuditTimestamp({ value }: { value: string }) {
  const { i18n } = useTranslation("booking");
  return (
    <time dateTime={value}>
      {new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "long", timeZone: "UTC" }).format(
        new Date(value),
      )}
    </time>
  );
}

/**
 * Delegated changes are recorded as "subject=<username>", and a permanent delete also as
 * "permanent=true". Show the subject in words and drop the marker the action already states; any
 * other text, such as older free-form descriptions, passes through unchanged.
 */
function readableDescription(
  description: string | null | undefined,
  format: (parts: string[], subject: string | undefined) => string,
): string | null {
  if (description === null || description === undefined) return null;
  const parts = description.split(";").map((part) => part.trim());
  const subject = parts.find((part) => part.startsWith("subject="))?.slice("subject=".length);
  const rest = parts.filter((part) => part !== "permanent=true" && !part.startsWith("subject="));
  return format(rest, subject) || null;
}

export function AuditEventItem({ row }: { row: AuditRow }) {
  const { t } = useTranslation(["booking", "common"]);
  const [open, setOpen] = useState(false);
  const description = readableDescription(row.description, (parts, subject) => {
    const text = parts.reduce(
      (description, part, index) =>
        index > 0 ? t("booking:bookableItemDetails.audit.descriptionParts", { description, part }) : part,
      "",
    );
    return subject
      ? parts.length > 0
        ? t("booking:bookableItemDetails.audit.descriptionWithSubject", { description: text, name: subject })
        : t("booking:bookableItemDetails.audit.onBehalfOf", { name: subject })
      : text;
  });
  return (
    <li>
      <article aria-label={description ?? row.action} className="overflow-hidden rounded-sm border bg-card">
        <Collapsible open={open} onOpenChange={setOpen}>
          <div className="space-y-3 p-4">
            <div className="flex items-start justify-between gap-3">
              <AuditTimestamp value={row.timestamp} />
              <CollapsibleTrigger
                render={
                  <Button type="button" size="sm" variant="ghost">
                    {open ? t("common:actions.collapse") : t("common:actions.expand")}
                    <ChevronRightIcon
                      aria-hidden="true"
                      className={`transition-transform ${open ? "rotate-90" : ""}`}
                    />
                  </Button>
                }
              />
            </div>
            <div>
              <Badge variant="outline">{row.action}</Badge>
              {description === null ? null : <p className="mt-2 text-muted-foreground">{description}</p>}
            </div>
            <dl className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] items-center gap-4 text-sm">
              <div className="flex min-w-0 items-center gap-2">
                <dt className="shrink-0 text-muted-foreground">
                  {t("booking:bookableItemDetails.audit.fields.actor")}
                </dt>
                <dd className="min-w-0">
                  <UserBadge name={row.fullName ?? row.username} username={row.username} />
                </dd>
              </div>
              <div className="flex min-w-0 items-center gap-2">
                <dt className="shrink-0 text-muted-foreground">{t("booking:bookableItems.fields.id")}</dt>
                <dd className="min-w-0 truncate">
                  <AuditTarget target={row.target} />
                </dd>
              </div>
            </dl>
          </div>
          <CollapsibleContent className="border-t px-4 py-3">
            <h3 className="mb-2 font-medium">{t("booking:bookableItemDetails.audit.fields.values")}</h3>
            <RecordedValues row={row} />
          </CollapsibleContent>
        </Collapsible>
      </article>
    </li>
  );
}
