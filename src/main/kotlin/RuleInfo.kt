package tk.mcsog

import kotlinx.serialization.Serializable

@Serializable
data class RuleInfo(
    val ruleName: String,
    val admin: ArrayList<Long> = arrayListOf(PluginConf.manager),
    var msgControl: Boolean = false,
    val keyword: ArrayList<String> = arrayListOf(),
    var joinControl: Boolean = false,
    val whiteList: ArrayList<Long> = arrayListOf(),
    val blackList: ArrayList<Long> = arrayListOf()
)
