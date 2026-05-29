package com.Badnng.moe.ui.screen.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Badnng.moe.data.db.OrderEntity
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixOrderDetailScreen(
    order: OrderEntity,
    onBack: () -> Unit
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "识别详情",
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "基本信息")
            Card {
                BasicComponent(
                    title = "来源应用",
                    summary = order.sourceApp ?: "无数据"
                )
                BasicComponent(
                    title = "来源包名",
                    summary = order.sourcePackage ?: "暂无记录"
                )
                BasicComponent(
                    title = "识别类型",
                    summary = order.orderType
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SmallTitle(text = "原文记录")
            Card {
                BasicComponent(
                    title = "原文内容",
                    summary = order.fullText ?: "无数据"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
