import { afterEach, describe, expect, it, vi } from "vitest";

vi.hoisted(() => {
  if (typeof window !== "undefined" && !window.matchMedia) {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }),
    });
  }
});

vi.mock("../GlobalId", () => ({
  default: ({ record }: { record: { globalId: string | null } }) => (
    <div data-testid="globalid" data-value={record.globalId ?? ""} />
  ),
}));

vi.mock("../Date", () => ({
  default: ({ label, date }: { label: string; date: string }) => (
    <div data-testid={`date-${label.replace(/\s+/g, "-").toLowerCase()}`} data-value={date} />
  ),
}));

vi.mock("../VersionHistory", () => ({
  default: () => <div data-testid="version-history" />,
}));

vi.mock("../LatestTemplateActions", () => ({
  default: () => <div data-testid="latest-template-actions" />,
}));

vi.mock("../LinkedDocuments", () => ({
  default: ({ globalId }: { globalId: string | null }) => (
    <div data-testid="linked-documents" data-globalid={globalId ?? ""} />
  ),
}));

import React from "react";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import SidebarBody from "../SidebarBody";

function makeRecord(overrides: Partial<{
  globalId: string | null;
  created: string;
  lastModified: string;
  usableInLoM: boolean;
}> = {}) {
  return {
    globalId: "SA42",
    created: "2026-01-01T00:00:00Z",
    lastModified: "2026-02-02T00:00:00Z",
    usableInLoM: true,
    ...overrides,
  };
}

describe("SidebarBody", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders Global ID, Created and Last Modified for the supplied record", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <SidebarBody record={makeRecord() as never} factory={null} />
      </ThemeProvider>,
    );
    expect(screen.getByTestId("globalid")).toHaveAttribute("data-value", "SA42");
    expect(screen.getByTestId("date-created")).toHaveAttribute(
      "data-value",
      "2026-01-01T00:00:00Z",
    );
    expect(screen.getByTestId("date-last-modified")).toHaveAttribute(
      "data-value",
      "2026-02-02T00:00:00Z",
    );
  });

  it("includes LinkedDocuments when the record is usable in a List of Materials and has a Global ID", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <SidebarBody record={makeRecord() as never} factory={null} />
      </ThemeProvider>,
    );
    expect(screen.getByTestId("linked-documents")).toHaveAttribute(
      "data-globalid",
      "SA42",
    );
  });

  it("omits LinkedDocuments when the record is not usable in a List of Materials", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <SidebarBody
          record={makeRecord({ usableInLoM: false }) as never}
          factory={null}
        />
      </ThemeProvider>,
    );
    expect(screen.queryByTestId("linked-documents")).not.toBeInTheDocument();
  });

  it("omits LinkedDocuments when the record has no Global ID yet", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <SidebarBody
          record={makeRecord({ globalId: null }) as never}
          factory={null}
        />
      </ThemeProvider>,
    );
    expect(screen.queryByTestId("linked-documents")).not.toBeInTheDocument();
  });
});
