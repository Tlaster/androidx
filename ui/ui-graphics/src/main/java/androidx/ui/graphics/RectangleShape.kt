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

package androidx.ui.graphics

import androidx.ui.unit.Density
import androidx.ui.unit.PxSize
import androidx.ui.unit.toRect

/**
 * A shape describing the rectangle.
 */
val RectangleShape: Shape = object : Shape {
    override fun createOutline(size: PxSize, density: Density) =
        Outline.Rectangle(size.toRect())
}
