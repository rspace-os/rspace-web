/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React, { useState } from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import Name from "../Name";
import fc from "fast-check";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function renderNameField(
  initialValue: string,
  onErrorStateChange: (value: boolean) => void
) {
  const Wrapper = () => {
    const [name, setName] = useState(initialValue);
    return (
      <ThemeProvider theme={materialTheme}>
        <Name
          fieldOwner={{
            fieldValues: {
              name,
            },
            isFieldEditable: () => true,
            setFieldsDirty: ({ name: newName }: { name: string }) => {
              setName(newName);
            },
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: {
              name: "",
            },
          }}
          onErrorStateChange={onErrorStateChange}
        />
      </ThemeProvider>
    );
  };
  return render(<Wrapper />);
}

describe("Name", () => {
  test("Should initially not be in an error state even though the value is the empty string.", () => {
    const { container } = renderNameField("", () => {});
    expect(container).not.toHaveTextContent(
      "Name must be at least 2 characters."
    );
    expect(container).not.toHaveTextContent(
      "Name must include at least one non-whitespace character."
    );
    expect(container).toHaveTextContent("0 / 255");
  });

  test("Should enter an error state when value is only a single character.", () => {
    fc.assert(
      fc.property(
        fc
          .string({ minLength: 1, maxLength: 1 })
          .filter((name) => /\S/.test(name)),
        (name) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderNameField("", onErrorStateChange);

          fireEvent.input(screen.getByRole("textbox"), {
            target: { value: name },
          });

          expect(container).toHaveTextContent(
            "Name must be at least 2 characters."
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(true);
        }
      )
    );
  });

  test("Should not enter an error state when value is longer than 1 character but shorter than 256.", () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 3, maxLength: 255 }),
        (generatedName) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderNameField("", onErrorStateChange);

          fireEvent.change(screen.getByRole("textbox"), {
            target: { value: generatedName },
          });

          expect(container).not.toHaveTextContent(
            "Name must be at least 2 characters."
          );
          expect(container).not.toHaveTextContent(
            "Name must be no longer than 255 characters."
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(false);
        }
      )
    );
  });

  test("Should enter an error state when value is longer than 255 characters.", () => {
    fc.assert(
      fc.property(fc.string({ minLength: 256 }), (name) => {
        cleanup();
        const onErrorStateChange = jest.fn();
        const { container } = renderNameField("", onErrorStateChange);

        fireEvent.change(screen.getByRole("textbox"), {
          target: { value: name },
        });

        expect(container).toHaveTextContent(
          "Name must be no longer than 255 characters."
        );
        expect(onErrorStateChange).toHaveBeenCalledWith(true);
      })
    );
  });

  test("Should enter an error state when value is just whitespace.", () => {
    fc.assert(
      fc.property(
        fc
          .string({ minLength: 2, maxLength: 255 })
          .filter((name) => /^\s+$/.test(name)),
        (name) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderNameField("", onErrorStateChange);

          fireEvent.change(screen.getByRole("textbox"), {
            target: { value: name },
          });

          expect(container).toHaveTextContent(
            "Name must include at least one non-whitespace character."
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(true);
        }
      ),
      { numRuns: 10 }
    );
  });

  test("Entering fewer than 2 characters, after having entered something valid, should error.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc
            .string({ minLength: 3, maxLength: 255 })
            .filter((firstValidValue) => /\S+/.test(firstValidValue)),
          fc
            .string({ minLength: 0, maxLength: 1 })
            .filter((secondInvalidValue) => /\S+/.test(secondInvalidValue))
        ),
        ([firstValidValue, secondInvalidValue]) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderNameField("", onErrorStateChange);

          fireEvent.change(screen.getByRole("textbox"), {
            target: { value: firstValidValue },
          });

          expect(container).not.toHaveTextContent(
            "Name must be at least 2 characters."
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(false);

          fireEvent.change(screen.getByRole("textbox"), {
            target: { value: secondInvalidValue },
          });

          expect(container).toHaveTextContent(
            "Name must be at least 2 characters."
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(true);
        }
      )
    );
  });

  test("When the entered text is of a valid length, there should be character count shown.", () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 3, maxLength: 255 }),
        (generatedName) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderNameField("", onErrorStateChange);

          fireEvent.change(screen.getByRole("textbox"), {
            target: { value: generatedName },
          });

          expect(container).toHaveTextContent(`${generatedName.length} / 255`);
        }
      )
    );
  });

  test("When disabled, the Global ID of the passed record should be shown.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <Name
          fieldOwner={{
            fieldValues: {
              name: "",
            },
            isFieldEditable: () => false,
            setFieldsDirty: () => {},
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: {
              name: "",
            },
          }}
          onErrorStateChange={() => {}}
          record={makeMockContainer()}
        />
      </ThemeProvider>
    );

    expect(screen.getByRole("link", { name: "IC1" })).toBeVisible();
  });
});
