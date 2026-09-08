# Barrido de paridad visual del APK

Escrito el 2026-09-02 como plan; **cerrado el 2026-09-02**. Se queda porque el
método sirve para la próxima vez, no porque falte trabajo: el estado vigente
vive en [`ANDROID_PARITY.md`](ANDROID_PARITY.md).

Se comparó pantalla por pantalla contra la web al ancho del teléfono, midiendo
capturas: panel (con el periodo puesto en un mes con movimiento, para que se
dibujen los widgets que dependen de él), carteras, movimientos, inversiones,
simulador, metas, presupuestos, suscripciones, categorías, ajustes, los
formularios de alta y edición de todo lo anterior, las dos pantallas de
detalle, el desglose de una categoría, entrar y crear cuenta, cambiar
contraseña, novedades, monedas y la bandeja de salida. Después se repitió el
recorrido en modo claro.

Lo que salió está en los commits de la rama `feat/android-apk`; lo que conviene
recordar, en la lista de «cosas que conviene no repetir» de `ANDROID_PARITY.md`.
Lo que se dejó a propósito, en «diferencias conocidas» del mismo archivo.

---

## Método

Es el único que ha funcionado. No sustituirlo por «se ve bien».

1. Las dos superficies contra **los mismos datos**: `wrangler dev` local y el
   APK compilado apuntando a la IP LAN.
2. Capturar la web **al tamaño exacto del teléfono** (viewport 411×891,
   `deviceScaleFactor` 1.75 → imagen de 720×1560, igual que `screencap`).
3. Poner las dos imágenes lado a lado y compararlas.
4. Cuando algo «se ve casi igual», **medir**. La diferencia de 8 dp en los
   márgenes de página no se veía; se encontró midiendo el borde de la tarjeta.

### Preparar el entorno

```sh
# 1. IP de la LAN (la que ve el teléfono)
ip -4 addr show | grep -oP '(?<=inet\s)192\.168\.[0-9.]+' | head -1

# 2. Servidor local con los datos de prueba
npm run build
cd worker && npx wrangler dev --ip 0.0.0.0
#    OJO: tras CADA `npm run build` hay que reiniciar wrangler; no sirve los
#    assets con hash nuevo en caliente.

# 3. APK contra ese servidor
cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ./gradlew assembleDebug -PbrokeApiBase=http://<ip-lan>:8787
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Cuenta de prueba: `alan@test.mx` / `test1234`. El teléfono (S25U) se conecta por
**adb inalámbrico**; el cable no sirve.

### Capturar la web

```sh
npm run shots:web                          # las 30 y pico pantallas
npm run shots:web -- wallet-detail         # sólo una
OUT=/tmp/shots npm run shots:web           # a otra carpeta (default ./parity-shots)
PERIOD=2026-08 npm run shots:web -- home   # fija el mes del panel
SCHEME=light npm run shots:web             # el tema claro
```

`PERIOD` importa más de lo que parece: con el mes corriente vacío, la gráfica de
flujo, los dos desgloses y «ingresos vs gastos» no se dibujan y no hay nada que
comparar.

`scripts/parity-shots.mjs` entra una sola vez (el worker limita los intentos de
login: entrar por pantalla lo tumba a media corrida), abre los modales que hacen
falta y baja por cada pantalla en pasos de una altura de viewport.

Para agregar una pantalla, agrégala a `SCREENS`: una regex abre un botón por su
nombre, `{ css }` abre un enlace (las filas que llevan a un detalle son `<Link>`,
no botones).

### Capturar el APK

```sh
adb exec-out screencap -p > apk-<pantalla>.png
adb shell input tap <x> <y>          # navegar
adb shell input swipe 360 1200 360 300 300   # bajar una pantalla
```

Para medir si algo es instantáneo o si aparece por partes, grabar en vez de
capturar:

```sh
adb shell screenrecord --time-limit 8 /sdcard/x.mp4
adb pull /sdcard/x.mp4 && ffmpeg -i x.mp4 -vf fps=10 frames/f%03d.png
```

### Medir bordes (para las diferencias que no se ven)

```python
from PIL import Image
def edges(path, y):
    im = Image.open(path).convert("L"); w, _ = im.size
    row = [im.getpixel((x, y)) for x in range(w)]
    base = min(row[:20])
    izq = next(x for x in range(w) if row[x] > base + 6)
    der = next(x for x in range(w - 1, -1, -1) if row[x] > base + 6)
    return izq, der
# El borde de la tarjeta tiene que caer en el mismo pixel en las dos.
```

---

## Orden de trabajo

Por dónde más se usa la app, no por dónde es más fácil.

### Bloque 1 — captura (es donde vive el usuario)

1. **Alta de movimiento** (`TransactionFormModal` ↔ `TransactionFormSheet`).
   Los tres tipos: ingreso, gasto y transferencia — la transferencia cambia de
   campos, y en multimoneda aparece el segundo importe. Revisar también el campo
   de hora y la compra a MSI desde el alta.
2. **Editar movimiento** (`TransactionEditSheet`), incluida una transferencia,
   que edita las dos patas.
3. **Editar la pata de un movimiento de inversión** (`InvestmentLegEditSheet`) y
   **editar una aportación a meta** (`ApartadoEditSheet`) desde la lista.
4. **Bandeja de salida** (`OutboxPanel`): se ve apagando el servidor, capturando
   un movimiento y volviendo a prenderlo.

### Bloque 2 — detalle

5. **Detalle de cartera** (`WalletDetailPage` ↔ `WalletDetailScreen`): saldo,
   apartados, sus movimientos. En una tarjeta de crédito además el panel de
   corte / por pagar / utilización, y el plan MSI (`MsiPlanSheet`). La cuenta de
   prueba tiene «Tarjeta Test A» y un plan «funda (1/3)».
6. **Detalle de inversión** (`InvestmentDetailPage` ↔ `InvestmentDetailScreen`):
   encabezado, gráfica de proyección, movimientos, snapshots.
7. **Alta de inversión** (`InvestmentFormModal` ↔ `NewInvestmentSheet`): el
   catálogo con las tasas de Banxico en vivo, y luego el formulario de cada tipo
   (CETES, Nu, cripto, tasa fija, valor manual).
8. **Editar movimiento de inversión** (`MovementEditModal` ↔ `InvestmentSheets`).

### Bloque 3 — el resto de los formularios

9. Alta/edición de **cartera** (`WalletFormModal` ↔ `WalletFormSheet`),
   incluidos los skins y el `grad:` personalizado.
10. Alta/edición de **meta** y **aportar** (`GoalFormModal`, `ContributeModal` ↔
    `GoalSheets`), con y sin fecha límite.
11. Alta/edición de **presupuesto** (↔ `BudgetFormSheet`).
12. Alta/edición de **suscripción** (↔ `SubscriptionFormSheet`), con su logo.
13. Alta/edición de **categoría** (↔ `CategoryFormSheet`).
14. **Diálogo de desglose por categoría** desde el panel
    (`CategoryDetailModal` ↔ `CategoryDetailDialog`).

### Bloque 4 — panel con datos y pantallas sueltas

15. **Los widgets que dependen del periodo.** Hoy (septiembre 2026) el mes
    corriente está vacío, así que la gráfica de flujo, los desgloses y
    «ingresos vs gastos» ni se dibujan. Poner el periodo en **agosto 2026** en
    las dos superficies (ahí está el movimiento de la cuenta de prueba) y
    comparar los cinco widgets.
16. **Login y alta de cuenta** (`LoginPage` ↔ `LoginScreen`), incluido el estado
    de error.
17. **Cambiar contraseña**, **Novedades**, **Monedas** (esta última no existe en
    la web: sólo revisar que se vea de la casa, no compararla).

### Bloque 5 — cierre

18. Repetir en **modo claro** las pantallas que hayan cambiado
    (`adb shell "cmd uimode night no"`, y volver a `yes` al terminar).
19. Actualizar `docs/ANDROID_PARITY.md`: la tabla y, si salió algo que valga la
    pena recordar, la lista de «cosas que conviene no repetir».
20. `cargo test --workspace`, `npx tsc --noEmit`, compilar el APK.
21. Cortar el release: `chore(release)` (bump en `package.json` + entrada en
    `src/lib/changelog.ts` + `npm run gen:android-strings` si cambiaron textos),
    merge `--no-ff` a `main`, tag semver. Sólo se despliega un tag.
22. **Desplegar sólo con el visto bueno del usuario, después de que él lo pruebe
    en su teléfono.** Compilarle antes un APK firmado contra producción.

---

## Reglas al arreglar lo que salga

- La web es el plano. Si Android difiere, se cambia Android — salvo que la web
  esté claramente mal (pasó dos veces en esta rama: un dorado suelto de la
  paleta vieja, y un eje que etiquetaba un solo año). En ese caso se arregla la
  web **y** Android en el mismo commit, y se dice por qué.
- Nada de cadenas a mano en un composable: van en `src/i18n/es.ts` + `en.ts` y
  se regeneran con `npm run gen:android-strings`.
- Nada de aritmética de dinero en Kotlin. Si aparece, es de `finanzas-core`.
- Toda lectura nueva va por `loadSynced` con su propia llave, o la pantalla
  vuelve a tardar al entrar (ver la sección «Reads» de `android/README.md`).
- Márgenes de página: 16 dp. Tarjetas: 16 dp de radio.
