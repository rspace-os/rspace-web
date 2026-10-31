import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { LocalizedTableListError } from "../utils/errors";

export function TableListErrorState({ error }: { error: unknown }) {
  const { t } = useTranslation("common");
  return (
    <Empty className="rounded-sm border-0 p-6">
      <EmptyHeader>
        <EmptyTitle>{t("tableList.error.title")}</EmptyTitle>
        <EmptyDescription>
          {error instanceof LocalizedTableListError ? error.message : t("tableList.error.description")}
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
