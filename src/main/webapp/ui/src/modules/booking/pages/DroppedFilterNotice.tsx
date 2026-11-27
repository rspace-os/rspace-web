import { uniq } from "es-toolkit";
import { useTranslation } from "react-i18next";
import { bookingLocale } from "@/modules/booking/domain/bookingTime";
import { formatList } from "@/modules/common/i18n/listFormat";

/** Labels of the filter fields that saved-view recovery can drop on the booking pages. */
const droppedFieldLabelKeys = {
  availability: "calendar.availability",
  enabled: "allBookableItems.fields.enabled",
  "requester.value": "calendar.fields.requester",
  state: "allBookableItems.fields.status",
  targetOwner: "filters.owner",
} as const;

function isKnownField(field: string): field is keyof typeof droppedFieldLabelKeys {
  return Object.hasOwn(droppedFieldLabelKeys, field);
}

export function DroppedFilterNotice({ fields }: { fields: readonly string[] }) {
  const { t } = useTranslation("booking");
  const labels = uniq(fields.map((field) => (isKnownField(field) ? t(droppedFieldLabelKeys[field]) : field)));
  return labels.length > 0 ? (
    <p role="status" className="py-3 text-sm">
      {t("filters.dropped", { fields: formatList(labels, bookingLocale()) })}
    </p>
  ) : null;
}
