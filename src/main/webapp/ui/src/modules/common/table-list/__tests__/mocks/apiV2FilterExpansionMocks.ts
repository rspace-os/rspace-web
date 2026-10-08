import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import type { RuntimeFieldDefinition } from "@/modules/common/table-list/adapters/apiV2/runtimeFieldCatalog";

export function runtimeFieldId(name: string): string {
  const hex = Array.from(new TextEncoder().encode(name), (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `XFt${hex}`;
}

export function runtimeField(name: string, type: RuntimeFieldDefinition["type"] = "text"): RuntimeFieldDefinition {
  return {
    id: runtimeFieldId(name),
    selector: `extraFields.${runtimeFieldId(name)}`,
    label: name,
    type,
    jsonType: type === "number" ? "number" : "string",
    operators: ["==", "!=", "=contains="],
    supportsWildcards: true,
    columnSelectable: true,
    sortable: false,
    source: { id: "extraFields", label: "Extra fields" },
    options: [],
  };
}

export function runtimeCatalog(
  fields: readonly RuntimeFieldDefinition[],
  { page = 1, limit = 20, hasMore = false }: { page?: number; limit?: number; hasMore?: boolean } = {},
) {
  return { fields, totalFields: fields.length + (hasMore ? 1 : 0), hasMore, page, limit };
}

export function bookingCataloguePage(instruments: readonly { id: number; name: string }[] = []) {
  return {
    items: instruments.map(({ id, name }) => ({
      configurationId: id + 5000,
      configurationVersion: 1,
      targetType: "INSTRUMENT" as const,
      targetId: id,
      globalId: `IN${id}`,
      name,
      timezone: "Europe/Berlin",
      ...DEFAULT_SCHEDULING_SETTINGS,
      effectiveRole: "OWNER",
      capabilities: {
        canEditConfiguration: true,
        canViewAudit: true,
        canViewAccess: true,
        canManageAssignments: true,
        canManageOwners: true,
        canCreateBooking: true,
        canManageOwnBookings: true,
        canManageAllEvents: true,
        canCreateBlockout: true,
        canSubscribeCalendar: true,
        canLeaveConfiguration: false,
      },
      location: null,
    })),
    page: 1,
    pageSize: 20,
    total: instruments.length,
    facets: { types: ["INSTRUMENT"] },
  };
}

export function apiV2Page() {
  return {
    docs: [],
    totalDocs: 0,
    limit: 20,
    page: 1,
    pagingCounter: 1,
    totalPages: 0,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}
