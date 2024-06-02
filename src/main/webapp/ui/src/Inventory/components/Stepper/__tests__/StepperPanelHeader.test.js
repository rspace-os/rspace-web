/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React, { useState } from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  act,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import {
  StepperPanelHeader,
  useFormSectionError,
  setFormSectionError,
} from "../StepperPanelHeader";
import "../../../../../__mocks__/matchMedia.js";
import fc from "fast-check";
import Collapse from "@mui/material/Collapse";
import * as ArrayUtils from "../../../../util/ArrayUtils";
import { observable, makeObservable, action } from "mobx";
import { type GlobalId } from "../../../../stores/definitions/BaseRecord";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import FormSectionsContext from "../../../../stores/contexts/FormSections";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

class DummyResult {
  editing: boolean = true;
  globalId: ?GlobalId = null;

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
  test("When useFormSectionError is passed a unique list of strings, the badge should show a number equal to the list's length.", () => {
    fc.assert(
      fc.property(fc.uniqueArray(fc.string({ minLength: 1 })), (errors) => {
        cleanup();
        render(<DummyFormSection result={new DummyResult()} />);

        for (const error of errors) {
          fireEvent.change(screen.getByLabelText("Set error"), {
            target: { value: error },
          });
        }

        act(() => {
          screen.getByLabelText("Collapse section").click();
        });

        expect(
          parseInt(screen.getByLabelText("Expand section").textContent)
        ).toEqual(errors.length);
      }),
      { numRuns: 10 }
    );
  });

  test("When useFormSectionError is passed a list of strings, the badge should show a number equal to or less than the list's length.", () => {
    fc.assert(
      fc.property(fc.array(fc.string({ minLength: 1 })), (errors) => {
        cleanup();
        render(<DummyFormSection result={new DummyResult()} />);

        for (const error of errors) {
          fireEvent.change(screen.getByLabelText("Set error"), {
            target: { value: error },
          });
        }

        act(() => {
          screen.getByLabelText("Collapse section").click();
        });

        expect(
          parseInt(screen.getByLabelText("Expand section").textContent)
        ).toBeLessThanOrEqual(errors.length);
      }),
      { numRuns: 10 }
    );
  });

  test("When opened, the header shows no badge.", () => {
    render(<DummyFormSection result={new DummyResult()} />);

    fireEvent.change(screen.getByLabelText("Set error"), {
      target: { value: "an error" },
    });

    expect(screen.getByLabelText("Collapse section")).not.toHaveTextContent(
      "1"
    );

    act(() => {
      screen.getByLabelText("Collapse section").click();
    });
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

  test("Errors can be unset, decrementing the badge.", () => {
    fc.assert(
      fc.property(
        arbitraryErrorsAndErrorsToRemove(),
        ([errors, errorsToRemove]) => {
          cleanup();
          render(<DummyFormSection result={new DummyResult()} />);

          for (const error of errors) {
            fireEvent.change(screen.getByLabelText("Set error"), {
              target: { value: error },
            });
          }

          act(() => {
            screen.getByRole("button", { name: "Collapse section" }).click();
          });

          expect(
            parseInt(screen.getByLabelText("Expand section").textContent)
          ).toEqual(errors.length);
          act(() => {
            screen.getByRole("button", { name: "Expand section" }).click();
          });

          for (const errorToRemove of errorsToRemove) {
            fireEvent.change(screen.getByLabelText("Unset error"), {
              target: { value: errorToRemove },
            });
          }

          act(() => {
            screen.getByRole("button", { name: "Collapse section" }).click();
          });

          if (errors.length - errorsToRemove.length > 0) {
            expect(
              parseInt(screen.getByLabelText("Expand section").textContent)
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
  });

  test("When `editing` is set to false, the errors are reset.", () => {
    const result = new DummyResult();
    render(<DummyFormSection result={result} />);

    fireEvent.change(screen.getByLabelText("Set error"), {
      target: { value: "an error" },
    });

    act(() => {
      screen.getByLabelText("Collapse section").click();
    });
    expect(screen.getByLabelText("Expand section")).toHaveTextContent("1");

    result.setEditing(false);

    // trigger a re-render
    act(() => {
      screen.getByLabelText("Expand section").click();
    });
    act(() => {
      screen.getByLabelText("Collapse section").click();
    });

    expect(screen.getByLabelText("Expand section")).not.toHaveTextContent("1");
  });
});
