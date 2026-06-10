import { Wallet } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { EmptyState } from "../../components/EmptyState";
import { es } from "../../i18n/es";

export function WalletsPage() {
  return (
    <>
      <PageHeader title={es.wallets.title} />
      <EmptyState icon={Wallet} title={es.common.comingSoon} />
    </>
  );
}
