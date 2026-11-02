import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { bookingConfigurationTargetQuery } from "@/modules/booking/pages/bookable-items/bookingConfigurationApi";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { isPermanentClientError } from "@/modules/common/queries/queryRetry";
import { Button } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { BookableItemSkeleton } from "./BookableItemSkeleton";
import { type BookableItemTab, LoadedBookableItemPage } from "./LoadedBookableItemPage";

export function BookableItemDetailsLoader({ globalId, tab }: { globalId: string; tab: BookableItemTab }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const configuration = useQuery({
    ...bookingConfigurationTargetQuery(globalId, token),
  });

  if (configuration.isPending) {
    return <BookableItemSkeleton />;
  }

  const target = configuration.data?.target;
  if (
    configuration.data === undefined ||
    isPermanentClientError(configuration.error) ||
    target === null ||
    target === undefined ||
    target.globalId !== globalId
  ) {
    const notFound = !configuration.isError || isPermanentClientError(configuration.error);
    return (
      <main className="p-4 sm:p-8">
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>
              {notFound ? t("bookableItemDetails.notFound.title") : t("bookableItemDetails.error.title")}
            </EmptyTitle>
            <EmptyDescription>
              {notFound ? t("bookableItemDetails.notFound.description") : t("bookableItemDetails.error.description")}
            </EmptyDescription>
          </EmptyHeader>
          <Button type="button" variant="outline" onClick={() => void configuration.refetch()}>
            {commonT("actions.retry")}
          </Button>
        </Empty>
      </main>
    );
  }

  return (
    <LoadedBookableItemPage
      configuration={configuration.data}
      globalId={globalId}
      tab={tab}
      token={token}
      readError={configuration.isError}
      retrying={configuration.isFetching}
      onRetry={() => void configuration.refetch()}
    />
  );
}
