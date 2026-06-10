import { Settings } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { EmptyState } from "../../components/EmptyState";
import { es } from "../../i18n/es";

export function SettingsPage() {
  return (
    <>
      <PageHeader title={es.settings.title} />
      <EmptyState icon={Settings} title={es.common.comingSoon} />
    </>
  );
}
