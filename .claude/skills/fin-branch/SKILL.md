---
name: fin-branch
description: Paso 2 de fin-flow. Crea la rama de trabajo de una tarea de Jira FIN desde main actualizado y pasa la tarea a En curso.
argument-hint: FIN-n
---

# fin-branch — rama de trabajo

Tarea: $ARGUMENTS

## 1. Precondiciones
    git status --porcelain          # vacío
    git fetch origin
    git rev-list --left-right --count origin/main...main
- **Árbol sucio:** para y averigua de quién es. Nunca `stash`/`reset`/`checkout --` sobre trabajo ajeno.
- **`main` por detrás:** `git checkout main && git pull --ff-only`.
- **`main` por delante:** hay commits sin publicar en `main`, lo que rompe «main = último release».
  Para y avisa: o falta un push de un release, o alguien commiteó directo.

## 2. Nombre
`<tipo>/fin-<n>-<slug>` con los tipos de `CLAUDE.md` §Flujo → Ramas (`feat`, `fix`, `chore`,
`docs`, `refactor`); slug en español, 2-5 palabras con guiones, como las ramas históricas.
Si la rama ya existe (local u origin), se retoma: checkout de ella.

    git checkout -b <rama> main

## 3. Jira
`transitionJiraIssue` id `21` (En curso) y comentario `Rama: <rama>`.
