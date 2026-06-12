import { test, describe, expect } from "vitest";
import React, { useState, useEffect } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import { type Validator, mkValidator } from "../Validator";
import { runInAction, observable } from "mobx";
function Child1({ validator }: { validator: Validator }) {
  const [state] = useState(observable({ text: "" }));
  useEffect(() => {
    validator.setValidFunc(() => {
      return Promise.resolve(state.text.length > 2);
    });
  }, [state.text.length, validator]);
  return (
    <>
      <h1>Child 1</h1>
      <TextField
        value={state.text}
        onChange={({ target: { value } }) => {
          runInAction(() => {
            state.text = value;
          });
        }}
      />
    </>
  );
}
function Child2() {
  return <h1>Child 2</h1>;
}
function ParentComponent() {
  const [activePane, setActivePane] = useState(0);

  const [panel1Validator] = useState(mkValidator());
  return (
    <>
      {activePane === 0 && <Child1 validator={panel1Validator} />}
      {activePane === 1 && <Child2 />}
      <Button
        onClick={() => {
          void (async () => {
            if (await panel1Validator.isValid()) setActivePane(activePane + 1);
          })();
        }}
      >
        Next
      </Button>
    </>
  );
}
describe("Validator", () => {
  test("Example", async () => {
    const user = userEvent.setup();
    render(<ParentComponent />);
    // initially, this button will do nothing because the validator fails
    await user.click(screen.getByRole("button"));

    expect(screen.getByText("Child 1")).toBeVisible();
    const input = screen.getByRole("textbox");
    await user.click(input);
    await user.clear(input);
    await user.paste("moreThanTwoChars");
    // now that the validation passes the button will work
    await user.click(screen.getByRole("button"));
    expect(await screen.findByText("Child 2")).toBeVisible();
  });
});
