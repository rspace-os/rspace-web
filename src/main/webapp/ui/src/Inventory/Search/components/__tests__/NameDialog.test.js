/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import NameDialog from "../NameDialog";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("NameDialog", () => {
  test("Naming a new saved search the same name as an existing saved search should be an error.", () => {
    render(
      <NameDialog
        open={true}
        setOpen={() => {}}
        name="foo"
        setName={() => {}}
        existingNames={["foo"]}
        onChange={() => {}}
      />
    );

    expect(
      screen.getByText("This name is already taken. Please modify it.")
    ).toBeVisible();
  });
});
