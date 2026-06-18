// tinymce is pulled in transitively at import time and calls window.matchMedia
// during module evaluation, which jsdom does not implement. The shared per-file
// mock supplies it and must be imported before any source module that reads it.
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@/__tests__/customQueries";
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
  // custom (extra) Link fields are edited via the field's settings cog, so
  // ExtraFields no longer passes onEdit and LinkField shows no inline Edit button.
  // LinkField now renders its own static Open link from the target/versionPin, so
  // ExtraFields no longer passes an onOpen callback (open routing is covered by
  // LinkField's own tests).
  default: (props: { name: string; link: { targetGlobalId: string; versionPin: number | null } }) => (
    <div
      data-testid="link-field"
      data-name={props.name}
      data-target={props.link.targetGlobalId}
      data-version-pin={props.link.versionPin == null ? "" : String(props.link.versionPin)}
    />
  ),
}));

import ExtraFields from "../ExtraFields";

function makeLinkField(
  overrides: {
    name?: string;
    versionPin?: number | null;
    targetGlobalId?: string;
    setEditing?: (v: boolean) => void;
    setAttributesDirty?: (attrs: Record<string, unknown>) => void;
  } = {},
) {
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
      targetGlobalId: overrides.targetGlobalId ?? "SA42",
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
        <ExtraFields onErrorStateChange={() => {}} result={result as unknown as never} />
      </ThemeProvider>,
    );
    const cards = screen.getAllByTestId("link-field");
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveAttribute("data-name", "a");
    expect(cards[0]).toHaveAttribute("data-version-pin", "");
    expect(cards[1]).toHaveAttribute("data-name", "b");
    expect(cards[1]).toHaveAttribute("data-version-pin", "3");
  });

  it("renders a placeholder instead of crashing when a Link field has no link payload", () => {
    // an ExtraLinkField row can exist before its InventoryLink is populated;
    // passing that null into LinkField would crash on render
    const field = { ...makeLinkField({}), link: null };
    const result = makeResult([field as unknown as ReturnType<typeof makeLinkField>]);
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields onErrorStateChange={() => {}} result={result as unknown as never} />
      </ThemeProvider>,
    );
    expect(screen.queryByTestId("link-field")).not.toBeInTheDocument();
    expect(screen.getByText(/no link set/i)).toBeInTheDocument();
  });

  it("enters edit mode via the settings cog, without mutating the model from the view card", async () => {
    // links are edited like every other custom field type: through the field's
    // settings cog, not an inline Edit button on the card. The pin (and every
    // other link property) is staged in the editor and committed on Update, so
    // the view card never mutates the model directly.
    const setAttributesDirty = vi.fn();
    const setEditing = vi.fn();
    const result = makeResult([makeLinkField({ setAttributesDirty, setEditing, versionPin: 7 })]);
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields onErrorStateChange={() => {}} result={result as unknown as never} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Field settings" }));

    expect(setEditing).toHaveBeenCalledWith(true);
    expect(setAttributesDirty).not.toHaveBeenCalled();
  });
});
