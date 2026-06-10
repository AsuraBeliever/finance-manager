// All user-facing UI strings live here (es-MX).
export const es = {
  app: {
    name: "Finanzas",
  },
  nav: {
    dashboard: "Resumen",
    wallets: "Carteras",
    transactions: "Transacciones",
    investments: "Inversiones",
    settings: "Ajustes",
  },
  common: {
    comingSoon: "Próximamente",
    loading: "Cargando…",
    error: "Ocurrió un error",
    save: "Guardar",
    cancel: "Cancelar",
    delete: "Eliminar",
    edit: "Editar",
    close: "Cerrar",
    confirm: "Confirmar",
    empty: "No hay datos todavía",
  },
  dashboard: {
    title: "Resumen",
    netWorth: "Patrimonio total",
  },
  wallets: {
    title: "Carteras",
    detailTitle: "Detalle de cartera",
    newWallet: "Nueva cartera",
    editWallet: "Editar cartera",
    name: "Nombre",
    namePlaceholder: "Ej. Efectivo, Nu débito…",
    category: "Categoría",
    currency: "Moneda",
    initialBalance: "Saldo inicial",
    color: "Color",
    notes: "Notas",
    balance: "Saldo",
    archive: "Archivar",
    unarchive: "Desarchivar",
    archived: "Archivada",
    showArchived: "Mostrar archivadas",
    emptyTitle: "Aún no tienes carteras",
    emptyDescription:
      "Crea tu primera cartera: efectivo, tarjeta, cuenta de ahorro o lo que necesites.",
    invalidAmount: "Monto inválido",
  },
  transactions: {
    title: "Transacciones",
  },
  investments: {
    title: "Inversiones",
    detailTitle: "Detalle de inversión",
  },
  settings: {
    title: "Ajustes",
    currencies: "Monedas",
    walletCategories: "Categorías de cartera",
    exchangeRates: "Tipos de cambio",
  },
} as const;
