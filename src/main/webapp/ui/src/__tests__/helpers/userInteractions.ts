import userEvent from "@testing-library/user-event";

type ValidityOption = {
  checkValidity?: HTMLInputElement["checkValidity"];
};

export async function replaceValue(
  element: HTMLElement,
  value: string | number,
  options: ValidityOption = {},
): Promise<void> {
  const restoreCheckValidity =
    element instanceof HTMLInputElement ||
    element instanceof HTMLTextAreaElement
      ? element.checkValidity.bind(element)
      : null;

  if (
    options.checkValidity &&
    (element instanceof HTMLInputElement ||
      element instanceof HTMLTextAreaElement)
  ) {
    element.checkValidity = options.checkValidity;
  }

  try {
    await userEvent.click(element);

    const text = String(value);
    if (text === "") {
      await userEvent.clear(element);
      return;
    }

    if (
      element instanceof HTMLInputElement ||
      element instanceof HTMLTextAreaElement
    ) {
      element.setSelectionRange?.(0, element.value.length);
    }

    if (element instanceof HTMLInputElement && element.type === "number") {
      await userEvent.clear(element);
      await userEvent.type(element, text);
      return;
    }

    await userEvent.paste(text);
  } finally {
    if (
      restoreCheckValidity &&
      (element instanceof HTMLInputElement ||
        element instanceof HTMLTextAreaElement)
    ) {
      element.checkValidity = restoreCheckValidity;
    }
  }
}
