---
name: fin-status
description: Estado de Finanzas desde Jira FIN y GitHub — qué está en curso, qué está en revisión o probándose en dev sin publicar, PRs abiertos, decisiones pendientes del cliente y qué sigue. Usar al empezar una sesión o cuando el cliente pregunte "cómo vamos", "qué sigue", "qué falta".
---

# fin-status

- Jira (cloudId `f04e906b-0ec4-4966-b049-acd498427118`):
  `project = FIN AND statusCategory != Done ORDER BY status, priority DESC`, agrupado por estado.
- Etiqueta `en-revision` → «PR abierto, sin publicar todavía»: o falta la revisión, o la prueba en
  dev, o falló, o el cliente pidió esperar. Di cuál, con el último veredicto de `fin-review` y de
  `fin-dev-test` (sha y PASA/FALLA) que haya en los comentarios.
- `decision-cliente` → lo que espera al cliente, cada cosa como pregunta de producto.
- `gh pr list --state open`; último release (`git describe --tags --abbrev=0`) y si `main` está
  limpio respecto a él; último run de «Release apps».
- Comentarios del cliente en Jira desde la última sesión.
- **Qué sigue:** la tarea de mayor prioridad sin bloquear; ofrece arrancarla con `fin-flow`.
Respuesta corta, en español, con enlaces (`https://alansethmanjarrez.atlassian.net/browse/FIN-n`).
