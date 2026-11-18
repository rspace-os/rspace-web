import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import materialTheme from "@/theme";
import DisplayRuleSummary from "../DisplayRuleSummary";
import FieldCard from "../FieldCard";

type CardProps = ComponentProps<typeof FieldCard<"text" | "radio">>;

const props = (overrides: Partial<CardProps> = {}): CardProps => ({
  name: "Notes",
  onNameChange: vi.fn(),
  type: "text",
  typeOptions: [
    { value: "text", label: "Text" },
    { value: "radio", label: "Radio" },
  ],
  onTypeChange: vi.fn(),
  canChangeType: true,
  mandatory: false,
  onMandatoryChange: vi.fn(),
  position: 0,
  count: 2,
  onMove: vi.fn(),
  canDelete: true,
  onDelete: vi.fn(),
  editable: true,
  ...overrides,
});

const renderCard = (overrides: Partial<CardProps> = {}) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <FieldCard {...props(overrides)} />
    </ThemeProvider>,
  );

describe("FieldCard", () => {
  it("changes the type through the host while the type can change", async () => {
    const user = userEvent.setup();
    const onTypeChange = vi.fn();
    renderCard({ onTypeChange });
    await user.click(screen.getByRole("menuitem", { name: "common:fieldEditor.card.changeType" }));
    await user.click(screen.getByRole("menuitem", { name: "Radio" }));
    expect(onTypeChange).toHaveBeenCalledWith("radio");
  });

  it("shows the type as text once the host fixes it", () => {
    renderCard({ canChangeType: false });
    expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
    expect(screen.getByText("Text")).toBeInTheDocument();
  });

  it("offers delete only when the host allows it", () => {
    const { unmount } = renderCard();
    expect(screen.getByRole("button", { name: "common:fieldEditor.card.delete" })).toBeInTheDocument();
    unmount();
    renderCard({ canDelete: false });
    expect(screen.queryByRole("button", { name: "common:fieldEditor.card.delete" })).not.toBeInTheDocument();
  });

  it("toggles mandatory", async () => {
    const user = userEvent.setup();
    const onMandatoryChange = vi.fn();
    renderCard({ onMandatoryChange });
    await user.click(screen.getByRole("switch", { name: "common:fieldEditor.card.mandatory" }));
    expect(onMandatoryChange).toHaveBeenCalledWith(true);
  });

  it("cannot move the first field further up", async () => {
    const user = userEvent.setup();
    const onMove = vi.fn();
    renderCard({ onMove });
    expect(screen.getByRole("button", { name: "common:fieldEditor.card.moveUp" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "common:fieldEditor.card.moveToBottom" }));
    expect(onMove).toHaveBeenCalledWith(1);
  });

  it("shows the name as a heading when not editable", () => {
    renderCard({ editable: false });
    expect(screen.getByRole("region", { name: "Notes" })).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("is accessible", async () => {
    const { baseElement } = renderCard();
    await expectAccessible(baseElement);
  });
});

describe("DisplayRuleSummary", () => {
  const renderSummary = (summary: ComponentProps<typeof DisplayRuleSummary>) =>
    renderWithRealI18n(<DisplayRuleSummary {...summary} />, { resources: { common }, defaultNS: "common" });

  it("names the source and its options", async () => {
    await renderSummary({ rule: createDisplayRule("gmo", "IS_ANY_OF", ["Yes", "Unsure"]), sourceName: "GMO?" });
    expect(screen.getByText("Shown when GMO? is Yes or Unsure")).toBeInTheDocument();
  });

  it("hints when the source comes after the field", async () => {
    await renderSummary({ rule: createDisplayRule("gmo", "HAS_VALUE", []), sourceName: "GMO?", sourceIsBelow: true });
    expect(screen.getByText("Shown when GMO? has a value Placing it below GMO? reads better.")).toBeInTheDocument();
  });

  it("says the field is always shown when its source is gone", async () => {
    await renderSummary({ rule: createDisplayRule("gone", "IS_NONE_OF", ["Yes"]), sourceName: null });
    expect(screen.getByText("Always shown: the field its rule read no longer exists.")).toBeInTheDocument();
  });
});
