/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { RecordLink } from "../RecordLink";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import {
  makeMockBench,
  makeMockContainer,
} from "../../../stores/models/__tests__/ContainerModel/mocking";
import { storesContext } from "../../../stores/stores-context";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("RecordLink", () => {
  test("Clicking a link to a bench should show the search results.", async () => {
    const user = userEvent.setup();
    const rootStore = makeMockRootStore({
      uiStore: {
        setVisiblePanel: () => {},
      },
      trackingStore: {
        trackEvent: () => {},
      },
    });
    const spy = jest.spyOn(rootStore.uiStore, "setVisiblePanel");
    const bench = makeMockBench({});
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={rootStore}>
          <RecordLink record={bench} />
        </storesContext.Provider>
      </ThemeProvider>
    );
    await user.click(screen.getByRole("link", { name: /User User's Bench/ }));
    expect(spy).toHaveBeenCalledWith("left");
  });
  test("Clicking a link to a container should show the container's details.", async () => {
    const user = userEvent.setup();
    const rootStore = makeMockRootStore({
      uiStore: {
        setVisiblePanel: () => {},
      },
      trackingStore: {
        trackEvent: () => {},
      },
    });
    const spy = jest.spyOn(rootStore.uiStore, "setVisiblePanel");
    const container = makeMockContainer();
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={rootStore}>
          <RecordLink record={container} />
        </storesContext.Provider>
      </ThemeProvider>
    );
    await user.click(screen.getByRole("link", { name: /A list container/ }));
    expect(spy).toHaveBeenCalledWith("right");
  });
});
