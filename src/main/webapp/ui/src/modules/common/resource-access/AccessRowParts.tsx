import {
  CheckIcon,
  ChevronDownIcon,
  GlobeIcon,
  LockIcon,
  LogOutIcon,
  Trash2Icon,
  Undo2Icon,
  UsersIcon,
} from "lucide-react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Avatar, AvatarFallback } from "@/modules/common/ui/avatar";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import type { AccessRow, AccessRowStatus } from "./accessRows";
import type { ResourceAccessRole } from "./ResourceAccessEditor";
import type { ResourceGrantee } from "./schemas";

/** Two initials, so a user reads as a person before their name is parsed. */
function initialsOf(name: string): string {
  const parts = name.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return `${parts[0][0] ?? ""}${parts.length > 1 ? (parts.at(-1)?.[0] ?? "") : ""}`.toUpperCase();
}

/**
 * The staged-change marker, left of the identity. Colour carries it at a glance; the title and the
 * screen-reader text carry the same meaning, so the state is never colour-only.
 */
export function StatusDot({ status, label }: { status: AccessRowStatus; label: string | null }) {
  if (label === null) return <span className="size-2 shrink-0" aria-hidden="true" />;
  return (
    <span className="shrink-0 leading-none" title={label}>
      <span
        className={cn(
          "block size-2 rounded-full",
          status === "added" && "bg-primary",
          status === "changed" && "bg-amber-500",
          status === "removed" && "bg-destructive",
        )}
        aria-hidden="true"
      />
      <span className="sr-only">{label}</span>
    </span>
  );
}

/** A user reads as an avatar and a name; a group and the audience keep an icon and a name. */
function PrincipalLabel({ grantee }: { grantee: ResourceGrantee }) {
  const { t } = useTranslation("common");
  const name =
    grantee.available || grantee.kind === "AUDIENCE"
      ? grantee.name
      : grantee.kind === "USER"
        ? t("resourceAccess.unavailableUser")
        : t("resourceAccess.unavailableGroup");
  if (grantee.kind === "USER") {
    return (
      <span className="inline-flex min-w-0 items-center gap-1.5">
        <Avatar size="sm" aria-hidden="true">
          <AvatarFallback>{initialsOf(name)}</AvatarFallback>
        </Avatar>
        <span className="truncate font-medium">{name}</span>
      </span>
    );
  }
  const Glyph = grantee.kind === "GROUP" ? UsersIcon : GlobeIcon;
  return (
    <span className="inline-flex min-w-0 items-center gap-1.5">
      <Glyph className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      <span className="truncate font-medium">{name}</span>
    </span>
  );
}

/** Only a group needs a line under its name; an avatar already says who a user is. */
function detailOf(grantee: ResourceGrantee): string | null {
  return grantee.kind !== "AUDIENCE" && grantee.detail ? grantee.detail : null;
}

export function GranteeIdentity({ row, stagedLabel }: { row: AccessRow; stagedLabel: string | null }) {
  const { t } = useTranslation("common");
  const grantee = row.assignment.grantee;
  const detail = detailOf(grantee);
  const kindLabel =
    grantee.kind === "USER"
      ? t("resourceAccess.kind.user")
      : grantee.kind === "GROUP"
        ? t("resourceAccess.kind.group")
        : null;
  return (
    // Two grid rows: the dot shares row 1 with the name line and centres on it, while a group's
    // detail sits in row 2 under the name. A margin cannot do this, because the first line's
    // height varies with the avatar.
    <span className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] items-center gap-x-2">
      <StatusDot status={row.status} label={stagedLabel} />
      <span className="flex min-w-0 flex-wrap items-center gap-1.5 font-medium">
        <PrincipalLabel grantee={grantee} />
        {row.isSelf ? <Badge variant="outline">{t("resourceAccess.you")}</Badge> : null}
        {kindLabel ? <Badge variant="secondary">{kindLabel}</Badge> : null}
        {!grantee.available ? <Badge variant="destructive">{t("resourceAccess.unavailable")}</Badge> : null}
      </span>
      {detail ? <span className="col-start-2 truncate text-xs text-muted-foreground">{detail}</span> : null}
    </span>
  );
}

/**
 * The role control. A Menu rather than a native select, so a role can carry an icon and the trigger
 * can show a lock instead when the row is not the caller's to change.
 */
export function RoleMenu({
  row,
  roles,
  disabled,
  lockReason,
  onChange,
}: {
  row: AccessRow;
  roles: readonly ResourceAccessRole[];
  disabled: boolean;
  lockReason: string | null;
  onChange: (role: string) => void;
}) {
  const { t } = useTranslation("common");
  const name = row.assignment.grantee.name;
  const current = roles.find(({ key }) => key === row.assignment.role);
  const label = current?.label ?? row.assignment.role;

  if (lockReason !== null) {
    return (
      <span className="flex items-center gap-1 text-sm" title={lockReason}>
        <LockIcon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
        {label}
        <span className="sr-only">{lockReason}</span>
      </span>
    );
  }

  return (
    <Menu>
      <MenuTrigger
        render={
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="w-56 justify-between rounded-sm max-sm:w-auto max-sm:min-w-0 max-sm:flex-1"
          />
        }
        disabled={disabled}
        aria-label={t("resourceAccess.roleFor", { name })}
      >
        {label}
        <ChevronDownIcon aria-hidden="true" />
      </MenuTrigger>
      <MenuContent align="start" className="w-56 rounded-sm">
        {roles.map((role) => (
          <MenuItem key={role.key} onClick={() => onChange(role.key)}>
            {/* Icon slot: a scheme can drop its per-role icon here. */}
            <CheckIcon
              className={cn("size-4 shrink-0", role.key !== row.assignment.role && "invisible")}
              aria-hidden="true"
            />
            {role.label}
          </MenuItem>
        ))}
      </MenuContent>
    </Menu>
  );
}

/**
 * Remove, Leave, or Restore as one icon button. A blocked button uses `aria-disabled` rather than
 * `disabled` so it keeps focus and hover: its name and the reason still reach pointer and keyboard
 * users. Base UI does not point `aria-describedby` at its tooltip popup, so the reason also lives
 * in a visually hidden span the button references.
 */
export function RowActions({
  row,
  leaveLabel,
  blockedReason,
  onRemove,
  onRestore,
  onLeave,
}: {
  row: AccessRow;
  leaveLabel: string;
  blockedReason: string | null;
  onRemove: () => void;
  onRestore: () => void;
  onLeave: () => void;
}) {
  const { t } = useTranslation("common");
  const reasonId = useId();
  const grantee = row.assignment.grantee;

  // The audience is not a membership this editor revokes: its row carries no remove action.
  if (grantee.kind === "AUDIENCE") return null;

  const restoring = row.status === "removed";
  const verb = restoring ? t("resourceAccess.restore") : row.isSelf ? leaveLabel : t("resourceAccess.remove");
  const label = restoring
    ? t("resourceAccess.restoreNamed", { name: grantee.name })
    : row.isSelf
      ? t("resourceAccess.leaveSelf")
      : t("resourceAccess.removeNamed", { name: grantee.name });
  const Icon = restoring ? Undo2Icon : row.isSelf ? LogOutIcon : Trash2Icon;

  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <Button
            type="button"
            size="icon-sm"
            variant={restoring ? "outline" : "ghost"}
            aria-label={label}
            aria-disabled={blockedReason === null ? undefined : true}
            aria-describedby={blockedReason === null ? undefined : reasonId}
            className={cn(
              !restoring && "text-destructive hover:text-destructive",
              blockedReason !== null && "cursor-not-allowed opacity-50",
            )}
            onClick={(event) => {
              if (blockedReason !== null) {
                event.preventDefault();
                return;
              }
              if (restoring) onRestore();
              else if (row.isSelf) onLeave();
              else onRemove();
            }}
          />
        }
      >
        <Icon aria-hidden="true" />
      </TooltipTrigger>
      <TooltipContent>{blockedReason ?? verb}</TooltipContent>
      {blockedReason === null ? null : (
        <span id={reasonId} className="sr-only">
          {blockedReason}
        </span>
      )}
    </Tooltip>
  );
}
