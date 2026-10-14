import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { isPermanentClientError, retryUnlessClientError } from "../queryRetry";
import { BookableItemSkeleton } from "./BookableItemSkeleton";
import { fetchBookingConfigurationDetailsByTarget } from "./bookingConfiguration";
import { type BookableItemTab, LoadedBookableItemPage } from "./LoadedBookableItemPage";

export function BookableItemDetailsLoader({ globalId, tab }: { globalId: string; tab: BookableItemTab }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const configuration = useQuery({
    queryKey: ["api-v2", "booking-configurations", "target", globalId],
    queryFn: ({ signal }) => fetchBookingConfigurationDetailsByTarget(globalId, token, signal),
    // A missing or unreadable item answers the same way on every attempt, so show it at once.
    retry: retryUnlessClientError,
  });

  if (configuration.isPending) {
    return <BookableItemSkeleton />;
  }

  const target = configuration.data?.target;
  if (configuration.isError || target === null || target === undefined || target.globalId !== globalId) {
    const notFound = !configuration.isError || isPermanentClientError(configuration.error);
    return (
      <main className="p-4 sm:p-8">
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>
              {t(notFound ? "bookableItemDetails.notFound.title" : "bookableItemDetails.error.title")}
            </EmptyTitle>
            <EmptyDescription>
              {t(notFound ? "bookableItemDetails.notFound.description" : "bookableItemDetails.error.description")}
            </EmptyDescription>
          </EmptyHeader>
          <Button type="button" variant="outline" onClick={() => void configuration.refetch()}>
            {commonT("actions.retry")}
          </Button>
        </Empty>
      </main>
    );
  }

  return <LoadedBookableItemPage configuration={configuration.data} globalId={globalId} tab={tab} token={token} />;
}
