---
name: fin-merge
description: Paso 7' de fin-flow, solo para cambios SIN efecto en el producto (chore, docs, refactor, CI). Mergea en main un PR aprobado por fin-review, sin release ni deploy, y cierra la tarea FIN. Los cambios de producto se mergean dentro de fin-release.
argument-hint: <nº de PR>
---

# fin-merge — merge sin release

## 1. Puertas (todas, o no se mergea)
`gh pr view <nº> --json state,mergeable,baseRefName,headRefName,commits,files,comments` · `gh pr checks <nº>`
1. **No es un cambio de producto.** La rama es `chore/`, `docs/` o `refactor/` y el diff no cambia
   lo que el usuario ve ni lo que hace el worker en producción (nada en `src/` visible, `worker/src/`,
   `worker/migrations/`, `crates/` con efecto, `android/app/src/main/`). Si lo cambia: no es para
   esta skill → `fin-dev-test` + `fin-release`.
2. `baseRefName` es `main`.
3. El último comentario de `fin-review` dice **APROBADO** y es posterior al último push.
4. Checks en verde sobre el último commit, si los hay (FIN-4). Si fallan: `gh run view <id> --log-failed` → `fin-implement`.
5. `mergeable` = `MERGEABLE`. Si hay conflicto, rebasa la rama sobre `main`, re-prueba, y se repite la revisión.

## 2. Merge
    gh pr merge <nº> --merge --delete-branch --subject "Merge <rama> (#<nº>)" --body "Refs: FIN-n"
    git checkout main && git pull --ff-only origin main
    git branch -d <rama> 2>/dev/null || true

No hay tag ni `wrangler deploy`: solo se deploya un tag, y un chore no lo necesita.

## 3. Jira
Quita `en-revision`, transición `31` (Listo) y comentario `Mergeado en main: <sha> (PR #<nº>). Sin release: no cambia el producto.`
