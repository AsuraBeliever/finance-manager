import { LayoutDashboard } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { EmptyState } from "../../components/EmptyState";
import { es } from "../../i18n/es";

export function DashboardPage() {
  return (
    <>
      <PageHeader title={es.dashboard.title} />
      <EmptyState icon={LayoutDashboard} title={es.common.comingSoon} />
    </>
  );
}
