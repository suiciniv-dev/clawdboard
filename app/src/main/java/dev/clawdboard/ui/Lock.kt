package dev.clawdboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.clawdboard.core.Repository
import kotlinx.coroutines.launch

@Composable
fun PinPad(
    title: String,
    subtitle: String?,
    message: String?,
    messageIsError: Boolean,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    footer: String? = null,
) {
    var pin by remember { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
        val landscape = maxWidth > maxHeight
        val info: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 380.dp)) {
                Clawd(Modifier.width(92.dp), seed = 42, reserveTop = false)
                Spacer(Modifier.height(16.dp))
                Text(title, color = C.text, fontSize = 26.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                subtitle?.let { Text(it, color = C.muted, fontSize = 15.sp, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(18.dp))
                PinDots(pin.length)
                Spacer(Modifier.height(14.dp))
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = C.clawd, strokeWidth = 2.dp)
                } else if (message != null) {
                    Text(message, color = if (messageIsError) C.bad else C.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                footer?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = C.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
        val pad: @Composable () -> Unit = {
            Keypad(
                onDigit = { if (pin.length < 8 && !busy) pin += it },
                onBack = { pin = pin.dropLast(1) },
                onOk = {
                    if (pin.length >= 4 && !busy) {
                        val p = pin
                        pin = ""
                        onSubmit(p)
                    }
                },
            )
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                info(); pad()
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                info(); Spacer(Modifier.height(28.dp)); pad()
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart)) {
                Text("Cancelar", color = C.muted)
            }
        }
    }
}

@Composable
private fun PinDots(len: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxOf(4, len)) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < len) C.clawd else C.card2)
                    .border(1.dp, if (i < len) C.clawd else C.line, CircleShape)
            )
        }
    }
}

@Composable
private fun Keypad(onDigit: (String) -> Unit, onBack: () -> Unit, onOk: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("<", "0", "OK"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { k ->
                    val isAction = k == "<" || k == "OK"
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(if (k == "OK") C.clawd.copy(alpha = 0.2f) else C.card2)
                            .clickable {
                                when (k) {
                                    "<" -> onBack()
                                    "OK" -> onOk()
                                    else -> onDigit(k)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (k == "<") "⌫" else k,
                            color = if (k == "OK") C.clawd else C.text,
                            fontSize = if (isAction) 20.sp else 28.sp,
                            fontFamily = Fredoka,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(repo: Repository, st: Repository.State) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val remaining = 10 - st.failures
    PinPad(
        title = "Clawdboard bloqueado",
        subtitle = "Digite o PIN para liberar o token",
        message = msg ?: if (st.failures > 0) "Restam $remaining tentativas antes de apagar tudo" else null,
        messageIsError = msg != null || st.failures > 0,
        busy = busy,
        onSubmit = { pin ->
            busy = true
            scope.launch {
                msg = when (val r = repo.unlock(pin)) {
                    Repository.Outcome.Ok -> null
                    is Repository.Outcome.WrongPin -> "PIN incorreto. Restam ${r.remaining} tentativas."
                    Repository.Outcome.Wiped -> null
                    is Repository.Outcome.Error -> r.message
                }
                busy = false
            }
        },
        footer = st.panelUrl?.let { "ou faça login no painel: $it" },
    )
}

@Composable
fun PinGate(repo: Repository, st: Repository.State, onOk: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    PinPad(
        title = "Configurações",
        subtitle = "Confirme o PIN",
        message = msg,
        messageIsError = true,
        busy = busy,
        onSubmit = { pin ->
            busy = true
            scope.launch {
                when (val r = repo.unlock(pin)) {
                    Repository.Outcome.Ok -> onOk()
                    is Repository.Outcome.WrongPin -> msg = "PIN incorreto. Restam ${r.remaining} tentativas."
                    Repository.Outcome.Wiped -> Unit
                    is Repository.Outcome.Error -> msg = r.message
                }
                busy = false
            }
        },
        onCancel = onCancel,
    )
}

@Composable
fun SetupScreen(repo: Repository, st: Repository.State) {
    var local by remember { mutableStateOf(false) }
    if (local) {
        SetupForm(repo, onBack = { local = false })
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val landscape = maxWidth > maxHeight
        val hero: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Clawd(Modifier.width(150.dp), seed = 7, reserveTop = false)
                Spacer(Modifier.height(18.dp))
                Text("Clawdboard", color = C.text, fontSize = 38.sp, fontFamily = Fredoka, fontWeight = FontWeight.Bold)
                Text("uso do Claude na sua mesa", color = C.muted, fontSize = 15.sp)
            }
        }
        val steps: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 460.dp)) {
                if (st.wiped) {
                    Text(
                        "O PIN foi errado 10 vezes ou o aparelho foi resetado. O token e o histórico foram apagados.",
                        color = C.bad, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Step("1", "No PC, rode no terminal:", "claude setup-token")
                Step("2", "Abra no navegador do PC:", st.panelUrl ?: "conecte o celular ao Wi-Fi...")
                Step("3", "Crie um PIN e cole o token.", null)
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { local = true }) {
                    Text("Prefiro configurar aqui no celular", color = C.clawd, fontSize = 15.sp)
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                hero(); steps()
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                hero(); Spacer(Modifier.height(32.dp)); steps()
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String, code: String?) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(C.clawd.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(n, color = C.clawd, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text, color = C.text, fontSize = 16.sp)
            code?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it, color = C.clawd, fontSize = 22.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.card2).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupForm(repo: Repository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Voltar", color = C.muted) }
            Text("Configurar", color = C.text, fontSize = 28.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
            Text("O token fica cifrado com AES-256-GCM e só abre com o PIN.", color = C.muted, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            SecretField(pin, { pin = it.filter(Char::isDigit).take(8) }, "PIN (4 a 8 dígitos)", numeric = true)
            SecretField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, "Repita o PIN", numeric = true)
            SecretField(token, { token = it.trim() }, "Token (sk-ant-oat...)")
            TextButton(onClick = { clipboard.getText()?.text?.trim()?.let { token = it } }) {
                Text("Colar da área de transferência", color = C.clawd)
            }
            Spacer(Modifier.height(8.dp))
            error?.let { Text(it, color = C.bad, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
            Button(
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = C.clawd, contentColor = C.bg),
                onClick = {
                    if (pin != pin2) {
                        error = "Os PINs não conferem"
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        val r = repo.provision(pin, token)
                        if (r is Repository.Outcome.Error) error = r.message
                        busy = false
                    }
                },
            ) {
                Text(if (busy) "Verificando com a API..." else "Verificar e salvar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SecretField(value: String, onChange: (String) -> Unit, label: String, numeric: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
