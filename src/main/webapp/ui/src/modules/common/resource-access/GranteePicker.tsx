import { useQuery } from "@tanstack/react-query";
import { GlobeIcon, UsersIcon } from "lucide-react";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { Avatar, AvatarFallback } from "@/modules/common/ui/avatar";
import { Badge } from "@/modules/common/ui/badge";
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "@/modules/common/ui/combobox";
import { Label } from "@/modules/common/ui/label";
import { searchResourceGrantees } from "./resourceAccess";
import type { ResourceGranteeDirectoryEntry } from "./schemas";

const MINIMUM_QUERY = 2;

/**
 * Server-backed grantee search in the same combobox the record pickers use.
 *
 * It does not go through `RelationshipPicker`: that component is wired to the API v2 collection
 * convention — `/api/v2/<resourceName>` with global-ID recognition — and grantees are searched on a
 * resource-scoped endpoint and have no global IDs. Reusing it would mean changing a shared
 * component, so this composes the same `Combobox` primitives it does.
 */
export function GranteePicker({
  resource,
  resourceId,
  token,
  assignedKeys,
  disabled,
  onPick,
}: {
  resource: string;
  resourceId: number;
  token: string;
  assignedKeys: ReadonlySet<string>;
  disabled?: boolean;
  onPick: (grantee: ResourceGranteeDirectoryEntry) => void;
}) {
  const { t } = useTranslation("common");
  const inputId = useId();
  const [query, setQuery] = useState("");
  // Remounts the combobox after each pick, which clears the input without a controlled value.
  const [pickCount, setPickCount] = useState(0);

  const directory = useQuery({
    queryKey: ["api-v2", resource, resourceId, "access-grantees", query],
    queryFn: ({ signal }) => searchResourceGrantees(resource, resourceId, query, token, signal),
    enabled: query.length >= MINIMUM_QUERY,
  });

  // The audience is never offered here: it is added by its own action, not searched for.
  const results = (directory.data ?? []).filter((entry) => entry.kind !== "AUDIENCE" && !assignedKeys.has(entry.key));

  return (
    <div className="space-y-1">
      <Label htmlFor={inputId}>{t("resourceAccess.addUserOrGroup")}</Label>
      <Combobox<ResourceGranteeDirectoryEntry>
        key={pickCount}
        items={results}
        // The server already ranked and filtered; re-filtering locally would hide matches.
        filter={null}
        disabled={disabled}
        itemToStringLabel={(entry) => entry.name}
        onInputValueChange={(value) => setQuery(value.trim())}
        onValueChange={(entry) => {
          if (!entry) return;
          onPick(entry);
          setQuery("");
          setPickCount((count) => count + 1);
        }}
      >
        <ComboboxInput
          id={inputId}
          className="rounded-sm"
          placeholder={t("resourceAccess.searchPlaceholder")}
          triggerLabel={t("resourceAccess.showGrantees")}
          disabled={disabled}
        />
        <ComboboxContent className="rounded-sm">
          <ComboboxList>
            {(entry: ResourceGranteeDirectoryEntry) => (
              <ComboboxItem key={entry.key} value={entry}>
                {entry.kind === "USER" ? (
                  <Avatar size="sm" aria-hidden="true">
                    <AvatarFallback>
                      {entry.name
                        .split(/\s+/)
                        .filter(Boolean)
                        .slice(0, 2)
                        .map((part) => part[0])
                        .join("")
                        .toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                ) : entry.kind === "GROUP" ? (
                  <UsersIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                ) : (
                  <GlobeIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                )}
                <span className="min-w-0">
                  <span className="flex flex-wrap items-center gap-1.5">
                    {entry.name}
                    <Badge variant="secondary">
                      {entry.kind === "USER" ? t("resourceAccess.kind.user") : t("resourceAccess.kind.group")}
                    </Badge>
                  </span>
                  {entry.detail ? (
                    <span className="block text-xs font-normal text-muted-foreground">{entry.detail}</span>
                  ) : null}
                </span>
              </ComboboxItem>
            )}
          </ComboboxList>
          <ComboboxEmpty>
            {directory.isError
              ? t("resourceAccess.searchError")
              : query.length < MINIMUM_QUERY
                ? t("resourceAccess.searchHint", { count: MINIMUM_QUERY })
                : directory.isFetching
                  ? t("resourceAccess.searching")
                  : t("resourceAccess.noGrantees")}
          </ComboboxEmpty>
        </ComboboxContent>
      </Combobox>
      {directory.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t("resourceAccess.searchError")}
        </p>
      ) : null}
    </div>
  );
}
