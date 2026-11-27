import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import materialTheme from "../../../../../theme";

vi.mock("@/common/InvApiService", () => ({ default: {} }));
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) } }),
}));
vi.mock("../../Link/LinkTargetBrowser", () => ({ default: () => null }));
vi.mock("../../Link/ElnRecordPicker", () => ({ default: () => null }));
vi.mock("../../Link/linkTargetExists", () => ({
  checkLinkTargetExists: vi.fn(() => Promise.resolve(true)),
}));
vi.mock("@/components/Inputs/StyledTinyMceEditor", () => ({
  default: ({ value, onEditorChange }: { value: string; onEditorChange: (value: string) => void }) => (
    <textarea aria-label="Custom field content" value={value} onChange={(e) => onEditorChange(e.target.value)} />
  ),
}));

import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import ExtraFields from "../ExtraFields";

describe("ExtraFields empty API content", () => {
  it.each([{ content: null }, {}])("renders and edits text with API attributes %j", async (contentAttrs) => {
    const user = userEvent.setup();
    const sample = makeMockSample({
      extraFields: [
        {
          id: 1,
          globalId: null,
          name: "Custom text",
          type: "text",
          parentGlobalId: "SA1",
          lastModified: null,
          ...contentAttrs,
        },
      ],
    });
    sample.setEditable(new Set(["extraFields"]), true);
    render(
      <ThemeProvider theme={materialTheme}>
        <ExtraFields onErrorStateChange={vi.fn()} result={sample} />
      </ThemeProvider>,
    );

    expect(screen.getByText("Custom text")).toBeInTheDocument();
    const input = screen.getByRole("textbox", { name: "Custom field content" });
    expect(input).toHaveValue("");
    expect(sample.extraFields[0].isValid.isOk).toBe(true);

    await user.type(input, "Updated content");

    expect(input).toHaveValue("Updated content");
    expect(sample.extraFields[0].content).toBe("Updated content");
    expect(sample.extraFields[0].isValid.isOk).toBe(true);
  });
});
