export function formatNumber(value: number, language: string | undefined): string {
  return new Intl.NumberFormat(language).format(value);
}

export function firstGrapheme(value: string, language: string | undefined): string {
  return (
    new Intl.Segmenter(language, { granularity: "grapheme" }).segment(value)[Symbol.iterator]().next().value?.segment ??
    ""
  );
}
