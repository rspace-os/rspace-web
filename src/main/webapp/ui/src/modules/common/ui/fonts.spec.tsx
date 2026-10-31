import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";

afterEach(cleanup);

test("loads CJK slices for content and typed input in an English UI", async () => {
  document.documentElement.lang = "en";
  render(
    <>
      <p>{"Book the microscope"}</p>
      <input aria-label="Instrument name" />
    </>,
  );
  await document.fonts.ready;
  const loadedCjk = () =>
    [...document.fonts].filter(
      (face) => /Noto Sans (SC|TC|HK|JP|KR) Variable/.test(face.family) && face.status === "loaded",
    );
  expect(loadedCjk()).toHaveLength(0);

  await userEvent.fill(page.getByRole("textbox", { name: "Instrument name" }), "共聚焦顯微鏡");
  await expect
    .poll(async () => {
      await document.fonts.ready;
      return loadedCjk().length;
    })
    .toBeGreaterThan(0);
  const inputSlices = loadedCjk().length;
  render(<p>{"實驗預約儀器"}</p>);
  await expect
    .poll(async () => {
      await document.fonts.ready;
      return loadedCjk().length;
    })
    .toBeGreaterThan(inputSlices);
  expect(loadedCjk().length).toBeLessThan(20);
});
