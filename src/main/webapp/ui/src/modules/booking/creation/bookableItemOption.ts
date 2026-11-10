import type { BookingConfigurationCapabilities } from "@/modules/common/api/v2/generated/models/bookingConfigurationCapabilities";
import type { Item } from "@/modules/common/api/v2/generated/models/item";

export type BookableItemOption = Required<
  Pick<
    Item,
    | "configurationId"
    | "targetId"
    | "globalId"
    | "name"
    | "timezone"
    | "slotGranularityMinutes"
    | "openingHours"
    | "bufferBeforeMinutes"
    | "bufferAfterMinutes"
    | "maxBookingDurationMinutes"
    | "allowDoubleBooking"
  >
> & { capabilities?: Required<Pick<BookingConfigurationCapabilities, "canCreateBlockout">> };

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
    openingHours: source.openingHours,
    bufferBeforeMinutes: source.bufferBeforeMinutes,
    bufferAfterMinutes: source.bufferAfterMinutes,
    maxBookingDurationMinutes: source.maxBookingDurationMinutes,
    allowDoubleBooking: source.allowDoubleBooking,
    capabilities: source.capabilities,
  };
}
