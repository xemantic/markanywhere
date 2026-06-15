/*
 * Copyright 2026 Kazimierz Pogoda / Xemantic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xemantic.markanywhere.html

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.SemanticEventDump
import com.xemantic.markanywhere.markanywhereJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

fun dumpFlow(
    json: String
): Flow<SemanticEvent> = markanywhereJson.decodeFromString<SemanticEventDump>(
    json
).events.asFlow()
