import type { BookingCatalogueManagerItem } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerItem";
import type { BookingConfigurationCapabilities } from "@/modules/common/api/v2/generated/models/bookingConfigurationCapabilities";

export type BookableItemOption = Required<
  Pick<
    BookingCatalogueManagerItem,
    | "configurationId"
    | "targetId"
    | "globalId"
    | "name"
    | "timezone"
    | "slotGranularityMinutes"
    | "incrementUnit"
    | "openingHours"
    | "bufferBeforeMinutes"
    | "bufferAfterMinutes"
    | "maxBookingDurationMinutes"
    | "allowDoubleBooking"
  >
> & {
  capabilities?: Required<Pick<BookingConfigurationCapabilities, "canCreateBlockout">> &
    Pick<BookingConfigurationCapabilities, "canCreateBooking" | "canEditConfiguration">;
};

type CompleteBookableItemOptionSource = Omit<
  BookableItemOption,
  "configurationId" | "targetId" | "globalId" | "name"
> & {
  id: number;
  target: {
    globalId: string;
    value: { id: number; name: string };
  };
};

type BookableItemOptionSource = {
  id: number;
  target?: CompleteBookableItemOptionSource["target"] | null;
} & Partial<Omit<BookableItemOption, "configurationId" | "targetId" | "globalId" | "name">>;

export function bookableItemOption(source: CompleteBookableItemOptionSource): BookableItemOption;
export function bookableItemOption(source: BookableItemOptionSource): BookableItemOption | null;
export function bookableItemOption(source: BookableItemOptionSource): BookableItemOption | null {
  if (
    !source.target ||
    source.timezone === undefined ||
    source.slotGranularityMinutes === undefined ||
    source.incrementUnit === undefined ||
    source.openingHours === undefined ||
    source.bufferBeforeMinutes === undefined ||
    source.bufferAfterMinutes === undefined ||
    source.maxBookingDurationMinutes === undefined ||
    source.allowDoubleBooking === undefined
  ) {
    return null;
  }
  return {
    configurationId: source.id,
    targetId: source.target.value.id,
    globalId: source.target.globalId,
    name: source.target.value.name,
    timezone: source.timezone,
    slotGranularityMinutes: source.slotGranularityMinutes,
    incrementUnit: source.incrementUnit,
    openingHours: source.openingHours,
    bufferBeforeMinutes: source.bufferBeforeMinutes,
    bufferAfterMinutes: source.bufferAfterMinutes,
    maxBookingDurationMinutes: source.maxBookingDurationMinutes,
    allowDoubleBooking: source.allowDoubleBooking,
    capabilities: source.capabilities,
  };
}
