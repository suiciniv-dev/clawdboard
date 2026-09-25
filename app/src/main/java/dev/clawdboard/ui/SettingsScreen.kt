package dev.clawdboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.clawdboard.core.Nudge
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.clawdboard.BuildConfig
import dev.clawdboard.core.Backdrop
import dev.clawdboard.core.Brightness
import dev.clawdboard.core.Orientation
import dev.clawdboard.core.Prefs
import dev.clawdboard.core.Repository
import dev.clawdboard.core.ScreenMode
import dev.clawdboard.core.Skin
import dev.clawdboard.core.Species
import dev.clawdboard.core.Tint
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: Repository, st: Repository.State, prefs: Prefs, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val now = LocalNow.current

    var pairMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var curPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var newPin2 by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Configurações", color = C.text, fontSize = 28.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Fechar", color = C.clawd, fontSize = 16.sp) }
            }
            st.panelUrl?.let { Text("Painel web: $it  (login com o mesmo PIN)", color = C.muted, fontSize = 14.sp) }

            Section("Claude Code no PC")
            val at = st.lastPushAt
            Text(
                if (at != null) "Último envio ${fmtAgo(now - at)}" else "Nenhum envio ainda",
                color = if (at != null) C.ok else C.warn, fontSize = 15.sp,
            )
            Hint(
                "O uso vem do próprio Claude Code no PC: um hook roda o /usage ao fim das respostas, no VS Code ou no terminal, " +
                    "no máximo a cada 2 minutos e sem gastar tokens. O celular não guarda token nenhum. " +
                    "Para conectar, abra ${st.panelUrl ?: "o painel web"} no PC, entre com o PIN e rode no PowerShell o comando de \"Conectar ao Claude Code\"."
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    pairMsg = when (val r = repo.newPairKey()) {
                        Repository.Outcome.Ok -> true to "Chave nova gerada. Rode o comando do painel de novo no PC."
                        is Repository.Outcome.Error -> false to r.message
                        else -> false to "Não foi possível gerar a chave"
                    }
                }
            }) { Text("Gerar nova chave", color = C.text) }
            pairMsg?.let { (ok, m) -> Text(m, color = if (ok) C.ok else C.bad, fontSize = 14.sp) }

            Section("Tela")
            Label("Modo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenMode.entries.forEach { m -> Chip(m.label, prefs.mode == m) { repo.updateSettings { it.copy(mode = m) } } }
            }
            if (prefs.mode == ScreenMode.CAROUSEL) {
                Label("Tempo em cada tela")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prefs.DWELL_OPTIONS.forEach { d -> Chip("${d}s", prefs.dwellSec == d) { repo.updateSettings { it.copy(dwellSec = d) } } }
                }
            }
            Label("Fundo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Backdrop.entries.forEach { b -> Chip(b.label, prefs.backdrop == b) { repo.updateSettings { it.copy(backdrop = b) } } }
            }
            Label("Brilho")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Brightness.entries.forEach { b -> Chip(b.label, prefs.brightness == b) { repo.updateSettings { it.copy(brightness = b) } } }
            }
            Label("Orientação")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Orientation.entries.forEach { o -> Chip(o.label, prefs.orientation == o) { repo.updateSettings { it.copy(orientation = o) } } }
            }
            Label("Zoom")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Prefs.ZOOM_OPTIONS.forEach { z -> Chip("$z%", prefs.zoom == z) { repo.updateSettings { it.copy(zoom = z) } } }
            }
            Hint("Aumenta textos e mascotes das telas e destes ajustes. Com zoom alto, linhas secundárias somem para caber.")
            Toggle("Mover o conteúdo alguns pixels por minuto", "Protege a tela AMOLED contra marcas", prefs.pixelShift) { v -> repo.updateSettings { it.copy(pixelShift = v) } }
            Toggle("Abrir sozinho quando o celular ligar", null, prefs.autostart) { v -> repo.updateSettings { it.copy(autostart = v) } }
            Toggle("Painel web na rede local", st.panelUrl, prefs.panelEnabled) { v -> repo.updateSettings { it.copy(panelEnabled = v) } }

            Section("Mascotes")
            MascotRow(st.status, Modifier.widthIn(max = 420.dp), usage = st.usage, mascotWidth = 72.dp)
            if (prefs.clawdUnlocked) {
                Label("Mascote")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Species.entries.forEach { m -> Chip(m.label, prefs.species == m) { repo.updateSettings { it.copy(species = m) } } }
                }
            }
            Label("Acessórios")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skin.entries.forEach { s -> Chip(s.label, prefs.skin == s) { repo.updateSettings { it.copy(skin = s) } } }
            }
            if (prefs.skin == Skin.MODELS) Hint("Cartola no Fable, o mais caro. Óculos no Opus, fone no Sonnet e um broto no Haiku.")
            Label("Cor")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tint.entries.forEach { t -> Chip(t.label, prefs.tint == t) { repo.updateSettings { it.copy(tint = t) } } }
            }
            Toggle(
                "Animações",
                "Olham para os lados, mexem as patas e acenam. Dormem com a sessão zerada, suam a partir de 85%, " +
                    "ficam vermelhos a partir de 90% e estouram em 100%. Com a tela de música ligada, dançam enquanto a música toca.",
                prefs.animations,
            ) { v -> repo.updateSettings { it.copy(animations = v) } }

            Section("Música")
            Toggle(
                "Tela de música",
                "Mostra o que está tocando no celular, com play, pausa e troca de faixa. Enquanto a música toca, os mascotes dançam em todas as telas.",
                prefs.music,
            ) { v -> repo.updateSettings { it.copy(music = v) } }
            if (prefs.music) {
                val access by repo.music.access.collectAsStateWithLifecycle()
                if (access) {
                    Spacer(Modifier.height(8.dp))
                    Text("Acesso ao player liberado", color = C.ok, fontSize = 14.sp)
                    if (!prefs.animations) Hint("Com as animações desligadas, os mascotes não dançam.")
                } else {
                    Hint("Para ver o que está tocando, o Android pede acesso às notificações. O Banditboard usa esse acesso só para ler e controlar o player; as notificações não são lidas.")
                    Spacer(Modifier.height(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = C.clawd, contentColor = C.bg),
                        onClick = { repo.music.openAccess(context) },
                    ) { Text("Liberar acesso") }
                    Hint("Se o Android avisar que é uma configuração restrita: Configurações → Apps → Banditboard → ⋮ → Permitir configurações restritas, e tente de novo.")
                }
            }

            Section("Trocar PIN")
            SecretField(curPin, { curPin = it.filter(Char::isDigit).take(8) }, "PIN atual", numeric = true)
            SecretField(newPin, { newPin = it.filter(Char::isDigit).take(8) }, "Novo PIN", numeric = true)
            SecretField(newPin2, { newPin2 = it.filter(Char::isDigit).take(8) }, "Repita o novo PIN", numeric = true)
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = C.card2, contentColor = C.text),
                onClick = {
                    if (newPin != newPin2) {
                        pinMsg = false to "Os PINs novos não conferem"
                        return@Button
                    }
                    scope.launch {
                        pinMsg = when (val r = repo.changePin(curPin, newPin)) {
                            Repository.Outcome.Ok -> { curPin = ""; newPin = ""; newPin2 = ""; true to "PIN trocado" }
                            is Repository.Outcome.WrongPin -> false to "PIN atual incorreto. Restam ${r.remaining} tentativas."
                            is Repository.Outcome.Error -> false to r.message
                            Repository.Outcome.Wiped -> null
                        }
                    }
                },
            ) { Text("Trocar PIN") }
            pinMsg?.let { (ok, m) -> Text(m, color = if (ok) C.ok else C.bad, fontSize = 14.sp) }

            Section("Segurança")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { repo.lock(); onClose() }) { Text("Bloquear agora", color = C.text) }
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = if (confirmReset) C.bad else C.card2, contentColor = C.text),
                    onClick = {
                        if (confirmReset) {
                            repo.factoryReset(); onClose()
                        } else {
                            confirmReset = true
                        }
                    },
                ) { Text(if (confirmReset) "Toque de novo para apagar tudo" else "Apagar tudo") }
            }
            Hint("10 PINs errados seguidos também apagam a chave de pareamento, o histórico e os ajustes.")

            Section("Créditos")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(Modifier.width(72.dp), seed = 99, reserveTop = false)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Banditboard ${BuildConfig.VERSION_NAME}", color = C.text, fontSize = 18.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
                    Text("Criado por Vinícius Pires da Silva", color = C.clawd, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Credit(
                "Mascote",
                if (prefs.mascot() == Species.CLAWD) "Clawd é o mascote do Claude Code, da Anthropic" else "Racco, o guaxinim do Banditboard",
            )
            Credit("Dados", "Claude Code no PC, status.claude.com e o feed Olshansk/rss-feeds")
            Credit("Feedback", Nudge.EMAIL, C.clawd) { sendFeedback(context) }
            Hint("Projeto pessoal de fã, sem vínculo com a Anthropic.")
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    Text(title, color = C.clawd, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = C.muted, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, color = C.dim, fontSize = 13.sp)
}

@Composable
private fun Toggle(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 15.sp)
            desc?.let { Text(it, color = C.dim, fontSize = 13.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = C.bg, checkedTrackColor = C.clawd),
        )
    }
}

@Composable
private fun Credit(title: String, text: String, color: Color = C.text, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 3.dp)) {
        Text(title, color = C.muted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(text, color = color, fontSize = 13.sp)
    }
}
