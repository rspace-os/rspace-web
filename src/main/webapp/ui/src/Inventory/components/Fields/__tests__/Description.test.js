/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../../__mocks__/matchMedia";
import React, { useState } from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import Description from "../Description";
import fc from "fast-check";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

jest.mock("@tinymce/tinymce-react", () => ({
  __esModule: true,
  Editor: jest.fn(({ onEditorChange, value }) => (
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
  onErrorStateChange: (boolean) => void
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
              description: string,
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
  test("Should not enter an error state when value is shorter than 251.", () => {
    fc.assert(
      fc.property(fc.string({ maxLength: 250 }), (generatedDescription) => {
        cleanup();
        const onErrorStateChange = jest.fn<[boolean], void>();
        const { container } = renderDescriptionField("foo", onErrorStateChange);

        fireEvent.change(screen.getByRole("textbox"), {
          target: { value: generatedDescription },
        });

        expect(container).not.toHaveTextContent(
          "Description must be no longer than 250 characters (including HTML tags)."
        );
        expect(onErrorStateChange).toHaveBeenCalledWith(false);
      })
    );
  });

  test("Should enter an error state when value is longer than 250 characters.", () => {
    fc.assert(
      fc.property(fc.string({ minLength: 251 }), (generatedDescription) => {
        cleanup();
        const onErrorStateChange = jest.fn<[boolean], void>();
        const { container } = renderDescriptionField("", onErrorStateChange);

        fireEvent.change(screen.getByRole("textbox"), {
          target: { value: generatedDescription },
        });

        expect(container).toHaveTextContent(
          "Description must be no longer than 250 characters (including HTML tags)."
        );
        expect(onErrorStateChange).toHaveBeenCalledWith(true);
      })
    );
  });

  test("When the entered text is of a valid length, there should be character count shown.", () => {
    fc.assert(
      fc.property(fc.string({ maxLength: 250 }), (generatedDescription) => {
        cleanup();
        const onErrorStateChange = jest.fn<[boolean], void>();
        const { container } = renderDescriptionField("", onErrorStateChange);

        fireEvent.change(screen.getByRole("textbox"), {
          target: { value: generatedDescription },
        });

        expect(container).toHaveTextContent(
          `${generatedDescription.length} / 250`
        );
      })
    );
  });
});
