package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AssistantStatus
import com.example.ui.theme.LocalMinaPalette
import com.example.ui.theme.StatusListeningAmber
import com.example.ui.theme.StatusReadyGreen
import com.example.ui.theme.StatusSpeakingGold
import com.example.ui.theme.StatusThinkingCoral
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class StarParticle(
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float,
    val speedFactor: Float,
    val phase: Float
)

@Composable
fun MinaBackdrop(
    particlesEnabled: Boolean = true,
    glowIntensity: Float = 0.85f,
    showHeroArt: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalMinaPalette.current
    val particles = remember {
        val rng = Random(42)
        List(26) {
            StarParticle(
                xFrac = rng.nextFloat(),
                yFrac = rng.nextFloat(),
                radiusDp = 1.2f + rng.nextFloat() * 2.2f,
                speedFactor = 0.4f + rng.nextFloat() * 0.8f,
                phase = rng.nextFloat() * 6.28f
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "particles")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.bgTop, palette.bgBottom, palette.bgTop)
                )
            )
    ) {
        if (showHeroArt && palette.isDark) {
            Image(
                painter = painterResource(id = R.drawable.img_mina_hero_bg_1795466490450),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.28f * glowIntensity.coerceIn(0.3f, 1.2f))
            )
        }

        if (particlesEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Warm radial ambient glow near upper center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.gradientEnd.copy(alpha = 0.14f * glowIntensity),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.5f, size.height * 0.22f)
                )

                particles.forEach { p ->
                    val y = ((p.yFrac - drift * p.speedFactor) % 1f + 1f) % 1f
                    val x = p.xFrac + 0.018f * sin((drift * 2f * PI + p.phase).toFloat())
                    val alpha = (0.18f + 0.35f * sin((drift * 4f * PI + p.phase).toFloat()).let { (it + 1f) / 2f }) * glowIntensity.coerceIn(0.3f, 1f)
                    drawCircle(
                        color = palette.particleColor.copy(alpha = alpha),
                        radius = p.radiusDp.dp.toPx(),
                        center = Offset(x * size.width, y * size.height)
                    )
                }
            }
        }

        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalMinaPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = Brush.linearGradient(
        colors = listOf(palette.glassBorderStart, palette.glassBorderEnd)
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(borderWidth, borderBrush), shape)
            .then(clickModifier),
        shape = shape,
        color = palette.glassFill,
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun AmberGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp
) {
    val palette = LocalMinaPalette.current
    val shape = RoundedCornerShape(26.dp)
    val gradient = if (enabled) {
        Brush.horizontalGradient(listOf(palette.gradientStart, palette.gradientEnd))
    } else {
        Brush.horizontalGradient(
            listOf(
                palette.gradientStart.copy(alpha = 0.4f),
                palette.gradientEnd.copy(alpha = 0.4f)
            )
        )
    }

    Box(
        modifier = modifier
            .height(height)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = palette.gradientStart, spotColor = palette.gradientEnd)
            .clip(shape)
            .background(gradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1A0E07),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF1A0E07),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MinaTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val palette = LocalMinaPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("top_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = palette.goldHighlight
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun AssistantStatusPill(status: AssistantStatus, modifier: Modifier = Modifier) {
    val dotColor = when (status) {
        AssistantStatus.READY -> StatusReadyGreen
        AssistantStatus.LISTENING -> StatusListeningAmber
        AssistantStatus.THINKING -> StatusThinkingCoral
        AssistantStatus.SPEAKING -> StatusSpeakingGold
    }
    GlassCard(
        modifier = modifier,
        cornerRadius = 50.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun MinaAnimatedOrb(
    status: AssistantStatus,
    amplitude: Float,
    size: Dp = 156.dp,
    showAvatar: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalMinaPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.LISTENING -> 650
                    AssistantStatus.SPEAKING -> 550
                    AssistantStatus.THINKING -> 800
                    AssistantStatus.READY -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val dynamicBoost = 1f + (amplitude * 0.14f)

    Box(
        modifier = Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = (this.size.minDimension / 2f) * 0.92f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientEnd.copy(alpha = 0.34f),
                        palette.gradientStart.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * pulse * dynamicBoost
                ),
                radius = radius * pulse * dynamicBoost,
                center = center
            )

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        palette.gradientStart,
                        palette.goldHighlight,
                        Color.Transparent,
                        palette.gradientEnd
                    )
                ),
                startAngle = rotation,
                sweepAngle = 250f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = palette.goldHighlight.copy(alpha = 0.4f),
                startAngle = -rotation * 0.7f,
                sweepAngle = 140f,
                useCenter = false,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val innerSize = size * 0.74f
        if (showAvatar) {
            Image(
                painter = painterResource(id = R.drawable.img_mina_avatar_1795466489374),
                contentDescription = stringResource(R.string.cd_mina_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(innerSize)
                    .clip(CircleShape)
                    .border(2.dp, palette.goldHighlight.copy(alpha = 0.85f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(palette.gradientStart, palette.gradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.cd_mic),
                    tint = Color(0xFF1A0E07),
                    modifier = Modifier.size(innerSize * 0.42f)
                )
            }
        }
    }
}

enum class BottomNavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    CHAT("chat", R.string.nav_chat, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    VOICE("voice", R.string.nav_voice, Icons.Filled.Mic, Icons.Outlined.MicNone),
    VISION("vision", R.string.nav_vision, Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MinaFloatingBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMinaPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = palette.gradientStart),
            cornerRadius = 32.dp,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val isCenterMic = tab == BottomNavTab.VOICE
                    if (isCenterMic) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(palette.gradientStart, palette.gradientEnd))
                                )
                                .clickable { onNavigate(tab.route) }
                                .testTag("nav_${tab.route}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.selectedIcon,
                                contentDescription = stringResource(tab.labelRes),
                                tint = Color(0xFF1A0E07),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) palette.gradientEnd.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .clickable { onNavigate(tab.route) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("nav_${tab.route}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.labelRes),
                                tint = if (selected) palette.goldHighlight else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) palette.goldHighlight else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
