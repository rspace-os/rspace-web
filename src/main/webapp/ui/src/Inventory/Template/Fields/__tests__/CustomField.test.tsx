import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeMockField } from "../../../../stores/models/__tests__/FieldModel/mocking";
import materialTheme from "../../../../theme";
import CustomField from "../CustomField";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));
vi.mock("../../../../components/Ketcher/KetcherDialog", () => ({
  default: vi.fn(() => <div></div>),
}));
describe("CustomField", () => {
  describe("Should be able to delete the field", () => {
    test("Keep field in existing samples", async () => {
      const user = userEvent.setup();
      const field = makeMockField({
        type: "choice",
        definition: {
          options: ["foo", "bar"],
        },
        selectedOptions: ["foo"],
      });

      const onRemove = vi.fn();
      render(
        <ThemeProvider theme={materialTheme}>
          <CustomField
            field={field}
            i={0}
            editable={true}
            onErrorStateChange={() => {}}
            onRemove={onRemove}
            forceColumnLayout={false}
            onMove={() => {}}
          />
        </ThemeProvider>,
      );
      await user.click(screen.getByRole("button", { name: "Delete field" }));
      await user.click(screen.getByRole("menuitem", { name: "Keep field in existing samples" }));
      expect(onRemove).toHaveBeenCalledWith(false);
    });
    test("Remove field in existing samples", async () => {
      const user = userEvent.setup();
      const field = makeMockField({
        type: "choice",
        definition: {
          options: ["foo", "bar"],
        },
        selectedOptions: ["foo"],
      });

      const onRemove = vi.fn();
      render(
        <ThemeProvider theme={materialTheme}>
          <CustomField
            field={field}
            i={0}
            editable={true}
            onErrorStateChange={() => {}}
            onRemove={onRemove}
            forceColumnLayout={false}
            onMove={() => {}}
          />
        </ThemeProvider>,
      );
      await user.click(screen.getByRole("button", { name: "Delete field" }));
      await user.click(
        screen.getByRole("menuitem", {
          name: "Remove field from existing samples",
        }),
      );
      expect(onRemove).toHaveBeenCalledWith(true);
    });
  });
});
