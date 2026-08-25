package com.aka.alarm.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * A vertical wheel picker, Compose-native. Matches the iOS [NumberWheel] in row
 * height and selected-row treatment: glass-like translucent pill behind the
 * centered row, numbers above and below faded with distance.
 */
@Composable
fun NumberWheel(
    values: List<Int>,
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 80.dp,
    visibleRows: Int = 5,
    fontSize: Int = 44,
) {
    val configuration = LocalConfiguration.current

    val initialIndex = values.indexOf(selection).coerceAtLeast(0)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centeredIndex by remember(values) {
        derivedStateOf {
            val info = listState.layoutInfo

            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f

            info.visibleItemsInfo
                .minByOrNull {abs(it.offset + it.size / 2f - viewportCenter)}
                ?.index ?: initialIndex
        }
    }

    LaunchedEffect(listState.isScrollInProgress, centeredIndex) {
        if (
            !listState.isScrollInProgress &&
            centeredIndex in values.indices
        ) {
            val value = values[centeredIndex]

            if (value != selection) {
                onSelectionChange(value)
            }
        }
    }

    val totalHeight = rowHeight * visibleRows
    val centerPadding = rowHeight * ((visibleRows - 1) / 2)

    LaunchedEffect(configuration.orientation) {

        androidx.compose.runtime.withFrameNanos { }

        val target = values.indexOf(selection)

        if (target !in values.indices) {
            return@LaunchedEffect
        }

        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == target }
            ?: return@LaunchedEffect

        val info = listState.layoutInfo

        val viewportCenter =
            (info.viewportStartOffset + info.viewportEndOffset) / 2f

        val itemCenter =
            item.offset + item.size / 2f

        val delta = itemCenter - viewportCenter

        if (delta != 0f) {
            listState.scroll {
                scrollBy(delta)
            }
        }
    }

    LaunchedEffect(selection) {
        val target = values.indexOf(selection)

        if (
            target in values.indices &&
            target != centeredIndex &&
            !listState.isScrollInProgress
        ) {
            listState.animateScrollToItem(target)
        }
    }

    Box(modifier = modifier.height(totalHeight)) {
        // Glass selection pill behind the centered row.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(rowHeight)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(percent = 50)
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = centerPadding)
        ) {
            itemsIndexed(values) { index, value ->
                val distance = abs(index - centeredIndex)
                val alpha = (1f - 0.22f * distance).coerceAtLeast(0.18f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(rowHeight)
                ) {
                    Text(
                        text = "%02d".format(value),
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                }
            }
        }
    }
}
