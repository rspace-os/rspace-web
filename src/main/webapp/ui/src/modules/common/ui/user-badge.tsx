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

/**
 * Complete class names (Tailwind cannot compile interpolated ones). Each pair keeps the initials at
 * 6.8:1 contrast or better, and the dark pair is the same two shades swapped.
 */
const avatarSwatches = [
  "bg-sky-200 text-sky-900 dark:bg-sky-900 dark:text-sky-200",
  "bg-emerald-200 text-emerald-900 dark:bg-emerald-900 dark:text-emerald-200",
  "bg-amber-200 text-amber-900 dark:bg-amber-900 dark:text-amber-200",
  "bg-violet-200 text-violet-900 dark:bg-violet-900 dark:text-violet-200",
  "bg-rose-200 text-rose-900 dark:bg-rose-900 dark:text-rose-200",
  "bg-teal-200 text-teal-900 dark:bg-teal-900 dark:text-teal-200",
  "bg-fuchsia-200 text-fuchsia-900 dark:bg-fuchsia-900 dark:text-fuchsia-200",
  "bg-lime-200 text-lime-900 dark:bg-lime-900 dark:text-lime-200",
] as const;

/**
 * A stable avatar colour for one identity, so two people with the same initials still look
 * different. Seed it with the username, or the full name when there is none.
 */
export function userSwatch(seed: string): string {
  // FNV-1a: tiny, and stable across sessions and browsers.
  let hash = 0x811c9dc5;
  for (let index = 0; index < seed.length; index += 1) {
    hash ^= seed.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return avatarSwatches[(hash >>> 0) % avatarSwatches.length];
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
      title={label}
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
          className={cn(userSwatch(username || name), compact && "group-data-[size=sm]/avatar:text-[8px] leading-none")}
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
