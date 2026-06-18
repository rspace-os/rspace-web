import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import materialTheme from "../../../../../theme";

import Organization from "../Organization";

describe("Organization", () => {
  test("The text 'well plate' should be shown to give the user a hint to use a grid container.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <Organization container={makeMockContainer({ id: null })} />
      </ThemeProvider>,
    );
    expect(screen.getByRole("group", { name: "Type" })).toHaveTextContent("well plate");
  });
});
