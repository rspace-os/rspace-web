import { replaceValue } from "@/__tests__/helpers/userInteractions";
import { test, describe, expect, vi } from "vitest";
import React, { useState } from "react";
import { render, cleanup, screen } from "@testing-library/react";
import Description from "../Description";
import fc from "fast-check";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
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
function renderDescriptionField(
  initialValue: string,
  onErrorStateChange: (isError: boolean) => void,
) {
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
            setFieldsDirty: ({
              description: newDescription,
            }: {
              description: string;
            }) => {
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
  test("Should not enter an error state when value is shorter than 251.", async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.string({
          maxLength: 250,
        }),
        async (generatedDescription) => {
          cleanup();
          const onErrorStateChange = vi.fn();
          const { container } = renderDescriptionField(
            "foo",
            onErrorStateChange,
          );
          await replaceValue(screen.getByRole("textbox"), generatedDescription);
          expect(container).not.toHaveTextContent(
            "Description must be no longer than 250 characters (including HTML tags).",
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(false);
        },
      ),
    );
  });
  test("Should enter an error state when value is longer than 250 characters.", async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.string({
          minLength: 251,
        }),
        async (generatedDescription) => {
          cleanup();
          const onErrorStateChange = vi.fn();
          const { container } = renderDescriptionField("", onErrorStateChange);
          await replaceValue(screen.getByRole("textbox"), generatedDescription);
          expect(container).toHaveTextContent(
            "Description must be no longer than 250 characters (including HTML tags).",
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(true);
        },
      ),
    );
  });
  test("When the entered text is of a valid length, there should be character count shown.", async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.string({
          maxLength: 250,
        }),
        async (generatedDescription) => {
          cleanup();
          const onErrorStateChange = vi.fn();
          const { container } = renderDescriptionField("", onErrorStateChange);
          await replaceValue(screen.getByRole("textbox"), generatedDescription);
          expect(container).toHaveTextContent(
            `${generatedDescription.length} / 250`,
          );
        },
      ),
    );
  });
});
