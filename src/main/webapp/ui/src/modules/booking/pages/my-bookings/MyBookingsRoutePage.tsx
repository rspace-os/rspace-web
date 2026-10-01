import { useQueryState } from "nuqs";
import { myBookingsPeriodParser } from "./routes";
import { UserBookingsPage, type UserBookingsPageProps } from "./UserBookingsPage";

export function MyBookingsRoutePage({ requesterId, title }: Pick<UserBookingsPageProps, "requesterId" | "title">) {
  const [period, setPeriod] = useQueryState("period", myBookingsPeriodParser);
  return (
    <UserBookingsPage
      requesterId={requesterId}
      title={title}
      period={period}
      onPeriodChange={(nextPeriod) => void setPeriod(nextPeriod)}
    />
  );
}
