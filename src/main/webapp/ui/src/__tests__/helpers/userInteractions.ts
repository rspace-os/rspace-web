import userEvent from "@testing-library/user-event";

export async function replaceValue(
  element: HTMLElement,
  value: string | number,
): Promise<void> {
  await userEvent.click(element);
  await userEvent.clear(element);

  const text = String(value);
  if (text === "") return;

  if (element instanceof HTMLInputElement && element.type === "number") {
    await userEvent.type(element, text);
    return;
  }

  await userEvent.paste(text);
}
