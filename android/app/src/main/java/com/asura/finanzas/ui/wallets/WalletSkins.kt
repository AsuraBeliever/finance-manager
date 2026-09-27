package com.asura.finanzas.ui.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.ui.components.FieldLabel
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.theme.Broke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import com.asura.finanzas.ui.parseHexColor

/**
 * Wallet card art, ported from `src/lib/skins.ts`. Original gradients (no bank
 * marks). A wallet stores a catalog id, a `grad:<hex>` custom gradient, an
 * `img:` import, or nothing — and "nothing" does **not** mean "use the wallet
 * colour": the web falls back to a default per wallet category, which is why an
 * Efectivo wallet is leather-brown before anyone picks a skin.
 */
data class WalletSkin(
    val colors: List<Color>,
    /**
     * Where each colour lands along the gradient, as the CSS percentages.
     * Several skins put their bright band off-centre (`… 55%, …`), and
     * spreading the stops evenly instead shifted the sheen visibly.
     */
    val stops: List<Float>,
    /** Text colour that reads on this background. */
    val fg: Color,
    /** Motif drawn behind the figures. */
    val art: SkinArt,
)

/** Card skins get a chip; cash skins get a money illustration instead. */
enum class SkinArt { Chip, Banknote, Wallet, Coins, Piggy, None }

private fun skin(
    vararg hex: Long,
    fg: Long,
    stops: List<Float>? = null,
    art: SkinArt = SkinArt.Chip,
): WalletSkin {
    val colors = hex.map { Color(it) }
    val positions = stops ?: List(colors.size) { i ->
        if (colors.size == 1) 0f else i / (colors.size - 1f)
    }
    return WalletSkin(colors, positions, Color(fg), art)
}

private val CATALOG: Map<String, WalletSkin> = mapOf(
    // color tones
    "azul" to skin(0xFF0A3D91, 0xFF1565D8, 0xFF0A3D91, fg = 0xFFEAF2FF, stops = listOf(0f, .55f, 1f)),
    "marino" to skin(0xFF0B1F4D, 0xFF16357E, fg = 0xFFE8EEFC),
    "turquesa" to skin(0xFF0E7490, 0xFF22B8CF, fg = 0xFFE9FBFF),
    "rojo" to skin(0xFF9E1414, 0xFFE1232B, 0xFF9E1414, fg = 0xFFFFF0F0, stops = listOf(0f, .60f, 1f)),
    "vino" to skin(0xFF5B0B22, 0xFF9C1F3D, fg = 0xFFFFEEF2),
    "morado" to skin(0xFF5B1EA6, 0xFF9333EA, 0xFF6D28D9, fg = 0xFFF6EFFE, stops = listOf(0f, .60f, 1f)),
    "verde" to skin(0xFF065F46, 0xFF10B981, fg = 0xFFEAFFF4),
    // tiers
    "oro" to skin(0xFFB8860B, 0xFFF5D479, 0xFFCAA12F, fg = 0xFF3A2C06, stops = listOf(0f, .45f, 1f)),
    "platino" to skin(0xFF9AA3AD, 0xFFE6EBF0, 0xFFAEB6C0, fg = 0xFF23262E, stops = listOf(0f, .50f, 1f)),
    "black" to skin(0xFF0A0A0F, 0xFF23232E, 0xFF0A0A0F, fg = 0xFFECE9F5, stops = listOf(0f, .55f, 1f)),
    "infinite" to skin(0xFF0B1026, 0xFF1E2A78, 0xFF0B1026, fg = 0xFFE7EEFF, stops = listOf(0f, .50f, 1f)),
    // cash — money illustrations, no chip
    "efectivo" to skin(
        0xFF1B5E3A, 0xFF2F9E63, 0xFF1B5E3A,
        fg = 0xFFEAFFF0, stops = listOf(0f, .50f, 1f), art = SkinArt.Banknote,
    ),
    "cuero" to skin(
        0xFF5A3413, 0xFF8A5A2B, 0xFF4A2A0F,
        fg = 0xFFFBEEDE, stops = listOf(0f, .55f, 1f), art = SkinArt.Wallet,
    ),
    "monedas" to skin(
        0xFF7A5210, 0xFFD9A93A, 0xFF6B450C,
        fg = 0xFFFFF6E6, stops = listOf(0f, .50f, 1f), art = SkinArt.Coins,
    ),
    "ahorro" to skin(
        0xFF0B3B46, 0xFF0EA5A3, 0xFF0B3B46,
        fg = 0xFFEAFDFF, stops = listOf(0f, .55f, 1f), art = SkinArt.Piggy,
    ),
    // neon glass — matches the app; its last stop runs past the end at 110%
    "neon" to skin(0xFF7C3AED, 0xFFA855F7, 0xFF22D3EE, fg = 0xFFF4F0FF, stops = listOf(0f, .45f, 1.10f)),
    "holo" to skin(
        0xFFA5F3FC, 0xFFC4B5FD, 0xFFFBCFE8, 0xFFFDE68A,
        fg = 0xFF1A1430, stops = listOf(0f, .35f, .70f, 1f),
    ),
    "noche" to skin(0xFF141228, 0xFF2A2350, fg = 0xFFE8E6F4),
)

/**
 * Default skin per wallet category, so each category looks distinct before the
 * user picks one. Matches `categoryDefaultSkin` in the web (seeded es-MX names).
 */
private fun categoryDefaultSkin(categoryName: String?): String {
    val c = (categoryName ?: "").lowercase()
    return when {
        c.contains("efectivo") -> "cuero"
        c.contains("crédito") || c.contains("credito") -> "morado"
        c.contains("débito") || c.contains("debito") -> "azul"
        c.contains("ahorro") -> "ahorro"
        else -> "neon"
    }
}

/** Resolve a wallet's stored skin (or its category default) to a gradient. */
fun walletSkin(skinValue: String?, categoryName: String?): WalletSkin {
    if (!skinValue.isNullOrBlank() && skinValue != "null") {
        if (skinValue.startsWith("grad:")) {
            val parts = skinValue.removePrefix("grad:").split(",")
            val from = parseHexColor(parts.getOrNull(0))
            val to = parseHexColor(parts.getOrNull(1)) ?: from?.let { mixWithBlack(it) }
            if (from != null && to != null) {
                return WalletSkin(listOf(from, to), listOf(0f, 1f), Color.White, SkinArt.Chip)
            }
        }
        if (skinValue.startsWith("img:")) {
            // The photo is painted by the card itself; what matters here is that
            // it carries no chip and no motif (the web's `skinArt` → "none")
            // and that the figures go white on top of it. The gradient is only
            // what shows through if the image ever fails to decode.
            return CATALOG.getValue(categoryDefaultSkin(categoryName))
                .copy(fg = Color.White, art = SkinArt.None)
        }
        CATALOG[skinValue]?.let { return it }
    }

    // The wallet's own colour deliberately does NOT come into this. The web
    // resolves `effectiveSkin(skin, category)` first, which never yields null,
    // so `resolveSkin`'s colour fallback is unreachable from a wallet card:
    // honouring it here painted every coloured pocket as a plain card with a
    // chip where the browser drew the leather wallet of its category.
    return CATALOG.getValue(categoryDefaultSkin(categoryName))
}

/** The web's `color-mix(in oklab, c 52%, #000)`, close enough in sRGB. */
private fun mixWithBlack(color: Color, keep: Float = 0.52f) =
    Color(
        red = color.red * keep,
        green = color.green * keep,
        blue = color.blue * keep,
        alpha = color.alpha,
    )

/**
 * The photo behind an `img:` skin, decoded once per value. The web paints it
 * `center / cover`, so the card crops it the same way and lays the legibility
 * scrim over the top. Without this an imported card fell back to its category
 * gradient here while the browser showed the picture.
 */
@Composable
fun walletSkinImage(skinValue: String?): ImageBitmap? {
    if (skinValue == null || !skinValue.startsWith("img:data:")) return null
    return remember(skinValue) {
        runCatching {
            val base64 = skinValue.substringAfter("base64,", "")
            if (base64.isBlank()) return@runCatching null
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory
                .decodeByteArray(bytes, 0, bytes.size)
                ?.asImageBitmap()
        }.getOrNull()
    }
}

/** Whether the skin carries its own artwork, so the card adds the dark scrim. */
fun isImageSkin(skinValue: String?): Boolean = skinValue?.startsWith("img:") == true

/**
 * Colour stops clamped into the 0..1 a shader accepts. `neon` ends at 110%, a
 * legal CSS position that simply means the last colour is never fully reached
 * inside the box, so it is mixed back to whatever it would be at the edge.
 */
private fun WalletSkin.colorStops(): List<Pair<Float, Color>> {
    if (colors.size == 1) return listOf(0f to colors[0], 1f to colors[0])
    val raw = colors.mapIndexed { i, c ->
        (stops.getOrNull(i) ?: (i / (colors.size - 1f))) to c
    }
    val last = raw.last()
    if (last.first <= 1f) return raw
    val prev = raw[raw.size - 2]
    val t = (1f - prev.first) / (last.first - prev.first)
    val edge = androidx.compose.ui.graphics.lerp(prev.second, last.second, t)
    return raw.dropLast(1) + (1f to edge)
}

/**
 * The web's `linear-gradient(135deg, …)` for a box of this size.
 *
 * CSS runs that axis at a true 45° through the centre of the box and makes it
 * long enough to cover the corners — it is NOT the corner-to-corner diagonal,
 * which on a card twice as wide as it is tall points somewhere near 32° and
 * put the bright band in the wrong place.
 */
fun WalletSkin.brushFor(size: Size): Brush {
    val pairs = colorStops()
    // Half the axis length, resolved onto x and y: |W·sin45| + |H·cos45| over 2,
    // then projected back onto each axis — (W + H) / 4.
    val half = (size.width + size.height) / 4f
    val cx = size.width / 2f
    val cy = size.height / 2f
    return Brush.linearGradient(
        colorStops = pairs.toTypedArray(),
        start = Offset(cx - half, cy - half),
        end = Offset(cx + half, cy + half),
    )
}

/** The same gradient for a square swatch, where the size is not known yet. */
fun WalletSkin.brush(): Brush {
    val pairs = colorStops()
    return Brush.linearGradient(
        colorStops = pairs.toTypedArray(),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}


/**
 * The catalogue as the form shows it, in the web's four groups and order
 * (`SkinGroup` in `src/lib/skins.ts`). Ids match, so a card designed on one
 * surface is the same card on the other.
 */
val SKIN_GROUPS: List<Pair<Int, List<String>>> = listOf(
    com.asura.finanzas.R.string.wallets_skin_group_banco to
        listOf("azul", "marino", "turquesa", "rojo", "vino", "morado", "verde"),
    com.asura.finanzas.R.string.wallets_skin_group_nivel to
        listOf("oro", "platino", "black", "infinite"),
    com.asura.finanzas.R.string.wallets_skin_group_efectivo to
        listOf("efectivo", "cuero", "monedas", "ahorro"),
    com.asura.finanzas.R.string.wallets_skin_group_glass to
        listOf("neon", "holo", "noche"),
)

/** Each catalogue skin's `accent`, from the web's `SKINS` — the solid colour
 *  a wallet stores as its own `color` (its dot in charts and lists). */
private val SKIN_ACCENT = mapOf(
    "azul" to "#1565d8", "marino" to "#16357e", "turquesa" to "#22b8cf", "rojo" to "#e1232b",
    "vino" to "#9c1f3d", "morado" to "#9333ea", "verde" to "#10b981",
    "oro" to "#caa12f", "platino" to "#aeb6c0", "black" to "#3a3a48", "infinite" to "#1e2a78",
    "efectivo" to "#2f9e63", "cuero" to "#8a5a2b", "monedas" to "#c08a2e", "ahorro" to "#0ea5a3",
    "neon" to "#a855f7", "holo" to "#c4b5fd", "noche" to "#2a2350",
)

/**
 * The colour the web saves with a wallet: `skinAccent(effectiveSkin(skin,
 * category)) ?? COLORS[0]` — a custom gradient's own hue, a catalogue skin's
 * accent, the first chart colour for a photo.
 */
fun walletColorFor(skin: String?, categoryName: String?): String {
    val effective = skin?.takeIf { it.isNotBlank() && it != "null" } ?: categoryDefaultSkin(categoryName)
    return when {
        effective.startsWith("grad:") -> effective.removePrefix("grad:").substringBefore(",")
        effective.startsWith("img:") -> null
        else -> SKIN_ACCENT[effective]
    } ?: "#a855f7"
}

/** The catalogue entry for an id, for drawing a swatch. */
fun skinById(id: String): WalletSkin? = CATALOG[id]

/**
 * The card-design picker from the web's wallet form: the "auto" tile, the
 * custom-colour tile, and the catalogue laid out in its four labelled groups.
 * Selecting one stores the skin id, which is exactly what the web stores.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SkinPicker(
    selected: String?,
    categoryName: String?,
    onSelect: (String?) -> Unit,
    /** The colour the custom tile shows until a gradient is picked. */
    customSeed: String? = null,
) {
    val colors = Broke.colors
    var showCustom by remember { mutableStateOf(false) }
    val borderColor = colors.borderMuted
    // The web keeps the last custom colour in its own state; a `grad:` skin
    // brings its colour back, anything else starts from the first chart hue.
    val gradSelected = selected?.startsWith("grad:") == true
    val customHex = if (gradSelected) selected!!.removePrefix("grad:").substringBefore(",") else customSeed ?: "#a855f7"
    val imgSelected = selected?.startsWith("img:") == true
    Column {
        FieldLabel(stringResource(R.string.wallets_skin))
        val context = LocalContext.current
        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> uri?.let { encodeImageSkin(context, it)?.let(onSelect) } }

        // `space-y-3`; first row `flex flex-wrap items-center gap-2`: the auto
        // tile, the custom colour, the imported photo (once there is one) and
        // the dashed import button.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkinTile(
                brush = CATALOG.getValue(categoryDefaultSkin(categoryName)).brush(),
                selected = selected == null,
                onClick = { onSelect(null) },
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            SkinTile(
                brush = walletSkin("grad:$customHex", categoryName).brush(),
                selected = gradSelected,
                onClick = { showCustom = true },
                idleMark = Lucide.Palette,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (imgSelected) {
                SkinTile(
                    brush = CATALOG.getValue(categoryDefaultSkin(categoryName)).brush(),
                    image = walletSkinImage(selected),
                    selected = true,
                    onClick = {},
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = borderColor,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                                ),
                            ),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.Upload, contentDescription = null, tint = colors.fgMuted, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.wallets_skin_import),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fgMuted,
                )
            }
        }
        SKIN_GROUPS.forEach { (labelRes, ids) ->
            Spacer(Modifier.height(12.dp))
            // `mb-1.5 text-[0.65rem] font-medium uppercase tracking-[0.12em]`.
            Text(
                stringResource(labelRes).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.4.sp,
                    lineHeight = 15.6.sp,
                    letterSpacing = 0.12.em,
                ),
                color = colors.fgSubtle,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ids.forEach { id ->
                    SkinTile(
                        brush = CATALOG.getValue(id).brush(),
                        selected = selected == id,
                        onClick = { onSelect(id) },
                    )
                }
            }
        }
    }

    if (showCustom) {
        com.asura.finanzas.ui.components.CustomColorDialog(
            initial = parseHexColor(customHex) ?: colors.accent,
            onDismiss = { showCustom = false },
            onPick = { hex -> onSelect("grad:$hex"); showCustom = false },
        )
    }
}

/**
 * The web's `SkinSwatch`: a 72 × 44 tile with an inset white/15 hairline and
 * a 120° gloss; the chosen one grows 5 % inside a 2 px accent outline and
 * carries a white check.
 */
@Composable
private fun SkinTile(
    brush: Brush,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: androidx.compose.ui.graphics.ImageBitmap? = null,
    /** A glyph shown while the tile is not chosen (the custom colour's palette). */
    idleMark: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val accent = Broke.colors.accent
    Box(
        modifier
            .size(width = 72.dp, height = 44.dp)
            .graphicsLayer {
                if (selected) { scaleX = 1.05f; scaleY = 1.05f }
            }
            .then(
                if (selected) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            accent,
                            topLeft = androidx.compose.ui.geometry.Offset(-1.dp.toPx(), -1.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width + 2.dp.toPx(), size.height + 2.dp.toPx()),
                            cornerRadius = CornerRadius(9.dp.toPx()),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    com.asura.finanzas.ui.components.cssLinearGradient(
                        120f,
                        listOf(0f to Color.White.copy(alpha = 0.35f), 0.45f to Color.Transparent, 1f to Color.Transparent),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
        )
        val mark = if (selected) Lucide.Check else idleMark
        mark?.let {
            Icon(
                it,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (selected) 1f else 0.9f),
                modifier = Modifier.size(if (selected) 16.dp else 15.dp),
            )
        }
    }
}

/**
 * Scale an imported photo down and hand it back as a `img:<data-url>` skin —
 * the same shape the web's `compressImage` produces, so the card renders the
 * same on both surfaces and the value round-trips through the API unchanged.
 */
private fun encodeImageSkin(context: android.content.Context, uri: android.net.Uri): String? =
    runCatching {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
        // 640 px on the long edge is plenty for a card and keeps the row small.
        val scale = 640f / maxOf(bitmap.width, bitmap.height).toFloat()
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        } else {
            bitmap
        }
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
        "img:data:image/jpeg;base64," +
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }.getOrNull()
