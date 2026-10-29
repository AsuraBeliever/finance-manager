---
name: fin-ticket
description: Paso 1 de fin-flow. Convierte un bug, mejora o cambio que pide el cliente en una tarea de Jira bien formada (tipo, épica, etiquetas, «Hecho cuando») en el proyecto FIN. Devuelve la clave FIN-n.
argument-hint: <descripción del cliente>
---

# fin-ticket — crear la tarea en Jira

Constantes en `CLAUDE.md` §Flujo de desarrollo → Jira (cloudId `f04e906b-0ec4-4966-b049-acd498427118`,
clave `FIN`, transiciones, etiquetas).

Petición: $ARGUMENTS

## 1. No duplicar
`project = FIN AND statusCategory != Done AND text ~ "<2-3 palabras clave>"`. Si ya existe,
**úsala**: comenta lo que el cliente acaba de decir y devuelve esa clave. Si no tiene «Hecho cuando»,
añádeselo antes de seguir.

## 2. Entender antes de escribir
Investiga lo justo para que sea concreta: dónde vive el código (`crates/finanzas-core`,
`worker/src/handlers/`, `src/features/`, `android/app/src/main/`), cómo se reproduce, causa
probable. No implementes nada todavía.

## 3. Clasificar
| Campo | Regla |
|---|---|
| Tipo | `Historia` si el cliente lo nota; `Tarea` si no (build, CI, docs, refactor); `Error` si es un fallo de producto |
| Etiquetas | ámbitos que toca (`web`, `android`, `backend`, `core`, `i18n`, `ci`, `docs`) |
| Épica | `Release vX.Y.Z` abierta si se están agrupando cambios; si no, `Mantenimiento continuo` (FIN-2) para bugs/chores, ninguna para features sueltas |
| Responsable | sin asignar. Al cliente (`70121:f22207c0-2104-4fb2-97e6-8f23667e6a63`) solo si necesita **su** decisión, con `decision-cliente` |

## 4. Redactar (Markdown, en español)
Resumen corto con lo que nota el usuario, no el nombre de una función.

    **Qué pasa / qué se pide:** …
    **Qué debería pasar:** …
    **Cómo se reproduce:** … (si es bug)
    **Hecho cuando:**
    1. <criterio comprobable en la web>
    2. <el mismo criterio en el APK> (regla de paridad: todo cambio de producto, en las dos)
    3. <criterio comprobable por test (fórmula con valor calculado a mano, parser, RPC)>
    **Notas técnicas:** ficheros, causa probable, migración si toca esquema

El «Hecho cuando» es el contrato de `fin-test`, `fin-review` y `fin-dev-test`: verificable, no intención.
Nada de casillas `- [ ]` (Jira las escapa): listas numeradas.

## 5. Crear y devolver
`createJiraIssue` (`projectKey: FIN`, `issueType`, `parent` si va en épica, `labels`). Devuelve clave
y enlace `https://alansethmanjarrez.atlassian.net/browse/FIN-n`.
