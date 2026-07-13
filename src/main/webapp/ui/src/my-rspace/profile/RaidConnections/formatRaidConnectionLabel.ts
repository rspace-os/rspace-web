export function formatRaidConnectionLabel({
  raidIdentifier,
  raidTitle,
}: {
  raidIdentifier: string;
  raidTitle: string;
}): string {
  const title = raidTitle.trim();

  if (!raidIdentifier) return title;

  return title ? `${title} (${raidIdentifier})` : `(${raidIdentifier})`;
}
