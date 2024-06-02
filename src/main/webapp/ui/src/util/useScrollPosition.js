import { useRef, useLayoutEffect } from "react";

const isBrowser = typeof window !== `undefined`;

function getScrollPosition({ element, useWindow }) {
  if (!isBrowser) return { x: 0, y: 0 };

  const target = element ? element.current : document.body;
  const position = target.getBoundingClientRect();

  return useWindow
    ? { x: window.scrollX, y: window.scrollY }
    : { x: position.left, y: position.top };
}

export function useScrollPosition(effect, deps, element, useWindow, wait) {
  const position = useRef(getScrollPosition({ useWindow }));
  let throttleTimeout = null;

  const callBack = () => {
    const currentPosition = getScrollPosition({ element, useWindow });
    effect({ previousPosition: position.current, currentPosition });
    position.current = currentPosition;
    throttleTimeout = null;
  };

  const handleScroll = () => {
    if (wait) {
      if (throttleTimeout === null) {
        throttleTimeout = setTimeout(callBack, wait);
      }
    } else {
      callBack();
    }
  };

  useLayoutEffect(() => {
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, deps);

  // dynamic body height should also be perceived as a scroll
  const resizeObserver = new ResizeObserver(() => handleScroll());
  useLayoutEffect(() => {
    resizeObserver.observe(document.body);
    return () => resizeObserver.unobserve(document.body);
  }, deps);
}
