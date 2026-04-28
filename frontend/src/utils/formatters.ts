const cnyFormatter = new Intl.NumberFormat("en-US", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatCny(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return "-";
  }
  return `CNY ${cnyFormatter.format(value)}`;
}

export function formatLocalDateTime(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}
