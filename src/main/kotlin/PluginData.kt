package tk.mcsog

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object PluginData: AutoSavePluginData("Data") {
    @ValueDescription("群数据")
    val groupData: MutableMap<Long, GroupInfo> by value()

    @ValueDescription("规则数据")
    val ruleData: MutableMap<String, RuleInfo> by value()

    @ValueDescription("验证数据")
    val validData: MutableMap<String, ValidInfo> by value()
}