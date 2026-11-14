import type { ReactNode } from "react";
import { UserBadge } from "@/modules/common/ui/user-badge";

const labelClassName = "text-muted-foreground text-xs";
const valueClassName = "text-xs leading-4";

type BookingDetailRowsProps = {
  actor?: string | null;
  showActor: boolean;
  actorLabel: string;
  detailsLabel: string;
  details: ReactNode;
  rowClassName: string;
};

export function BookingDetailRows({
  actor,
  showActor,
  actorLabel,
  detailsLabel,
  details,
  rowClassName,
}: BookingDetailRowsProps) {
  return (
    <>
      {showActor ? (
        <div className={rowClassName}>
          <dt className={labelClassName}>{actorLabel}</dt>
          <dd className="min-w-0">
            <UserBadge name={actor ?? ""} />
          </dd>
        </div>
      ) : null}
      <div className={rowClassName}>
        <dt className={labelClassName}>{detailsLabel}</dt>
        <dd className={valueClassName}>{details}</dd>
      </div>
    </>
  );
}
