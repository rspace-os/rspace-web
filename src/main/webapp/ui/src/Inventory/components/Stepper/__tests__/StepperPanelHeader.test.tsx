import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, test } from "vitest";
import { StepperPanelHeader, setFormSectionError, useFormSectionError } from "../StepperPanelHeader";
import "@/__tests__/__mocks__/matchMedia";
import Collapse from "@mui/material/Collapse";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import fc from "fast-check";
import { action, makeObservable, observable } from "mobx";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import type { GlobalId } from "../../../../stores/definitions/BaseRecord";
import materialTheme from "../../../../theme";
import * as ArrayUtils from "../../../../util/ArrayUtils";

async function replaceInputValue(user: ReturnType<typeof userEvent.setup>, label: string, value: string) {
  const input = screen.getAllByLabelText(label).at(-1);
  if (!(input instanceof HTMLInputElement)) {
    throw new Error(`Could not find input labelled "${label}"`);
  }
  await user.click(input);
  input.setSelectionRange(0, input.value.length);
  await user.paste(value);
}

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
          setExpanded: (_recordType, _sectionName, value) => setOpen(value),
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
            {"Set error"}
            <input
              onChange={({ target: { value } }) => {
                setFormSectionError(formSectionError, value, true);
              }}
            />
          </label>
          <label>
            {"Unset error"}
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
            await replaceInputValue(user, "Set error", error);
          }
          await user.click(screen.getByRole("button", { name: "inventory:formSections.collapseSection" }));
          await waitFor(() => {
            expect(screen.getByRole("button", { name: "inventory:formSections.expandSection" })).toBeVisible();
          });
          expect(
            parseInt((await screen.findByLabelText("inventory:formSections.expandSection")).textContent ?? "", 10),
          ).toEqual(errors.length);
        },
      ),
      { numRuns: 10 },
    );
  });
  test("When useFormSectionError is passed a list of strings, the badge should show a number equal to or less than the list's length.", async () => {
    await fc.assert(
      fc.asyncProperty(fc.array(fc.string({ minLength: 1 })), async (errors) => {
        cleanup();
        const user = userEvent.setup();

        render(<DummyFormSection result={new DummyResult()} />);
        for (const error of errors) {
          await replaceInputValue(user, "Set error", error);
        }

        await user.click(screen.getByLabelText("inventory:formSections.collapseSection"));
        expect(
          parseInt(screen.getByLabelText("inventory:formSections.expandSection").textContent ?? "", 10),
        ).toBeLessThanOrEqual(errors.length);
      }),
      { numRuns: 10 },
    );
  });
  test("When opened, the header shows no badge.", async () => {
    cleanup();
    const user = userEvent.setup();

    render(<DummyFormSection result={new DummyResult()} />);
    await replaceInputValue(user, "Set error", "an error");
    expect(screen.getByLabelText("inventory:formSections.collapseSection")).not.toHaveTextContent("1");
    await user.click(screen.getByLabelText("inventory:formSections.collapseSection"));
    expect(screen.getByLabelText("inventory:formSections.expandSection")).toHaveTextContent("1");
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
          fc.array(fc.boolean(), { minLength: n, maxLength: n }),
        ),
      )
      .map(([errors, shouldRemove]) => [errors, ArrayUtils.takeWhere(errors, shouldRemove)]);
  test("Errors can be unset, decrementing the badge.", async () => {
    await fc.assert(
      fc.asyncProperty(arbitraryErrorsAndErrorsToRemove(), async ([errors, errorsToRemove]) => {
        cleanup();
        const user = userEvent.setup();

        render(<DummyFormSection result={new DummyResult()} />);
        for (const error of errors) {
          await replaceInputValue(user, "Set error", error);
        }
        await user.click(screen.getByRole("button", { name: "inventory:formSections.collapseSection" }));
        expect(parseInt(screen.getByLabelText("inventory:formSections.expandSection").textContent ?? "", 10)).toEqual(
          errors.length,
        );
        await user.click(screen.getByRole("button", { name: "inventory:formSections.expandSection" }));
        for (const errorToRemove of errorsToRemove) {
          await replaceInputValue(user, "Unset error", errorToRemove);
        }
        await user.click(screen.getByRole("button", { name: "inventory:formSections.collapseSection" }));
        if (errors.length - errorsToRemove.length > 0) {
          expect(parseInt(screen.getByLabelText("inventory:formSections.expandSection").textContent ?? "", 10)).toEqual(
            errors.length - errorsToRemove.length,
          );
        } else {
          /*
           * There's no way to assert that the badge is not visible with Jest
           * as Mui uses CSS transform, leaving the previously number in the
           * DOM
           */
          expect(true).toBe(true);
        }
      }),
      { numRuns: 10 },
    );
    cleanup();
  });
  test("When `editing` is set to false, the errors are reset.", async () => {
    cleanup();
    const user = userEvent.setup();
    const result = new DummyResult();

    render(<DummyFormSection result={result} />);
    await replaceInputValue(user, "Set error", "an error");
    await user.click(screen.getByRole("button", { name: "inventory:formSections.collapseSection" }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "inventory:formSections.expandSection" })).toBeVisible();
    });
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "inventory:formSections.expandSection" })).toHaveTextContent("1");
    });

    result.setEditing(false);
    // trigger a re-render
    await user.click(screen.getByLabelText("inventory:formSections.expandSection"));
    await waitFor(() => {
      expect(screen.getByLabelText("inventory:formSections.collapseSection")).toBeVisible();
    });

    await user.click(screen.getByLabelText("inventory:formSections.collapseSection"));
    expect(screen.getByLabelText("inventory:formSections.expandSection")).not.toHaveTextContent("1");
  });
});
