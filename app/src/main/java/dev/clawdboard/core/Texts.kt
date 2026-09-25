package dev.clawdboard.core

import java.time.DayOfWeek
import java.util.Locale

enum class Language { AUTO, PT, EN }

object I18n {
    @Volatile var language: Language = Language.AUTO
}

fun Language.resolved(): Language = when (this) {
    Language.AUTO -> if (Locale.getDefault().language == "pt") Language.PT else Language.EN
    else -> this
}

fun textsFor(language: Language): Texts = if (language.resolved() == Language.EN) En else Pt

val txt: Texts get() = textsFor(I18n.language)

interface Texts {
    val code: String
    val settings: String
    val close: String
    val cancel: String
    val back: String
    val save: String
    val saving: String
    val tagline: String

    val now: String
    fun weekShort(d: DayOfWeek): String
    fun weekLong(d: DayOfWeek): String
    fun month(i: Int): String
    fun monthShort(i: Int): String
    fun todayAt(hm: String): String
    fun tomorrowAt(hm: String): String
    fun dayAt(d: DayOfWeek, day: Int, month: Int, hm: String): String
    fun agoSec(n: Long): String
    fun agoMin(n: Long): String
    fun agoHour(n: Long): String
    fun dateLong(d: DayOfWeek, day: Int, month: Int): String
    fun dateShort(day: Int, month: Int): String

    val resetsIn: String
    val waitingData: String
    val noResetScheduled: String
    val noReset: String
    val checkingStatus: String
    val allOperational: String

    val session: String
    val sessionWindow: String
    val week: String
    val weekWindow: String
    val hours5: String
    val days7: String
    fun updatedAgo(ago: String): String
    val waitingClaude: String
    fun panelShort(url: String): String
    val last7Days: String
    val legendSession: String
    val legendWeek: String
    fun peak5h(p: String): String
    fun weekNow(p: String): String
    val noSamples: String
    fun samples(n: Int): String
    val loadingNews: String
    val unstable: String
    val exhausted: String

    val lockedTitle: String
    val enterPin: String
    fun triesBeforeWipe(n: Int): String
    fun wrongPin(n: Int): String
    fun orLoginPanel(url: String): String
    val confirmPin: String
    val wipedNotice: String
    val step1: String
    val connectWifi: String
    val step2: String
    val step3: String
    val pinHere: String
    val setupTitle: String
    val setupHint: String
    val pinField: String
    val repeatPin: String
    val pinsDontMatch: String

    val needAccess: String
    val needAccessHint: String
    val nothingPlaying: String
    val nothingPlayingHint: String
    val cover: String
    val untitled: String
    val volume: String
    val prevTrack: String
    val pause: String
    val play: String
    val nextTrack: String
    val open: String
    val paused: String
    val demoArtist: String

    val enjoying: String
    fun feedbackAsk(email: String): String
    val sendEmail: String
    val notNow: String
    val dontShowAgain: String

    fun webPanel(url: String): String
    val claudeOnPc: String
    fun lastPush(ago: String): String
    val noPushYet: String
    fun usageExplain(url: String?): String
    val newKeyDone: String
    val newKeyFailed: String
    val newKey: String
    val screen: String
    val mode: String
    val dwell: String
    val backdrop: String
    val brightness: String
    val orientation: String
    val zoom: String
    val zoomHint: String
    val pixelShift: String
    val pixelShiftHint: String
    val autostart: String
    val panelToggle: String
    val language: String
    val mascots: String
    val mascot: String
    val accessories: String
    val modelsHint: String
    val color: String
    val animations: String
    val animationsHint: String
    val music: String
    val musicScreen: String
    val musicScreenHint: String
    val playerAccessOk: String
    val noDanceWithoutAnimations: String
    val accessWhy: String
    val grantAccess: String
    val restrictedHint: String
    val changePin: String
    val currentPin: String
    val newPin: String
    val repeatNewPin: String
    val newPinsDontMatch: String
    val pinChanged: String
    fun wrongCurrentPin(n: Int): String
    val security: String
    val lockNow: String
    val tapAgainToErase: String
    val eraseAll: String
    val wipeHint: String
    val credits: String
    val createdBy: String
    val mascotClawd: String
    val mascotRacco: String
    val whyRaccoon: String
    val data: String
    val dataSources: String
    val feedback: String
    val fanProject: String

    fun label(m: ScreenMode): String
    fun label(b: Backdrop): String
    fun label(b: Brightness): String
    fun label(o: Orientation): String
    fun label(s: Skin): String
    fun label(s: Species): String
    fun label(t: Tint): String
    fun label(l: Language): String

    val noInternet: String
    val timeout: String
    fun tlsFailed(msg: String): String
    val emptyVault: String
    fun keystoreUnavailable(name: String): String
    val pinLength: String
    val pinDigits: String
    val incident: String
    val notConfigured: String
    val alreadyConfigured: String
    val unlockFirst: String
    val bodyTooLarge: String
    val hostNotAllowed: String
    val badPairKeyScript: String
    val notFound: String
    val missingHeader: String
    val badJson: String
    val badPairKey: String
    val noRateLimits: String
    val setupFailed: String
    val wrongPinPanel: String
    val wipedPanel: String
    val loginFirst: String
    val keyFailed: String
    val wrongCurrentPinPanel: String
    val eraseWord: String
    fun typeToConfirm(word: String): String
    val unknownRoute: String
    val installConnected: String
    fun installBackup(path: String): String
    fun installEvery(url: String): String
}

private fun languageLabel(l: Language, auto: String) = when (l) {
    Language.AUTO -> auto
    Language.PT -> "Português"
    Language.EN -> "English"
}

object Pt : Texts {
    override val code = "pt"
    override val settings = "Configurações"
    override val close = "Fechar"
    override val cancel = "Cancelar"
    override val back = "Voltar"
    override val save = "Salvar"
    override val saving = "Salvando..."
    override val tagline = "uso do Claude na sua mesa"

    private val weekShort = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    private val weekLong = listOf("segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado", "domingo")
    private val months = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    override val now = "agora"
    override fun weekShort(d: DayOfWeek) = weekShort[d.value - 1]
    override fun weekLong(d: DayOfWeek) = weekLong[d.value - 1]
    override fun month(i: Int) = months[i - 1]
    override fun monthShort(i: Int) = months[i - 1].take(3)
    override fun todayAt(hm: String) = "hoje às $hm"
    override fun tomorrowAt(hm: String) = "amanhã às $hm"
    override fun dayAt(d: DayOfWeek, day: Int, month: Int, hm: String) = "${weekShort(d)} $day/%02d às $hm".format(month)
    override fun agoSec(n: Long) = "há ${n}s"
    override fun agoMin(n: Long) = "há $n min"
    override fun agoHour(n: Long) = "há ${n}h"
    override fun dateLong(d: DayOfWeek, day: Int, month: Int) = "${weekLong(d)}, $day de ${month(month)}"
    override fun dateShort(day: Int, month: Int) = "$day ${monthShort(month)}"

    override val resetsIn = "libera em"
    override val waitingData = "aguardando dados"
    override val noResetScheduled = "sem reset agendado"
    override val noReset = "sem reset"
    override val checkingStatus = "Verificando status.claude.com..."
    override val allOperational = "Todos os sistemas operacionais"

    override val session = "Sessão"
    override val sessionWindow = "janela de 5 horas"
    override val week = "Semana"
    override val weekWindow = "janela de 7 dias"
    override val hours5 = "5 horas"
    override val days7 = "7 dias"
    override fun updatedAgo(ago: String) = "atualizado $ago · Claude Code"
    override val waitingClaude = "aguardando o Claude Code · conecte pelo painel no PC"
    override fun panelShort(url: String) = "painel: $url"
    override val last7Days = "Últimos 7 dias"
    override val legendSession = "sessão 5h"
    override val legendWeek = "semana 7d"
    override fun peak5h(p: String) = "pico 5h: $p"
    override fun weekNow(p: String) = "semana agora: $p"
    override val noSamples = "sem amostras ainda, uma a cada 30 min"
    override fun samples(n: Int) = "$n de 336 amostras"
    override val loadingNews = "Carregando notícias..."
    override val unstable = "instável"
    override val exhausted = "esgotado"

    override val lockedTitle = "Banditboard bloqueado"
    override val enterPin = "Digite o PIN para liberar o painel"
    override fun triesBeforeWipe(n: Int) = "Restam $n tentativas antes de apagar tudo"
    override fun wrongPin(n: Int) = "PIN incorreto. Restam $n tentativas."
    override fun orLoginPanel(url: String) = "ou faça login no painel: $url"
    override val confirmPin = "Confirme o PIN"
    override val wipedNotice = "O PIN foi errado 10 vezes ou o aparelho foi resetado. A chave de pareamento e o histórico foram apagados."
    override val step1 = "Abra no navegador do PC:"
    override val connectWifi = "conecte o celular ao Wi-Fi..."
    override val step2 = "Crie um PIN."
    override val step3 = "Rode no PowerShell o comando que aparece. O Claude Code passa a mandar o uso para cá."
    override val pinHere = "Prefiro criar o PIN aqui no celular"
    override val setupTitle = "Configurar"
    override val setupHint = "O PIN protege os ajustes e o painel web. Depois, conecte o Claude Code pelo painel no PC."
    override val pinField = "PIN (4 a 8 dígitos)"
    override val repeatPin = "Repita o PIN"
    override val pinsDontMatch = "Os PINs não conferem"

    override val needAccess = "Falta liberar o acesso"
    override val needAccessHint = "Em Configurações → Música, toque em \"Liberar acesso\". O Android pede acesso às notificações para mostrar o que está tocando."
    override val nothingPlaying = "Nada tocando"
    override val nothingPlayingHint = "Dê play no Spotify, no YouTube Music ou em outro app de música. Os mascotes dançam junto."
    override val cover = "Capa"
    override val untitled = "Sem título"
    override val volume = "Volume"
    override val prevTrack = "Faixa anterior"
    override val pause = "Pausar"
    override val play = "Tocar"
    override val nextTrack = "Próxima faixa"
    override val open = "abrir"
    override val paused = "pausado"
    override val demoArtist = "Racco e os Modelos"

    override val enjoying = "Está gostando do Banditboard?"
    override fun feedbackAsk(email: String) = "Feedbacks, sugestões ou quer apoiar? Escreve para $email"
    override val sendEmail = "Mandar e-mail"
    override val notNow = "Agora não"
    override val dontShowAgain = "Não mostrar mais"

    override fun webPanel(url: String) = "Painel web: $url  (login com o mesmo PIN)"
    override val claudeOnPc = "Claude Code no PC"
    override fun lastPush(ago: String) = "Último envio $ago"
    override val noPushYet = "Nenhum envio ainda"
    override fun usageExplain(url: String?) =
        "O uso vem do próprio Claude Code no PC: um hook roda o /usage ao fim das respostas, no VS Code ou no terminal, " +
            "no máximo a cada 2 minutos e sem gastar tokens. O celular não guarda token nenhum. " +
            "Para conectar, abra ${url ?: "o painel web"} no PC, entre com o PIN e rode no PowerShell o comando do cartão \"Claude Code no PC\"."
    override val newKeyDone = "Chave nova gerada. Rode o comando do painel de novo no PC."
    override val newKeyFailed = "Não foi possível gerar a chave"
    override val newKey = "Gerar nova chave"
    override val screen = "Tela"
    override val mode = "Modo"
    override val dwell = "Tempo em cada tela"
    override val backdrop = "Fundo"
    override val brightness = "Brilho"
    override val orientation = "Orientação"
    override val zoom = "Zoom"
    override val zoomHint = "Aumenta textos e mascotes das telas e destes ajustes. Com zoom alto, linhas secundárias somem para caber."
    override val pixelShift = "Mover o conteúdo alguns pixels por minuto"
    override val pixelShiftHint = "Protege a tela AMOLED contra marcas"
    override val autostart = "Abrir sozinho quando o celular ligar"
    override val panelToggle = "Painel web na rede local"
    override val language = "Idioma"
    override val mascots = "Mascotes"
    override val mascot = "Mascote"
    override val accessories = "Acessórios"
    override val modelsHint = "Cartola no Fable, o mais caro. Óculos no Opus, fone no Sonnet e um broto no Haiku."
    override val color = "Cor"
    override val animations = "Animações"
    override val animationsHint = "Olham para os lados, mexem as patas e acenam. Dormem com a sessão zerada, suam a partir de 85%, " +
        "ficam vermelhos a partir de 90% e estouram em 100%. Com a tela de música ligada, dançam enquanto a música toca."
    override val music = "Música"
    override val musicScreen = "Tela de música"
    override val musicScreenHint = "Mostra o que está tocando no celular, com play, pausa e troca de faixa. Enquanto a música toca, os mascotes dançam em todas as telas."
    override val playerAccessOk = "Acesso ao player liberado"
    override val noDanceWithoutAnimations = "Com as animações desligadas, os mascotes não dançam."
    override val accessWhy = "Para ver o que está tocando, o Android pede acesso às notificações. O Banditboard usa esse acesso só para ler e controlar o player; as notificações não são lidas."
    override val grantAccess = "Liberar acesso"
    override val restrictedHint = "Se o Android avisar que é uma configuração restrita: Configurações → Apps → Banditboard → ⋮ → Permitir configurações restritas, e tente de novo."
    override val changePin = "Trocar PIN"
    override val currentPin = "PIN atual"
    override val newPin = "Novo PIN"
    override val repeatNewPin = "Repita o novo PIN"
    override val newPinsDontMatch = "Os PINs novos não conferem"
    override val pinChanged = "PIN trocado"
    override fun wrongCurrentPin(n: Int) = "PIN atual incorreto. Restam $n tentativas."
    override val security = "Segurança"
    override val lockNow = "Bloquear agora"
    override val tapAgainToErase = "Toque de novo para apagar tudo"
    override val eraseAll = "Apagar tudo"
    override val wipeHint = "10 PINs errados seguidos também apagam a chave de pareamento, o histórico e os ajustes."
    override val credits = "Créditos"
    override val createdBy = "Criado por Vinícius Pires da Silva"
    override val mascotClawd = "Clawd é o mascote do Claude Code, da Anthropic"
    override val mascotRacco = "Racco, o guaxinim do Banditboard"
    override val whyRaccoon = "Por que um guaxinim? Porque ele vive espiando e se esgueirando, sempre de olho nos seus limites. " +
        "E é uma pequena homenagem à minha linda futura esposa, que se identifica com guaxinins: apesar dela dizer que é um, ela não é."
    override val data = "Dados"
    override val dataSources = "Claude Code no PC, status.claude.com e o feed Olshansk/rss-feeds"
    override val feedback = "Feedback"
    override val fanProject = "Projeto pessoal de fã, sem vínculo com a Anthropic."

    override fun label(m: ScreenMode) = when (m) {
        ScreenMode.STATIC -> "Estático"
        ScreenMode.MASCOTS -> "Mascotes"
        ScreenMode.CAROUSEL -> "Carrossel"
        ScreenMode.CLOCK -> "Relógio"
    }
    override fun label(b: Backdrop) = when (b) {
        Backdrop.DEFAULT -> "Tema padrão"
        Backdrop.BLACK -> "Preto AMOLED"
    }
    override fun label(b: Brightness) = when (b) {
        Brightness.SYSTEM -> "Sistema"
        Brightness.LOW -> "Baixo"
        Brightness.MEDIUM -> "Médio"
        Brightness.HIGH -> "Alto"
    }
    override fun label(o: Orientation) = when (o) {
        Orientation.LANDSCAPE -> "Paisagem"
        Orientation.PORTRAIT -> "Retrato"
        Orientation.AUTO -> "Automática"
    }
    override fun label(s: Skin) = when (s) {
        Skin.CLASSIC -> "Clássico"
        Skin.MODELS -> "Por modelo"
        Skin.CROWNS -> "Coroas"
        Skin.XMAS -> "Natal"
    }
    override fun label(s: Species) = when (s) {
        Species.RACCOON -> "Racco"
        Species.CLAWD -> "Clawd"
    }
    override fun label(t: Tint) = when (t) {
        Tint.NATURAL -> "Natural"
        Tint.RAINBOW -> "Arco-íris"
        Tint.LAVENDER -> "Lavanda"
        Tint.MINT -> "Menta"
        Tint.BUBBLEGUM -> "Chiclete"
    }
    override fun label(l: Language) = languageLabel(l, "Automático")

    override val noInternet = "Sem internet (DNS falhou)"
    override val timeout = "Tempo esgotado na conexão"
    override fun tlsFailed(msg: String) = "Falha TLS: $msg"
    override val emptyVault = "cofre vazio"
    override fun keystoreUnavailable(name: String) = "Chave do Keystore indisponível: $name"
    override val pinLength = "O PIN precisa ter de 4 a 8 dígitos"
    override val pinDigits = "O PIN só pode ter números"
    override val incident = "Incidente"
    override val notConfigured = "O Banditboard ainda não foi configurado"
    override val alreadyConfigured = "O Banditboard já está configurado. Faça login com o PIN."
    override val unlockFirst = "Desbloqueie com o PIN primeiro"
    override val bodyTooLarge = "Corpo grande demais"
    override val hostNotAllowed = "Host não permitido"
    override val badPairKeyScript = "Chave do Banditboard inválida. Copie o comando de novo no painel."
    override val notFound = "Não encontrado"
    override val missingHeader = "Requisição sem cabeçalho do painel"
    override val badJson = "JSON inválido"
    override val badPairKey = "Chave de pareamento inválida"
    override val noRateLimits = "Sem rate_limits"
    override val setupFailed = "Falha na configuração"
    override val wrongPinPanel = "PIN incorreto"
    override val wipedPanel = "10 PINs errados: o aparelho foi apagado"
    override val loginFirst = "Faça login com o PIN"
    override val keyFailed = "Falha ao gerar a chave"
    override val wrongCurrentPinPanel = "PIN atual incorreto"
    override val eraseWord = "APAGAR"
    override fun typeToConfirm(word: String) = "Digite $word para confirmar"
    override val unknownRoute = "Rota desconhecida"
    override val installConnected = "Banditboard conectado ao Claude Code."
    override fun installBackup(path: String) = "Backup dos seus ajustes: $path"
    override fun installEvery(url: String) = "A cada resposta do Claude Code (VS Code ou terminal) o uso vai para o celular, no maximo a cada 2 minutos ($url)."
}

object En : Texts {
    override val code = "en"
    override val settings = "Settings"
    override val close = "Close"
    override val cancel = "Cancel"
    override val back = "Back"
    override val save = "Save"
    override val saving = "Saving..."
    override val tagline = "your Claude usage on your desk"

    private val weekShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val weekLong = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    override val now = "now"
    override fun weekShort(d: DayOfWeek) = weekShort[d.value - 1]
    override fun weekLong(d: DayOfWeek) = weekLong[d.value - 1]
    override fun month(i: Int) = months[i - 1]
    override fun monthShort(i: Int) = months[i - 1].take(3)
    override fun todayAt(hm: String) = "today at $hm"
    override fun tomorrowAt(hm: String) = "tomorrow at $hm"
    override fun dayAt(d: DayOfWeek, day: Int, month: Int, hm: String) = "${weekShort(d)}, ${monthShort(month)} $day at $hm"
    override fun agoSec(n: Long) = "${n}s ago"
    override fun agoMin(n: Long) = "$n min ago"
    override fun agoHour(n: Long) = "${n}h ago"
    override fun dateLong(d: DayOfWeek, day: Int, month: Int) = "${weekLong(d)}, ${month(month)} $day"
    override fun dateShort(day: Int, month: Int) = "${monthShort(month)} $day"

    override val resetsIn = "resets in"
    override val waitingData = "waiting for data"
    override val noResetScheduled = "no reset scheduled"
    override val noReset = "no reset"
    override val checkingStatus = "Checking status.claude.com..."
    override val allOperational = "All systems operational"

    override val session = "Session"
    override val sessionWindow = "5-hour window"
    override val week = "Week"
    override val weekWindow = "7-day window"
    override val hours5 = "5 hours"
    override val days7 = "7 days"
    override fun updatedAgo(ago: String) = "updated $ago · Claude Code"
    override val waitingClaude = "waiting for Claude Code · connect it from the dashboard on your PC"
    override fun panelShort(url: String) = "dashboard: $url"
    override val last7Days = "Last 7 days"
    override val legendSession = "5h session"
    override val legendWeek = "7d week"
    override fun peak5h(p: String) = "5h peak: $p"
    override fun weekNow(p: String) = "week now: $p"
    override val noSamples = "no samples yet, one every 30 min"
    override fun samples(n: Int) = "$n of 336 samples"
    override val loadingNews = "Loading news..."
    override val unstable = "unstable"
    override val exhausted = "maxed out"

    override val lockedTitle = "Banditboard locked"
    override val enterPin = "Enter the PIN to unlock the dashboard"
    override fun triesBeforeWipe(n: Int) = "$n tries left before everything is erased"
    override fun wrongPin(n: Int) = "Wrong PIN. $n tries left."
    override fun orLoginPanel(url: String) = "or log in on the dashboard: $url"
    override val confirmPin = "Confirm your PIN"
    override val wipedNotice = "The PIN was wrong 10 times or the device was reset. The pairing key and the history were erased."
    override val step1 = "Open this in your PC browser:"
    override val connectWifi = "connect the phone to Wi-Fi..."
    override val step2 = "Create a PIN."
    override val step3 = "Run the command it shows in PowerShell. Claude Code starts sending your usage here."
    override val pinHere = "I'd rather create the PIN on this phone"
    override val setupTitle = "Set up"
    override val setupHint = "The PIN protects the settings and the web dashboard. Then connect Claude Code from the dashboard on your PC."
    override val pinField = "PIN (4 to 8 digits)"
    override val repeatPin = "Repeat the PIN"
    override val pinsDontMatch = "The PINs don't match"

    override val needAccess = "Access not granted yet"
    override val needAccessHint = "In Settings → Music, tap \"Grant access\". Android asks for notification access to show what is playing."
    override val nothingPlaying = "Nothing playing"
    override val nothingPlayingHint = "Press play on Spotify, YouTube Music or any other music app. The mascots dance along."
    override val cover = "Cover"
    override val untitled = "Untitled"
    override val volume = "Volume"
    override val prevTrack = "Previous track"
    override val pause = "Pause"
    override val play = "Play"
    override val nextTrack = "Next track"
    override val open = "open"
    override val paused = "paused"
    override val demoArtist = "Racco and the Models"

    override val enjoying = "Enjoying Banditboard?"
    override fun feedbackAsk(email: String) = "Feedback, ideas or want to support it? Write to $email"
    override val sendEmail = "Send email"
    override val notNow = "Not now"
    override val dontShowAgain = "Don't show again"

    override fun webPanel(url: String) = "Web dashboard: $url  (log in with the same PIN)"
    override val claudeOnPc = "Claude Code on your PC"
    override fun lastPush(ago: String) = "Last push $ago"
    override val noPushYet = "No push yet"
    override fun usageExplain(url: String?) =
        "Usage comes from Claude Code itself on your PC: a hook runs /usage after responses, in VS Code or in the terminal, " +
            "at most every 2 minutes and without using tokens. The phone holds no token at all. " +
            "To connect, open ${url ?: "the web dashboard"} on your PC, log in with the PIN and run the command from the \"Claude Code on your PC\" card in PowerShell."
    override val newKeyDone = "New key created. Run the dashboard command on your PC again."
    override val newKeyFailed = "Could not create the key"
    override val newKey = "Create new key"
    override val screen = "Screen"
    override val mode = "Mode"
    override val dwell = "Time on each screen"
    override val backdrop = "Background"
    override val brightness = "Brightness"
    override val orientation = "Orientation"
    override val zoom = "Zoom"
    override val zoomHint = "Makes text and mascots bigger on the screens and in these settings. At high zoom, secondary lines hide to fit."
    override val pixelShift = "Shift the content a few pixels every minute"
    override val pixelShiftHint = "Protects AMOLED screens against burn-in"
    override val autostart = "Open automatically when the phone starts"
    override val panelToggle = "Web dashboard on the local network"
    override val language = "Language"
    override val mascots = "Mascots"
    override val mascot = "Mascot"
    override val accessories = "Accessories"
    override val modelsHint = "Top hat on Fable, the most expensive one. Glasses on Opus, headphones on Sonnet and a sprout on Haiku."
    override val color = "Color"
    override val animations = "Animations"
    override val animationsHint = "They look around, move their paws and wave. They sleep when the session is empty, sweat from 85%, " +
        "turn red from 90% and burst at 100%. With the music screen on, they dance while music plays."
    override val music = "Music"
    override val musicScreen = "Music screen"
    override val musicScreenHint = "Shows what is playing on the phone, with play, pause and track controls. While music plays, the mascots dance on every screen."
    override val playerAccessOk = "Player access granted"
    override val noDanceWithoutAnimations = "With animations off, the mascots don't dance."
    override val accessWhy = "To see what is playing, Android asks for notification access. Banditboard only uses it to read and control the player; your notifications are not read."
    override val grantAccess = "Grant access"
    override val restrictedHint = "If Android says it is a restricted setting: Settings → Apps → Banditboard → ⋮ → Allow restricted settings, and try again."
    override val changePin = "Change PIN"
    override val currentPin = "Current PIN"
    override val newPin = "New PIN"
    override val repeatNewPin = "Repeat the new PIN"
    override val newPinsDontMatch = "The new PINs don't match"
    override val pinChanged = "PIN changed"
    override fun wrongCurrentPin(n: Int) = "Wrong current PIN. $n tries left."
    override val security = "Security"
    override val lockNow = "Lock now"
    override val tapAgainToErase = "Tap again to erase everything"
    override val eraseAll = "Erase everything"
    override val wipeHint = "10 wrong PINs in a row also erase the pairing key, the history and the settings."
    override val credits = "Credits"
    override val createdBy = "Created by Vinícius Pires da Silva"
    override val mascotClawd = "Clawd is the Claude Code mascot, by Anthropic"
    override val mascotRacco = "Racco, the Banditboard raccoon"
    override val whyRaccoon = "Why a raccoon? Because it is always peeking and sneaking around, keeping an eye on your limits. " +
        "It is also a small tribute to my lovely future wife, who relates to raccoons: even though she says she is one, she is not."
    override val data = "Data"
    override val dataSources = "Claude Code on your PC, status.claude.com and the Olshansk/rss-feeds feed"
    override val feedback = "Feedback"
    override val fanProject = "Personal fan project, not affiliated with Anthropic."

    override fun label(m: ScreenMode) = when (m) {
        ScreenMode.STATIC -> "Static"
        ScreenMode.MASCOTS -> "Mascots"
        ScreenMode.CAROUSEL -> "Carousel"
        ScreenMode.CLOCK -> "Clock"
    }
    override fun label(b: Backdrop) = when (b) {
        Backdrop.DEFAULT -> "Default theme"
        Backdrop.BLACK -> "AMOLED black"
    }
    override fun label(b: Brightness) = when (b) {
        Brightness.SYSTEM -> "System"
        Brightness.LOW -> "Low"
        Brightness.MEDIUM -> "Medium"
        Brightness.HIGH -> "High"
    }
    override fun label(o: Orientation) = when (o) {
        Orientation.LANDSCAPE -> "Landscape"
        Orientation.PORTRAIT -> "Portrait"
        Orientation.AUTO -> "Automatic"
    }
    override fun label(s: Skin) = when (s) {
        Skin.CLASSIC -> "Classic"
        Skin.MODELS -> "Per model"
        Skin.CROWNS -> "Crowns"
        Skin.XMAS -> "Christmas"
    }
    override fun label(s: Species) = when (s) {
        Species.RACCOON -> "Racco"
        Species.CLAWD -> "Clawd"
    }
    override fun label(t: Tint) = when (t) {
        Tint.NATURAL -> "Natural"
        Tint.RAINBOW -> "Rainbow"
        Tint.LAVENDER -> "Lavender"
        Tint.MINT -> "Mint"
        Tint.BUBBLEGUM -> "Bubblegum"
    }
    override fun label(l: Language) = languageLabel(l, "Automatic")

    override val noInternet = "No internet (DNS failed)"
    override val timeout = "Connection timed out"
    override fun tlsFailed(msg: String) = "TLS failure: $msg"
    override val emptyVault = "empty vault"
    override fun keystoreUnavailable(name: String) = "Keystore key unavailable: $name"
    override val pinLength = "The PIN must have 4 to 8 digits"
    override val pinDigits = "The PIN can only have numbers"
    override val incident = "Incident"
    override val notConfigured = "Banditboard is not set up yet"
    override val alreadyConfigured = "Banditboard is already set up. Log in with the PIN."
    override val unlockFirst = "Unlock with the PIN first"
    override val bodyTooLarge = "Body too large"
    override val hostNotAllowed = "Host not allowed"
    override val badPairKeyScript = "Invalid Banditboard key. Copy the command from the dashboard again."
    override val notFound = "Not found"
    override val missingHeader = "Request without the dashboard header"
    override val badJson = "Invalid JSON"
    override val badPairKey = "Invalid pairing key"
    override val noRateLimits = "No rate_limits"
    override val setupFailed = "Setup failed"
    override val wrongPinPanel = "Wrong PIN"
    override val wipedPanel = "10 wrong PINs: the device was erased"
    override val loginFirst = "Log in with the PIN"
    override val keyFailed = "Could not create the key"
    override val wrongCurrentPinPanel = "Wrong current PIN"
    override val eraseWord = "ERASE"
    override fun typeToConfirm(word: String) = "Type $word to confirm"
    override val unknownRoute = "Unknown route"
    override val installConnected = "Banditboard connected to Claude Code."
    override fun installBackup(path: String) = "Backup of your settings: $path"
    override fun installEvery(url: String) = "After each Claude Code response (VS Code or terminal) the usage goes to the phone, at most every 2 minutes ($url)."
}
