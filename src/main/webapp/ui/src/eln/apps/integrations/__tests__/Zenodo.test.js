/*
 * @jest-environment jsdom
 */
//@flow strict
/* eslint-env jest */
import React, { useState } from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import fc, { type Command } from "fast-check";
import Zenodo from "../Zenodo";
import { Optional } from "../../../../util/optional";
import "../../../../../__mocks__/matchMedia";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const ZenodoWrapper = () => {
  const [state, setState] = useState({
    mode: "DISABLED",
    credentials: { ZENODO_USER_TOKEN: Optional.present("") },
  });
  return (
    <Zenodo
      integrationState={state}
      update={(newState) => {
        setState(newState);
      }}
    />
  );
};

describe("Zenodo", () => {
  /**
   * This model-based test generates a random sequence of the five actions that a user may perform
   * with regards to the Zenodo integration card:
   *   - Open the dialog
   *   - Close the dialog
   *   - Enable the integration
   *   - Disable the integration
   *   - Set an API key
   *
   * This test asserts that these actions may be composed in any order provided that these
   * constraints are met:
   *   - Only opening the dialog may be performed when the dialog is closed
   *   - Enabling the integration is only possible if the integration is disabled
   *   - Disabling the integration is only possible if the integration is enabled
   */

  type Model = {|
    open: boolean,
    enabled: boolean,
  |};

  type Real = {|
    getByRole: typeof screen.getByRole,
    getByLabelText: typeof screen.getByLabelText,
  |};

  class OpenDialogCommand implements Command<Model, Real> {
    constructor() {}

    check(model: Model): boolean {
      return !model.open;
    }

    run(model: Model, { getByRole }: Real) {
      fireEvent.click(getByRole("button", { name: /^zenodo/i }));
      expect(getByRole("dialog")).toBeVisible();
      model.open = true;
    }

    toString(): string {
      return "open";
    }
  }

  class CloseDialogCommand implements Command<Model, Real> {
    constructor() {}

    check(model: Model): boolean {
      return model.open;
    }

    async run(model: Model, { getByRole }: Real) {
      fireEvent.click(
        within(getByRole("dialog")).getByRole("button", { name: /close/i })
      );
      await waitFor(() => {
        expect(getByRole("dialog")).not.toBeVisible();
      });
      await waitFor(() => {
        expect(getByRole("button", { name: /^zenodo/i })).toBeVisible();
      });
      model.open = false;
    }

    toString(): string {
      return "close";
    }
  }

  class EnableCommand implements Command<Model, Real> {
    constructor() {}

    check(model: Model): boolean {
      return model.open && !model.enabled;
    }

    async run(model: Model, { getByRole }: Real) {
      fireEvent.click(
        within(getByRole("dialog")).getByRole("button", { name: /enable/i })
      );
      await waitFor(() => {
        expect(
          within(getByRole("dialog")).getByRole("button", { name: /disable/i })
        ).toBeVisible();
      });
      model.enabled = true;
    }

    toString(): string {
      return "enable";
    }
  }

  class DisableCommand implements Command<Model, Real> {
    constructor() {}

    check(model: Model): boolean {
      return model.open && model.enabled;
    }

    async run(model: Model, { getByRole }: Real) {
      fireEvent.click(
        within(getByRole("dialog")).getByRole("button", { name: /disable/i })
      );
      await waitFor(() => {
        expect(
          within(getByRole("dialog")).getByRole("button", { name: /enable/i })
        ).toBeVisible();
      });
      model.enabled = false;
    }

    toString(): string {
      return "disabled";
    }
  }

  class SetApiKeyCommand implements Command<Model, Real> {
    apiKey: string;

    constructor(apiKey: string) {
      this.apiKey = apiKey;
    }

    check(model: Model): boolean {
      return model.open;
    }

    async run(model: Model, { getByRole, getByLabelText }: Real) {
      /*
       * We have to use getByLabelText instead of getByRole because password
       * fields do not have a role. For more info, see
       * https://github.com/testing-library/dom-testing-library/issues/567
       */
      fireEvent.change(getByLabelText("API Key"), {
        target: { value: this.apiKey },
      });
      fireEvent.click(getByRole("button", { name: /save/i }));
    }

    toString(): string {
      return "set API key " + '"' + this.apiKey + '"';
    }
  }

  test(
    "Model testing all possible actions",
    async () => {
      const allCommands = [
        fc.constant<Command<Model, Real>>(new OpenDialogCommand()),
        fc.constant<Command<Model, Real>>(new CloseDialogCommand()),
        fc.constant<Command<Model, Real>>(new EnableCommand()),
        fc.constant<Command<Model, Real>>(new DisableCommand()),
        fc
          .string()
          .map<Command<Model, Real>>((apiKey) => new SetApiKeyCommand(apiKey)),
      ];
      await fc.assert(
        fc.asyncProperty(
          fc.commands<Model, Real>(allCommands, { size: "medium" }),
          async (cmds: Array<Command<Model, Real>>) => {
            cleanup();
            const { getByRole, getByLabelText } = render(<ZenodoWrapper />);
            const s = () => ({
              model: {
                open: false,
                enabled: false,
              },
              real: { getByRole, getByLabelText },
            });
            await waitFor(() => {
              screen.getByText("Zenodo");
            });
            await fc.asyncModelRun(s, cmds);
            cleanup();
          }
        ),
        { numRuns: 2 }
      );
    },
    30 * 1000
  );
});
