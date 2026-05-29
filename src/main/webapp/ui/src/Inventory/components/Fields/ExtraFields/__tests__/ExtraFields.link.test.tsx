import { afterEach, describe, expect, it, vi } from "vitest";

// tinymce is pulled in transitively at import time and calls window.matchMedia
// during module evaluation. Stub it before any imports of source modules.
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

import React from "react";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

// Mock heavyweight collaborators so the ExtraFields wrapper can be exercised in
// isolation. InventoryBaseRecord pulls in the full stores layer at module load,
// which transitively constructs ApiServiceBase; we only need the type, not the
// runtime, in this test.
vi.mock("../../../../../stores/models/InventoryBaseRecord", () => ({
  default: class {},
}));

vi.mock("../UpdateField", () => ({
  default: () => <div data-testid="update-field" />,
}));

vi.mock("../NewField", () => ({
  default: () => <div data-testid="new-field" />,
}));

vi.mock("../../Link/LinkField", () => ({
  default: (props: {
    name: string;
    link: { targetGlobalId: string; versionPin: number | null };
    onOpen: () => void;
    onEdit: () => void;
    onVersionPinChange: (v: number | null) => void;
  }) => (
    <div
      data-testid="link-field"
      data-name={props.name}
      data-target={props.link.targetGlobalId}
      data-version-pin={
        props.link.versionPin == null ? "" : String(props.link.versionPin)
      }
    >
      <button type="button" onClick={props.onOpen} data-testid="onOpen">
        open
      </button>
      <button type="button" onClick={props.onEdit} data-testid="onEdit">
        edit
      </button>
      <button
        type="button"
        onClick={() => props.onVersionPinChange(5)}
        data-testid="onVersionPinChange-5"
      >
        pin 5
      </button>
      <button
        type="button"
        onClick={() => props.onVersionPinChange(null)}
        data-testid="onVersionPinChange-null"
      >
        unpin
      </button>
    </div>
  ),
}));

import ExtraFields from "../ExtraFields";

function makeLinkField(overrides: {
  name?: string;
  versionPin?: number | null;
  setEditing?: (v: boolean) => void;
  setAttributesDirty?: (attrs: Record<string, unknown>) => void;
} = {}) {
  return {
    id: 1,
    name: overrides.name ?? "linked sample",
    type: "Link",
    content: "",
    editing: false,
    editable: true,
    initial: false,
    invalidInput: false,
    deleteFieldRequest: false,
    newFieldRequest: false,
    link: {
      relationType: "References",
      targetGlobalId: "SA42",
      versionPin: overrides.versionPin ?? null,
    },
    setEditing: overrides.setEditing ?? vi.fn(),
    setAttributesDirty: overrides.setAttributesDirty ?? vi.fn(),
    setAttributes: vi.fn(),
    setInvalidInput: vi.fn(),
  };
}

function makeResult(extraFields: ReturnType<typeof makeLinkField>[]) {
  return {
    globalId: "SA1",
    extraFields,
    visibleExtraFields: extraFields,
    isFieldEditable: () => true,
    removeExtraField: vi.fn(),
  };
}

describe("ExtraFields - Link extra-field branch", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("renders a LinkField for each Link extra-field, passing name and target", () => {
    const result = makeResult([
      makeLinkField({ name: "a", versionPin: null }),
      makeLinkField({ name: "b", versionPin: 3 }),
    ]);
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields
          onErrorStateChange={() => {}}
          result={result as unknown as never}
        />
      </ThemeProvider>,
    );
    const cards = screen.getAllByTestId("link-field");
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveAttribute("data-name", "a");
    expect(cards[0]).toHaveAttribute("data-version-pin", "");
    expect(cards[1]).toHaveAttribute("data-name", "b");
    expect(cards[1]).toHaveAttribute("data-version-pin", "3");
  });

  it("propagates onVersionPinChange to the extra-field, merging into the existing link", async () => {
    const setAttributesDirty = vi.fn();
    const result = makeResult([
      makeLinkField({ setAttributesDirty, versionPin: null }),
    ]);
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields
          onErrorStateChange={() => {}}
          result={result as unknown as never}
        />
      </ThemeProvider>,
    );
    await user.click(screen.getByTestId("onVersionPinChange-5"));
    expect(setAttributesDirty).toHaveBeenCalledWith({
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: 5,
      },
    });
  });

  it("clears versionPin via onVersionPinChange(null), preserving relation and target", async () => {
    const setAttributesDirty = vi.fn();
    const result = makeResult([
      makeLinkField({ setAttributesDirty, versionPin: 7 }),
    ]);
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields
          onErrorStateChange={() => {}}
          result={result as unknown as never}
        />
      </ThemeProvider>,
    );
    await user.click(screen.getByTestId("onVersionPinChange-null"));
    expect(setAttributesDirty).toHaveBeenCalledWith({
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: null,
      },
    });
  });

  it("opens the target in a new tab when LinkField.onOpen fires", async () => {
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
    const result = makeResult([makeLinkField({})]);
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields
          onErrorStateChange={() => {}}
          result={result as unknown as never}
        />
      </ThemeProvider>,
    );
    await user.click(screen.getByTestId("onOpen"));
    expect(openSpy).toHaveBeenCalledWith("/globalId/SA42", "_blank");
  });

  it("enters edit mode when LinkField.onEdit fires", async () => {
    const setEditing = vi.fn();
    const result = makeResult([makeLinkField({ setEditing })]);
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields
          onErrorStateChange={() => {}}
          result={result as unknown as never}
        />
      </ThemeProvider>,
    );
    await user.click(screen.getByTestId("onEdit"));
    expect(setEditing).toHaveBeenCalledWith(true);
  });
});
