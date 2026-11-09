export type AdvancedFilter = { id: string; label: string };
export const MOCK_FILTERS: readonly AdvancedFilter[] = [
  { id: "status", label: "Status is Confirmed" },
  { id: "location", label: "Location is Imaging suite" },
  { id: "kind", label: "Kind is Maintenance" },
];

export const ITEMS = [
  { name: "Confocal microscope", owned: true },
  { name: "Flow cytometer", owned: false },
  { name: "Plate reader", owned: true },
  { name: "Mass spectrometer", owned: false },
] as const;

export type MockEvent = {
  id: number;
  item: (typeof ITEMS)[number]["name"];
  bookedBy: string;
  mine: boolean;
  /** Days after the selected date. */
  day: number;
  start: number;
  end: number;
  purpose: string;
};

export const EVENTS: readonly MockEvent[] = [
  {
    id: 1,
    item: "Confocal microscope",
    bookedBy: "You",
    mine: true,
    day: 0,
    start: 9,
    end: 11,
    purpose: "Live-cell imaging",
  },
  {
    id: 2,
    item: "Flow cytometer",
    bookedBy: "Priya Shah",
    mine: false,
    day: 0,
    start: 10,
    end: 12.5,
    purpose: "Panel validation",
  },
  {
    id: 3,
    item: "Plate reader",
    bookedBy: "Tom Okafor",
    mine: false,
    day: 0,
    start: 13,
    end: 14,
    purpose: "Assay run",
  },
  {
    id: 4,
    item: "Mass spectrometer",
    bookedBy: "You",
    mine: true,
    day: 0,
    start: 15,
    end: 17,
    purpose: "Metabolomics batch",
  },
  {
    id: 5,
    item: "Confocal microscope",
    bookedBy: "Lena Novak",
    mine: false,
    day: 1,
    start: 8,
    end: 10,
    purpose: "Fixed samples",
  },
  { id: 6, item: "Plate reader", bookedBy: "You", mine: true, day: 2, start: 11, end: 12, purpose: "Calibration" },
  { id: 7, item: "Flow cytometer", bookedBy: "You", mine: true, day: 3, start: 14, end: 16, purpose: "Sorting" },
  {
    id: 8,
    item: "Confocal microscope",
    bookedBy: "Priya Shah",
    mine: false,
    day: 4,
    start: 9,
    end: 12,
    purpose: "Time-lapse",
  },
];
