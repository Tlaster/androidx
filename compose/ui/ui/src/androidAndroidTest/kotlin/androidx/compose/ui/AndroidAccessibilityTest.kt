/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui

import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_MOVE
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.view.accessibility.AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
import android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
import android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION
import android.view.accessibility.AccessibilityNodeProvider
import android.view.accessibility.AccessibilityRecord
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat
import androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.Companion.ClassName
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TestActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.internal.matchers.apachecommons.ReflectionEquals
import java.lang.reflect.Method

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class)
class AndroidAccessibilityTest {
    @get:Rule
    val rule = createAndroidComposeRule<TestActivity>()

    private lateinit var androidComposeView: AndroidComposeView
    private lateinit var container: OpenComposeView
    private lateinit var delegate: AndroidComposeViewAccessibilityDelegateCompat
    private lateinit var provider: AccessibilityNodeProvider

    private val argument = ArgumentCaptor.forClass(AccessibilityEvent::class.java)

    @Before
    fun setup() {
        // Use uiAutomation to enable accessibility manager.
        InstrumentationRegistry.getInstrumentation().uiAutomation

        rule.activityRule.scenario.onActivity { activity ->
            container = spy(OpenComposeView(activity)) {
                on { onRequestSendAccessibilityEvent(any(), any()) } doReturn false
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            activity.setContentView(container)
            androidComposeView = container.getChildAt(0) as AndroidComposeView
            delegate = ViewCompat.getAccessibilityDelegate(androidComposeView) as
                AndroidComposeViewAccessibilityDelegateCompat
            delegate.accessibilityForceEnabledForTesting = true
            provider = delegate.getAccessibilityNodeProvider(androidComposeView).provider
                as AccessibilityNodeProvider
        }
    }

    @Test
    fun testCreateAccessibilityNodeInfo_forToggleable() {
        val tag = "Toggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(value = checked, onValueChange = { checked = it })
                    .testTag(tag)
            ) {
                BasicText("ToggleableText")
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(toggleableNode.id)
        assertEquals("android.view.View", accessibilityNodeInfo.className)
        assertTrue(accessibilityNodeInfo.isClickable)
        assertTrue(accessibilityNodeInfo.isVisibleToUser)
        assertTrue(accessibilityNodeInfo.isCheckable)
        assertTrue(accessibilityNodeInfo.isChecked)
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, "toggle")
            )
        )
    }

    @Test
    fun testCreateAccessibilityNodeInfo_forSwitch() {
        val tag = "Toggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = { checked = it }
                    )
                    .testTag(tag)
            ) {
                BasicText("ToggleableText")
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag, true)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(toggleableNode.id)

        // We temporary send Switch role as a separate fake node
        val switchRoleNode = toggleableNode.children.last()
        val switchRoleNodeInfo = provider.createAccessibilityNodeInfo(switchRoleNode.id)
        assertEquals("android.widget.Switch", switchRoleNodeInfo.className)

        val stateDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                accessibilityNodeInfo.stateDescription
            }
            Build.VERSION.SDK_INT >= 19 -> {
                accessibilityNodeInfo.extras.getCharSequence(
                    "androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY"
                )
            }
            else -> {
                null
            }
        }
        assertEquals("On", stateDescription)
        assertTrue(accessibilityNodeInfo.isClickable)
        assertTrue(accessibilityNodeInfo.isVisibleToUser)
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null)
            )
        )
    }

    @Test
    fun testCreateAccessibilityNodeInfo_forSelectable() {
        val tag = "Selectable"
        container.setContent {
            Box(Modifier.selectable(selected = true, onClick = {}).testTag(tag)) {
                BasicText("Text")
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(toggleableNode.id)
        assertEquals("android.view.View", accessibilityNodeInfo.className)
        val stateDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                accessibilityNodeInfo.stateDescription
            }
            Build.VERSION.SDK_INT >= 19 -> {
                accessibilityNodeInfo.extras.getCharSequence(
                    "androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY"
                )
            }
            else -> {
                null
            }
        }
        assertEquals("Selected", stateDescription)
        assertFalse(accessibilityNodeInfo.isClickable)
        assertTrue(accessibilityNodeInfo.isVisibleToUser)
        assertFalse(accessibilityNodeInfo.isCheckable)
        assertFalse(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null)
            )
        )
    }

    @Test
    fun testCreateAccessibilityNodeInfo_forTab() {
        val tag = "Selectable"
        container.setContent {
            Box(Modifier.selectable(selected = true, onClick = {}, role = Role.Tab).testTag(tag)) {
                BasicText("Text")
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(toggleableNode.id)
        assertEquals("android.view.View", accessibilityNodeInfo.className)
        val stateDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                accessibilityNodeInfo.stateDescription
            }
            Build.VERSION.SDK_INT >= 19 -> {
                accessibilityNodeInfo.extras.getCharSequence(
                    "androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY"
                )
            }
            else -> {
                null
            }
        }
        assertNull(stateDescription)
        assertFalse(accessibilityNodeInfo.isClickable)
        assertTrue(accessibilityNodeInfo.isVisibleToUser)
        assertTrue(accessibilityNodeInfo.isSelected)
        assertFalse(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null)
            )
        )
    }

    @Test
    fun testCreateAccessibilityNodeInfo_progressIndicator_determinate() {
        val tag = "progress"
        container.setContent {
            Box(Modifier.progressSemantics(0.5f).testTag(tag)) {
                BasicText("Text")
            }
        }

        val node = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(node.id)
        assertEquals("android.widget.ProgressBar", accessibilityNodeInfo.className)
        val stateDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                accessibilityNodeInfo.stateDescription
            }
            Build.VERSION.SDK_INT >= 19 -> {
                accessibilityNodeInfo.extras.getCharSequence(
                    "androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY"
                )
            }
            else -> {
                null
            }
        }
        assertEquals("50 percent.", stateDescription)
        assertEquals(
            AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT,
            accessibilityNodeInfo.rangeInfo.getType()
        )
        assertEquals(0.5f, accessibilityNodeInfo.rangeInfo.getCurrent())
        assertEquals(0f, accessibilityNodeInfo.rangeInfo.getMin())
        assertEquals(1f, accessibilityNodeInfo.rangeInfo.getMax())
    }

    @Test
    fun testCreateAccessibilityNodeInfo_progressIndicator_determinate_indeterminate() {
        val tag = "progress"
        container.setContent {
            Box(
                Modifier
                    .progressSemantics()
                    .testTag(tag)
            ) {
                BasicText("Text")
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(toggleableNode.id)
        assertEquals("android.widget.ProgressBar", accessibilityNodeInfo.className)
        val stateDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                accessibilityNodeInfo.stateDescription
            }
            Build.VERSION.SDK_INT >= 19 -> {
                accessibilityNodeInfo.extras.getCharSequence(
                    "androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY"
                )
            }
            else -> {
                null
            }
        }
        assertEquals("In progress", stateDescription)
        assertNull(accessibilityNodeInfo.rangeInfo)
    }

    @Test
    fun testCreateAccessibilityNodeInfo_forTextField() {
        val tag = "TextField"
        container.setContent {
            var value by remember { mutableStateOf(TextFieldValue("hello")) }
            BasicTextField(
                modifier = Modifier.testTag(tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        val textFieldNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(textFieldNode.id)

        assertEquals("android.widget.EditText", accessibilityNodeInfo.className)
        assertEquals("hello", accessibilityNodeInfo.text.toString())
        assertTrue(accessibilityNodeInfo.isFocusable)
        assertFalse(accessibilityNodeInfo.isFocused)
        assertTrue(accessibilityNodeInfo.isEditable)
        assertTrue(accessibilityNodeInfo.isVisibleToUser)
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null)
            )
        )
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_SET_SELECTION, null)
            )
        )
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_NEXT_AT_MOVEMENT_GRANULARITY, null)
            )
        )
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(
                    ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, null
                )
            )
        )
        assertEquals(
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE,
            accessibilityNodeInfo.movementGranularities
        )
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(
                listOf(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY),
                accessibilityNodeInfo.availableExtraData
            )
        }
    }

    @Test
    fun testPerformAction_showOnScreen() {
        val scrollState = ScrollState(initial = 0)
        val target1Tag = "target1"
        val target2Tag = "target2"
        container.setContent {
            Box {
                Column(
                    Modifier
                        .size(200.dp)
                        .verticalScroll(scrollState)
                ) {
                    BasicText("Backward", Modifier.testTag(target2Tag).size(150.dp))
                    BasicText("Forward", Modifier.testTag(target1Tag).size(150.dp))
                }
            }
        }

        waitForSubtreeEventToSend()
        assertThat(scrollState.value).isEqualTo(0)

        val showOnScreen = android.R.id.accessibilityActionShowOnScreen
        val targetNode1 = rule.onNodeWithTag(target1Tag)
            .fetchSemanticsNode("couldn't find node with tag $target1Tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(targetNode1.id, showOnScreen, null))
        }
        with(rule.density) {
            assertThat(scrollState.value).isGreaterThan(99.dp.toPx().toInt())
        }

        val targetNode2 = rule.onNodeWithTag(target2Tag)
            .fetchSemanticsNode("couldn't find node with tag $target2Tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(targetNode2.id, showOnScreen, null))
        }
        assertThat(scrollState.value).isEqualTo(0)
    }

    @Test
    fun testPerformAction_showOnScreen_lazy() {
        val lazyState = LazyListState()
        val target1Tag = "target1"
        val target2Tag = "target2"
        container.setContent {
            Box {
                LazyColumn(
                    modifier = Modifier.size(200.dp),
                    state = lazyState
                ) {
                    item {
                        BasicText("Backward", Modifier.testTag(target2Tag).size(150.dp))
                    }
                    item {
                        BasicText("Forward", Modifier.testTag(target1Tag).size(150.dp))
                    }
                }
            }
        }

        waitForSubtreeEventToSend()
        assertThat(lazyState.firstVisibleItemScrollOffset).isEqualTo(0)

        val showOnScreen = android.R.id.accessibilityActionShowOnScreen
        val targetNode1 = rule.onNodeWithTag(target1Tag)
            .fetchSemanticsNode("couldn't find node with tag $target1Tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(targetNode1.id, showOnScreen, null))
        }
        with(rule.density) {
            assertThat(lazyState.firstVisibleItemIndex).isEqualTo(0)
            assertThat(lazyState.firstVisibleItemScrollOffset).isGreaterThan(99.dp.toPx().toInt())
        }

        val targetNode2 = rule.onNodeWithTag(target2Tag)
            .fetchSemanticsNode("couldn't find node with tag $target2Tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(targetNode2.id, showOnScreen, null))
        }
        assertThat(lazyState.firstVisibleItemIndex).isEqualTo(0)
        assertThat(lazyState.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun testPerformAction_showOnScreen_lazynested() {
        val parentLazyState = LazyListState()
        val lazyState = LazyListState()
        val target1Tag = "target1"
        val target2Tag = "target2"
        container.setContent {
            Box {
                LazyRow(
                    modifier = Modifier.size(250.dp),
                    state = parentLazyState
                ) {
                    item {
                        LazyColumn(
                            modifier = Modifier.size(200.dp),
                            state = lazyState
                        ) {
                            item {
                                BasicText("Backward", Modifier.testTag(target2Tag).size(150.dp))
                            }
                            item {
                                BasicText("Forward", Modifier.testTag(target1Tag).size(150.dp))
                            }
                        }
                    }
                }
            }
        }

        waitForSubtreeEventToSend()
        assertThat(lazyState.firstVisibleItemIndex).isEqualTo(0)
        assertThat(lazyState.firstVisibleItemScrollOffset).isEqualTo(0)

        // Test that child column scrolls to make it fully visible in its context, without being
        // influenced by or influencing the parent row.
        // TODO(b/190865803): Is this the ultimate right behavior we want?
        val showOnScreen = android.R.id.accessibilityActionShowOnScreen
        val targetNode1 = rule.onNodeWithTag(target1Tag)
            .fetchSemanticsNode("couldn't find node with tag $target1Tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(targetNode1.id, showOnScreen, null))
        }
        with(rule.density) {
            assertThat(lazyState.firstVisibleItemIndex).isEqualTo(0)
            assertThat(lazyState.firstVisibleItemScrollOffset).isGreaterThan(99.dp.toPx().toInt())
        }
        assertThat(parentLazyState.firstVisibleItemScrollOffset).isEqualTo(0)

        val targetNode2 = rule.onNodeWithTag(target2Tag)
            .fetchSemanticsNode("couldn't find node with tag $target2Tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(targetNode2.id, showOnScreen, null))
        }
        assertThat(lazyState.firstVisibleItemIndex).isEqualTo(0)
        assertThat(lazyState.firstVisibleItemScrollOffset).isEqualTo(0)
        assertThat(parentLazyState.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun testPerformAction_succeedOnEnabledNodes() {
        val tag = "Toggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(value = checked, onValueChange = { checked = it })
                    .testTag(tag)
            ) {
                BasicText("ToggleableText")
            }
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertIsOn()

        waitForSubtreeEventToSend()
        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(toggleableNode.id, ACTION_CLICK, null))
        }
        rule.onNodeWithTag(tag)
            .assertIsOff()
    }

    @Test
    fun testPerformAction_failOnDisabledNodes() {
        val tag = "DisabledToggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(
                        value = checked,
                        enabled = false,
                        onValueChange = { checked = it }
                    )
                    .testTag(tag),
                content = {
                    BasicText("ToggleableText")
                }
            )
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertIsOn()

        waitForSubtreeEventToSend()
        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        rule.runOnUiThread {
            assertFalse(provider.performAction(toggleableNode.id, ACTION_CLICK, null))
        }
        rule.onNodeWithTag(tag)
            .assertIsOn()
    }

    @Test
    fun testTextField_performClickAction_succeedOnEnabledNode() {
        val tag = "TextField"
        container.setContent {
            BasicTextField(
                modifier = Modifier.testTag(tag),
                value = "value",
                onValueChange = {}
            )
        }

        val textFieldNode = rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .fetchSemanticsNode("couldn't find node with tag $tag")

        rule.runOnUiThread {
            assertTrue(provider.performAction(textFieldNode.id, ACTION_CLICK, null))
        }

        rule.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
    }

    @Test
    fun testTextField_performSetSelectionAction_succeedOnEnabledNode() {
        val tag = "TextField"
        var textFieldSelectionOne = false
        container.setContent {
            var value by remember { mutableStateOf(TextFieldValue("hello")) }
            BasicTextField(
                modifier = Modifier
                    .semantics {
                        // Make sure this block will be executed when selection changes.
                        this.textSelectionRange = value.selection
                        if (value.selection == TextRange(1)) {
                            textFieldSelectionOne = true
                        }
                    }
                    .testTag(tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        val textFieldNode = rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val argument = Bundle()
        argument.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
        argument.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 1)

        rule.runOnUiThread {
            textFieldSelectionOne = false
            assertTrue(provider.performAction(textFieldNode.id, ACTION_SET_SELECTION, argument))
        }
        rule.waitUntil(5_000) { textFieldSelectionOne }

        rule.onNodeWithTag(tag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.TextSelectionRange,
                    TextRange(1)
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun testAddExtraDataToAccessibilityNodeInfo_notMerged() {
        val tag = "TextField"
        lateinit var textLayoutResult: TextLayoutResult

        container.setContent {
            BasicTextField(
                modifier = Modifier.testTag(tag),
                value = "texy",
                onValueChange = {},
                onTextLayout = { textLayoutResult = it }
            )
        }

        val textFieldNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val info = AccessibilityNodeInfo.obtain()
        val argument = Bundle()
        argument.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
        argument.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 1)
        provider.addExtraDataToAccessibilityNodeInfo(
            textFieldNode.id,
            info,
            AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
            argument
        )
        val data = info.extras
            .getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
        assertEquals(1, data!!.size)

        val rectF = data[0] as RectF // result in screen coordinates
        val expectedRectInLocalCoords = textLayoutResult.getBoundingBox(0).translate(
            textFieldNode.positionInWindow
        )
        val expectedTopLeftInScreenCoords = androidComposeView.localToScreen(
            expectedRectInLocalCoords.toAndroidRect().topLeftToOffset()
        )
        assertEquals(expectedTopLeftInScreenCoords.x, rectF.left)
        assertEquals(expectedTopLeftInScreenCoords.y, rectF.top)
        assertEquals(expectedRectInLocalCoords.width, rectF.width())
        assertEquals(expectedRectInLocalCoords.height, rectF.height())
    }

    @Test
    fun sendClickedEvent_whenClick() {
        val tag = "Clickable"
        container.setContent {
            Box(Modifier.clickable(onClick = {}).testTag(tag)) {
                BasicText("Text")
            }
        }

        waitForSubtreeEventToSend()
        val node = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(node.id, ACTION_CLICK, null))
        }

        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == node.id &&
                            it.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
                    }
                )
            )
        }
    }

    @Test
    fun sendStateChangeEvent_whenClickToggleable() {
        val tag = "Toggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(value = checked, onValueChange = { checked = it })
                    .testTag(tag)
            ) {
                BasicText("ToggleableText")
            }
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertIsOn()

        waitForSubtreeEventToSend()
        rule.onNodeWithTag(tag)
            .performClick()
            .assertIsOff()

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")

        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                    }
                )
            )
        }
    }

    @Test
    fun sendStateChangeEvent_whenSelectedChange() {
        val tag = "Selectable"
        container.setContent {
            var selected by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .selectable(selected = selected, onClick = { selected = true })
                    .testTag(tag)
            ) {
                BasicText("Text")
            }
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertIsNotSelected()

        waitForSubtreeEventToSend()
        rule.onNodeWithTag(tag)
            .performClick()
            .assertIsSelected()

        val node = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == node.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                    }
                )
            )
        }
    }

    @Test
    fun sendStateChangeEvent_whenRangeInfoChange() {
        val tag = "Progress"
        var current by mutableStateOf(0.5f)
        container.setContent {
            Box(Modifier.progressSemantics(current).testTag(tag)) {
                BasicText("Text")
            }
        }
        waitForSubtreeEventToSend()

        current = 0.9f

        val node = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == node.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                    }
                )
            )
        }
    }

    @Test
    fun sendTextEvents_whenSetText() {
        val tag = "TextField"
        val initialText = "h"
        val text = "hello"
        container.setContent {
            var value by remember { mutableStateOf(TextFieldValue(initialText)) }
            BasicTextField(
                modifier = Modifier.testTag(tag),
                value = value,
                onValueChange = { value = it }
            )
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(initialText)
                )
            )

        waitForSubtreeEventToSend()
        rule.onNodeWithTag(tag)
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
        rule.onNodeWithTag(tag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(text)
                )
            )

        val textFieldNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")

        val textEvent = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        )
        textEvent.fromIndex = initialText.length
        textEvent.removedCount = 0
        textEvent.addedCount = text.length - initialText.length
        textEvent.beforeText = initialText
        textEvent.text.add(text)

        val selectionEvent = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        )
        selectionEvent.fromIndex = text.length
        selectionEvent.toIndex = text.length
        selectionEvent.itemCount = text.length
        selectionEvent.text.add(text)

        rule.runOnIdle {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView), argument.capture()
            )
            val values = argument.allValues
            assertTrue(containsEvent(values, textEvent))
            assertTrue(containsEvent(values, selectionEvent))
        }
    }

    @Test
    @Ignore("b/177656801")
    fun sendSubtreeChangeEvents_whenNodeRemoved() {
        val columnTag = "topColumn"
        val textFieldTag = "TextFieldTag"
        var isTextFieldVisible by mutableStateOf(true)

        container.setContent {
            Column(Modifier.testTag(columnTag)) {
                if (isTextFieldVisible) {
                    BasicTextField(
                        modifier = Modifier.testTag(textFieldTag),
                        value = "text",
                        onValueChange = {}
                    )
                }
            }
        }

        val parentNode = rule.onNodeWithTag(columnTag)
            .fetchSemanticsNode("couldn't find node with tag $columnTag")
        rule.onNodeWithTag(textFieldTag)
            .assertExists()
        // wait for the subtree change events from initialization to send
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == parentNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }

        // TextField is removed compared to setup.
        isTextFieldVisible = false

        rule.onNodeWithTag(textFieldTag)
            .assertDoesNotExist()
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == parentNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }
    }

    @Test
    fun selectionEventBeforeTraverseEvent_whenTraverseTextField() {
        val tag = "TextFieldTag"
        val text = "h"
        container.setContent {
            var value by remember { mutableStateOf(TextFieldValue(text)) }
            BasicTextField(
                modifier = Modifier.testTag(tag),
                value = value,
                onValueChange = { value = it },
                visualTransformation = PasswordVisualTransformation(),
                decorationBox = {
                    BasicText("Label")
                    it()
                }
            )
        }

        val textFieldNode = rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .fetchSemanticsNode("couldn't find node with tag $tag")
        waitForSubtreeEventToSend()
        rule.runOnUiThread {
            provider.performAction(
                textFieldNode.id,
                AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
                createMovementGranularityCharacterArgs()
            )
        }

        val selectionEvent = createSelectionChangedFromIndexOneToOneEvent(textFieldNode)
        val traverseEvent = createCharacterTraverseFromIndexZeroEvent(textFieldNode)
        rule.runOnIdle {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView), argument.capture()
            )
            val values = argument.allValues
            val traverseEventIndex = eventIndex(values, traverseEvent)
            val selectionEventIndex = eventIndex(values, selectionEvent)
            assertNotEquals(-1, traverseEventIndex)
            assertNotEquals(-1, selectionEventIndex)
            assertTrue(traverseEventIndex > selectionEventIndex)
        }
    }

    @Test
    fun selectionEventBeforeTraverseEvent_whenTraverseText() {
        val tag = "TextTag"
        val text = "h"
        container.setContent {
            BasicText(text, Modifier.testTag(tag))
        }

        val textNode = rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .fetchSemanticsNode("couldn't find node with tag $tag")
        waitForSubtreeEventToSend()
        rule.runOnUiThread {
            provider.performAction(
                textNode.id,
                AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
                createMovementGranularityCharacterArgs()
            )
        }

        val selectionEvent = createSelectionChangedFromIndexOneToOneEvent(textNode)
        val traverseEvent = createCharacterTraverseFromIndexZeroEvent(textNode)
        rule.runOnIdle {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView), argument.capture()
            )
            val values = argument.allValues
            val traverseEventIndex = eventIndex(values, traverseEvent)
            val selectionEventIndex = eventIndex(values, selectionEvent)
            assertNotEquals(-1, traverseEventIndex)
            assertNotEquals(-1, selectionEventIndex)
            assertTrue(traverseEventIndex > selectionEventIndex)
        }
    }

    @Test
    @Ignore("b/177656801")
    fun semanticsNodeBeingMergedLayoutChange_sendThrottledSubtreeEventsForMergedSemanticsNode() {
        val tag = "Toggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(value = checked, onValueChange = { checked = it })
                    .testTag(tag)
            ) {
                BasicText("ToggleableText")
                Box {
                    BasicText("TextNode")
                }
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val textNode = rule.onNodeWithText("TextNode", useUnmergedTree = true)
            .fetchSemanticsNode("couldn't find node with text TextNode")
        // wait for the subtree change events from initialization to send
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }

        rule.runOnUiThread {
            // Directly call onLayoutChange because this guarantees short time.
            for (i in 1..10) {
                delegate.onLayoutChange(textNode.layoutNode)
            }
        }

        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }
    }

    @Test
    @Ignore("b/177656801")
    fun layoutNodeWithoutSemanticsLayoutChange_sendThrottledSubtreeEventsForMergedSemanticsNode() {
        val tag = "Toggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(value = checked, onValueChange = { checked = it })
                    .testTag(tag)
            ) {
                BasicText("ToggleableText")
                Box {
                    BasicText("TextNode")
                }
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val textNode = rule.onNodeWithText("TextNode", useUnmergedTree = true)
            .fetchSemanticsNode("couldn't find node with text TextNode")
        // wait for the subtree change events from initialization to send
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }

        rule.runOnUiThread {
            // Directly call onLayoutChange because this guarantees short time.
            for (i in 1..10) {
                // layout change for the parent box node
                delegate.onLayoutChange(textNode.layoutNode.parent!!)
            }
        }

        waitForSubtreeEventToSendAndVerify {
            // One from initialization and one from layout changes.
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }
    }

    @Test
    fun testSemanticsHitTest() {
        val tag = "Toggleable"
        container.setContent {
            var checked by remember { mutableStateOf(true) }
            Box(
                Modifier
                    .toggleable(value = checked, onValueChange = { checked = it })
                    .testTag(tag)
            ) {
                BasicText("ToggleableText")
            }
        }

        val toggleableNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("couldn't find node with tag $tag")
        val toggleableNodeBounds = toggleableNode.boundsInRoot

        val toggleableNodeId = delegate.hitTestSemanticsAt(
            (toggleableNodeBounds.left + toggleableNodeBounds.right) / 2,
            (toggleableNodeBounds.top + toggleableNodeBounds.bottom) / 2,
        )
        assertEquals(toggleableNode.id, toggleableNodeId)
    }

    @Test
    fun testSemanticsHitTest_overlappedChildren() {
        val childOneTag = "OverlappedChildOne"
        val childTwoTag = "OverlappedChildTwo"
        container.setContent {
            Box {
                BasicText(
                    "Child One",
                    Modifier
                        .zIndex(1f)
                        .testTag(childOneTag)
                        .requiredSize(50.dp)
                )
                BasicText(
                    "Child Two",
                    Modifier
                        .testTag(childTwoTag)
                        .requiredSize(50.dp)
                )
            }
        }

        val overlappedChildOneNode = rule.onNodeWithTag(childOneTag)
            .fetchSemanticsNode("couldn't find node with tag $childOneTag")
        val overlappedChildTwoNode = rule.onNodeWithTag(childTwoTag)
            .fetchSemanticsNode("couldn't find node with tag $childTwoTag")
        val overlappedChildNodeBounds = overlappedChildTwoNode.boundsInRoot
        val overlappedChildNodeId = delegate.hitTestSemanticsAt(
            (overlappedChildNodeBounds.left + overlappedChildNodeBounds.right) / 2,
            (overlappedChildNodeBounds.top + overlappedChildNodeBounds.bottom) / 2
        )
        assertEquals(overlappedChildOneNode.id, overlappedChildNodeId)
        assertNotEquals(overlappedChildTwoNode.id, overlappedChildNodeId)
    }

    @Test
    fun testSemanticsHitTest_scrolled() {
        val scrollState = ScrollState(initial = 0)
        val targetTag = "target"
        var scope: CoroutineScope? = null
        container.setContent {
            val actualScope = rememberCoroutineScope()
            SideEffect { scope = actualScope }

            Box {
                Column(
                    Modifier
                        .size(200.dp)
                        .verticalScroll(scrollState)
                ) {
                    BasicText("Before scroll", Modifier.size(200.dp))
                    BasicText("After scroll", Modifier.testTag(targetTag).size(200.dp))
                }
            }
        }

        waitForSubtreeEventToSend()
        assertThat(scrollState.value).isEqualTo(0)

        scope!!.launch {
            // Scroll to the bottom
            scrollState.scrollBy(10000f)
        }
        rule.waitForIdle()

        with(rule.density) {
            assertThat(scrollState.value).isGreaterThan(199.dp.toPx().toInt())
        }

        val childNode = rule.onNodeWithTag(targetTag)
            .fetchSemanticsNode("couldn't find node with tag $targetTag")
        val childNodeBounds = childNode.boundsInRoot
        val hitTestedId = delegate.hitTestSemanticsAt(
            (childNodeBounds.left + childNodeBounds.right) / 2,
            (childNodeBounds.top + childNodeBounds.bottom) / 2
        )
        assertEquals(childNode.id, hitTestedId)
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.P)
    fun testViewInterop_findViewByAccessibilityId() {
        val androidViewTag = "androidView"
        container.setContent {
            Column {
                AndroidView(
                    { context ->
                        LinearLayout(context).apply {
                            addView(TextView(context).apply { text = "Text1" })
                            addView(TextView(context).apply { text = "Text2" })
                        }
                    },
                    Modifier.testTag(androidViewTag)
                )
                BasicText("text")
            }
        }

        val getViewRootImplMethod = View::class.java.getDeclaredMethod("getViewRootImpl")
        getViewRootImplMethod.isAccessible = true
        val rootView = getViewRootImplMethod.invoke(container)

        val forName = Class::class.java.getMethod("forName", String::class.java)
        val getDeclaredMethod = Class::class.java.getMethod(
            "getDeclaredMethod",
            String::class.java,
            arrayOf<Class<*>>()::class.java
        )

        val viewRootImplClass = forName.invoke(null, "android.view.ViewRootImpl") as Class<*>
        val getAccessibilityInteractionControllerMethod = getDeclaredMethod.invoke(
            viewRootImplClass,
            "getAccessibilityInteractionController",
            arrayOf<Class<*>>()
        ) as Method
        getAccessibilityInteractionControllerMethod.isAccessible = true
        val accessibilityInteractionController =
            getAccessibilityInteractionControllerMethod.invoke(rootView)

        val accessibilityInteractionControllerClass =
            forName.invoke(null, "android.view.AccessibilityInteractionController") as Class<*>
        val findViewByAccessibilityIdMethod =
            getDeclaredMethod.invoke(
                accessibilityInteractionControllerClass,
                "findViewByAccessibilityId",
                arrayOf<Class<*>>(Int::class.java)
            ) as Method
        findViewByAccessibilityIdMethod.isAccessible = true

        val androidView = rule.onNodeWithTag(androidViewTag)
            .fetchSemanticsNode("can't find node with tag $androidViewTag")
        val viewGroup = androidComposeView.androidViewsHandler
            .layoutNodeToHolder[androidView.layoutNode]!!.view as ViewGroup
        val getAccessibilityViewIdMethod = View::class.java
            .getDeclaredMethod("getAccessibilityViewId")
        getAccessibilityViewIdMethod.isAccessible = true

        val textTwo = viewGroup.getChildAt(1)
        val textViewTwoId = getAccessibilityViewIdMethod.invoke(textTwo)
        val foundView = findViewByAccessibilityIdMethod.invoke(
            accessibilityInteractionController,
            textViewTwoId
        )
        assertNotNull(foundView)
        assertEquals(textTwo, foundView)
    }

    @Test
    fun testViewInterop_viewChildExists() {
        val colTag = "ColTag"
        val buttonText = "button text"
        container.setContent {
            Column(Modifier.testTag(colTag)) {
                AndroidView(::Button) {
                    it.text = buttonText
                    it.setOnClickListener {}
                }
                BasicText("text")
            }
        }

        val colSemanticsNode = rule.onNodeWithTag(colTag)
            .fetchSemanticsNode("can't find node with tag $colTag")
        val colAccessibilityNode = provider.createAccessibilityNodeInfo(colSemanticsNode.id)
        assertEquals(2, colAccessibilityNode.childCount)
        assertEquals(2, colSemanticsNode.children.size)
        val buttonHolder = androidComposeView.androidViewsHandler
            .layoutNodeToHolder[colSemanticsNode.children[0].layoutNode]
        assertNotNull(buttonHolder)
        assertEquals(
            ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES,
            buttonHolder!!.importantForAccessibility
        )
        assertEquals(buttonText, (buttonHolder.getChildAt(0) as Button).text)
    }

    @Test
    fun testViewInterop_hoverEnterExit() {
        val colTag = "ColTag"
        val textTag = "TextTag"
        val buttonText = "button text"
        container.setContent {
            Column(Modifier.testTag(colTag)) {
                AndroidView(::Button) {
                    it.text = buttonText
                    it.setOnClickListener {}
                }
                BasicText(text = "text", modifier = Modifier.testTag(textTag))
            }
        }

        val colSemanticsNode = rule.onNodeWithTag(colTag)
            .fetchSemanticsNode("can't find node with tag $colTag")
        rule.runOnUiThread {
            val bounds = colSemanticsNode.children[0].boundsInRoot
            val hoverEnter = MotionEvent.obtain(
                0 /* downTime */, 0 /* eventTime */,
                ACTION_HOVER_ENTER, (bounds.left + bounds.right) / 2 /* x */,
                (bounds.top + bounds.bottom) / 2/* y */, 0 /* metaState*/
            )
            hoverEnter.source = InputDevice.SOURCE_CLASS_POINTER
            assertTrue(androidComposeView.dispatchHoverEvent(hoverEnter))
            assertEquals(
                AndroidComposeViewAccessibilityDelegateCompat.InvalidId,
                delegate.hoveredVirtualViewId
            )
        }
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        it.eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
                    }
                )
            )
        }

        val textNode = rule.onNodeWithTag(textTag)
            .fetchSemanticsNode("can't find node with tag $textTag")
        rule.runOnUiThread {
            val bounds = textNode.boundsInRoot
            val hoverEnter = MotionEvent.obtain(
                0 /* downTime */, 0 /* eventTime */,
                ACTION_HOVER_MOVE, (bounds.left + bounds.right) / 2 /* x */,
                (bounds.top + bounds.bottom) / 2/* y */, 0 /* metaState*/
            )
            hoverEnter.source = InputDevice.SOURCE_CLASS_POINTER
            assertTrue(androidComposeView.dispatchHoverEvent(hoverEnter))
            assertEquals(
                textNode.id,
                delegate.hoveredVirtualViewId
            )
        }
        // verify hover exit accessibility event is sent from the previously hovered view
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        it.eventType == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT
                    }
                )
            )
        }
    }

    @Test
    fun testAccessibilityNodeInfoTreePruned_completelyCovered() {
        val parentTag = "ParentForOverlappedChildren"
        val childOneTag = "OverlappedChildOne"
        val childTwoTag = "OverlappedChildTwo"
        container.setContent {
            Box(Modifier.testTag(parentTag)) {
                BasicText(
                    "Child One",
                    Modifier
                        .zIndex(1f)
                        .testTag(childOneTag)
                        .requiredSize(50.dp)
                )
                BasicText(
                    "Child Two",
                    Modifier
                        .testTag(childTwoTag)
                        .requiredSize(50.dp)
                )
            }
        }

        val parentNode = rule.onNodeWithTag(parentTag)
            .fetchSemanticsNode("couldn't find node with tag $parentTag")
        val overlappedChildOneNode = rule.onNodeWithTag(childOneTag)
            .fetchSemanticsNode("couldn't find node with tag $childOneTag")
        val overlappedChildTwoNode = rule.onNodeWithTag(childTwoTag)
            .fetchSemanticsNode("couldn't find node with tag $childTwoTag")
        assertEquals(1, provider.createAccessibilityNodeInfo(parentNode.id).childCount)
        assertEquals(
            "Child One",
            provider.createAccessibilityNodeInfo(overlappedChildOneNode.id).text.toString()
        )
        assertNull(provider.createAccessibilityNodeInfo(overlappedChildTwoNode.id))
    }

    @Test
    fun testAccessibilityNodeInfoTreePruned_partiallyCovered() {
        val parentTag = "parent"
        val density = Density(2f)
        container.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                Box(Modifier.testTag(parentTag)) {
                    BasicText(
                        "Child One",
                        Modifier
                            .zIndex(1f)
                            .requiredSize(100.dp)
                    )
                    BasicText(
                        "Child Two",
                        Modifier.requiredSize(200.dp, 100.dp)
                    )
                }
            }
        }

        val parentNode = rule.onNodeWithTag(parentTag)
            .fetchSemanticsNode("couldn't find node with tag $parentTag")
        assertEquals(2, provider.createAccessibilityNodeInfo(parentNode.id).childCount)

        val childTwoNode = rule.onNodeWithText("Child Two")
            .fetchSemanticsNode("couldn't find node with text Child Two")
        val childTwoBounds = Rect()
        provider.createAccessibilityNodeInfo(childTwoNode.id)
            .getBoundsInScreen(childTwoBounds)
        val expectedSize: Int
        with(density) {
            expectedSize = 100.dp.roundToPx()
        }
        assertEquals(expectedSize, childTwoBounds.height())
        assertEquals(expectedSize, childTwoBounds.width())
    }

    @Test
    fun testPaneAppear() {
        val paneTag = "Pane"
        var isPaneVisible by mutableStateOf(false)
        val paneTestTitle by mutableStateOf("pane title")

        container.setContent {
            if (isPaneVisible) {
                Box(
                    Modifier
                        .testTag(paneTag)
                        .semantics { paneTitle = paneTestTitle }
                ) {}
            }
        }

        rule.onNodeWithTag(paneTag).assertDoesNotExist()

        isPaneVisible = true
        rule.onNodeWithTag(paneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    "pane title"
                )
            )
            .assertIsDisplayed()
        waitForSubtreeEventToSend()
        val paneNode = rule.onNodeWithTag(paneTag).fetchSemanticsNode()
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == paneNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED
                    }
                )
            )
        }
    }

    @Test
    fun testPaneTitleChange() {
        val paneTag = "Pane"
        var isPaneVisible by mutableStateOf(false)
        var paneTestTitle by mutableStateOf("pane title")

        container.setContent {
            if (isPaneVisible) {
                Box(
                    Modifier
                        .testTag(paneTag)
                        .semantics { paneTitle = paneTestTitle }
                ) {}
            }
        }

        rule.onNodeWithTag(paneTag).assertDoesNotExist()

        isPaneVisible = true
        rule.onNodeWithTag(paneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    "pane title"
                )
            )
            .assertIsDisplayed()
        waitForSubtreeEventToSend()

        paneTestTitle = "new pane title"
        rule.onNodeWithTag(paneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    "new pane title"
                )
            )
        val paneNode = rule.onNodeWithTag(paneTag).fetchSemanticsNode()
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == paneNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE
                    }
                )
            )
        }
    }

    @Test
    fun testPaneDisappear() {
        val paneTag = "Pane"
        var isPaneVisible by mutableStateOf(false)
        val paneTestTitle by mutableStateOf("pane title")

        container.setContent {
            if (isPaneVisible) {
                Box(Modifier.testTag(paneTag).semantics { paneTitle = paneTestTitle }) {}
            }
        }

        rule.onNodeWithTag(paneTag).assertDoesNotExist()

        isPaneVisible = true
        rule.onNodeWithTag(paneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    "pane title"
                )
            )
            .assertIsDisplayed()
        waitForSubtreeEventToSend()

        isPaneVisible = false
        rule.onNodeWithTag(paneTag).assertDoesNotExist()
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED
                    }
                )
            )
        }
    }

    @Test
    fun testEventForPasswordTextField() {
        val tag = "TextField"
        container.setContent {
            BasicTextField(
                modifier = Modifier.testTag(tag),
                value = "value",
                onValueChange = {},
                visualTransformation = PasswordVisualTransformation()
            )
        }

        val textFieldNode = rule.onNodeWithTag(tag)
            .fetchSemanticsNode("Couldn't fetch node with tag $tag")
        val event = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        )

        assertTrue(event.isPassword)
    }

    @Test
    fun testLayerParamChange_setCorrectBounds_syntaxOne() {
        var scale by mutableStateOf(1f)
        container.setContent {
            // testTag must not be on the same node with graphicsLayer, otherwise we will have
            // semantics change notification.
            Box(Modifier.graphicsLayer(scaleX = scale, scaleY = scale).requiredSize(300.dp)) {
                Box(Modifier.matchParentSize().testTag("node"))
            }
        }

        val node = rule.onNodeWithTag("node").fetchSemanticsNode()
        var info: AccessibilityNodeInfo = AccessibilityNodeInfo.obtain()
        rule.runOnUiThread {
            info = provider.createAccessibilityNodeInfo(node.id)
        }
        with(rule.density) {
            val size = 300.dp.roundToPx()
            val rect = Rect()
            info.getBoundsInScreen(rect)
            assertEquals(size, rect.width())
            assertEquals(size, rect.height())
        }

        scale = 0.5f
        info.recycle()
        rule.runOnIdle {
            info = provider.createAccessibilityNodeInfo(node.id)
        }
        with(rule.density) {
            val size = 150.dp.roundToPx()
            val rect = Rect()
            info.getBoundsInScreen(rect)
            assertEquals(size, rect.width())
            assertEquals(size, rect.height())
        }
    }

    @Test
    fun testLayerParamChange_setCorrectBounds_syntaxTwo() {
        var scale by mutableStateOf(1f)
        container.setContent {
            // testTag must not be on the same node with graphicsLayer, otherwise we will have
            // semantics change notification.
            Box(
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.requiredSize(300.dp)
            ) {
                Box(Modifier.matchParentSize().testTag("node"))
            }
        }

        val node = rule.onNodeWithTag("node").fetchSemanticsNode()
        var info: AccessibilityNodeInfo = AccessibilityNodeInfo.obtain()
        rule.runOnUiThread {
            info = provider.createAccessibilityNodeInfo(node.id)
        }
        with(rule.density) {
            val size = 300.dp.roundToPx()
            val rect = Rect()
            info.getBoundsInScreen(rect)
            assertEquals(size, rect.width())
            assertEquals(size, rect.height())
        }

        scale = 0.5f
        info.recycle()
        rule.runOnIdle {
            info = provider.createAccessibilityNodeInfo(node.id)
        }
        with(rule.density) {
            val size = 150.dp.roundToPx()
            val rect = Rect()
            info.getBoundsInScreen(rect)
            assertEquals(size, rect.width())
            assertEquals(size, rect.height())
        }
    }

    @Test
    fun testDialog_setCorrectBounds() {
        var dialogComposeView: AndroidComposeView? = null
        container.setContent {
            Dialog(onDismissRequest = {}) {
                dialogComposeView = LocalView.current as AndroidComposeView
                delegate = ViewCompat.getAccessibilityDelegate(dialogComposeView!!) as
                    AndroidComposeViewAccessibilityDelegateCompat
                provider = delegate.getAccessibilityNodeProvider(dialogComposeView).provider
                    as AccessibilityNodeProvider

                Box(Modifier.size(300.dp)) {
                    BasicText(
                        text = "text",
                        modifier = Modifier.offset(100.dp, 100.dp).fillMaxSize()
                    )
                }
            }
        }

        val textNode = rule.onNodeWithText("text").fetchSemanticsNode()
        var info: AccessibilityNodeInfo = AccessibilityNodeInfo.obtain()
        rule.runOnUiThread {
            info = provider.createAccessibilityNodeInfo(textNode.id)
        }

        val viewPosition = intArrayOf(0, 0)
        dialogComposeView!!.getLocationOnScreen(viewPosition)
        with(rule.density) {
            val offset = 100.dp.roundToPx()
            val size = 200.dp.roundToPx()
            val textPositionOnScreenX = viewPosition[0] + offset
            val textPositionOnScreenY = viewPosition[1] + offset

            val textRect = Rect()
            info.getBoundsInScreen(textRect)
            assertEquals(
                Rect(
                    textPositionOnScreenX,
                    textPositionOnScreenY,
                    textPositionOnScreenX + size,
                    textPositionOnScreenY + size
                ),
                textRect
            )
        }
    }

    @Test
    fun testContentDescription_notMergingDescendants_withOwnContentDescription() {
        val tag = "Column"
        container.setContent {
            Column(Modifier.semantics { contentDescription = "Column" }.testTag(tag)) {
                BasicText("Text")
                Box(Modifier.size(100.dp).semantics { contentDescription = "Box" })
            }
        }

        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val info = provider.createAccessibilityNodeInfo(node.id)

        assertEquals("Column", info.contentDescription)
    }

    @Test
    fun testContentDescription_notMergingDescendants_withoutOwnContentDescription() {
        val tag = "Column"
        container.setContent {
            Column(Modifier.semantics {}.testTag(tag)) {
                BasicText("Text")
                Box(Modifier.size(100.dp).semantics { contentDescription = "Box" })
            }
        }

        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val info = provider.createAccessibilityNodeInfo(node.id)

        assertEquals(null, info.contentDescription)
    }

    @Test
    fun testRole_doesNotMerge() {
        container.setContent {
            Row(Modifier.semantics(true) {}.testTag("Row")) {
                Box(Modifier.size(100.dp).semantics { role = Role.Button })
                Box(Modifier.size(100.dp).semantics { role = Role.Image })
            }
        }

        val node = rule.onNodeWithTag("Row").fetchSemanticsNode()
        val info = provider.createAccessibilityNodeInfo(node.id)

        assertEquals(AndroidComposeViewAccessibilityDelegateCompat.ClassName, info.className)
    }

    @Test
    fun testReportedBounds_clickableNode_includesPadding() {
        val size = 100.dp
        container.setContent {
            Column {
                Box(
                    Modifier
                        .testTag("tag")
                        .clickable {}
                        .size(size)
                        .padding(10.dp)
                        .semantics {
                            contentDescription = "Button"
                        }
                )
            }
        }

        val node = rule.onNodeWithTag("tag").fetchSemanticsNode()
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(node.id)

        val rect = android.graphics.Rect()
        accessibilityNodeInfo.getBoundsInScreen(rect)
        val resultWidth = rect.right - rect.left
        val resultHeight = rect.bottom - rect.top

        with(rule.density) {
            assertEquals(size.roundToPx(), resultWidth)
            assertEquals(size.roundToPx(), resultHeight)
        }
    }

    @Test
    fun testReportedBounds_clickableNode_excludesPadding() {
        val size = 100.dp
        val density = Density(2f)
        container.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                Column {
                    Box(
                        Modifier
                            .testTag("tag")
                            .semantics { contentDescription = "Test" }
                            .size(size)
                            .padding(10.dp)
                            .clickable {}
                    )
                }
            }
        }

        val node = rule.onNodeWithTag("tag").fetchSemanticsNode()
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(node.id)

        val rect = android.graphics.Rect()
        accessibilityNodeInfo.getBoundsInScreen(rect)
        val resultWidth = rect.right - rect.left
        val resultHeight = rect.bottom - rect.top

        with(density) {
            assertEquals((size - 20.dp).roundToPx(), resultWidth)
            assertEquals((size - 20.dp).roundToPx(), resultHeight)
        }
    }

    @Test
    fun testReportedBounds_withClearAndSetSemantics() {
        val size = 100.dp
        container.setContent {
            Column {
                Box(
                    Modifier
                        .testTag("tag")
                        .size(size)
                        .padding(10.dp)
                        .clearAndSetSemantics {}
                        .clickable {}
                )
            }
        }

        val node = rule.onNodeWithTag("tag").fetchSemanticsNode()
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(node.id)

        val rect = android.graphics.Rect()
        accessibilityNodeInfo.getBoundsInScreen(rect)
        val resultWidth = rect.right - rect.left
        val resultHeight = rect.bottom - rect.top

        with(rule.density) {
            assertEquals(size.roundToPx(), resultWidth)
            assertEquals(size.roundToPx(), resultHeight)
        }
    }

    @Test
    fun testReportedBounds_withTwoClickable_outermostWins() {
        val size = 100.dp
        container.setContent {
            Column {
                Box(
                    Modifier
                        .testTag("tag")
                        .clickable {}
                        .size(size)
                        .padding(10.dp)
                        .clickable {}
                )
            }
        }

        val node = rule.onNodeWithTag("tag").fetchSemanticsNode()
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(node.id)

        val rect = android.graphics.Rect()
        accessibilityNodeInfo.getBoundsInScreen(rect)
        val resultWidth = rect.right - rect.left
        val resultHeight = rect.bottom - rect.top

        with(rule.density) {
            assertEquals(size.roundToPx(), resultWidth)
            assertEquals(size.roundToPx(), resultHeight)
        }
    }

    @Test
    fun testReportedBounds_outerMostSemanticsUsed() {
        val size = 100.dp
        container.setContent {
            Column {
                Box(
                    Modifier
                        .testTag("tag")
                        .semantics { contentDescription = "Test1" }
                        .size(size)
                        .padding(10.dp)
                        .semantics { contentDescription = "Test2" }
                )
            }
        }

        val node = rule.onNodeWithTag("tag").fetchSemanticsNode()
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(node.id)

        val rect = android.graphics.Rect()
        accessibilityNodeInfo.getBoundsInScreen(rect)
        val resultWidth = rect.right - rect.left
        val resultHeight = rect.bottom - rect.top

        with(rule.density) {
            assertEquals(size.roundToPx(), resultWidth)
            assertEquals(size.roundToPx(), resultHeight)
        }
    }

    @Test
    fun testReportedBounds_withOffset() {
        val size = 100.dp
        val offset = 10.dp
        val density = Density(1f)
        container.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                Column {
                    Box(
                        Modifier
                            .size(size)
                            .offset(offset, offset)
                            .testTag("tag")
                            .semantics { contentDescription = "Test" }
                    )
                }
            }
        }

        val node = rule.onNodeWithTag("tag").fetchSemanticsNode()
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(node.id)

        val rect = android.graphics.Rect()
        accessibilityNodeInfo.getBoundsInScreen(rect)
        val resultWidth = rect.right - rect.left
        val resultHeight = rect.bottom - rect.top
        val resultInLocalCoords = androidComposeView.screenToLocal(rect.topLeftToOffset())

        with(density) {
            assertEquals(size.roundToPx(), resultWidth)
            assertEquals(size.roundToPx(), resultHeight)
            assertEquals(10.dp.toPx(), resultInLocalCoords.x)
            assertEquals(10.dp.toPx(), resultInLocalCoords.y)
        }
    }

    @Test
    fun testSemanticsNodePositionAndBounds_doesNotThrow_whenLayoutNodeNotAttached() {
        var emitNode by mutableStateOf(true)
        rule.setContent {
            if (emitNode) {
                Box(Modifier.size(100.dp).testTag("tag"))
            }
        }

        val semanticNode = rule.onNodeWithTag("tag").fetchSemanticsNode()
        rule.runOnIdle {
            emitNode = false
        }

        rule.runOnIdle {
            assertEquals(Offset.Zero, semanticNode.positionInRoot)
            assertEquals(Offset.Zero, semanticNode.positionInWindow)
            assertEquals(androidx.compose.ui.geometry.Rect.Zero, semanticNode.boundsInRoot)
            assertEquals(androidx.compose.ui.geometry.Rect.Zero, semanticNode.boundsInWindow)
        }
    }

    @Test
    fun testSemanticsSort_doesNotThrow_whenLayoutNodeWrapperNotAttached() {
        rule.setContent {
            Box(Modifier.size(100.dp).testTag("parent")) {
                Box(Modifier.size(100.dp).testTag("child"))
            }
        }

        val parent = rule.onNodeWithTag("parent").fetchSemanticsNode()
        val child = rule.onNodeWithTag("child").fetchSemanticsNode()

        rule.runOnIdle {
            child.layoutNode.innerLayoutNodeWrapper.detach()
            child.outerSemanticsNodeWrapper.detach()
        }

        rule.runOnIdle {
            assertEquals(1, parent.unmergedChildren(true).size)
            assertEquals(0, child.unmergedChildren(true).size)
        }
    }

    @Test
    fun testSemanticsSort_doesNotThrow_whenLayoutNodeWrapperNotAttached_compare() {
        rule.setContent {
            Box(Modifier.size(100.dp).testTag("parent")) {
                Box(Modifier.size(100.dp).testTag("child1")) {
                    Box(Modifier.size(50.dp).testTag("grandChild1"))
                }
                Box(Modifier.size(100.dp).testTag("child2")) {
                    Box(Modifier.size(50.dp).testTag("grandChild2"))
                }
            }
        }

        val parent = rule.onNodeWithTag("parent").fetchSemanticsNode()
        val grandChild1 = rule.onNodeWithTag("grandChild1").fetchSemanticsNode()
        val grandChild2 = rule.onNodeWithTag("grandChild2").fetchSemanticsNode()
        rule.runOnIdle {
            grandChild1.layoutNode.innerLayoutNodeWrapper.detach()
            grandChild1.outerSemanticsNodeWrapper.detach()
            grandChild2.layoutNode.innerLayoutNodeWrapper.detach()
            grandChild2.outerSemanticsNodeWrapper.detach()
        }

        rule.runOnIdle {
            assertEquals(2, parent.unmergedChildren(true).size)
        }
    }

    @Test
    fun testFakeNodeCreated_forContentDescriptionSemantics() {
        container.setContent {
            Column(
                Modifier
                    .semantics(true) { contentDescription = "Test" }
                    .testTag("Column")
            ) {
                BasicText("Text")
                Box(Modifier.size(100.dp).semantics { contentDescription = "Hello" })
            }
        }

        val columnNode = rule.onNodeWithTag("Column", true).fetchSemanticsNode()
        val firstChild = columnNode.children.firstOrNull()
        assertNotNull(firstChild)
        assertTrue(firstChild!!.isFake)
        assertEquals(
            firstChild.unmergedConfig.getOrNull(SemanticsProperties.ContentDescription)!!.first(),
            "Test"
        )
    }

    @Test
    fun testFakeNode_createdForButton() {
        container.setContent {
            Column(Modifier.clickable(role = Role.Button) {}.testTag("button")) {
                BasicText("Text")
            }
        }

        val buttonNode = rule.onNodeWithTag("button", true).fetchSemanticsNode()
        val lastChild = buttonNode.children.lastOrNull()
        assertNotNull("Button has no children", lastChild)
        assertTrue("Last child should be fake Button role node", lastChild!!.isFake)
        assertEquals(
            lastChild.unmergedConfig.getOrNull(SemanticsProperties.Role),
            Role.Button
        )
    }

    @Test
    fun testFakeNode_notCreatedForButton_whenNoChildren() {
        container.setContent {
            Box(Modifier.size(100.dp).clickable(role = Role.Button) {}.testTag("button"))
        }
        val buttonNode = rule.onNodeWithTag("button").fetchSemanticsNode()
        assertFalse(buttonNode.unmergedChildren().any { it.isFake })
        val info = provider.createAccessibilityNodeInfo(buttonNode.id)
        assertEquals("android.widget.Button", info.className)
    }

    @Test
    fun testContentDescription_withFakeNode_mergedCorrectly() {
        val testTag = "Column"
        container.setContent {
            Column(
                Modifier
                    .testTag(testTag)
                    .semantics(true) { contentDescription = "Hello" }
            ) {
                Box(Modifier.semantics { contentDescription = "World" })
            }
        }

        rule.onNodeWithTag(testTag).assertContentDescriptionEquals("Hello", "World")
    }

    @Test
    fun testImageRole_notSet_whenAncestorMergesDescendants() {
        container.setContent {
            Column(Modifier.semantics(true) { }) {
                Image(ImageBitmap(100, 100), "Image", Modifier.testTag("image"))
            }
        }

        val imageNode = rule.onNodeWithTag("image", true).fetchSemanticsNode()
        val imageInfo = provider.createAccessibilityNodeInfo(imageNode.id)
        assertEquals(ClassName, imageInfo.className)
    }

    @Test
    fun testImageRole_set_whenAncestorDoesNotMerge() {
        container.setContent {
            Column(Modifier.semantics { isEnabled() }) {
                Image(ImageBitmap(100, 100), "Image", Modifier.testTag("image"))
            }
        }

        val imageNode = rule.onNodeWithTag("image", true).fetchSemanticsNode()
        val imageInfo = provider.createAccessibilityNodeInfo(imageNode.id)
        assertEquals("android.widget.ImageView", imageInfo.className)
    }

    @Test
    fun testImageRole_set_whenImageItseldMergesDescendants() {
        container.setContent {
            Column(Modifier.semantics(true) {}) {
                Image(
                    ImageBitmap(100, 100),
                    "Image",
                    Modifier.testTag("image").semantics(true) { /* imitate clickable node */ }
                )
            }
        }

        val imageNode = rule.onNodeWithTag("image", true).fetchSemanticsNode()
        val imageInfo = provider.createAccessibilityNodeInfo(imageNode.id)
        assertEquals("android.widget.ImageView", imageInfo.className)
    }

    private fun eventIndex(list: List<AccessibilityEvent>, event: AccessibilityEvent): Int {
        for (i in list.indices) {
            if (ReflectionEquals(list[i], null).matches(event)) {
                return i
            }
        }
        return -1
    }

    private fun containsEvent(list: List<AccessibilityEvent>, event: AccessibilityEvent): Boolean {
        return eventIndex(list, event) != -1
    }

    private fun getAccessibilityEventSourceSemanticsNodeId(event: AccessibilityEvent): Int {
        val getSourceNodeIdMethod = AccessibilityRecord::class.java
            .getDeclaredMethod("getSourceNodeId")
        getSourceNodeIdMethod.isAccessible = true
        return (getSourceNodeIdMethod.invoke(event) as Long shr 32).toInt()
    }

    private fun waitForSubtreeEventToSendAndVerify(verify: () -> Unit) {
        // TODO(aelias): Make this wait after the 100ms delay to check the second batch is also correct
        rule.waitForIdle()
        verify()
    }

    private fun waitForSubtreeEventToSend() {
        // When the subtree events are sent, we will also update our previousSemanticsNodes,
        // which will affect our next accessibility events from semantics tree comparison.
        rule.mainClock.advanceTimeBy(5000)
        rule.waitForIdle()
    }

    private fun createMovementGranularityCharacterArgs(): Bundle {
        return Bundle().apply {
            this.putInt(
                AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER
            )
            this.putBoolean(
                AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                false
            )
        }
    }

    private fun createSelectionChangedFromIndexOneToOneEvent(
        textNode: SemanticsNode
    ): AccessibilityEvent {
        return delegate.createEvent(
            textNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ).apply {
            this.fromIndex = 1
            this.toIndex = 1
            getTraversedText(textNode)?.let {
                this.itemCount = it.length
                this.text.add(it)
            }
        }
    }

    private fun createCharacterTraverseFromIndexZeroEvent(
        textNode: SemanticsNode
    ): AccessibilityEvent {
        return delegate.createEvent(
            textNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY
        ).apply {
            this.fromIndex = 0
            this.toIndex = 1
            this.action = AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            this.movementGranularity = AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER
            getTraversedText(textNode)?.let { this.text.add(it) }
        }
    }

    private fun getTraversedText(textNode: SemanticsNode): String? {
        return (
            textNode.config.getOrNull(SemanticsProperties.EditableText)?.text
                ?: textNode.config.getOrNull(SemanticsProperties.Text)?.joinToString(",")
            )
    }
}

private fun Rect.topLeftToOffset() = Offset(this.left.toFloat(), this.top.toFloat())
