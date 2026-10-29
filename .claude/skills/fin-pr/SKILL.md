---
name: fin-pr
description: Paso 5 de fin-flow. Empuja la rama de una tarea FIN y abre (o actualiza) su PR contra main con la evidencia de pruebas, y enlaza PR y tarea de Jira.
argument-hint: FIN-n
---

# fin-pr — subir el PR

## 1. Antes de empujar
Rama de la tarea, árbol limpio, `fin-test` terminó con todo ✅ y existe su `informe.md`. Todos los
commits de `git log main..HEAD` llevan `Refs: FIN-n` y ninguno es «WIP».

## 2. Empujar
`git push -u origin HEAD`. Si el PR ya existía (nueva ronda), basta con el push, más un comentario
con el informe nuevo y lo que cambió en esta ronda.

## 3. Abrir
`gh pr create --base main --head <rama> --title "<tipo>(<ámbito>): <resumen en español>" --body-file <f>`,
siguiendo `.github/pull_request_template.md`:

    ## Qué cambia
    <para el usuario, 1-3 frases; web y Android>
    ## Por qué
    ## Cómo
    <decisiones técnicas; DECISIONS.md si aplica>
    ## Pruebas
    <informe.md>
    ## Jira
    [FIN-n](https://alansethmanjarrez.atlassian.net/browse/FIN-n)

    🤖 Generated with [Claude Code](https://claude.com/claude-code)

## 4. Checks y Jira
Todavía no hay CI en PRs (FIN-4): `gh pr checks <nº>` saldrá vacío y la puerta es el informe.
Cuando exista, espera **el run del último commit**:
`gh run list --commit $(git rev-parse HEAD) -L 1 --json databaseId -q '.[0].databaseId'` →
`gh run watch <id> --exit-status` con `run_in_background`, y sigue con la revisión mientras tanto.
Jira: etiqueta `en-revision` (con `editJiraIssue`, enviando la lista completa de etiquetas) y
comentario `PR: <url>`. La tarea sigue en *En curso*. Devuelve el número del PR.
