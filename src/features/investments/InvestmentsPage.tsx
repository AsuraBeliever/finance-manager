import { TrendingUp } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { EmptyState } from "../../components/EmptyState";
import { es } from "../../i18n/es";

export function InvestmentsPage() {
  return (
    <>
      <PageHeader title={es.investments.title} />
      <EmptyState icon={TrendingUp} title={es.common.comingSoon} />
    </>
  );
}
