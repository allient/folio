package com.allient.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ToolDialog(title: String, onDismiss: () -> Unit, expanded: Boolean = false,
                        footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val availableHeight = LocalConfiguration.current.screenHeightDp.dp * .94f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = !expanded)) {
        Surface(modifier = Modifier.semantics { testTagsAsResourceId = true }, shape = RoundedCornerShape(28.dp), color = androidx.compose.ui.graphics.Color.White) {
            Column(Modifier.widthIn(max = if (expanded) 500.dp else 440.dp)
                .then(if (expanded) Modifier.fillMaxWidth(.94f).height(availableHeight) else Modifier.heightIn(max = minOf(720.dp, availableHeight)))) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    InkToolButton(InkIcon.Close, "Close $title", onDismiss)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ChromeLine))
                Column(Modifier.weight(1f, fill = expanded).fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                footer?.let {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(ChromeLine))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) { it() }
                }
            }
        }
    }
}

@Composable
internal fun ToolAction(icon: InkIcon, label: String, description: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        InkSymbol(icon)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(label, fontSize = 14.sp, color = ChromeInk)
            description?.let { Text(it, fontSize = 11.sp, color = ChromeMuted, modifier = Modifier.padding(top = 3.dp)) }
        }
        InkSymbol(InkIcon.Next, Modifier.size(16.dp), ChromeMuted)
    }
}
