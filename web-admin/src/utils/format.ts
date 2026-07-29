export function formatDate(value?: string | null): string {
  return value ? value.slice(0, 10) : "-";
}

export function formatDateTime(value?: string | null): string {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}
