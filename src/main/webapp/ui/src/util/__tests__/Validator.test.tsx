import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import { fireEvent, render, screen } from "@testing-library/react";
import { observable, runInAction } from "mobx";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React, { useEffect, useState } from "react";
import { describe, expect, test } from "vitest";
import { mkValidator, type Validator } from "../Validator";

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
    render(<ParentComponent />);
    // initially, this button will do nothing because the validator fails
    fireEvent.click(screen.getByRole("button"));

    expect(screen.getByText("Child 1")).toBeVisible();
    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "moreThanTwoChars" },
    });
    // now that the validation passes the button will work
    fireEvent.click(screen.getByRole("button"));
    expect(await screen.findByText("Child 2")).toBeVisible();
  });
});
