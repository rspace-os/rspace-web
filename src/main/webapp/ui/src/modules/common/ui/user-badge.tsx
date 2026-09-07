import type * as React from "react";
import { Avatar, AvatarBadge, AvatarFallback, AvatarImage } from "@/modules/common/ui/avatar";
import { Badge } from "@/modules/common/ui/badge";
import { cn } from "@/modules/common/utils/cn";

export type UserBadgeProps = {
  name: string;
  username?: string;
  imageSrc?: string;
  role?: string;
  accountEnabled?: boolean;
  density?: "default" | "compact";
  className?: string;
};

function displayName(name: string, username: string | undefined): string {
  if (!username || name === username || name.includes(`(${username})`)) return name;
  return `${name} (${username})`;
}

export function userInitials(name: string): string {
  const identity = name
    .replace(/\s*\([^)]*\)\s*$/, "")
    .replace(/^(?:dr|prof)\.?\s+/i, "")
    .trim();
  const parts = identity.split(/[\s._-]+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return `${parts[0]?.[0] ?? ""}${parts.length > 1 ? (parts.at(-1)?.[0] ?? "") : ""}`.toUpperCase();
}

/** A compact, non-interactive identity badge. It never fetches user data. */
export function UserBadge({
  name,
  username,
  imageSrc,
  role,
  accountEnabled = true,
  density = "default",
  className,
}: UserBadgeProps): React.ReactNode {
  const label = displayName(name, username);
  const compact = density === "compact";

  return (
    <span
      data-slot="user-badge"
      className={cn(
        "inline-flex max-w-full min-w-0 items-center rounded-full border bg-background text-foreground",
        compact ? "gap-1 py-0 pr-1.5 pl-0.5 text-[10px] leading-4" : "gap-1.5 py-0.5 pr-2.5 pl-0.5 text-sm",
        !accountEnabled && "text-muted-foreground",
        className,
      )}
    >
      <Avatar size="sm" className={cn(compact && "data-[size=sm]:size-4")} aria-hidden="true">
        {imageSrc ? <AvatarImage src={imageSrc} alt="" /> : null}
        <AvatarFallback
          className={cn("text-foreground", compact && "group-data-[size=sm]/avatar:text-[8px] leading-none")}
        >
          {userInitials(label)}
        </AvatarFallback>
        {!accountEnabled ? <AvatarBadge className="bg-muted-foreground" /> : null}
      </Avatar>
      <span className="truncate">{label}</span>
      {role ? <Badge variant={role === "PI" ? "default" : "secondary"}>{role}</Badge> : null}
    </span>
  );
}
