import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import fc from "fast-check";
import { useState } from "react";
import materialTheme from "../../../../theme";
import Description from "../Description";

vi.mock("@tinymce/tinymce-react", () => ({
  __esModule: true,
  Editor: vi.fn(({ onEditorChange, value }) => (
    <input
      value={value}
      onChange={(e) => {
        onEditorChange(e.target.value);
      }}
    />
  )),
}));
function renderDescriptionField(initialValue: string, onErrorStateChange: (isError: boolean) => void) {
  const Wrapper = () => {
    const [description, setDescription] = useState(initialValue);
    return (
      <ThemeProvider theme={materialTheme}>
        <Description
          fieldOwner={{
            fieldValues: {
              description,
            },
            isFieldEditable: () => true,
            setFieldsDirty: ({ description: newDescription }: { description: string }) => {
              setDescription(newDescription);
            },
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: {
              description: "",
            },
          }}
          onErrorStateChange={onErrorStateChange}
        />
      </ThemeProvider>
    );
  };
  return render(<Wrapper />);
}
describe("Description", () => {
  test("Should not enter an error state when value is shorter than 2001.", async () => {
    await fc.assert(
      fc.asyncProperty(fc.string({ maxLength: 2000, size: "max" }), async (generatedDescription) => {
        const user = userEvent.setup();
        cleanup();
        const onErrorStateChange = vi.fn();

        const { container } = renderDescriptionField("foo", onErrorStateChange);
        const input = screen.getByRole("textbox");
        await user.clear(input);
        await user.paste(generatedDescription);
        expect(container).not.toMatchTextContent("fields.description.maxLength");
        expect(onErrorStateChange).toHaveBeenCalledWith(false);
      }),
    );
  });
  test("Should enter an error state when value is longer than 2000 characters.", async () => {
    await fc.assert(
      fc.asyncProperty(fc.string({ minLength: 2001 }), async (generatedDescription) => {
        const user = userEvent.setup();
        cleanup();
        const onErrorStateChange = vi.fn();

        const { container } = renderDescriptionField("", onErrorStateChange);
        const input = screen.getByRole("textbox");
        await user.click(input);
        await user.paste(generatedDescription);
        expect(container).toMatchTextContent("fields.description.maxLength");
        expect(onErrorStateChange).toHaveBeenCalledWith(true);
      }),
    );
  });
  test("When the entered text is of a valid length, there should be character count shown.", async () => {
    await fc.assert(
      fc.asyncProperty(fc.string({ maxLength: 2000, size: "max" }), async (generatedDescription) => {
        const user = userEvent.setup();
        cleanup();
        const onErrorStateChange = vi.fn();

        const { container } = renderDescriptionField("", onErrorStateChange);
        const input = screen.getByRole("textbox");
        await user.click(input);
        await user.paste(generatedDescription);
        expect(container).toMatchTextContent(`${generatedDescription.length} / 2000`);
      }),
    );
  });
});
