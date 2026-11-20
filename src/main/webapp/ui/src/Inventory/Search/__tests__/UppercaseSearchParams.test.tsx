/*
 * @vitest-environment jsdom
 */

import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation, useNavigationType } from "react-router";
import { describe, expect, test } from "vitest";
import NavigateContext from "../../../stores/contexts/Navigate";
import UppercaseSearchParams from "../UppercaseSearchParams";

function LocationDisplay() {
  const location = useLocation();
  return (
    <>
      <div data-testid="location-search">{location.search}</div>
      <div data-testid="navigation-type">{useNavigationType()}</div>
    </>
  );
}

function renderAt(initialUrl: string) {
  return render(
    <MemoryRouter initialEntries={[initialUrl]}>
      <NavigateContext.Provider value={{ useNavigate: () => () => {}, useLocation }}>
        <Routes>
          <Route
            path="/inventory/search/*"
            element={
              <UppercaseSearchParams>
                <LocationDisplay />
              </UppercaseSearchParams>
            }
          />
        </Routes>
      </NavigateContext.Provider>
    </MemoryRouter>,
  );
}

describe("UppercaseSearchParams", () => {
  test.each([
    ["resultType=sample&query=mice", "?resultType=SAMPLE&query=mice"],
    ["resultType=sample_template", "?resultType=SAMPLE_TEMPLATE"],
    ["deletedItems=deleted_only", "?deletedItems=DELETED_ONLY"],
  ])("replaces %s with its uppercase form", (query, expected) => {
    renderAt(`/inventory/search?${query}`);

    expect(screen.getByTestId("location-search")).toHaveTextContent(expected);
    // a replace, so going back does not return to the lowercase link
    expect(screen.getByTestId("navigation-type")).toHaveTextContent("REPLACE");
  });

  test.each([
    ["resultType=SAMPLE", "?resultType=SAMPLE"],
    ["resultType=template", "?resultType=template"],
  ])("leaves %s unchanged", (query, expected) => {
    renderAt(`/inventory/search?${query}`);

    expect(screen.getByTestId("location-search")).toHaveTextContent(expected);
    expect(screen.getByTestId("navigation-type")).toHaveTextContent("POP");
  });
});
