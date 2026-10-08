import { containerAttrs, makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";

/** A 2x2 grid box with A1 holding a rack. */
export const boxWithA1Taken = () =>
  makeMockContainer({
    id: 1,
    globalId: "IC1",
    name: "Box",
    cType: "GRID",
    gridLayout: { columnsNumber: 2, rowsNumber: 2, columnsLabelType: "N123", rowsLabelType: "ABC" },
    locationsCount: 4,
    contentSummary: { totalCount: 1, subSampleCount: 0, containerCount: 1, instrumentCount: 0 },
    locations: [
      {
        id: 11,
        coordX: 1,
        coordY: 1,
        content: containerAttrs({ id: 9, globalId: "IC9", name: "Rack" }),
      },
    ],
  });

/** An empty 2x2 grid box. */
export const emptyBox = () =>
  makeMockContainer({
    id: 2,
    globalId: "IC2",
    name: "Empty box",
    cType: "GRID",
    gridLayout: { columnsNumber: 2, rowsNumber: 2, columnsLabelType: "N123", rowsLabelType: "ABC" },
    locationsCount: 4,
    contentSummary: { totalCount: 0, subSampleCount: 0, containerCount: 0, instrumentCount: 0 },
    locations: [],
  });
