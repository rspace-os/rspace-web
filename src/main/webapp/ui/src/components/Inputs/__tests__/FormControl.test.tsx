import FormLabel from "@mui/material/FormLabel";
import { ThemeProvider } from "@mui/material/styles";
import { render } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import FormControl from "../FormControl";

vi.mock("@mui/material/FormLabel", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@mui/material/FormLabel")>();
  return {
    ...actual,
    default: vi.fn(() => <div></div>),
  };
});
describe("FormControl", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });
  describe("Label correctly", () => {
    test("FormLabel is rendered when label is passed.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FormControl label="foo">
            <div></div>
          </FormControl>
        </ThemeProvider>,
      );
      expect(FormLabel).toHaveBeenCalledWith(
        expect.objectContaining({
          children: expect.objectContaining({
            props: expect.objectContaining({
              label: "foo",
            }),
          }),
        }),
        expect.anything(),
      );
    });
    test("FormLabel is not rendered when label is not passed.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FormControl>
            <div></div>
          </FormControl>
        </ThemeProvider>,
      );
      expect(FormLabel).not.toHaveBeenCalled();
    });
  });
});
