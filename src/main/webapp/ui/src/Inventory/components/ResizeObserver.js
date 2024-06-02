//@flow

import { useRef, useLayoutEffect } from "react";
import ResizeObserver from "resize-observer-polyfill";

const useResizeObserver = ({
  callback,
  element,
}: {
  callback: () => void,
  element: {| current: ?HTMLElement |},
}) => {
  const current = element && element.current;

  const observer = useRef(null);

  const observe = () => {
    if (element && element.current && observer.current) {
      observer.current.observe(element.current);
    }
  };

  useLayoutEffect(() => {
    // if we are already observing old element
    if (observer.current && current) {
      observer.current.unobserve(current);
    }
    const resizeObserverOrPolyfill = ResizeObserver;
    observer.current = new resizeObserverOrPolyfill(callback);
    observe();

    return () => {
      if (observer.current && element && element.current) {
        observer.current.unobserve(element.current);
      }
    };
  }, [current]);
};

export default useResizeObserver;
