package com.Badnng.moe.ui.component

import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.ocr.TextRecognitionHelper
import com.Badnng.moe.viewmodel.OrderViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun AddOrderBottomSheet(
    show: Boolean,
    viewModel: OrderViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (show) BlurState.show()
    WindowBottomSheet(
        show = show,
        title = "添加记录",
        enableWindowDim = false,
        allowDismiss = true,
        enableNestedScroll = true,
        onDismissRequest = {
            BlurState.hide()
            onDismiss()
        }
    ) {
        val dismiss = LocalDismissState.current

        var text by remember { mutableStateOf("") }
        var detectedQrData by remember { mutableStateOf<String?>(null) }
        var orderType by remember { mutableStateOf("餐食") }
        var brandName by remember { mutableStateOf<String?>(null) }
        var pickupLocation by remember { mutableStateOf<String?>(null) }
        val options = listOf("餐食", "饮品", "快递")
        var screenshotPath by remember { mutableStateOf<String?>(null) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val originalBitmap = if (Build.VERSION.SDK_INT >= 28) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    val statusBarHeight = 150
                    val sideMargin = (originalBitmap.width * 0.02).toInt()
                    val targetWidth = (originalBitmap.width * 0.96).toInt()
                    val targetHeight = (originalBitmap.height * 0.81).toInt()
                    val bitmap = if (originalBitmap.height > statusBarHeight + targetHeight && originalBitmap.width > sideMargin + targetWidth) {
                        Bitmap.createBitmap(originalBitmap, sideMargin, statusBarHeight, targetWidth, targetHeight)
                    } else originalBitmap

                    val helper = TextRecognitionHelper(context)
                    helper.initOcr()
                    val (result, _) = helper.recognizeAll(bitmap)
                    text = result.code ?: ""
                    detectedQrData = result.qr
                    orderType = result.type
                    brandName = result.brand
                    pickupLocation = result.pickupLocation
                    if (result.code != null) {
                        val screenshotFile = java.io.File(context.filesDir, "screenshots/manual_${System.currentTimeMillis()}.png")
                        screenshotFile.parentFile?.mkdirs()
                        val outputStream = java.io.FileOutputStream(screenshotFile)
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.close()
                        screenshotPath = screenshotFile.absolutePath
                    }
                    helper.close()
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "输入取餐码/取件码",
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(
                                if (detectedQrData != null) Icons.Default.QrCodeScanner else Icons.Default.PhotoLibrary,
                                contentDescription = "选择图片识别"
                            )
                        }
                    }
                )
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    WindowDropdownPreference(
                        title = "类别",
                        items = options,
                        selectedIndex = options.indexOf(orderType).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            orderType = options[index]
                        }
                    )
                }
                if (detectedQrData != null) {
                    Text("已识别到二维码信息", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { dismiss?.invoke() },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "添加",
                        onClick = {
                            viewModel.addOrder(
                                OrderEntity(
                                    takeoutCode = text,
                                    qrCodeData = detectedQrData,
                                    screenshotPath = screenshotPath ?: "",
                                    recognizedText = "手动输入",
                                    orderType = orderType,
                                    brandName = brandName,
                                    pickupLocation = pickupLocation
                                )
                            )
                            dismiss?.invoke()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
