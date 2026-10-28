package com.astrocam.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.astrocam.app.postprocess.ImageExporter
import kotlinx.coroutines.launch

@Composable
fun ResultPreviewDialog(
    bitmap: Bitmap,
    stackedCount: Int,
    rejectedCount: Int,
    totalExposureSec: Float,
    modeName: String,
    isRedMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var savedUriString by remember { mutableStateOf<String?>(null) }

    val accentColor = if (isRedMode) Color(0xFFFF2A4B) else Color(0xFF00E5FF)
    val bgColor = Color(0xF00A0E17)
    val cardBg = Color(0xFF141926)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "✨ ASTROPHOTOGRAPHY RESULT",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Text(
                            text = "$modeName • Total Exposure: ${totalExposureSec.toInt()}s",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // High-Res Image View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Stacked Astrophotography Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Statistics Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip(
                        title = "STACKED",
                        value = "$stackedCount frames",
                        color = Color(0xFF00E676),
                        bgColor = cardBg
                    )
                    StatChip(
                        title = "REJECTED",
                        value = "$rejectedCount frames",
                        color = Color(0xFFFFAB00),
                        bgColor = cardBg
                    )
                    StatChip(
                        title = "TOTAL EXP",
                        value = "${totalExposureSec.toInt()}s",
                        color = accentColor,
                        bgColor = cardBg
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Save to Gallery & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                coroutineScope.launch {
                                    val uri = ImageExporter.saveJpeg(
                                        context = context,
                                        bitmap = bitmap,
                                        stackCount = stackedCount,
                                        totalExposure = totalExposureSec
                                    )
                                    isSaving = false
                                    if (uri != null) {
                                        savedUriString = uri.toString()
                                        Toast.makeText(context, "✅ Photo saved to Gallery!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "❌ Failed to save photo to Gallery", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            text = if (isSaving) "Saving..." else (if (savedUriString != null) "✓ Saved to Gallery" else "💾 Save to Gallery"),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Continue", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    title: String,
    value: String,
    color: Color,
    bgColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
            Text(text = value, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
