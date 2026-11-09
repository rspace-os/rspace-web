export const crossResourceInvalidation: ReadonlyArray<{
  onMutations: readonly string[];
  invalidates: ReadonlyArray<{ query: string; params?: readonly string[] }>;
}> = [];
