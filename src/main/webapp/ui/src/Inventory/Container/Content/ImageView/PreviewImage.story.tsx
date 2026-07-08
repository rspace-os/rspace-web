import { ThemeProvider } from "@mui/material/styles";
import type React from "react";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "../../../../stores/models/ContainerModel";
import LocationModel from "../../../../stores/models/LocationModel";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import type { BlobUrl } from "../../../../util/types";
import PreviewImage from "./PreviewImage";

// A 400x300 solid-teal PNG. A fixed-pixel PNG (not an SVG) renders at identical
// dimensions across chromium/firefox/webkit, so marker positions are deterministic.
export const LOCATIONS_IMAGE =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAZAAAAEsCAIAAABi1XKVAAAD70lEQVR4nO3UsQkAIBDAwJ/Eia2d1xXsJHBwA6TKrH0AEuZ7AcAjwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8i44C0SV9mfVtgAAAAASUVORK5CYII=";

// Rising diagonal on the 0-1000 coordinate space; ids fix the displayed 1..3
// ordering (sortedLocations sorts by id).
export const IMAGE_LOCATIONS = [
  { id: 50, coordX: 250, coordY: 250 },
  { id: 51, coordX: 500, coordY: 500 },
  { id: 52, coordX: 750, coordY: 750 },
];

// The extremes of the coordinate space: (0,0) top-left corner, (1000,1000)
// bottom-right, displayed as 1 and 2.
export const EDGE_LOCATIONS = [
  { id: 60, coordX: 0, coordY: 0 },
  { id: 61, coordX: 1000, coordY: 1000 },
];

function buildVisualContainer(
  locations: ReadonlyArray<{ id: number; coordX: number; coordY: number }>,
): ContainerModel {
  const container = makeMockContainer({
    name: "A visual container",
    globalId: "IC1",
    cType: "IMAGE",
    locations: [],
    locationsCount: locations.length,
    contentSummary: {
      totalCount: 0,
      subSampleCount: 0,
      containerCount: 0,
      instrumentCount: 0,
    },
  });
  container.locations = locations.map(
    ({ id, coordX, coordY }) => new LocationModel({ id, coordX, coordY, content: null, parentContainer: container }),
  );
  container.initializedLocations = true;
  container.locationsImage = LOCATIONS_IMAGE as BlobUrl;
  return container;
}

function renderPreview(container: ContainerModel): React.ReactNode {
  const search = new Search({ factory: mockFactory() });
  return (
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider
        value={{
          search,
          differentSearchForSettingActiveResult: search,
          scopedResult: container,
        }}
      >
        <PreviewImage />
      </SearchContext.Provider>
    </ThemeProvider>
  );
}

/**
 * PreviewImage showing a visual (IMAGE) container with three location markers
 * on its background image. This is the view rendered in the container details
 * "Locations and Content" section and in the move-picker destination panel.
 */
export function VisualContainerWithLocations(): React.ReactNode {
  return renderPreview(buildVisualContainer(IMAGE_LOCATIONS));
}

/**
 * PreviewImage for a visual container whose two markers sit at the extremes of
 * the coordinate space (0,0) and (1000,1000) — the top-left and bottom-right
 * corners of the image.
 */
export function VisualContainerWithEdgeLocations(): React.ReactNode {
  return renderPreview(buildVisualContainer(EDGE_LOCATIONS));
}
