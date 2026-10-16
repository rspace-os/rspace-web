import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import { newGeoLocation } from "../../../../../stores/definitions/GeoLocation";
import GeoLocationModel from "../../../../../stores/models/GeoLocationModel";
import materialTheme from "../../../../../theme";
import PolygonCard from "../PolygonCard";

describe("PolygonCard", () => {
  test("each point's latitude and longitude labels carry the point number", async () => {
    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <PolygonCard editable geoLocation={new GeoLocationModel(newGeoLocation)} doUpdateIdentifiers={() => {}} />
      </ThemeProvider>,
      { resources: { common: commonEn, inventory: inventoryEn }, defaultNS: "inventory" },
    );

    expect(screen.getByText("Point 1 latitude")).toBeInTheDocument();
    expect(screen.getByText("Point 4 longitude")).toBeInTheDocument();
  });
});
