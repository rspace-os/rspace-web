/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import DeleteButton from "../DeleteButton";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("DeleteButton", () => {
  function renderDeleteButton(
    props: ?{| disabled?: boolean, onClick?: () => void |}
  ) {
    return render(
      <ThemeProvider theme={materialTheme}>
        <DeleteButton
          onClick={() => {}}
          disabled={false}
          tooltipAfterClicked="foo"
          tooltipBeforeClicked="bar"
          tooltipWhenDisabled="baz"
          {...props}
        />
      </ThemeProvider>
    );
  }

  test("Shows disabled tooltip.", () => {
    renderDeleteButton({ disabled: true });
    expect(screen.getByLabelText("baz"));
  });

  test("Shows before clicked tooltip.", () => {
    renderDeleteButton();
    expect(screen.getByLabelText("bar"));
  });

  test("Shows after clicked tooltip.", () => {
    const onClick = jest.fn(() => {});
    renderDeleteButton({ onClick });
    act(() => {
      screen.getByRole("button").click();
    });
    expect(onClick).toHaveBeenCalled();
    expect(screen.getByLabelText("foo"));
  });

  test("Becomes disabled once clicked.", () => {
    const onClick = jest.fn(() => {});
    renderDeleteButton({ onClick });

    act(() => {
      screen.getByRole("button").click();
    });
    act(() => {
      screen.getByRole("button").click();
    });
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
