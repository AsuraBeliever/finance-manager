import { ArrowLeftRight } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { EmptyState } from "../../components/EmptyState";
import { es } from "../../i18n/es";

export function TransactionsPage() {
  return (
    <>
      <PageHeader title={es.transactions.title} />
      <EmptyState icon={ArrowLeftRight} title={es.common.comingSoon} />
    </>
  );
}
