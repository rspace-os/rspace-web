import { useRef, useState, useLayoutEffect } from "react";
import { Optional } from "./optional";

/**
 * This custom hook is for detecting when the text within an input field is
 * longer than the field is wide. This works equally well with MUI TextFields,
 * InputBase, and other similar components.
 *
 * To use call like so
 *     const { inputRef, textTooWide } = useIsTextWiderThanField();
 *
 * Then, attach the `inputRef` to the react component such that it references
 * the HTML Input element. Finally, `textTooWide` will be Optional.empty when
 * `inputRef.current` is null, otherwise it will contain a boolean value that
 * can be used wherever needed.
 */
export default function useIsTextWiderThanField(): {
  inputRef: React.MutableRefObject<HTMLInputElement | undefined>;
  textTooWide: Optional<boolean>;
} {
  const [width, setWidth] = useState(0);
  const ref = useRef<HTMLInputElement>();

  const resizeObserver = useRef(
    new ResizeObserver((entries) => {
      setWidth(entries[0].contentRect.width);
    })
  );

  useLayoutEffect(() => {
    const inputElement = ref.current;
    if (!inputElement) return;

    const x = resizeObserver.current;
    x.observe(inputElement);
    return () => {
      x.disconnect();
    };
  }, [ref]);

  function getCssStyle(element: HTMLElement, prop: string) {
    return window.getComputedStyle(element, null).getPropertyValue(prop);
  }

  function getCanvasFont(el: HTMLElement) {
    const fontWeight = getCssStyle(el, "font-weight") || "normal";
    const fontSize = getCssStyle(el, "font-size") || "16px";
    const fontFamily = getCssStyle(el, "font-family") || "sans-serif";
    return `${fontWeight} ${fontSize} ${fontFamily}`;
  }

  function getTextWidth(inputElement: HTMLInputElement) {
    const text = inputElement.value;
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Canvas context is not available");
    context.font = Optional.fromNullable(inputElement)
      .map(getCanvasFont)
      .orElse("");
    const metrics = context.measureText(text);
    return metrics.width;
  }

  return {
    textTooWide: Optional.fromNullable(ref.current).map(
      (inputElement) => getTextWidth(inputElement) > width
    ),
    inputRef: ref,
  };
}
