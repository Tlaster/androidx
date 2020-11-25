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

package androidx.compose.foundation.lazy

/**
 * The result of the measure pass for lazy list layout.
 */
internal class LazyListMeasureResult(
    /** Calculated size for the main axis.*/
    val mainAxisSize: Int,
    /** Calculated size for the cross axis.*/
    val crossAxisSize: Int,
    /** The list of items to be placed during the layout pass.*/
    val items: List<LazyMeasuredItem>,
    /** The main axis offset to be used for the first item in the [items] list.*/
    val itemsScrollOffset: Int
)
