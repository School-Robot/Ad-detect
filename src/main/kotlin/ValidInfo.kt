package tk.mcsog

import kotlinx.serialization.Serializable

@Serializable
data class ValidInfo (
    val groupNum: Long,
    val qqNum: Long,
    var time: Int = 0,
    val code: String
)