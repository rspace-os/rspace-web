import { useLayoutEffect, useRef } from "react";

const useResizeObserver = ({
    callback,
    element,
}: {
    callback: () => void;
    element: { current: HTMLElement | null };
}) => {
    const current = element?.current;

    const observer = useRef<ResizeObserver | null>(null);

    const observe = () => {
        if (element?.current && observer.current) {
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
    }, [current, callback, observe]);
};

export default useResizeObserver;
