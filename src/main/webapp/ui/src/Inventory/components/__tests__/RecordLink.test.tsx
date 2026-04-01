import { test, describe, expect, vi, beforeEach } from 'vitest';
import React from "react";
import { render, screen } from "@testing-library/react";
import { RecordLink } from "../RecordLink";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import {
  makeMockBench,
  makeMockContainer,
} from "@/stores/models/__tests__/ContainerModel/mocking";
import { storesContext } from "@/stores/stores-context";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import NavigateContext from "../../../stores/contexts/Navigate";

import userEvent from "@testing-library/user-event";

describe("RecordLink", () => {
  const navigate = vi.fn();

  beforeEach(() => {
    navigate.mockReset();
  });

  function renderRecordLink(
    record: Parameters<typeof RecordLink>[0]["record"],
    props?: Partial<Parameters<typeof RecordLink>[0]>,
  ) {
    const rootStore = makeMockRootStore({
      uiStore: {
        setVisiblePanel: () => {},
      },
      trackingStore: {
        trackEvent: () => {},
      },
    });

    render(
      <ThemeProvider theme={materialTheme}>
        <NavigateContext.Provider
          value={{
            useNavigate: () => navigate,
            useLocation: vi.fn(),
          }}
        >
          <storesContext.Provider value={rootStore}>
            <RecordLink record={record} {...props} />
          </storesContext.Provider>
        </NavigateContext.Provider>
      </ThemeProvider>
    );

    return {
      rootStore,
      setVisiblePanelSpy: vi.spyOn(rootStore.uiStore, "setVisiblePanel"),
      trackEventSpy: vi.spyOn(rootStore.trackingStore, "trackEvent"),
    };
  }

  test("Clicking a link to a bench should show the search results.", async () => {
    const user = userEvent.setup();
    const bench = makeMockBench({});
    const { setVisiblePanelSpy } = renderRecordLink(bench);

    await user.click(screen.getByRole("link", { name: /User User's Bench/ }));

    expect(setVisiblePanelSpy).toHaveBeenCalledWith("left");
  });

  test("Clicking a link to a container should show the container's details.", async () => {
    const user = userEvent.setup();
    const container = makeMockContainer();
    const { setVisiblePanelSpy } = renderRecordLink(container);

    await user.click(screen.getByRole("link", { name: /A list container/ }));

    expect(setVisiblePanelSpy).toHaveBeenCalledWith("right");
  });

  test("renders a native new-tab link when `newTab` is true.", () => {
    const container = makeMockContainer();
    renderRecordLink(container, { newTab: true });

    expect(screen.getByRole("link", { name: /A list container/ })).toHaveAttribute(
      "target",
      "_blank",
    );
  });

  test("shows a pointer cursor when `permalinkURL` is defined.", () => {
    const container = makeMockContainer();
    renderRecordLink(container);

    expect(screen.getByRole("link", { name: /A list container/ })).toHaveStyle({
      cursor: "pointer",
    });
  });

  test("does not show a pointer cursor when `permalinkURL` is not defined.", () => {
    const container = makeMockContainer();
    const recordWithoutPermalink = {
      ...container,
      permalinkURL: null,
      recordLinkLabel: container.recordLinkLabel,
      iconName: container.iconName,
      recordTypeLabel: container.recordTypeLabel,
      showRecordOnNavigate: container.showRecordOnNavigate,
    } as typeof container;
    renderRecordLink(recordWithoutPermalink);

    const chipRoot = screen.getByText("A list container").closest(".MuiChip-root");
    expect(chipRoot).not.toHaveStyle({ cursor: "pointer" });
  });

  test("does not use navigate context when `disableNavigationContext` is true.", async () => {
    const user = userEvent.setup();
    const container = makeMockContainer();
    const nativeContainer = {
      ...container,
      permalinkURL: "#native-navigation",
      recordLinkLabel: container.recordLinkLabel,
      iconName: container.iconName,
      recordTypeLabel: container.recordTypeLabel,
      showRecordOnNavigate: container.showRecordOnNavigate,
    } as typeof container;
    const { setVisiblePanelSpy, trackEventSpy } = renderRecordLink(nativeContainer, {
      disableNavigationContext: true,
    });

    await user.click(screen.getByRole("link", { name: /A list container/ }));

    expect(navigate).not.toHaveBeenCalled();
    expect(trackEventSpy).not.toHaveBeenCalled();
    expect(setVisiblePanelSpy).not.toHaveBeenCalled();
  });

  test("applies overflow styling when `overflow` is true.", () => {
    const container = makeMockContainer();
    renderRecordLink(container, { overflow: true });

    const link = screen.getByRole("link", { name: /A list container/ });
    expect(link).toHaveStyle({
      wordBreak: "break-word",
      height: "auto",
    });

    const label = screen.getByText("A list container");
    expect(label).toHaveStyle({ whiteSpace: "break-spaces" });
  });
});

