import { useTranslation } from "react-i18next";
import { type AuditRow, recordedValues } from "./bookableItemAudit";

export function RecordedValues({ row }: { row: AuditRow }) {
  const { t } = useTranslation("booking");
  const labels: Record<string, string> = {
    deletedAt: t("bookableItemDetails.audit.values.deletedAt"),
  };
  return (
    <dl className="grid min-w-52 grid-cols-[max-content_1fr] gap-x-3 gap-y-1 font-mono text-xs">
      {recordedValues(row.payload).map(([label, value]) => (
        <div className="contents" key={label}>
          <dt className="text-muted-foreground">{labels[label] ?? label}</dt>
          <dd className="break-all">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function bookingId(target: string | null | undefined): string | null {
  const match = target?.match(/^bookings:(\d+)$/);
  return match?.[1] ?? null;
}

export function AuditTimestamp({ value }: { value: string }) {
  const { i18n } = useTranslation("booking");
  return (
    <time dateTime={value}>
      {new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "medium" }).format(new Date(value))}
    </time>
  );
}
