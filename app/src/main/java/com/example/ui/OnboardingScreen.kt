package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.DecoderSupportInfo

val CyberCyan = Color(0xFF00FFCC)
val AcidGreen = Color(0xFFB3FF00)
val RedAlert = Color(0xFFFF3366)
val PurpleGlow = Color(0xFF9D00FF)
val DeepSpace = Color(0xFF0D1117)
val SurfaceBorder = Color(0xFF21262D)
val CoolGrayText = Color(0xFF8B949E)
val IceWhite = Color(0xFFE6EDF3)
val BrightOrange = Color(0xFFFF5722)

@Composable
fun OnboardingScreen(
    supportInfo: DecoderSupportInfo?,
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Regardless of grant, we complete onboarding because the app has a fallback logic when opening files anyway.
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (currentPage) {
                0 -> PageWelcome(onNext = { currentPage = 1 })
                1 -> PageCodecs(supportInfo = supportInfo, onNext = { currentPage = 2 })
                2 -> PagePermission(
                    onRequestPermission = {
                        val permissions = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    onSkip = onComplete
                )
            }
        }
        
        // Paging indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0..2) {
                Box(
                    modifier = Modifier
                        .size(if (i == currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == currentPage) BrightOrange else SurfaceBorder)
                )
            }
        }
    }
}

@Composable
fun PageWelcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(BrightOrange),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Logo", tint = IceWhite, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "REFRACT",
            color = IceWhite,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Mobile Dolby Atmos Decoder",
            color = CyberCyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Decode, analyze, and export spatial audio on Android",
            color = CoolGrayText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = BrightOrange),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Get Started →", color = IceWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun PageCodecs(supportInfo: DecoderSupportInfo?, onNext: () -> Unit) {
    val sdk = supportInfo?.sdkInt ?: Build.VERSION.SDK_INT
    val hasAc4 = supportInfo?.hasAc4Decoder == true
    val eac3DecoderName = supportInfo?.availableCodecs?.firstOrNull { 
        it.mimeType.contains("eac3", ignoreCase = true) && !it.isEncoder
    }?.name ?: ""
    val hasEac3 = eac3DecoderName.isNotEmpty()
    val isEac3Hw = hasEac3 && !eac3DecoderName.contains("google", ignoreCase = true)

    Column(
        modifier = Modifier.padding(32.dp).fillMaxWidth()
    ) {
        Text(
            text = "What can your device decode?",
            color = IceWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        CodecRow(
            title = "E-AC3-JOC (Dolby Digital Plus Atmos)",
            status = if (hasEac3) {
                if (isEac3Hw) "✅ Hardware decoder found" else "⚡ Software fallback"
            } else "❌ Unavailable",
            statusColor = if (hasEac3) (if (isEac3Hw) AcidGreen else CyberCyan) else RedAlert
        )
        CodecRow(
            title = "AC-4 IMS (Immersive Stereo / Binaural)",
            status = if (hasAc4) "✅ Hardware decoder found" else "⚡ Software fallback",
            statusColor = if (hasAc4) AcidGreen else CyberCyan
        )
        CodecRow(
            title = "AC-4 L4 (Multichannel)",
            status = if (sdk >= 36) (if (hasAc4) "✅ Android 16 detected" else "⚠️ API 36 (no codec)") else "⚠️ Requires Android 16 (current: $sdk)",
            statusColor = if (sdk >= 36 && hasAc4) AcidGreen else CyberCyan
        )
        CodecRow(
            title = "Object-based Atmos rendering",
            status = if (isEac3Hw) "✅ Spatializer available" else "⚡ Bed extraction fallback",
            statusColor = if (isEac3Hw) AcidGreen else CyberCyan
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Software fallback uses bundled ffmpeg — no internet required. Hardware decoding gives better performance and fidelity.",
            color = CoolGrayText,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(containerColor = BrightOrange),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Next →", color = IceWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun CodecRow(title: String, status: String, statusColor: Color) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = title, color = IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = status, color = statusColor, fontSize = 13.sp)
    }
}

@Composable
fun PagePermission(onRequestPermission: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, contentDescription = "Lock", tint = CyberCyan, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "One permission needed",
            color = IceWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Refract needs access to your audio files.\nWe only read files you explicitly open — nothing is accessed automatically.",
            color = CoolGrayText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = BrightOrange),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Access", color = IceWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = 8.dp)) {
            Text("Skip for now", color = CoolGrayText, fontSize = 12.sp)
        }
    }
}
