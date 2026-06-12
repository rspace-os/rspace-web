import { useLayoutEffect, useRef } from "react";

const useResizeObserver = ({
  callback,
  element,
}: {
  callback: () => void;
  element: { current: HTMLElement | null };
}) => {
  // biome-ignore lint/complexity/useOptionalChain: initial biome migration
  const current = element && element.current;

  const observer = useRef<ResizeObserver | null>(null);

  const observe = () => {
    // biome-ignore lint/complexity/useOptionalChain: initial biome migration
    if (element && element.current && observer.current) {
      observer.current.observe(element.current);
    }
  };

  useLayoutEffect(() => {
    // if we are already observing old element
    if (observer.current && current) {
      observer.current.unobserve(current);
    }

    observer.current = new ResizeObserver(callback);
    observe();

    return () => {
      if (observer.current && current) {
        observer.current.unobserve(current);
      }
    };
  }, [current]);
};

export default useResizeObserver;
