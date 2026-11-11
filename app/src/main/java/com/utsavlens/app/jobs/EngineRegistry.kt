package com.utsavlens.app.jobs

import com.utsavlens.core.EngineIds
import com.utsavlens.engine.api.TransformEngine

/** All engines the UI can offer, in display order. The UI only ever sees [TransformEngine]. */
class EngineRegistry(val engines: List<TransformEngine>) {
    operator fun get(id: String): TransformEngine? = engines.firstOrNull { it.id == id }
    val lite: TransformEngine get() = engines.first { it.id == EngineIds.LITE }
}
