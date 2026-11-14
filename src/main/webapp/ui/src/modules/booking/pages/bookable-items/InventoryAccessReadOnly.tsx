import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Link } from "@/modules/common/ui/typography";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

const SharingModeSchema = v.picklist(["OWNER_GROUPS", "WHITELIST", "OWNER_ONLY"]);
const InventoryGroupSchema = v.object({ id: v.number(), name: v.string() });
const SharedWithGroupSchema = v.object({
  group: InventoryGroupSchema,
  shared: v.optional(v.unknown()),
  itemOwnerGroup: v.optional(v.unknown()),
});
const InventoryAccessShapeSchema = v.object({
  sharingMode: v.unknown(),
  sharedWith: v.optional(v.unknown()),
});
const InventoryAccessRowsSchema = v.array(v.unknown());

type InventoryGroup = v.InferOutput<typeof InventoryGroupSchema>;
type SharedWithGroup = {
  group: InventoryGroup;
  shared: boolean;
  itemOwnerGroup: boolean;
};

function parseSharedWith(value: unknown): Array<SharedWithGroup> {
  const rows = v.safeParse(InventoryAccessRowsSchema, value);
  if (!rows.success) return [];
  return rows.output.flatMap((entry) => {
    const parsed = v.safeParse(SharedWithGroupSchema, entry);
    if (!parsed.success) return [];
    return [
      {
        group: parsed.output.group,
        shared: parsed.output.shared === true,
        itemOwnerGroup: parsed.output.itemOwnerGroup === true,
      },
    ];
  });
}

const InventoryAccessSchema = v.pipe(
  v.object({
    sharingMode: SharingModeSchema,
    sharedWith: v.optional(v.unknown()),
  }),
  v.transform(({ sharingMode, sharedWith }) => ({
    sharingMode,
    sharedWith: parseSharedWith(sharedWith),
  })),
);
type InventoryAccess = v.InferOutput<typeof InventoryAccessSchema>;

function parseInventoryAccess(value: unknown): InventoryAccess {
  const response = v.safeParse(InventoryAccessShapeSchema, value);
  if (!response.success) throw new Error("Invalid inventory access response");
  const parsed = v.safeParse(InventoryAccessSchema, response.output);
  if (!parsed.success) throw new Error("Invalid inventory sharing mode");
  return parsed.output;
}

async function fetchInventoryAccess(id: number, token: string): Promise<InventoryAccess> {
  const response = await fetchWithUiToken(`/api/inventory/v1/instruments/${id}`, {
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
  const { data: token } = useOauthTokenQuery();
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
  const radioOptions = [
    {
      value: "OWNER_GROUPS",
      id: ownerGroupsId,
      title: t("fields.accessPermissions.ownerGroups.title"),
      description: t("fields.accessPermissions.ownerGroups.description", { tableNote: "" }),
    },
    {
      value: "WHITELIST",
      id: whitelistId,
      title: t("fields.accessPermissions.explicitAccess.title"),
      description: t("fields.accessPermissions.explicitAccess.description"),
    },
    {
      value: "OWNER_ONLY",
      id: ownerOnlyId,
      title: t("fields.accessPermissions.ownerOnly.title"),
      description: t("fields.accessPermissions.ownerOnly.description"),
    },
  ] as const;
  return (
    <fieldset className="w-full space-y-3" aria-label={t("fields.accessPermissions.label")}>
      <legend className="sr-only">{t("fields.accessPermissions.label")}</legend>
      <p className="text-sm text-muted-foreground">
        <TransRichText
          i18nKey="booking:access.editInventoryPermissions"
          components={{
            inventoryLink: <Link href={`/inventory/instrument/${instrumentId}`} />,
          }}
        />
      </p>
      <RadioGroup value={sharingMode} disabled aria-label={t("fields.accessPermissions.label")}>
        {radioOptions.map(({ value, id, title, description }) => {
          const ownerOnly = value === "OWNER_ONLY";
          const table =
            value === "OWNER_GROUPS" && sharingMode === value ? (
              <OwnerGroupsTable groups={ownerGroups} />
            ) : value === "WHITELIST" && sharingMode === value ? (
              <ExplicitAccessTable sharedWith={sharedWith} />
            ) : null;
          return (
            <label key={value} htmlFor={id} className="flex items-start gap-3 rounded-md p-2">
              <RadioGroupItem id={id} value={value} disabled aria-label={title} />
              <div className={ownerOnly ? "min-w-0 flex-1" : "min-w-0 flex-1 space-y-2"}>
                {ownerOnly ? (
                  <>
                    <p className="font-medium">{title}</p>
                    <p className="text-sm text-muted-foreground">{description}</p>
                  </>
                ) : (
                  <>
                    <div>
                      <p className="font-medium">{title}</p>
                      <p className="text-sm text-muted-foreground">{description}</p>
                    </div>
                    {table}
                  </>
                )}
              </div>
            </label>
          );
        })}
      </RadioGroup>
    </fieldset>
  );
}
