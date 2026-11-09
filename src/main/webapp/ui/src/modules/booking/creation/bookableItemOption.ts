import type { OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";

export type BookableItemOption = {
  configurationId: number;
  targetId: number;
  globalId: string;
  name: string;
  timezone: string;
  slotGranularityMinutes: number;
  openingHours: OpeningHoursEntry[];
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  maxBookingDurationMinutes: number;
  allowDoubleBooking: boolean;
  capabilities?: { canCreateBlockout: boolean };
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
