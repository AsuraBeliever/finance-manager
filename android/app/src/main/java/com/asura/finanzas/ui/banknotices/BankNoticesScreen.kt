package com.asura.finanzas.ui.banknotices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BankNoticeSettings
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.IngestResult
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.PendingTransaction
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.ConfirmDialog
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.GhostButton
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.MoneyField
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PanelCard
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.TimeField
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular
import com.asura.finanzas.ui.wallets.formatDayMonth
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The web's `/avisos` (`src/features/bankNotices/BankNoticesPage.tsx`): the
 * inbox of movements read from bank notices — confirm or dismiss each — then
 * the Gmail connection, the extra bank senders and card ending → wallet.
 * Nothing here computes money: amounts come in cents from the worker and go
 * back as the cents the user typed.
 */
@Composable
fun BankNoticesScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced("pendingTransactions", refetch = key) {
        repository.pendingTransactions()
    }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<BankNoticeSettings?>(null) }
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var confirming by remember { mutableStateOf<PendingTransaction?>(null) }
    var dismissing by remember { mutableStateOf<PendingTransaction?>(null) }
    var pasting by remember { mutableStateOf(false) }
    // The web's `?gmail=ok|denied|error` banner after the consent screen.
    var gmailResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key) {
        settings = runCatching { repository.bankNoticeSettings() }.getOrNull() ?: settings
        wallets = runCatching { repository.wallets().value }.getOrDefault(wallets)
    }

    val connectGmail = rememberGmailAuthorization(
        onCode = { code ->
            scope.launch {
                gmailResult = runCatching { repository.connectGmail(code) }
                    .fold(onSuccess = { "ok" }, onFailure = { "error" })
                reload()
            }
        },
        onFail = { message -> if (message != null) gmailResult = "error" else gmailResult = "denied" },
    )

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BackHandler(onBack = onBack)
                PageHeader(stringResource(R.string.bank_notices_title), Modifier.padding(bottom = 16.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.bank_notices_paste),
                        onClick = { pasting = true },
                        leadingIcon = Lucide.ClipboardPaste,
                    )
                }
            }
            gmailResult?.let { result ->
                item { ResultBanner(result) { gmailResult = null } }
            }
            if (current.data.isEmpty()) item {
                EmptyState(
                    Lucide.Inbox,
                    stringResource(R.string.bank_notices_empty_title),
                    stringResource(R.string.bank_notices_empty_description),
                )
            }
            items(current.data, key = { it.id }) { p ->
                PendingCard(
                    p = p,
                    walletName = wallets.firstOrNull { it.id == p.suggestedWalletId }?.name,
                    onConfirm = { confirming = p },
                    onDismiss = { dismissing = p },
                )
            }
            item {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    GmailPanel(settings, onConnect = connectGmail, onDisconnect = {
                        scope.launch {
                            runCatching { repository.disconnectGmail() }
                            reload()
                        }
                    })
                    BanksPanel(settings, wallets, repository, onChanged = reload)
                    CardsPanel(settings, wallets, repository, onChanged = reload)
                }
            }
        }
    }

    confirming?.let { p ->
        ConfirmSheet(
            repository = repository,
            pending = p,
            wallets = wallets,
            cardMapped = settings?.cards?.any { it.cardLast4 == p.cardLast4 } == true,
            onDismiss = { confirming = null },
            onSaved = { confirming = null; reload() },
        )
    }
    if (pasting) {
        PasteSheet(repository, onDismiss = { pasting = false }, onAdded = reload)
    }
    dismissing?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.bank_notices_dismiss),
            message = stringResource(R.string.bank_notices_dismiss_confirm),
            confirmLabel = stringResource(R.string.bank_notices_dismiss),
            onConfirm = {
                dismissing = null
                scope.launch {
                    runCatching { repository.dismissPendingTransaction(p.id) }
                    reload()
                }
            },
            onDismiss = { dismissing = null },
        )
    }
}

@Composable
private fun ResultBanner(result: String, onClose: () -> Unit) {
    val colors = Broke.colors
    val ok = result == "ok"
    val tint = if (ok) colors.accent else colors.danger
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.1f))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            stringResource(
                when (result) {
                    "ok" -> R.string.bank_notices_gmail_ok
                    "denied" -> R.string.bank_notices_gmail_denied
                    else -> R.string.bank_notices_gmail_error
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fg,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Lucide.X,
            contentDescription = stringResource(R.string.common_close),
            tint = colors.fgSubtle,
            modifier = Modifier.size(16.dp).clickable(onClick = onClose),
        )
    }
}

@Composable
private fun Pill(text: String, accent: Boolean = false) {
    val colors = Broke.colors
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = if (accent) colors.accent else colors.fgMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) colors.accent.copy(alpha = 0.1f) else colors.surfaceOverlay)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingCard(
    p: PendingTransaction,
    walletName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Broke.colors
    val settings = LocalAppSettings.current
    val income = p.kind == "income"
    val tint = if (income) colors.accent else colors.danger
    val title = p.merchant ?: p.subject ?: p.sender
    val when_ = formatDayMonth(p.occurredAt) + (clockLabel(p.occurredTime)?.let { " $it" } ?: "")
    val meta = listOfNotNull(
        p.bankName ?: p.sender.takeIf { it.isNotBlank() },
        p.cardLast4?.let { stringResource(R.string.bank_notices_card) + " •• " + it },
        when_,
    ).joinToString(" · ")

    GlassCard(Modifier.fillMaxWidth().testTag("pending-card"), padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = if (income) 0.15f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (income) Lucide.ArrowDownLeft else Lucide.ArrowUpRight,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fgSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Pill(
                        stringResource(
                            if (income) R.string.bank_notices_income else R.string.bank_notices_expense,
                        ),
                    )
                    Pill(
                        stringResource(
                            if (p.source == "gmail") R.string.bank_notices_via_gmail
                            else R.string.bank_notices_via_paste,
                        ),
                    )
                    walletName?.let { Pill(it, accent = true) }
                }
            }
            Text(
                (if (income) "+" else "−") +
                    maskIfHidden(formatMoney(p.amountCents, p.currencyCode), settings.hideBalances),
                style = MaterialTheme.typography.titleMedium
                    .copy(fontWeight = FontWeight.SemiBold).tabular(),
                color = if (income) colors.accent else colors.fg,
            )
        }
        p.duplicateOf?.let { d ->
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.cyan.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Lucide.TriangleAlert,
                    contentDescription = null,
                    tint = colors.cyan,
                    modifier = Modifier.size(14.dp).padding(top = 1.dp),
                )
                Text(
                    stringResource(R.string.bank_notices_duplicate) + ": " +
                        (d.description ?: "—") + " · " + d.walletName + " · " +
                        formatDayMonth(d.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fg,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            GhostButton(stringResource(R.string.bank_notices_dismiss), onDismiss)
            PrimaryButton(stringResource(R.string.bank_notices_confirm), onConfirm)
        }
    }
}

@Composable
private fun ConfirmSheet(
    repository: BrokeRepository,
    pending: PendingTransaction,
    wallets: List<Wallet>,
    cardMapped: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var wallet by remember { mutableStateOf(wallets.firstOrNull { it.id == pending.suggestedWalletId }) }
    var amount by remember { mutableStateOf(formatMoney(pending.amountCents, withSymbol = false)) }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var description by remember { mutableStateOf(pending.merchant.orEmpty()) }
    var date by remember {
        mutableStateOf(runCatching { LocalDate.parse(pending.occurredAt) }.getOrDefault(LocalDate.now()))
    }
    var time by remember { mutableStateOf(pending.occurredTime) }
    var remember_ by remember { mutableStateOf(pending.cardLast4 != null && !cardMapped) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val pickWallet = stringResource(R.string.bank_notices_pick_wallet)
    val noneLabel = stringResource(R.string.bank_notices_none)

    LaunchedEffect(Unit) {
        categories = runCatching { repository.transactionCategories(pending.kind) }
            .getOrDefault(emptyList())
        category = categories.firstOrNull { it.id == pending.suggestedCategoryId }
    }

    val cents = parseAmountToCents(amount)
    FormSheet(
        title = stringResource(R.string.bank_notices_confirm_title),
        busy = busy,
        error = error,
        canSave = !busy && cents != null && cents > 0 && wallet != null,
        saveLabel = stringResource(R.string.bank_notices_confirm),
        onDismiss = onDismiss,
        onSave = {
            val target = wallet ?: run { error = pickWallet; return@FormSheet }
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.confirmPendingTransaction(
                        id = pending.id,
                        walletId = target.id,
                        amountCents = cents ?: 0,
                        categoryId = category?.id,
                        description = description,
                        occurredAt = date.toString(),
                        occurredTime = time,
                        rememberCard = remember_,
                    )
                }
                    .onSuccess { onSaved() }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        PickerField(
            label = stringResource(R.string.bank_notices_wallet),
            options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { it?.name ?: pickWallet },
            onSelect = { wallet = it; error = null },
            emptyLabel = pickWallet,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth()) {
            MoneyField(
                label = stringResource(R.string.bank_notices_amount),
                value = amount,
                onValueChange = { amount = it; error = null },
                modifier = Modifier.fillMaxWidth(),
            )
            if (wallet != null && wallet?.currencyCode != pending.currencyCode) {
                Text(
                    text(R.string.bank_notices_currency_warning, "currency" to pending.currencyCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = Broke.colors.cyan,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        PickerField(
            label = stringResource(R.string.bank_notices_category),
            options = listOf<TransactionCategory?>(null) + categories,
            selected = category,
            optionLabel = { c -> c?.let { seedName(it.name, it.isSystem) } ?: noneLabel },
            onSelect = { category = it },
            emptyLabel = noneLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        FormField(
            label = stringResource(R.string.bank_notices_description),
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(
                label = stringResource(R.string.bank_notices_date),
                value = date,
                onChange = { date = it },
                modifier = Modifier.weight(1f),
            )
            TimeField(
                label = stringResource(R.string.bank_notices_time),
                value = time,
                onChange = { time = it },
                modifier = Modifier.weight(1f),
            )
        }
        pending.cardLast4?.let { card ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(8.dp))
                    .clickable { remember_ = !remember_ }
                    .padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WebCheckbox(
                    checked = remember_,
                    onCheckedChange = { remember_ = it },
                    boxSize = 16.dp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text(R.string.bank_notices_remember_card, "card" to card),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Broke.colors.fg,
                )
            }
        }
    }
}

@Composable
private fun PasteSheet(
    repository: BrokeRepository,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<IngestResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    FormSheet(
        title = stringResource(R.string.bank_notices_paste_title),
        busy = busy,
        error = error,
        canSave = !busy && value.isNotBlank(),
        saveLabel = stringResource(R.string.bank_notices_read),
        onDismiss = onDismiss,
        onSave = {
            busy = true
            error = null
            scope.launch {
                runCatching { repository.importBankNotice(value) }
                    .onSuccess {
                        result = it
                        if (it.status == "added") {
                            value = ""
                            onAdded()
                        }
                    }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                    }
                busy = false
            }
        },
    ) {
        Text(
            stringResource(R.string.bank_notices_paste_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Broke.colors.fgMuted,
        )
        FormField(
            label = "",
            value = value,
            onValueChange = { value = it; result = null },
            placeholder = stringResource(R.string.bank_notices_paste_placeholder),
            singleLine = false,
            modifier = Modifier.fillMaxWidth().testTag("paste-text"),
        )
        result?.let { r ->
            val message = when (r.status) {
                "added" -> stringResource(R.string.bank_notices_result_added)
                "duplicate" -> stringResource(R.string.bank_notices_result_duplicate)
                else -> stringResource(
                    when (r.reason) {
                        "promotion" -> R.string.bank_notices_rejected_promotion
                        "noAmount" -> R.string.bank_notices_rejected_no_amount
                        "ownTransfer" -> R.string.bank_notices_rejected_own_transfer
                        else -> R.string.bank_notices_rejected_not_amovement
                    },
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (r.status == "added") Broke.colors.accent else Broke.colors.cyan,
            )
        }
    }
}

@Composable
private fun PanelTitle(icon: ImageVector, title: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = Broke.colors.fgSubtle, modifier = Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Broke.colors.fg)
    }
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = Broke.colors.fgSubtle,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
}

@Composable
private fun GmailPanel(
    settings: BankNoticeSettings?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val colors = Broke.colors
    var confirmOff by remember { mutableStateOf(false) }
    PanelCard(Modifier.fillMaxWidth()) {
        PanelTitle(
            Lucide.Mail,
            stringResource(R.string.bank_notices_gmail_title),
            stringResource(R.string.bank_notices_gmail_hint),
        )
        val gmail = settings?.gmail
        when {
            settings == null -> Unit
            gmail == null && !settings.gmailAvailable -> Text(
                stringResource(R.string.bank_notices_gmail_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgMuted,
            )
            gmail == null -> PrimaryButton(stringResource(R.string.bank_notices_gmail_connect), onConnect)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text(R.string.bank_notices_gmail_connected, "email" to gmail.email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fg,
                )
                if (gmail.needsReconnect) {
                    Text(
                        stringResource(R.string.bank_notices_gmail_needs_reconnect),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.danger,
                    )
                } else {
                    Text(
                        gmail.lastPushAt?.let {
                            text(R.string.bank_notices_gmail_last_push, "when" to "$it UTC")
                        } ?: stringResource(R.string.bank_notices_gmail_never_pushed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.fgSubtle,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (gmail.needsReconnect && settings.gmailAvailable) {
                        PrimaryButton(stringResource(R.string.bank_notices_gmail_reconnect), onConnect)
                    }
                    GhostButton(
                        stringResource(R.string.bank_notices_gmail_disconnect),
                        { confirmOff = true },
                        tint = colors.danger,
                    )
                }
            }
        }
    }
    if (confirmOff) {
        ConfirmDialog(
            title = stringResource(R.string.bank_notices_gmail_disconnect),
            message = stringResource(R.string.bank_notices_gmail_disconnect_confirm),
            confirmLabel = stringResource(R.string.bank_notices_gmail_disconnect),
            onConfirm = { confirmOff = false; onDisconnect() },
            onDismiss = { confirmOff = false },
        )
    }
}

@Composable
private fun BanksPanel(
    settings: BankNoticeSettings?,
    wallets: List<Wallet>,
    repository: BrokeRepository,
    onChanged: () -> Unit,
) {
    val colors = Broke.colors
    val scope = rememberCoroutineScope()
    var pattern by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val defaultWallet = stringResource(R.string.bank_notices_sender_wallet)
    PanelCard(Modifier.fillMaxWidth()) {
        PanelTitle(
            Lucide.Landmark,
            stringResource(R.string.bank_notices_banks_title),
            stringResource(R.string.bank_notices_banks_hint),
        )
        Column(
            Modifier.padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            settings?.knownBanks?.forEach { b ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, style = MaterialTheme.typography.bodyMedium, color = colors.fg, modifier = Modifier.weight(1f))
                    Text(
                        (b.domains.firstOrNull() ?: "") + " · " + stringResource(R.string.bank_notices_built_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.fgSubtle,
                    )
                }
            }
            settings?.senders?.forEach { s ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        s.pattern,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    PickerField(
                        label = "",
                        options = listOf<Wallet?>(null) + wallets,
                        selected = wallets.firstOrNull { it.id == s.walletId },
                        optionLabel = { it?.name ?: defaultWallet },
                        onSelect = { w ->
                            scope.launch {
                                runCatching { repository.updateBankSender(s.id, w?.id) }
                                onChanged()
                            }
                        },
                        emptyLabel = defaultWallet,
                        modifier = Modifier.width(144.dp),
                    )
                    Icon(
                        Lucide.Trash,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = colors.fgSubtle,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                scope.launch {
                                    runCatching { repository.deleteBankSender(s.id) }
                                    onChanged()
                                }
                            }
                            .padding(6.dp)
                            .size(15.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormField(
                label = "",
                value = pattern,
                onValueChange = { pattern = it; error = null },
                placeholder = stringResource(R.string.bank_notices_sender_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                stringResource(R.string.bank_notices_add_sender),
                onClick = {
                    scope.launch {
                        runCatching { repository.addBankSender(pattern) }
                            .onSuccess { pattern = ""; onChanged() }
                            .onFailure { error = it.message }
                    }
                },
                enabled = pattern.isNotBlank(),
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CardsPanel(
    settings: BankNoticeSettings?,
    wallets: List<Wallet>,
    repository: BrokeRepository,
    onChanged: () -> Unit,
) {
    val colors = Broke.colors
    val scope = rememberCoroutineScope()
    var last4 by remember { mutableStateOf("") }
    var newWallet by remember { mutableStateOf<Wallet?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val pickWallet = stringResource(R.string.bank_notices_pick_wallet)
    fun save(card: String, walletId: Long?) {
        scope.launch {
            runCatching { repository.setCardWallet(card, walletId) }
                .onSuccess { error = null; onChanged() }
                .onFailure { error = it.message }
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        PanelTitle(
            Lucide.CreditCard,
            stringResource(R.string.bank_notices_cards_title),
            stringResource(R.string.bank_notices_cards_hint),
        )
        val cards = settings?.cards.orEmpty()
        if (cards.isEmpty()) {
            Text(
                stringResource(R.string.bank_notices_no_cards),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgMuted,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { c ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "•• " + c.cardLast4,
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = colors.fg,
                        modifier = Modifier.width(64.dp),
                    )
                    PickerField(
                        label = "",
                        options = wallets,
                        selected = wallets.firstOrNull { it.id == c.walletId },
                        optionLabel = { it.name },
                        onSelect = { w -> save(c.cardLast4, w.id) },
                        emptyLabel = pickWallet,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Lucide.Trash,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = colors.fgSubtle,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { save(c.cardLast4, null) }
                            .padding(6.dp)
                            .size(15.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormField(
                label = "",
                value = last4,
                onValueChange = { v -> last4 = v.filter { it.isDigit() }.take(4) },
                placeholder = stringResource(R.string.bank_notices_card_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp),
            )
            PickerField(
                label = "",
                options = wallets,
                selected = newWallet,
                optionLabel = { it.name },
                onSelect = { newWallet = it },
                emptyLabel = pickWallet,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                stringResource(R.string.bank_notices_add_card),
                onClick = {
                    val w = newWallet ?: return@PrimaryButton
                    save(last4, w.id)
                    last4 = ""
                },
                enabled = last4.length == 4 && newWallet != null,
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** 'HH:MM' as the web's `formatTime`: "2:35 p.m." / "2:35 PM" / "14:35". */
@Composable
private fun clockLabel(time: String?): String? {
    if (time.isNullOrBlank()) return null
    val settings = LocalAppSettings.current
    val locale = if (settings.locale.startsWith("en")) java.util.Locale.US else java.util.Locale.forLanguageTag("es-MX")
    val pattern = if (settings.clock24) "HH:mm" else "h:mm a"
    return runCatching {
        java.time.LocalTime.parse(time).format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
    }.getOrNull() ?: time
}
