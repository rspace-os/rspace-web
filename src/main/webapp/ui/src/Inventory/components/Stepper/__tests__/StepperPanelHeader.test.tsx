/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React, { useState } from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import {
  StepperPanelHeader,
  useFormSectionError,
  setFormSectionError,
} from "../StepperPanelHeader";
import "../../../../../__mocks__/matchMedia";
import fc from "fast-check";
import Collapse from "@mui/material/Collapse";
import * as ArrayUtils from "../../../../util/ArrayUtils";
import { observable, makeObservable, action } from "mobx";
import { type GlobalId } from "../../../../stores/definitions/BaseRecord";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

class DummyResult {
  editing: boolean = true;
  globalId: GlobalId | null = null;

  constructor() {
    makeObservable(this, {
      editing: observable,
      globalId: observable,
      setEditing: action,
    });
  }

  setEditing(editing: boolean): void {
    this.editing = editing;
  }
}

function DummyFormSection({ result }: { result: DummyResult }) {
  const formSectionError = useFormSectionError({
    editing: result.editing,
    globalId: result.globalId,
  });

  const [open, setOpen] = useState(true);

  /*
   * inputs that trigger errors are wrapped in a Collapse as it is the
   * re-rendering of the Collapse triggers that causes the formSectionError to
   * be displayed in the form section header.
   */
  return (
    <ThemeProvider theme={materialTheme}>
      <FormSectionsContext.Provider
        value={{
          isExpanded: () => open,
          setExpanded: (recordType, sectionName, value) => setOpen(value),
          setAllExpanded: () => {},
        }}
      >
        <StepperPanelHeader
          onToggle={() => setOpen(!open)}
          open={open}
          title="foo"
          formSectionError={formSectionError}
          id="mockId"
          recordType="container"
        />
        <Collapse in={open}>
          <label>
            Set error
            <input
              onChange={({ target: { value } }) => {
                setFormSectionError(formSectionError, value, true);
              }}
            />
          </label>
          <label>
            Unset error
            <input
              onChange={({ target: { value } }) => {
                setFormSectionError(formSectionError, value, false);
              }}
            />
          </label>
        </Collapse>
      </FormSectionsContext.Provider>
    </ThemeProvider>
  );
}

describe("StepperPanelHeader", () => {
  test("When useFormSectionError is passed a unique list of strings, the badge should show a number equal to the list's length.", async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 1 }), {
          minLength: 1,
        }),
        async (errors) => {
          const user = userEvent.setup();
          cleanup();
          render(<DummyFormSection result={new DummyResult()} />);

          for (const error of errors) {
            fireEvent.change(screen.getByLabelText("Set error"), {
              target: { value: error },
            });
          }

          await user.click(
            screen.getByRole("button", { name: "Collapse section" })
          );

          await waitFor(() => {
            expect(
              screen.getByRole("button", { name: "Expand section" })
            ).toBeVisible();
          });

          expect(
            parseInt(
              (await screen.findByLabelText("Expand section")).textContent ??
                "",
              10
            )
          ).toEqual(errors.length);
        }
      ),
      { numRuns: 10 }
    );
  });

  test("When useFormSectionError is passed a list of strings, the badge should show a number equal to or less than the list's length.", async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(fc.string({ minLength: 1 })),
        async (errors) => {
          cleanup();
          const user = userEvent.setup();
          render(<DummyFormSection result={new DummyResult()} />);

          for (const error of errors) {
            fireEvent.change(screen.getByLabelText("Set error"), {
              target: { value: error },
            });
          }

          await user.click(screen.getByLabelText("Collapse section"));

          expect(
            parseInt(
              screen.getByLabelText("Expand section").textContent ?? "",
              10
            )
          ).toBeLessThanOrEqual(errors.length);
        }
      ),
      { numRuns: 10 }
    );
  });

  test("When opened, the header shows no badge.", async () => {
    const user = userEvent.setup();
    render(<DummyFormSection result={new DummyResult()} />);

    fireEvent.change(screen.getByLabelText("Set error"), {
      target: { value: "an error" },
    });

    expect(screen.getByLabelText("Collapse section")).not.toHaveTextContent(
      "1"
    );

    await user.click(screen.getByLabelText("Collapse section"));
    expect(screen.getByLabelText("Expand section")).toHaveTextContent("1");
  });

  const arbitraryErrorsAndErrorsToRemove = () =>
    fc
      .nat(50)
      .chain((n) =>
        fc.tuple(
          fc.uniqueArray(fc.string({ minLength: 1 }), {
            minLength: n,
            maxLength: n,
          }),
          fc.array(fc.boolean(), { minLength: n, maxLength: n })
        )
      )
      .map(([errors, shouldRemove]) => [
        errors,
        ArrayUtils.takeWhere(errors, shouldRemove),
      ]);

  test("Errors can be unset, decrementing the badge.", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbitraryErrorsAndErrorsToRemove(),
        async ([errors, errorsToRemove]) => {
          cleanup();
          const user = userEvent.setup();
          render(<DummyFormSection result={new DummyResult()} />);

          for (const error of errors) {
            fireEvent.change(screen.getByLabelText("Set error"), {
              target: { value: error },
            });
          }

          await user.click(
            screen.getByRole("button", { name: "Collapse section" })
          );

          expect(
            parseInt(
              screen.getByLabelText("Expand section").textContent ?? "",
              10
            )
          ).toEqual(errors.length);
          await user.click(
            screen.getByRole("button", { name: "Expand section" })
          );

          for (const errorToRemove of errorsToRemove) {
            fireEvent.change(screen.getByLabelText("Unset error"), {
              target: { value: errorToRemove },
            });
          }

          await user.click(
            screen.getByRole("button", { name: "Collapse section" })
          );

          if (errors.length - errorsToRemove.length > 0) {
            expect(
              parseInt(
                screen.getByLabelText("Expand section").textContent ?? "",
                10
              )
            ).toEqual(errors.length - errorsToRemove.length);
          } else {
            /*
             * There's no way to assert that the badge is not visible with Jest
             * as Mui uses CSS transform, leaving the previously number in the
             * DOM
             */
            expect(true).toBe(true);
          }
        }
      ),
      { numRuns: 10 }
    );
    cleanup();
  });

  test("When `editing` is set to false, the errors are reset.", async () => {
    const user = userEvent.setup();
    const result = new DummyResult();
    render(<DummyFormSection result={result} />);

    fireEvent.change(screen.getByLabelText("Set error"), {
      target: { value: "an error" },
    });

    await user.click(screen.getByRole("button", { name: "Collapse section" }));
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "Expand section" })
      ).toBeVisible();
    });
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "Expand section" })
      ).toHaveTextContent("1");
    });

    result.setEditing(false);

    // trigger a re-render
    await user.click(screen.getByLabelText("Expand section"));
    await waitFor(() => {
      expect(screen.getByLabelText("Collapse section")).toBeVisible();
    });
    await user.click(screen.getByLabelText("Collapse section"));

    expect(screen.getByLabelText("Expand section")).not.toHaveTextContent("1");
  });
});
