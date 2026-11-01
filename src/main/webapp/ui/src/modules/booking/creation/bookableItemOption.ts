import type { IncrementUnit } from "@/modules/booking/domain/bookingIncrement";
import type { OpeningHoursEntry } from "@/modules/booking/domain/bookingOpeningHours";

export type BookableItemOption = {
  configurationId: number;
  targetId: number;
  globalId: string;
  name: string;
  timezone: string;
  slotGranularityMinutes: number;
  incrementUnit: IncrementUnit;
  openingHours: OpeningHoursEntry[];
  bufferMinutes: number;
  maxBookingDurationMinutes: number;
  allowDoubleBooking: boolean;
  capabilities?: {
    canCreateBlockout: boolean;
    canCreateBooking?: boolean;
    canEditConfiguration?: boolean;
  };
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
    source.bufferMinutes === undefined ||
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
    bufferMinutes: source.bufferMinutes,
    maxBookingDurationMinutes: source.maxBookingDurationMinutes,
    allowDoubleBooking: source.allowDoubleBooking,
    capabilities: source.capabilities,
  };
}
