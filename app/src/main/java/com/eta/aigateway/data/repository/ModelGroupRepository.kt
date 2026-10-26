package com.eta.aigateway.data.repository

import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelGroupRepository(private val db: AppDatabase) {

    suspend fun loadGroups(
        providers: List<ProviderConfig>,
        cache: Map<String, List<String>>
    ): List<ModelGroup> = withContext(Dispatchers.IO) {
        val states = db.modelStateDao().allGroups().associateBy { it.modelId }
        val sourceStates = db.modelStateDao().allSources().associateBy { it.key }
        val customs = db.customModelDao().all()

        val map = LinkedHashMap<String, MutableList<ModelSource>>()

        providers.filter { it.enabled && it.baseUrl.isNotBlank() }.forEach { p ->
            (cache[p.id] ?: emptyList()).forEach { mid ->
                val key = "${p.id}|$mid"
                map.getOrPut(mid) { mutableListOf() }.add(
                    ModelSource(
                        providerId = p.id,
                        providerName = p.name,
                        modelId = mid,
                        protocol = p.protocol,
                        enabled = sourceStates[key]?.enabled ?: true
                    )
                )
            }
        }

        customs.forEach { cm ->
            val p = providers.firstOrNull { it.id == cm.providerId } ?: return@forEach
            val list = map.getOrPut(cm.modelId) { mutableListOf() }
            if (list.none { it.providerId == p.id }) {
                list.add(
                    ModelSource(
                        providerId = p.id,
                        providerName = p.name,
                        modelId = cm.modelId,
                        protocol = cm.protocol,
                        enabled = true
                    )
                )
            }
        }

        map.map { (mid, sources) ->
            val st = states[mid]
            ModelGroup(
                modelId = mid,
                displayName = st?.displayName?.ifBlank { ModelClassifier.prettyName(mid) }
                    ?: customs.firstOrNull { it.modelId == mid }?.displayName
                    ?: ModelClassifier.prettyName(mid),
                sources = sources,
                enabled = st?.enabled ?: false,
                tags = st?.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                custom = customs.any { it.modelId == mid },
                vendor = ModelClassifier.vendorOf(mid)
            )
        }.sortedWith(
            compareByDescending<ModelGroup> { it.enabled }
                .thenByDescending { ModelClassifier.isKnownVendor(it.modelId) }
                .thenBy { it.modelId }
        )
    }

    suspend fun setGroupEnabled(modelId: String, enabled: Boolean) {
        val cur = db.modelStateDao().allGroups().firstOrNull { it.modelId == modelId }
        db.modelStateDao().upsert((cur ?: ModelState(modelId)).copy(enabled = enabled))
    }

    suspend fun setGroupTags(modelId: String, tags: List<String>) {
        val cur = db.modelStateDao().allGroups().firstOrNull { it.modelId == modelId }
        db.modelStateDao().upsert((cur ?: ModelState(modelId)).copy(tags = tags.joinToString(",")))
    }

    suspend fun setSourceEnabled(key: String, enabled: Boolean) {
        db.modelStateDao().upsertSource(SourceState(key, enabled))
    }

    suspend fun enableKnownVendors(groups: List<ModelGroup>): Int {
        var n = 0
        groups.forEach { g ->
            if (ModelClassifier.isKnownVendor(g.modelId) && !g.enabled) {
                setGroupEnabled(g.modelId, true)
                n++
            }
        }
        return n
    }

    suspend fun disableAll(groups: List<ModelGroup>) {
        groups.forEach { if (it.enabled) setGroupEnabled(it.modelId, false) }
    }
}
