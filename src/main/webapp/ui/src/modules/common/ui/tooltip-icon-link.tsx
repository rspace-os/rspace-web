import type { ReactElement, ReactNode } from "react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";

type TooltipIconLinkProps = {
  label: string;
  icon: ReactNode;
  children: ReactElement;
};

export function TooltipIconLink({ label, icon, children }: TooltipIconLinkProps) {
  return (
    <Tooltip>
      <TooltipTrigger render={children}>{icon}</TooltipTrigger>
      <TooltipContent role="tooltip" className="rounded-sm">
        {label}
      </TooltipContent>
    </Tooltip>
  );
}
