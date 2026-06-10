import { TrendingUp } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { EmptyState } from "../../components/EmptyState";
import { es } from "../../i18n/es";

export function InvestmentDetailPage() {
  return (
    <>
      <PageHeader title={es.investments.detailTitle} />
      <EmptyState icon={TrendingUp} title={es.common.comingSoon} />
    </>
  );
}
