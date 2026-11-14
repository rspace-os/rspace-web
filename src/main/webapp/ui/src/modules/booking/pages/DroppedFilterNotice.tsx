import { useTranslation } from "react-i18next";

export function DroppedFilterNotice({ fields }: { fields: readonly string[] }) {
  const { t } = useTranslation("booking");
  return fields.length > 0 ? (
    <p role="status" className="py-3 text-sm">
      {t("filters.dropped", { fields: [...new Set(fields)].join(", ") })}
    </p>
  ) : null;
}
