import type { ReactNode } from "react";
import { Button } from "@/modules/common/ui/button";

export function DashboardError({
  title,
  description,
  retryLabel,
  onRetry,
}: {
  title: string;
  description: string;
  retryLabel: string;
  onRetry: () => void;
}) {
  return (
    <div role="alert" className="space-y-3 p-6 text-sm">
      <p className="font-medium text-foreground">{title}</p>
      <p className="text-muted-foreground">{description}</p>
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        {retryLabel}
      </Button>
    </div>
  );
}

export function DashboardEmpty({ children }: { children: ReactNode }) {
  return <p className="p-6 text-sm text-muted-foreground">{children}</p>;
}
