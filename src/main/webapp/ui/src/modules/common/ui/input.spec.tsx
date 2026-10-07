import { cleanup, render } from "@testing-library/react";
import { useState } from "react";
import { afterEach, expect, test } from "vitest";
import { page, server, userEvent } from "vitest/browser";
import { Input } from "./input";

afterEach(cleanup);

const TRANSPARENT = "rgba(0, 0, 0, 0)";
// Only WebKit draws a misleading current time in an empty field.
const hiddenWhenEmpty = server.browser === "webkit";

function TimeInput() {
  const [value, setValue] = useState("");
  return (
    <>
      <Input aria-label="Start time" type="time" value={value} onChange={(event) => setValue(event.target.value)} />
      <button type="button">{"Next"}</button>
    </>
  );
}

const renderTimeInput = () => {
  render(<TimeInput />);
  const input = page.getByLabelText("Start time");
  // Input transitions its colour, so read it once any fade has finished.
  const color = async () => {
    await Promise.all(
      input
        .element()
        .getAnimations()
        .map((animation) => animation.finished),
    );
    return getComputedStyle(input.element()).color;
  };
  return { input, color };
};

test("hides empty native time text in WebKit only, without hiding entered times", async () => {
  const { input, color } = renderTimeInput();

  await expect.element(input).toHaveValue("");
  if (hiddenWhenEmpty) await expect.poll(color).toBe(TRANSPARENT);
  else await expect.poll(color).not.toBe(TRANSPARENT);

  await input.click();
  await expect.poll(color).not.toBe(TRANSPARENT);
  await input.fill("09:30");
  await page.getByRole("button", { name: "Next" }).click();
  await expect.element(input).toHaveValue("09:30");
  await expect.poll(color).not.toBe(TRANSPARENT);

  await userEvent.clear(input);
  await page.getByRole("button", { name: "Next" }).click();
  await expect.element(input).toHaveValue("");
  if (hiddenWhenEmpty) await expect.poll(color).toBe(TRANSPARENT);
});

test("keeps a half-typed time visible after blur", async () => {
  const { input, color } = renderTimeInput();

  await input.click();
  await userEvent.keyboard("09");
  // Tab would only move to the minute segment.
  await page.getByRole("button", { name: "Next" }).click();
  await expect.element(input).not.toHaveFocus();

  await expect.element(input).toHaveValue("");
  await expect.poll(() => (input.element() as HTMLInputElement).validity.badInput).toBe(true);
  await expect.poll(color).not.toBe(TRANSPARENT);
});
