package com.Badnng.moe.ui.screen.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.rules.RecognitionRules
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixRulesScreen(
    padding: PaddingValues
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var rules by remember { mutableStateOf(RecognitionRuleEngine.rules) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "识别规则",
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 100.dp
            )
        ) {
            item {
                SmallTitle(text = "本地规则")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "内置默认规则",
                        summary = "应用自带规则",
                        onClick = { }
                    )
                }
            }

            item {
                SmallTitle(text = "规则统计")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "饮品品牌",
                        summary = "${rules.brands.drink.size} 个",
                        onClick = { }
                    )
                    ArrowPreference(
                        title = "餐食品牌",
                        summary = "${rules.brands.food.size} 个",
                        onClick = { }
                    )
                    ArrowPreference(
                        title = "快递关键词",
                        summary = "${rules.brands.express.size} 个",
                        onClick = { }
                    )
                    ArrowPreference(
                        title = "取件码触发词",
                        summary = "${rules.codeExtraction.express.triggerKeywords.size} 个",
                        onClick = { }
                    )
                    ArrowPreference(
                        title = "取餐码触发词",
                        summary = "${rules.codeExtraction.food.triggerKeywords.size} 个",
                        onClick = { }
                    )
                }
            }

            item {
                SmallTitle(text = "在线规则")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "管理在线规则源",
                        summary = "添加、编辑或删除在线规则源",
                        onClick = { }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 100.dp),
        )
        }
    }
}
