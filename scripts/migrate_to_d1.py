#!/usr/bin/env python3
"""One-time migration: local desktop finanzas.db -> Cloudflare D1.

Reads the local SQLite database READ-ONLY (it is never modified; it stays as
the offline backup) and emits `migration-data.sql` with INSERTs assigning all
personal data to --user-id (the first registered account). Run it after
applying the D1 schema migrations and registering through the UI:

    python3 scripts/migrate_to_d1.py            # writes migration-data.sql + checksums
    npx wrangler d1 execute finanzas --remote --file=migration-data.sql
    python3 scripts/migrate_to_d1.py --verify   # re-print checksums to diff by hand

Category mapping: system categories are matched BY NAME to the D1 seed ids
(the local DB dropped 'Inversión', so wallet_categories ids diverge); user
created categories keep their original ids (locals start after the seed
range, so they can't collide).
"""

import argparse
import sqlite3
import sys
from pathlib import Path

LOCAL_DB = Path.home() / ".local/share/com.asura.finanzas/finanzas.db"
OUT_FILE = Path(__file__).resolve().parent.parent / "migration-data.sql"

# D1 seed ids (worker/migrations/0002_seed.sql), keyed by name.
D1_WALLET_CATEGORIES = {
    "Efectivo": 1,
    "Tarjeta de débito": 2,
    "Tarjeta de crédito": 3,
    "Cuenta de ahorro": 4,
    "Otro": 5,
}
D1_TX_CATEGORIES = {
    "Salario": 1,
    "Regalo": 2,
    "Intereses": 3,
    "Otro ingreso": 4,
    "Comida": 5,
    "Transporte": 6,
    "Hogar": 7,
    "Entretenimiento": 8,
    "Salud": 9,
    "Suscripciones": 10,
    "Otro gasto": 11,
}
SEEDED_CURRENCIES = {"MXN", "USD"}
# Settings keys that are global market cache -> system user (id 0).
GLOBAL_SETTINGS = {"bonddia_price"}


def q(value):
    """SQL literal."""
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def rows(conn, sql, args=()):
    cur = conn.execute(sql, args)
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, r)) for r in cur.fetchall()]


def chunked_inserts(out, table, columns, value_rows, chunk=50):
    for i in range(0, len(value_rows), chunk):
        block = value_rows[i : i + chunk]
        values = ",\n  ".join("(" + ", ".join(q(v) for v in r) + ")" for r in block)
        out.append(f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n  {values};")


def build_category_maps(conn):
    """local id -> D1 id for both category tables; collect user-created rows."""
    wc_map, extra_wc = {}, []
    for r in rows(conn, "SELECT * FROM wallet_categories ORDER BY id"):
        if r["is_system"] and r["name"] in D1_WALLET_CATEGORIES:
            wc_map[r["id"]] = D1_WALLET_CATEGORIES[r["name"]]
        else:
            wc_map[r["id"]] = r["id"]  # ids ≥ 7 locally; seed ends at 5
            extra_wc.append(r)

    tc_map, extra_tc = {}, []
    for r in rows(conn, "SELECT * FROM transaction_categories ORDER BY id"):
        if r["is_system"] and r["name"] in D1_TX_CATEGORIES:
            tc_map[r["id"]] = D1_TX_CATEGORIES[r["name"]]
        else:
            tc_map[r["id"]] = r["id"]  # ids ≥ 12 locally; seed ends at 11
            extra_tc.append(r)
    return wc_map, extra_wc, tc_map, extra_tc


def checksums(conn):
    print("== Checksums (compara contra D1 tras importar) ==")
    for table in (
        "wallets",
        "transactions",
        "transaction_categories",
        "investments",
        "investment_snapshots",
        "investment_movements",
        "exchange_rates",
        "rate_history",
        "crypto_prices",
        "settings",
        "currencies",
    ):
        n = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        print(f"  {table:24} {n} filas")
    print("  -- saldo calculado por cartera (centavos) --")
    for r in rows(
        conn,
        """SELECT w.id, w.name, w.currency_code,
                  w.initial_balance_cents + COALESCE((
                    SELECT SUM(CASE t.kind
                                 WHEN 'income' THEN t.amount_cents
                                 WHEN 'transfer_in' THEN t.amount_cents
                                 ELSE -t.amount_cents END)
                    FROM transactions t WHERE t.wallet_id = w.id), 0) AS balance
           FROM wallets w ORDER BY w.id""",
    ):
        print(f"  cartera {r['id']:3} {r['name']:24} {r['currency_code']} {r['balance']}")
    print("  -- suma de transacciones por moneda y tipo --")
    for r in rows(
        conn,
        """SELECT w.currency_code, t.kind, SUM(t.amount_cents) AS total, COUNT(*) AS n
           FROM transactions t JOIN wallets w ON w.id = t.wallet_id
           GROUP BY w.currency_code, t.kind ORDER BY 1, 2""",
    ):
        print(f"  {r['currency_code']} {r['kind']:13} n={r['n']:4} suma={r['total']}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user-id", type=int, default=1, help="D1 user id (default 1)")
    ap.add_argument("--db", type=Path, default=LOCAL_DB)
    ap.add_argument("--verify", action="store_true", help="solo imprime checksums")
    args = ap.parse_args()

    if not args.db.exists():
        sys.exit(f"no existe la base local: {args.db}")
    # READ-ONLY: this script must never touch the local backup.
    conn = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)

    checksums(conn)
    if args.verify:
        return

    uid = args.user_id
    wc_map, extra_wc, tc_map, extra_tc = build_category_maps(conn)
    out = [
        "-- Generated by scripts/migrate_to_d1.py — DO NOT COMMIT (gitignored).",
        f"-- Source: {args.db}",
        f"-- All personal data assigned to user_id = {uid}.",
        "PRAGMA defer_foreign_keys = on;",
    ]

    for r in rows(conn, "SELECT * FROM currencies"):
        if r["code"] not in SEEDED_CURRENCIES:
            chunked_inserts(
                out,
                "currencies",
                ["code", "name", "symbol", "decimals"],
                [[r["code"], r["name"], r["symbol"], r["decimals"]]],
            )

    if extra_wc:
        chunked_inserts(
            out,
            "wallet_categories",
            ["id", "name", "icon", "is_system"],
            [[r["id"], r["name"], r["icon"], 0] for r in extra_wc],
        )
    if extra_tc:
        chunked_inserts(
            out,
            "transaction_categories",
            ["id", "user_id", "name", "kind", "icon", "color", "is_system"],
            [
                [r["id"], uid, r["name"], r["kind"], r["icon"], r["color"], 0]
                for r in extra_tc
            ],
        )

    chunked_inserts(
        out,
        "wallets",
        [
            "id", "user_id", "name", "category_id", "currency_code",
            "initial_balance_cents", "color", "notes", "is_archived", "created_at",
        ],
        [
            [
                r["id"], uid, r["name"], wc_map[r["category_id"]], r["currency_code"],
                r["initial_balance_cents"], r["color"], r["notes"], r["is_archived"],
                r["created_at"],
            ]
            for r in rows(conn, "SELECT * FROM wallets ORDER BY id")
        ],
    )

    chunked_inserts(
        out,
        "transactions",
        [
            "id", "wallet_id", "kind", "amount_cents", "category_id",
            "transfer_group_id", "description", "occurred_at", "created_at",
        ],
        [
            [
                r["id"], r["wallet_id"], r["kind"], r["amount_cents"],
                tc_map.get(r["category_id"]), r["transfer_group_id"],
                r["description"], r["occurred_at"], r["created_at"],
            ]
            for r in rows(conn, "SELECT * FROM transactions ORDER BY id")
        ],
    )

    chunked_inserts(
        out,
        "investments",
        [
            "id", "user_id", "name", "calculator", "currency_code", "principal_cents",
            "start_date", "params_json", "linked_wallet_id", "is_closed", "notes",
            "created_at",
        ],
        [
            [
                r["id"], uid, r["name"], r["calculator"], r["currency_code"],
                r["principal_cents"], r["start_date"], r["params_json"],
                r["linked_wallet_id"], r["is_closed"], r["notes"], r["created_at"],
            ]
            for r in rows(conn, "SELECT * FROM investments ORDER BY id")
        ],
    )

    chunked_inserts(
        out,
        "investment_snapshots",
        ["id", "investment_id", "value_cents", "as_of", "source"],
        [
            [r["id"], r["investment_id"], r["value_cents"], r["as_of"], r["source"]]
            for r in rows(conn, "SELECT * FROM investment_snapshots ORDER BY id")
        ],
    )

    chunked_inserts(
        out,
        "investment_movements",
        ["id", "investment_id", "kind", "amount_cents", "occurred_at", "created_at"],
        [
            [
                r["id"], r["investment_id"], r["kind"], r["amount_cents"],
                r["occurred_at"], r["created_at"],
            ]
            for r in rows(conn, "SELECT * FROM investment_movements ORDER BY id")
        ],
    )

    chunked_inserts(
        out,
        "exchange_rates",
        ["id", "currency_code", "rate_to_mxn_micros", "as_of", "source"],
        [
            [r["id"], r["currency_code"], r["rate_to_mxn_micros"], r["as_of"], r["source"]]
            for r in rows(conn, "SELECT * FROM exchange_rates ORDER BY id")
        ],
    )

    chunked_inserts(
        out,
        "rate_history",
        ["series", "date", "rate_bps"],
        [
            [r["series"], r["date"], r["rate_bps"]]
            for r in rows(conn, "SELECT * FROM rate_history ORDER BY series, date")
        ],
    )

    chunked_inserts(
        out,
        "crypto_prices",
        ["symbol", "price_mxn_cents", "price_usd_cents", "as_of"],
        [
            [r["symbol"], r["price_mxn_cents"], r["price_usd_cents"], r["as_of"]]
            for r in rows(conn, "SELECT * FROM crypto_prices ORDER BY symbol")
        ],
    )

    chunked_inserts(
        out,
        "settings",
        ["user_id", "key", "value"],
        [
            [0 if r["key"] in GLOBAL_SETTINGS else uid, r["key"], r["value"]]
            for r in rows(conn, "SELECT * FROM settings ORDER BY key")
        ],
    )

    OUT_FILE.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"\nEscrito {OUT_FILE} ({len(out)} sentencias).")
    print("Siguiente paso: npx wrangler d1 execute finanzas --remote --file=migration-data.sql")


if __name__ == "__main__":
    main()
