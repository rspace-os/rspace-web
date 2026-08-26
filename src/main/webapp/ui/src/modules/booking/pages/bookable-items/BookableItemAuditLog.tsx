import { useQuery } from "@tanstack/react-query";
import { CalendarRangeIcon, SearchIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { TableList } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { InputGroup, InputGroupAddon, InputGroupText } from "@/modules/common/ui/input-group";
import { type AuditRow, fetchBookingConfigurationAudit, recordedValues } from "./bookableItemAudit";

const PRESET_DAYS = [7, 30, 90] as const;

// Decorative only: both inputs carry their own aria-label, so this glyph is
// hidden from assistive technology and never needs translating.
const RANGE_SEPARATOR = "\u2013";

function plainDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function daysAgo(days: number, today: Date): string {
  const from = new Date(today);
  from.setUTCDate(from.getUTCDate() - days);
  return plainDate(from);
}

function RecordedValues({ row }: { row: AuditRow }) {
  const values = recordedValues(row.payload);
  return (
    <dl className="grid min-w-52 grid-cols-[max-content_1fr] gap-x-3 gap-y-1 font-mono text-xs">
      {values.map(([label, value]) => (
        <div className="contents" key={label}>
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="break-all">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

const auditEventConfig = resolveCollectionConfig<AuditRow>({
  slug: "booking-configuration-audit",
  idField: "rowId",
  labels: {
    singularKey: "booking:bookableItemDetails.audit.singular",
    pluralKey: "booking:bookableItemDetails.audit.plural",
  },
  useAsTitle: "timestamp",
  defaultColumns: ["timestamp", "username", "action", "payload"],
  listSearchableFields: ["username", "fullName", "action", "description"],
  fields: [
    { name: "rowId", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.rowId", list: false },
    {
      name: "timestamp",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.timestamp",
      list: { width: 200, minWidth: 170, renderCell: ({ row }) => <AuditTimestamp value={row.timestamp} /> },
    },
    {
      name: "username",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.actor",
      list: {
        width: 190,
        minWidth: 160,
        dependencies: ["fullName"],
        renderCell: ({ row }) => (
          <span>
            <span className="block font-medium">{row.fullName ?? row.username}</span>
            <span className="text-xs text-muted-foreground">{row.username}</span>
          </span>
        ),
      },
    },
    { name: "fullName", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.fullName" },
    {
      name: "action",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.action",
      list: {
        width: 300,
        minWidth: 220,
        dependencies: ["description"],
        renderCell: ({ row }) => (
          <span>
            <Badge variant="outline">{row.action}</Badge>
            {row.description === null || row.description === undefined ? null : (
              <span className="mt-2 block whitespace-normal text-muted-foreground">{row.description}</span>
            )}
          </span>
        ),
      },
    },
    { name: "description", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.description" },
    { name: "domain", type: "text", labelKey: "booking:bookableItemDetails.audit.fields.domain", list: false },
    {
      name: "payload",
      type: "text",
      labelKey: "booking:bookableItemDetails.audit.fields.values",
      list: {
        width: 320,
        minWidth: 240,
        card: { fullWidth: true },
        renderCell: ({ row }) => <RecordedValues row={row} />,
      },
    },
  ],
});

function AuditTimestamp({ value }: { value: string }) {
  const { i18n } = useTranslation("booking");
  return (
    <time dateTime={value}>
      {new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "medium" }).format(new Date(value))}
    </time>
  );
}

export function BookableItemAuditLog({ configurationId }: { configurationId: number }) {
  const { t } = useTranslation("booking");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  // One clock reading per mount, so the presets and the query agree even if the
  // tab stays open across midnight.
  const [today] = useState(() => new Date());
  const [range, setRange] = useState(() => ({ from: daysAgo(30, today), to: plainDate(today) }));
  const [applied, setApplied] = useState(range);
  const [page, setPage] = useState(0);

  const activePreset = PRESET_DAYS.find((days) => range.from === daysAgo(days, today) && range.to === plainDate(today));

  const audit = useQuery({
    queryKey: ["api-v2", "booking-configurations", configurationId, "audit", applied.from, applied.to, page],
    queryFn: ({ signal }) =>
      fetchBookingConfigurationAudit({
        configurationId,
        // UTC day boundaries. The audit trail is a system-wide record and is not
        // scoped to the bookable item's own timezone, so the picked dates are
        // read as UTC days rather than local ones.
        dateFrom: `${applied.from}T00:00:00Z`,
        dateTo: `${applied.to}T23:59:59Z`,
        page,
        token,
        signal,
      }),
  });

  const table = useTableList({
    config: auditEventConfig,
    dataSource: { type: "client", rows: audit.data?.rows ?? [] },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });

  const applyRange = (next: { from: string; to: string }) => {
    setRange(next);
    setApplied(next);
    setPage(0);
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="mr-1 text-sm font-medium">{t("bookableItemDetails.audit.period")}</span>
        {PRESET_DAYS.map((days) => (
          <Button
            key={days}
            type="button"
            size="sm"
            variant={activePreset === days ? "default" : "outline"}
            aria-pressed={activePreset === days}
            onClick={() => applyRange({ from: daysAgo(days, today), to: plainDate(today) })}
          >
            {t("bookableItemDetails.audit.lastDays", { count: days })}
          </Button>
        ))}
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <InputGroup className="w-auto">
            <InputGroupAddon>
              <CalendarRangeIcon aria-hidden="true" />
            </InputGroupAddon>
            <input
              aria-label={t("bookableItemDetails.audit.from")}
              className="h-9 bg-transparent text-sm outline-none"
              type="date"
              max={range.to}
              value={range.from}
              onChange={(event) => setRange({ ...range, from: event.currentTarget.value })}
            />
            <InputGroupText aria-hidden="true" className="px-2">
              {RANGE_SEPARATOR}
            </InputGroupText>
            <input
              aria-label={t("bookableItemDetails.audit.to")}
              className="h-9 bg-transparent pr-3 text-sm outline-none"
              type="date"
              min={range.from}
              value={range.to}
              onChange={(event) => setRange({ ...range, to: event.currentTarget.value })}
            />
          </InputGroup>
          <Button type="button" onClick={() => applyRange(range)}>
            <SearchIcon aria-hidden="true" />
            {t("bookableItemDetails.audit.apply")}
          </Button>
        </div>
      </div>

      <TableList
        {...table.tableProps}
        status={audit.isPending ? "loading" : audit.isError ? "error" : "idle"}
        error={audit.error}
        presentations={{ table: "wide", cards: "narrow" }}
        emptyDescription={t("bookableItemDetails.audit.empty")}
        variant="transparent"
      />

      {(audit.data?.totalPages ?? 0) > 1 ? (
        <nav aria-label={t("bookableItemDetails.audit.pagination")} className="flex items-center justify-between">
          <Button type="button" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>
            {t("bookableItemDetails.audit.previous")}
          </Button>
          <span className="text-sm">
            {t("bookableItemDetails.audit.page", { page: page + 1, totalPages: audit.data?.totalPages ?? 1 })}
          </span>
          <Button
            type="button"
            variant="outline"
            disabled={page + 1 >= (audit.data?.totalPages ?? 1)}
            onClick={() => setPage(page + 1)}
          >
            {t("bookableItemDetails.audit.next")}
          </Button>
        </nav>
      ) : null}
    </div>
  );
}
