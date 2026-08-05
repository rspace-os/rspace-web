import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expectAccessible } from "@/__tests__/accessibility";
import { ProgressExample, SimpleExample } from "../ValidatingSubmitButton.story";

/*
 * The render, click, disabled, validation-popover and progress cases all
 * behave deterministically in jsdom, so they run as fast unit tests here.
 * The single WCAG AAA contrast case that needs a real browser (forcedColors /
 * color-contrast-enhanced) is in the .spec.tsx.
 */
describe("ValidatingSubmitButton", () => {
  test("The button shows its descendants", () => {
    render(<SimpleExample onClick={() => {}} />);
    const button = screen.getByRole("button", { name: "Submit" });
    expect(button).toBeVisible();
    expect(button).toHaveTextContent("Submit");
  });

  test("The button should have type 'submit'", () => {
    render(<ProgressExample onClick={() => {}} />);
    expect(screen.getByRole("button", { name: "Submit" })).toHaveAttribute("type", "submit");
  });

  test("When the button is loading, it should be disabled", async () => {
    const user = userEvent.setup();
    render(<SimpleExample onClick={() => {}} />);
    await user.click(screen.getByRole("button", { name: /Toggle Loading/ }));
    expect(screen.getByRole("button", { name: "Submit" })).toBeDisabled();
  });

  test("When the button is not loading, it should be enabled", () => {
    render(<SimpleExample onClick={() => {}} />);
    expect(screen.getByRole("button", { name: "Submit" })).toBeEnabled();
  });

  test("When validation fails, the validation error popover should be visible", async () => {
    const user = userEvent.setup();
    render(<SimpleExample onClick={() => {}} />);
    await user.click(screen.getByRole("button", { name: "Set Invalid" }));
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(await screen.findByRole("dialog")).toBeVisible();
  });

  test("When validation passes, the validation error popover should not be visible", async () => {
    const user = userEvent.setup();
    render(<SimpleExample onClick={() => {}} />);
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  test("When validation passes, the onClick handler should be called", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<SimpleExample onClick={onClick} />);
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(onClick).toHaveBeenCalled();
  });

  test("When validation fails, the onClick handler should not be called", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<SimpleExample onClick={onClick} />);
    await user.click(screen.getByRole("button", { name: "Set Invalid" }));
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(await screen.findByRole("dialog")).toBeVisible();
    expect(onClick).not.toHaveBeenCalled();
  });

  describe("Progress prop", () => {
    test("When progress is undefined, the progress indicator should not be visible", () => {
      render(<SimpleExample onClick={() => {}} />);
      expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    });

    test("When progress is set, the progress indicator should be visible", async () => {
      const user = userEvent.setup();
      render(<ProgressExample onClick={() => {}} />);
      await user.click(screen.getByRole("button", { name: "Submit" }));
      expect(await screen.findByRole("progressbar")).toBeVisible();
    });

    test("When progress reaches 100, the progress indicator should disappear", async () => {
      const user = userEvent.setup();
      render(<ProgressExample onClick={() => {}} />);
      await user.click(screen.getByRole("button", { name: "Submit" }));
      // The story increments progress to 100 over real-time intervals, after
      // which the component unmounts the progressbar.
      await waitFor(
        () => {
          expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
        },
        { timeout: 10000 },
      );
    }, 15000);
  });

  describe("Accessibility", () => {
    test("Should have no axe violations", async () => {
      const { baseElement } = render(<ProgressExample onClick={() => {}} />);
      await expectAccessible(baseElement);
    });

    /*
     * Note that the accessible role of the popup is asserted by the
     * "When validation fails, the validation error popover should be visible"
     * case.
     */
    test("When validation fails, the validation error popover should contain a warning alert", async () => {
      const user = userEvent.setup();
      render(<SimpleExample onClick={() => {}} />);
      await user.click(screen.getByRole("button", { name: "Set Invalid" }));
      await user.click(screen.getByRole("button", { name: "Submit" }));
      expect(await screen.findByRole("dialog")).toBeVisible();
      expect(await screen.findByRole("alert", { name: "common:alerts.warning" })).toBeVisible();
    });
  });
});
