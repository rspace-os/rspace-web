import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";

type SharingMode = "OWNER_GROUPS" | "WHITELIST" | "OWNER_ONLY";

type InventoryGroup = {
  id: number;
  name: string;
};

type SharedWithGroup = {
  group: InventoryGroup;
  shared: boolean;
  itemOwnerGroup: boolean;
};

type InventoryAccess = {
  sharingMode: SharingMode;
  sharedWith: Array<SharedWithGroup>;
};

function isSharingMode(value: unknown): value is SharingMode {
  return value === "OWNER_GROUPS" || value === "WHITELIST" || value === "OWNER_ONLY";
}

function parseInventoryAccess(value: unknown): InventoryAccess {
  if (typeof value !== "object" || value === null) throw new Error("Invalid inventory access response");
  const response = value as { sharingMode?: unknown; sharedWith?: unknown };
  if (!isSharingMode(response.sharingMode)) throw new Error("Invalid inventory sharing mode");
  const sharedWith = Array.isArray(response.sharedWith) ? response.sharedWith : [];
  return {
    sharingMode: response.sharingMode,
    sharedWith: sharedWith.flatMap((entry) => {
      if (typeof entry !== "object" || entry === null) return [];
      const candidate = entry as { group?: unknown; shared?: unknown; itemOwnerGroup?: unknown };
      if (typeof candidate.group !== "object" || candidate.group === null) return [];
      const group = candidate.group as { id?: unknown; name?: unknown };
      if (typeof group.id !== "number" || typeof group.name !== "string") return [];
      return [
        {
          group: { id: group.id, name: group.name },
          shared: candidate.shared === true,
          itemOwnerGroup: candidate.itemOwnerGroup === true,
        },
      ];
    }),
  };
}

async function fetchInventoryAccess(id: number, token: string): Promise<InventoryAccess> {
  const response = await fetch(`/api/inventory/v1/instruments/${id}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });
  if (!response.ok) throw new Error(`Inventory access request failed (${response.status})`);
  return parseInventoryAccess(await response.json());
}

function ExplicitAccessTable({ sharedWith }: { sharedWith: Array<SharedWithGroup> }) {
  const { t } = useTranslation("inventory");
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t("fields.accessPermissions.label")}</TableHead>
          <TableHead>{t("fields.accessPermissions.groupName")}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sharedWith.map(({ group, shared }) => (
          <TableRow key={group.id}>
            <TableCell className="w-10">
              <Checkbox checked={shared} disabled aria-disabled="true" aria-label={group.name} />
            </TableCell>
            <TableCell>{group.name}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function OwnerGroupsTable({ groups }: { groups: Array<InventoryGroup> }) {
  const { t } = useTranslation("inventory");
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t("fields.accessPermissions.groupName")}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {groups.map((group) => (
          <TableRow key={group.id}>
            <TableCell>
              <a href={`/groups/view/${group.id}`}>{group.name}</a>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function InventoryAccessReadOnly({ instrumentId }: { instrumentId: number }) {
  const { t } = useTranslation("inventory");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: false });
  const accessQuery = useQuery({
    queryKey: ["inventory", "instruments", instrumentId, "access"],
    queryFn: () => fetchInventoryAccess(instrumentId, token),
    enabled: Boolean(token),
    retry: false,
  });

  if (accessQuery.isPending)
    return (
      <p role="status" className="sr-only">
        {commonT("resourceAccess.loading")}
      </p>
    );
  if (accessQuery.isError) {
    return <p role="alert">{commonT("resourceAccess.loadError")}</p>;
  }

  const { sharingMode, sharedWith } = accessQuery.data;
  const ownerGroups = sharedWith.filter(({ itemOwnerGroup }) => itemOwnerGroup).map(({ group }) => group);
  const ownerGroupsId = `inventory-access-owner-groups-${instrumentId}`;
  const whitelistId = `inventory-access-whitelist-${instrumentId}`;
  const ownerOnlyId = `inventory-access-owner-only-${instrumentId}`;
  return (
    <fieldset className="w-full space-y-3" aria-label={t("fields.accessPermissions.label")}>
      <legend className="sr-only">{t("fields.accessPermissions.label")}</legend>
      <RadioGroup value={sharingMode} aria-label={t("fields.accessPermissions.label")}>
        <label htmlFor={ownerGroupsId} className="flex items-start gap-3 rounded-md p-2">
          <RadioGroupItem
            id={ownerGroupsId}
            value="OWNER_GROUPS"
            disabled
            aria-label={t("fields.accessPermissions.ownerGroups.title")}
          />
          <div className="min-w-0 flex-1 space-y-2">
            <div>
              <h3 className="font-medium">{t("fields.accessPermissions.ownerGroups.title")}</h3>
              <p className="text-sm text-muted-foreground">
                {t("fields.accessPermissions.ownerGroups.description", { tableNote: "" })}
              </p>
            </div>
            {sharingMode === "OWNER_GROUPS" ? <OwnerGroupsTable groups={ownerGroups} /> : null}
          </div>
        </label>
        <label htmlFor={whitelistId} className="flex items-start gap-3 rounded-md p-2">
          <RadioGroupItem
            id={whitelistId}
            value="WHITELIST"
            disabled
            aria-label={t("fields.accessPermissions.explicitAccess.title")}
          />
          <div className="min-w-0 flex-1 space-y-2">
            <div>
              <h3 className="font-medium">{t("fields.accessPermissions.explicitAccess.title")}</h3>
              <p className="text-sm text-muted-foreground">
                {t("fields.accessPermissions.explicitAccess.description")}
              </p>
            </div>
            {sharingMode === "WHITELIST" ? <ExplicitAccessTable sharedWith={sharedWith} /> : null}
          </div>
        </label>
        <label htmlFor={ownerOnlyId} className="flex items-start gap-3 rounded-md p-2">
          <RadioGroupItem
            id={ownerOnlyId}
            value="OWNER_ONLY"
            disabled
            aria-label={t("fields.accessPermissions.ownerOnly.title")}
          />
          <div className="min-w-0 flex-1">
            <h3 className="font-medium">{t("fields.accessPermissions.ownerOnly.title")}</h3>
            <p className="text-sm text-muted-foreground">{t("fields.accessPermissions.ownerOnly.description")}</p>
          </div>
        </label>
      </RadioGroup>
    </fieldset>
  );
}
