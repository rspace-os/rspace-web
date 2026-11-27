import type { Namespace } from "i18next";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/config";
import { cn } from "@/modules/common/utils/cn";

export function TableListHeader<TDocument>({
  config,
  collectionLabel,
  createAction,
  headingClassName,
  divided,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  collectionLabel: string;
  createAction?: ReactNode;
  headingClassName?: string;
  divided: boolean;
}) {
  const { t } = useTranslation<Namespace>("common");

  return (
    <header className={cn("flex flex-col justify-between gap-5 sm:flex-row sm:items-end", divided && "border-b pb-5")}>
      <div>
        <h1 className={cn("font-heading text-3xl font-bold tracking-tight", headingClassName)}>{collectionLabel}</h1>
        {config.labels.descriptionKey ? (
          <p className="mt-2 max-w-2xl text-sm text-muted-foreground">{t(config.labels.descriptionKey)}</p>
        ) : null}
      </div>
      {createAction}
    </header>
  );
}
