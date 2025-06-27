/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React, { useState } from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import BatchName from "../BatchName";
import { type BatchName as BatchNameType } from "../../../../stores/models/InventoryBaseRecordCollection";
import fc from "fast-check";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

function lengthOfSuffix(suffix: string): number {
  if (suffix === "NONE") return 0;
  if (suffix === "INDEX_NUMBER") return 2;
  if (suffix === "INDEX_LETTER") return 2;
  if (suffix === "CREATED") return 19;
  throw new Error("Invalid suffix string");
}

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function renderWithJustFieldValue(
  initialValue: BatchNameType,
  onErrorStateChange?: (errorState: boolean) => void
) {
  const Wrapper = () => {
    const [name, setName] = useState(initialValue);
    return (
      <ThemeProvider theme={materialTheme}>
        <BatchName
          onErrorStateChange={onErrorStateChange ?? (() => {})}
          fieldOwner={{
            fieldValues: {
              name,
            },
            isFieldEditable: () => true,
            canChooseWhichToEdit: false,
            setFieldsDirty: ({ name: newName }: { name: BatchNameType }) => {
              setName(newName);
            },
            setFieldEditable: () => {},
            noValueLabel: {
              name: "",
            },
          }}
          allowAlphabeticalSuffix={true}
        />
      </ThemeProvider>
    );
  };
  return render(<Wrapper />);
}

describe("BatchName", () => {
  test("Should initially not be in an error state even though the value is the empty string.", () => {
    const { container } = renderWithJustFieldValue({
      common: "",
      suffix: "NONE",
    });
    expect(container).not.toHaveTextContent(
      "Name must be at least 2 characters."
    );
  });

  test("Should enter an error state when value is only a single character and the suffix is NONE.", () => {
    fc.assert(
      fc.property(
        fc
          .string({ minLength: 1, maxLength: 1 })
          .filter((name) => /\S+/.test(name)),
        (name) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderWithJustFieldValue(
            { common: "", suffix: "NONE" },
            onErrorStateChange
          );

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

  /*
   * This is because all of the suffixes have a length >= 2, and the minimum
   * length allows is also 2 chars
   */
  test("Should not enter an error state when value is only a single character and the suffix is not NONE.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc.string({ minLength: 1, maxLength: 1 }),
          fc.constantFrom<"INDEX_NUMBER" | "INDEX_LETTER" | "CREATED">(
            "INDEX_NUMBER",
            "INDEX_LETTER",
            "CREATED"
          )
        ),
        ([common, suffix]) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderWithJustFieldValue(
            { common: "", suffix },
            onErrorStateChange
          );

          fireEvent.input(screen.getByRole("textbox"), {
            target: { value: common },
          });

          expect(container).not.toHaveTextContent(
            "Name must be at least 2 characters."
          );
          expect(onErrorStateChange).toHaveBeenCalledWith(false);
        }
      )
    );
  });

  describe("Should enter an error state when the value is too long.", () => {
    test('When the suffix is "NONE".', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 256 - lengthOfSuffix("NONE") }),
          (common) => {
            cleanup();
            const onErrorStateChange = jest.fn();
            const { container } = renderWithJustFieldValue(
              { common: "", suffix: "NONE" },
              onErrorStateChange
            );

            fireEvent.input(screen.getByRole("textbox"), {
              target: { value: common },
            });

            expect(container).toHaveTextContent(
              "Name must be no longer than 255 characters."
            );
            expect(onErrorStateChange).toHaveBeenCalledWith(true);
          }
        )
      );
    });

    test('When the suffix is "INDEX_NUMBER".', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 256 - lengthOfSuffix("INDEX_NUMBER") }),
          (common) => {
            cleanup();
            const onErrorStateChange = jest.fn();
            const { container } = renderWithJustFieldValue(
              { common: "", suffix: "INDEX_NUMBER" },
              onErrorStateChange
            );

            fireEvent.input(screen.getByRole("textbox"), {
              target: { value: common },
            });

            expect(container).toHaveTextContent(
              "Name must be no longer than 253 characters."
            );
            expect(onErrorStateChange).toHaveBeenCalledWith(true);
          }
        )
      );
    });

    test('When the suffix is "INDEX_LETTER".', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 256 - lengthOfSuffix("INDEX_LETTER") }),
          (common) => {
            cleanup();
            const onErrorStateChange = jest.fn();
            const { container } = renderWithJustFieldValue(
              { common: "", suffix: "INDEX_LETTER" },
              onErrorStateChange
            );

            fireEvent.input(screen.getByRole("textbox"), {
              target: { value: common },
            });

            expect(container).toHaveTextContent(
              "Name must be no longer than 253 characters."
            );
            expect(onErrorStateChange).toHaveBeenCalledWith(true);
          }
        )
      );
    });

    test('When the suffix is "CREATED".', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 256 - lengthOfSuffix("CREATED") }),
          (common) => {
            cleanup();
            const onErrorStateChange = jest.fn();
            const { container } = renderWithJustFieldValue(
              { common: "", suffix: "CREATED" },
              onErrorStateChange
            );

            fireEvent.input(screen.getByRole("textbox"), {
              target: { value: common },
            });

            expect(container).toHaveTextContent(
              "Name must be no longer than 236 characters."
            );
            expect(onErrorStateChange).toHaveBeenCalledWith(true);
          }
        )
      );
    });
  });

  /*
   * Only applies when suffix is NONE because there is no minimum common for
   * the other suffixes
   */
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
          const { container } = renderWithJustFieldValue(
            { common: "", suffix: "NONE" },
            onErrorStateChange
          );

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

  test("Should enter an error state when value is just whitespace.", () => {
    fc.assert(
      fc.property(
        fc
          .string({ minLength: 2, maxLength: 255 })
          .filter((name) => /^\s+$/.test(name)),
        (name) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderWithJustFieldValue(
            { common: "", suffix: "NONE" },
            onErrorStateChange
          );

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
  test("When the entered text is of a valid length, there should be character count shown.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc.string({ minLength: 3, maxLength: 255 }),
          fc.constantFrom<"INDEX_NUMBER" | "INDEX_LETTER" | "CREATED">(
            "INDEX_NUMBER",
            "INDEX_LETTER",
            "CREATED"
          )
        ),
        ([generatedName, suffix]) => {
          cleanup();
          const onErrorStateChange = jest.fn();
          const { container } = renderWithJustFieldValue(
            { common: "", suffix },
            onErrorStateChange
          );

          fireEvent.change(screen.getByRole("textbox"), {
            target: { value: generatedName },
          });

          expect(container).toHaveTextContent(
            `${generatedName.length} / ${255 - lengthOfSuffix(suffix)}`
          );
        }
      )
    );
  });
});
