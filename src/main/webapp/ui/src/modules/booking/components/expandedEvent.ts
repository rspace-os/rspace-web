export function expandedEventIdAfterChange(currentId: string | null, eventId: string, expanded: boolean) {
  return expanded ? eventId : currentId === eventId ? null : currentId;
}
