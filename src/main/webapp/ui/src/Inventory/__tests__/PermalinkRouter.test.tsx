/*
 * @vitest-environment jsdom
 */

import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import NavigateContext from "../../stores/contexts/Navigate";
import PermalinkRouter from "../PermalinkRouter";

vi.mock("../Search/SearchRouter", () => ({
  default: (props: { paramsOverride?: unknown }) => (
    <div data-testid="search-router">{JSON.stringify(props.paramsOverride)}</div>
  ),
}));

function LocationDisplay() {
  const location = useLocation();
  return (
    <>
      <div data-testid="location-pathname">{location.pathname}</div>
      <div data-testid="location-search">{location.search}</div>
    </>
  );
}

function renderAt(initialUrl: string, type: "sample" | "subsample") {
  return render(
    <MemoryRouter initialEntries={[initialUrl]}>
      <NavigateContext.Provider
        value={{
          useNavigate: () => () => {},
          useLocation,
        }}
      >
        <Routes>
          <Route
            path={`/inventory/${type}/:id`}
            element={
              <>
                <PermalinkRouter type={type} />
                <LocationDisplay />
              </>
            }
          />
        </Routes>
      </NavigateContext.Provider>
    </MemoryRouter>,
  );
}

describe("PermalinkRouter", () => {
  test("a versioned Global Id redirects with the version as a search param", () => {
    renderAt("/inventory/subsample/SS4v1", "subsample");

    expect(screen.getByTestId("location-pathname")).toHaveTextContent("/inventory/subsample/4");
    expect(screen.getByTestId("location-search")).toHaveTextContent("version=1");
    // and the redirected route resolves to the versioned permalink search
    expect(screen.getByTestId("search-router")).toHaveTextContent('"version":1');
  });

  test("an unversioned Global Id redirects without inventing a version", () => {
    renderAt("/inventory/sample/SA42", "sample");

    expect(screen.getByTestId("location-pathname")).toHaveTextContent("/inventory/sample/42");
    expect(screen.getByTestId("location-search")).not.toHaveTextContent("version");
  });

  test("a plain numeric id with a version param is passed straight through", () => {
    renderAt("/inventory/subsample/4?version=1", "subsample");

    expect(screen.getByTestId("search-router")).toHaveTextContent(
      '"permalink":{"type":"subsample","id":4,"version":1}',
    );
  });

  test("a numeric id without a version resolves a null version", () => {
    renderAt("/inventory/subsample/4", "subsample");

    expect(screen.getByTestId("search-router")).toHaveTextContent(
      '"permalink":{"type":"subsample","id":4,"version":null}',
    );
  });
});
