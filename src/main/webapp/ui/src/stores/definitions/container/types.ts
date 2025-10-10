export type WorkbenchId = number;
export type ContainerType = "LIST" | "GRID" | "IMAGE" | "WORKBENCH";

export type Axis = "ABC" | "CBA" | "N123" | "N321";

export const DEFAULT_ROW_AXIS: Axis = "ABC";
export const DEFAULT_COLUMN_AXIS: Axis = "N123";

export type GridLayout = {
  columnsNumber: number | "";
  rowsNumber: number | "";
  columnsLabelType: Axis;
  rowsLabelType: Axis;
};

export type ContentSummary = {
  totalCount: number;
  subSampleCount: number;
  containerCount: number;
};