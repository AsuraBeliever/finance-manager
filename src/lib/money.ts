// Money is always integer cents end-to-end; formatting is the only place
// where it becomes a decimal, and only for display.
const formatters = new Map<string, Intl.NumberFormat>();

export function formatCents(cents: number, currencyCode: string): string {
  let fmt = formatters.get(currencyCode);
  if (!fmt) {
    fmt = new Intl.NumberFormat("es-MX", {
      style: "currency",
      currency: currencyCode,
    });
    formatters.set(currencyCode, fmt);
  }
  return fmt.format(cents / 100);
}

/** Parse user input like "1,234.56" into cents. Returns null if invalid. */
export function parseToCents(input: string): number | null {
  const cleaned = input.replace(/[,\s$]/g, "");
  if (cleaned === "" || !/^-?\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  return Math.round(parseFloat(cleaned) * 100);
}

/** Format basis points as a percentage string, e.g. 1250 -> "12.50 %". */
export function formatBps(bps: number): string {
  return `${(bps / 100).toFixed(2)} %`;
}
