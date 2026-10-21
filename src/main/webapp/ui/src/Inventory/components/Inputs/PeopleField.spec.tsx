import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import materialTheme from "@/theme";
import PeopleField from "./PeopleField";

afterEach(cleanup);

test("names the people selector and its loading indicator", async () => {
  render(
    <ThemeProvider theme={materialTheme}>
      <PeopleField label="Owner" recipient={null} outsideGroup={false} onSelection={() => {}} />
    </ThemeProvider>,
  );
  await expect.element(page.getByRole("combobox", { name: "Owner", exact: true })).toBeVisible();
  await expect.element(page.getByRole("progressbar", { name: "Loading people", exact: true })).toBeVisible();
});
