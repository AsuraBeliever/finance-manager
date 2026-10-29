---
name: fin-flow
description: Flujo completo de un cambio en Finanzas, desde que el cliente lo pide hasta que está probado en dev (local), mergeado en main y publicado (tag + wrangler deploy + APK). Usar SIEMPRE que el cliente reporte un bug, pida una mejora o una feature, o pida cualquier cambio en el proyecto ("no funciona X", "quiero que Y", "cambia Z", "falla al…"). Encadena fin-ticket → fin-branch → fin-implement → fin-test → fin-pr → fin-review → fin-dev-test → fin-release (o fin-merge si no es de producto).
argument-hint: <descripción del bug, mejora o cambio>
---

# fin-flow — del reporte a la release

El cliente describe algo en lenguaje de producto. Este flujo lo convierte en una tarea de Jira,
una rama, código (web **y** Android), pruebas, un PR revisado, una prueba completa en dev y, si
pasa, un release publicado, **sin que el cliente tenga que tocar nada**. Las reglas de
`CLAUDE.md` mandan sobre todo lo que dice aquí.

Este repo **no tiene `develop`**: la rama de trabajo es la integración y `main` solo recibe
releases (`CLAUDE.md` §Flujo de desarrollo).

Petición: $ARGUMENTS

## Pasos

Ejecuta cada paso con la herramienta Skill, en orden. Cada uno recibe la clave `FIN-n` que
devuelve el primero.

| # | Skill | Sale con |
|---|---|---|
| 1 | `fin-ticket` | tarea en Jira, clave `FIN-n` |
| 2 | `fin-branch FIN-n` | rama desde `main`, tarea *En curso* |
| 3 | `fin-implement FIN-n` | commits en la rama, compilando y formateados, web + Android |
| 4 | `fin-test FIN-n` | pruebas de backend + uso real (web y emulador), informe |
| 5 | `fin-pr FIN-n` | PR contra `main`, etiqueta `en-revision` |
| 6 | `fin-review <nº PR>` | veredicto APROBADO o CAMBIOS, publicado en el PR |
| 7 | `fin-dev-test FIN-n` | sha de la rama probado entero en dev local: **PASA** o **FALLA** |
| 8 | `fin-release` | solo con PASA: bump + changelog, merge a `main`, tag, deploy, APK, tarea *Listo* |

**Cambio sin efecto en el producto** (rama `chore/`, `docs/`, `refactor/` sin cambio visible):
pasos 1-6 y luego `fin-merge <nº PR>` en vez de 7-8. Sin release ni deploy.

**Bucle de revisión.** Si `fin-review` pide cambios: paso 3 con los hallazgos → 4 → push → 6.
Máximo **3 rondas**. A la tercera sin aprobar, para y explícale al cliente qué bloquea, en lenguaje
de producto.

**Si falla una prueba**, se arregla dentro de 3/4, no se salta.

**Si dev FALLA (paso 7)**, no hay release y `main` no se toca. Se vuelve al paso 3 en la misma
rama con el fallo como hallazgo → 4 → push → 6 → 7. Máximo **2 vueltas**; a la segunda, para y
explícale al cliente qué pasa.

## Cuándo parar y preguntar al cliente

Solo en estos casos. Lo demás lo decides tú, como responsable técnico.
- La petición admite dos comportamientos de producto razonables y distintos → `AskUserQuestion`
  con opciones en lenguaje de producto.
- El cambio rompe una regla dura del proyecto (`CLAUDE.md` §Arquitectura, §Convenciones) → no se
  hace; se explica y se propone otra vía.
- Migración de datos del usuario en D1 de producción o algo irreversible.
- No se puede portar a Android en el momento → por la regla de paridad **no se mergea**; se le
  explica al cliente.
- Varias peticiones en un mensaje → una tarea y un flujo por cada una, en secuencia (cada una
  termina en su release, o se agrupan si el cliente lo pide así).
- El cliente pidió **no publicar todavía** («no lo saques aún») → para tras el paso 7 con el PR
  abierto y la tarea en *En curso* + `en-revision`; el release se hará cuando lo pida con `fin-release`.

## Al terminar

Informe breve al cliente, en su idioma y sin jerga: qué cambió visto desde el producto, cómo se
comprobó (web y Android), la versión publicada y enlaces (tarea, PR y release). Si dev falló,
qué falló, qué se hizo y que no se publicó nada.
