import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";

test("As a user, I can switch Sketch tools with the keyboard and type in a text box", async ({
  page,
  pageWorkspace,
}) => {
  await pageWorkspace.open();
  const editor = await pageWorkspace.createBasicDocument();
  const field = await editor.getField("", 0);
  await field.openMenu("Insert");
  await field.menuItem("Sketch").click();

  const sketch = page.getByRole("dialog", { name: "Sketch" }).frameLocator("iframe");
  // Zwibbler highlights the active tool's image without exposing a selected state.
  const activeToolBackground = "rgb(159, 179, 231)";
  for (const startingTool of ["Pick tool", "Circle tool", "Rectangle tool"]) {
    for (const [shortcut, nextTool] of [
      ["c", "Curve tool"],
      ["l", "Line tool"],
    ]) {
      await sketch.getByTitle(startingTool, { exact: true }).click();
      await page.keyboard.press(shortcut);
      await expect(sketch.getByTitle(nextTool, { exact: true }), `${shortcut} after ${startingTool}`).toHaveCSS(
        "background-color",
        activeToolBackground,
      );
    }
  }

  await sketch.getByTitle("Text tool", { exact: true }).click();
  // The drawing canvas is the only canvas Zwibbler makes keyboard focusable.
  await sketch.locator("canvas[tabindex='0']").click({ position: { x: 150, y: 150 } });
  const textBox = sketch.getByRole("textbox").filter({ visible: true });
  await expect(textBox).toBeFocused();
  await page.keyboard.type("cl");
  await expect(textBox).toHaveValue("cl");
});
