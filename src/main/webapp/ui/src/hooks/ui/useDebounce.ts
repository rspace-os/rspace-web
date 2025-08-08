import React from "react";

/**
 * Custom hook that provides debounced functionality for any value change
 * @param callback The function to call after the debounce period
 * @param delay The debounce delay in milliseconds
 * @returns A function that can be called with new values to debounce
 * @example
 * const [searchTerm, setSearchTerm] = useState("");
 * const debouncedSearch = useDebounce((term) => {
 *   // Perform network call with term
 * }, 500);
 *
 * const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
 *   setSearchTerm(event.target.value);
 *   debouncedSearch(event.target.value);
 * };
 */
export default function useDebounce<T>(
  callback: (value: T) => void,
  delay: number,
) {
  const timeoutRef = React.useRef<NodeJS.Timeout | null>(null);

  React.useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [callback, delay]);

  return React.useCallback(
    (value: T) => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
      timeoutRef.current = setTimeout(() => {
        callback(value);
      }, delay);
    },
    [callback, delay],
  );
}
