package tk.mcsog

import kotlinx.serialization.Serializable

@Serializable
data class GroupInfo(
    val groupNum: Long,
    var rule: String,
    val inviteChain: MutableMap<Long, Long> = mutableMapOf()
)
