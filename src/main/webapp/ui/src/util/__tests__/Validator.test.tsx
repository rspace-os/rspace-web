/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React, { useState, useEffect } from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import { type Validator, mkValidator } from "../Validator";
import { runInAction, observable } from "mobx";
import { doNotAwait } from "../Util";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
        onClick={doNotAwait(async () => {
          if (await panel1Validator.isValid()) setActivePane(activePane + 1);
        })}
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
