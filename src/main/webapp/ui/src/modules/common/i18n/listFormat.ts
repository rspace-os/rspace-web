const DEFAULT_LIST_FORMAT_OPTIONS: Intl.ListFormatOptions = {
  style: "long",
  type: "conjunction",
};

export const makeListFormatter = (
  language: string | undefined,
  options: Intl.ListFormatOptions = DEFAULT_LIST_FORMAT_OPTIONS,
): Intl.ListFormat => {
  try {
    return new Intl.ListFormat(language, options);
  } catch {
    return new Intl.ListFormat("en-US", options);
  }
};

export const formatList = (
  items: Iterable<string | number>,
  language: string | undefined,
  options?: Intl.ListFormatOptions,
): string => makeListFormatter(language, options).format([...items].map(String));
