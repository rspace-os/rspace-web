import { InfoIcon } from "lucide-react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { Card, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { BookingItemInformationContent } from "./BookingItemInformationContent";

export function BookingItemInformationCard({
  item,
  displayTimezone,
  as: Container = "aside",
}: {
  item: BookableItemOption;
  displayTimezone: string;
  as?: "aside" | "section";
}) {
  const { t } = useTranslation("booking");
  const headingId = useId();
  return (
    <Container aria-labelledby={headingId} className="min-w-0 @2xl:sticky @2xl:top-4 @2xl:self-start">
      <Card size="sm" className="gap-0 py-0">
        <CardHeader className="border-b py-4">
          <CardTitle id={headingId} className="flex items-center gap-2">
            <InfoIcon aria-hidden="true" className="size-4" />
            {t("bookings.itemInformation.title")}
          </CardTitle>
        </CardHeader>
        <BookingItemInformationContent item={item} displayTimezone={displayTimezone} />
      </Card>
    </Container>
  );
}
