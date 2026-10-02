import { PlusIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";

export function TableListHeader<TDocument>({
  config,
  collectionLabel,
  onCreate,
  createAction,
  createLabel,
  divided,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  collectionLabel: string;
  onCreate?: () => void;
  createAction?: ReactNode;
  createLabel?: string;
  divided: boolean;
}) {
  const { t } = useTranslation("common");
  const translate = (key: string) => t(key as never);

  return (
    <header className={cn("flex flex-col justify-between gap-5 sm:flex-row sm:items-end", divided && "border-b pb-5")}>
      <div>
        <h1 className="font-heading text-3xl font-bold tracking-tight">{collectionLabel}</h1>
        {config.labels.descriptionKey ? (
          <p className="mt-2 max-w-2xl text-sm text-muted-foreground">{translate(config.labels.descriptionKey)}</p>
        ) : null}
      </div>
      {createAction ??
        (onCreate ? (
          <Button onClick={onCreate}>
            <PlusIcon aria-hidden="true" data-icon="inline-start" />
            {createLabel ?? t("tableList.actions.createNew")}
          </Button>
        ) : null)}
    </header>
  );
}
