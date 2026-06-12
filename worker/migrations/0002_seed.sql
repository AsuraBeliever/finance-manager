-- Migration number: 0002 	 2026-06-11
-- Seed data, mirroring desktop migration 2 (minus 'Inversión', removed by
-- desktop migration 5). System transaction categories use user_id NULL so
-- every user sees them.

INSERT INTO currencies (code, name, symbol, decimals) VALUES
  ('MXN', 'Peso mexicano', '$', 2),
  ('USD', 'Dólar estadounidense', '$', 2);

INSERT INTO wallet_categories (name, icon, is_system) VALUES
  ('Efectivo', 'banknote', 1),
  ('Tarjeta de débito', 'credit-card', 1),
  ('Tarjeta de crédito', 'credit-card', 1),
  ('Cuenta de ahorro', 'piggy-bank', 1),
  ('Otro', 'wallet', 1);

INSERT INTO transaction_categories (user_id, name, kind, icon, is_system) VALUES
  (NULL, 'Salario', 'income', 'briefcase', 1),
  (NULL, 'Regalo', 'income', 'gift', 1),
  (NULL, 'Intereses', 'income', 'percent', 1),
  (NULL, 'Otro ingreso', 'income', 'plus', 1),
  (NULL, 'Comida', 'expense', 'utensils', 1),
  (NULL, 'Transporte', 'expense', 'bus', 1),
  (NULL, 'Hogar', 'expense', 'home', 1),
  (NULL, 'Entretenimiento', 'expense', 'gamepad-2', 1),
  (NULL, 'Salud', 'expense', 'heart-pulse', 1),
  (NULL, 'Suscripciones', 'expense', 'repeat', 1),
  (NULL, 'Otro gasto', 'expense', 'minus', 1);
