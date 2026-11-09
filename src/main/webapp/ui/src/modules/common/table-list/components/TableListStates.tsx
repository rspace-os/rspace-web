import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { LocalizedTableListError, RuntimeFieldProjectionLimitError } from "../utils/errors";

function TableListErrorDescription({ error }: { error: unknown }) {
  const { t } = useTranslation("common");
  if (error instanceof RuntimeFieldProjectionLimitError) {
    return t("tableList.error.customFieldColumnLimit", { limit: error.limit });
  }
  if (error instanceof LocalizedTableListError) return error.message;
  return t("tableList.error.description");
}

export function TableListErrorState({ error }: { error: unknown }) {
  const { t } = useTranslation("common");
  return (
    <Empty className="rounded-sm border-0 p-6">
      <EmptyHeader>
        <EmptyTitle>{t("tableList.error.title")}</EmptyTitle>
        <EmptyDescription>
          <TableListErrorDescription error={error} />
        </EmptyDescription>
      </EmptyHeader>
    </Empty>
  );
}

export function TableListEmptyState({ description }: { description?: ReactNode }) {
  const { t } = useTranslation("common");
  return (
    <Empty className="rounded-sm border-0 p-6">
      <EmptyHeader>
        <EmptyTitle>{t("tableList.empty.title")}</EmptyTitle>
        <EmptyDescription>{description ?? t("tableList.empty.description")}</EmptyDescription>
      </EmptyHeader>
    </Empty>
  );
}
