import { useSuspenseQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { QueryBoundary } from "@/modules/common/queries/QueryBoundary";
import { isPermanentClientError } from "@/modules/common/queries/queryRetry";
import { Button } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { BookableItemSkeleton } from "./BookableItemSkeleton";
import { bookingConfigurationTargetQuery } from "./bookingConfigurationApi";
import { type BookableItemTab, LoadedBookableItemPage } from "./LoadedBookableItemPage";

function BookableItemUnavailable({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  return (
    <main className="p-4 sm:p-8">
      <Empty className="border">
        <EmptyHeader>
          <EmptyTitle>{t("bookableItemDetails.error.title")}</EmptyTitle>
          <EmptyDescription>{t("bookableItemDetails.error.description")}</EmptyDescription>
        </EmptyHeader>
        <Button type="button" variant="outline" onClick={onRetry}>
          {commonT("actions.retry")}
        </Button>
      </Empty>
    </main>
  );
}

function BookableItemDetails({ globalId, tab }: { globalId: string; tab: BookableItemTab }) {
  const configuration = useSuspenseQuery(bookingConfigurationTargetQuery(globalId));
  const document = configuration.data;
  const target = document?.target;
  if (!document || isPermanentClientError(configuration.error) || !target || target.globalId !== globalId) {
    return <BookableItemUnavailable onRetry={() => void configuration.refetch()} />;
  }

  return (
    <LoadedBookableItemPage
      configuration={document}
      globalId={globalId}
      tab={tab}
      readError={configuration.isError}
      retrying={configuration.isFetching}
      onRetry={() => void configuration.refetch()}
    />
  );
}

export function BookableItemDetailsLoader({ globalId, tab }: { globalId: string; tab: BookableItemTab }) {
  return (
    <QueryBoundary
      fallback={<BookableItemSkeleton />}
      error={({ retry }) => <BookableItemUnavailable onRetry={retry} />}
    >
      <BookableItemDetails globalId={globalId} tab={tab} />
    </QueryBoundary>
  );
}
