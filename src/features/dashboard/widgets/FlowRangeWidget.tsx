import { useQuery } from "@tanstack/react-query";
import { StatWidget } from "../../../components/StatWidget";
import { getSpendingTrends } from "../../../lib/api";
import type { Period } from "../../../lib/types";
import { es } from "../../../i18n/es";
import { FlowTotalsChart } from "../FlowChart";

/** Income-vs-expense totals as two adjacent bars, for the global dashboard
 *  period (driven by the header selector, same range as the rest of the
 *  overview). */
export function FlowRangeWidget({ period }: { period: Period }) {
  const trends = useQuery({
    queryKey: ["spendingTrends", period],
    queryFn: () => getSpendingTrends(period),
  });

  return (
    <StatWidget title={es.dashboard.incomeVsExpense}>
      {trends.data && <FlowTotalsChart trends={trends.data} />}
    </StatWidget>
  );
}
