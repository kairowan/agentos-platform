package com.agentos.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this, AgentShellViewModel.factory(applicationContext))[AgentShellViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentOsTheme {
                AgentShellScreen(viewModel)
            }
        }
    }
}

@Composable
private fun AgentShellScreen(viewModel: AgentShellViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AgentShellContent(
        state = state,
        onPromptChanged = viewModel::updatePrompt,
        onSubmit = viewModel::submit,
        onSuggestion = viewModel::submit,
    )
}

@Composable
internal fun AgentShellContent(
    state: AgentUiState,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestion: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "AGENT OS / SYSTEM SHELL",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "你想让系统完成什么？",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "系统会组合受控能力，并为当前任务生成界面。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            GeneratedScreenView(state.screen, onSuggestion)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("输入目标") },
                    placeholder = { Text("例如：查看设备状态") },
                    minLines = 2,
                )
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.prompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("交给智能体")
                }
            }
        }
    }
}

@Composable
private fun GeneratedScreenView(screen: GeneratedScreen, onAction: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(screen.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            screen.blocks.forEach { block ->
                when (block) {
                    is UiBlock.Paragraph -> Text(
                        block.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is UiBlock.Fact -> FactRow(block)
                    is UiBlock.Action -> Button(
                        onClick = { onAction(block.prompt) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(block.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun FactRow(block: UiBlock.Fact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(block.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(block.value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AgentOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF83D5C5),
            onPrimary = Color(0xFF06201B),
            background = Color(0xFF0A1114),
            onBackground = Color(0xFFE4F1EE),
            surface = Color(0xFF111B1F),
            onSurface = Color(0xFFE4F1EE),
            surfaceVariant = Color(0xFF1A292D),
            onSurfaceVariant = Color(0xFFB6C9C5),
        ),
        content = content,
    )
}
