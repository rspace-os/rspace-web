import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Button } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
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
  });

  if (configuration.isPending) {
    return <BookableItemSkeleton />;
  }

  const target = configuration.data?.target;
  if (configuration.isError || target === null || target === undefined || target.globalId !== globalId) {
    return (
      <main className="p-4 sm:p-8">
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>{t("bookableItemDetails.error.title")}</EmptyTitle>
            <EmptyDescription>{t("bookableItemDetails.error.description")}</EmptyDescription>
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
