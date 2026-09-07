import { cn } from "@/modules/common/utils/cn";

function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("animate-pulse rounded-sm bg-muted forced-colors:border", className)}
      {...props}
    />
  );
}

export { Skeleton };
