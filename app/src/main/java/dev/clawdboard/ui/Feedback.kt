package dev.clawdboard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.clawdboard.BuildConfig
import dev.clawdboard.core.Nudge
import dev.clawdboard.core.Repository
import dev.clawdboard.core.txt
import kotlinx.coroutines.delay

fun sendFeedback(context: Context) {
    val uri = Uri.parse("mailto:${Nudge.EMAIL}?subject=" + Uri.encode("Banditboard ${BuildConfig.VERSION_NAME}"))
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackCard(repo: Repository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!visible && repo.nudge.due(System.currentTimeMillis())) visible = true
            delay(60_000)
        }
    }
    if (visible) {
        LaunchedEffect(Unit) {
            delay(30_000)
            repo.nudge.later(System.currentTimeMillis())
            visible = false
        }
    }
    val shape = RoundedCornerShape(18.dp)
    AnimatedVisibility(
        visible, modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 48.dp)
                .widthIn(max = 640.dp)
                .clip(shape)
                .background(C.card)
                .border(1.dp, C.line, shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(Modifier.width(60.dp), seed = 5, reserveTop = false)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(txt.enjoying, color = C.text, fontSize = 18.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
                Text(txt.feedbackAsk(Nudge.EMAIL), color = C.muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = C.clawd, contentColor = C.bg),
                        onClick = {
                            repo.nudge.wrote(System.currentTimeMillis())
                            visible = false
                            sendFeedback(context)
                        },
                    ) { Text(txt.sendEmail) }
                    TextButton(onClick = { repo.nudge.later(System.currentTimeMillis()); visible = false }) { Text(txt.notNow, color = C.muted) }
                    TextButton(onClick = { repo.nudge.never(); visible = false }) { Text(txt.dontShowAgain, color = C.dim) }
                }
            }
        }
    }
}
