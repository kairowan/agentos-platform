package com.agentos.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val AgentMint = Color(0xFF68F5CE)
internal val AgentBlue = Color(0xFF84AFFF)
internal val AgentAmber = Color(0xFFFFC66D)
internal val AgentDanger = Color(0xFFFF7A8A)
internal val AgentBackground = Color(0xFF061014)
internal val AgentSurface = Color(0xE6122026)
internal val AgentSurfaceHigh = Color(0xF01A2D34)

@Composable
internal fun AgentOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AgentMint,
            onPrimary = Color(0xFF002019),
            primaryContainer = Color(0xFF123D34),
            onPrimaryContainer = Color(0xFFB8FFE9),
            secondary = AgentBlue,
            onSecondary = Color(0xFF071A39),
            secondaryContainer = Color(0xFF1A3158),
            onSecondaryContainer = Color(0xFFD8E4FF),
            tertiary = AgentAmber,
            background = AgentBackground,
            onBackground = Color(0xFFF0FAF7),
            surface = AgentSurface,
            onSurface = Color(0xFFF0FAF7),
            surfaceVariant = AgentSurfaceHigh,
            onSurfaceVariant = Color(0xFFABC2C4),
            outline = Color(0xFF385159),
            error = AgentDanger,
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = { Surface(Modifier.fillMaxSize(), color = AgentBackground, content = content) },
    )
}

@Composable
internal fun AgentBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0C2025), AgentBackground, Color(0xFF081319))),
        ),
        content = content,
    )
}

@Composable
internal fun AgentPanel(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AgentSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) { content() }
}

@Composable
internal fun AgentPill(text: String, color: Color = AgentMint) {
    Surface(color = color.copy(alpha = 0.13f), shape = CircleShape,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.28f), CircleShape)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
internal fun AgentTopBar(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Text("←") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
