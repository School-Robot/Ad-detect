package tk.mcsog

import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info
import kotlin.random.Random

object AdDetect : KotlinPlugin(
    JvmPluginDescription(
        id = "tk.mcsog.ad-detect",
        name = "Ad Detect",
        version = "0.2.2",
    ) {
        author("MCSOG")
    }
) {
    override fun onEnable() {
        PluginConf.reload()
        PluginData.reload()
        logger.info { "Plugin loaded" }
        globalEventChannel().subscribeAlways<GroupMessageEvent> {
            val m: String = this.message.serializeToMiraiCode()
            PluginData.groupData[this.group.id]?.let { it1 ->
                PluginData.ruleData[it1.rule]?.let { it2 ->
                    if (this.sender.id in it2.blackList) {
                        this.message.let {
                            if (this.sender.permission.level < this.group.botPermission.level){
                                it.recall()
                            }else{
                                group.sendMessage(PlainText("权限不足，请撤回")+At(this.sender.id)+PlainText(" "+this.sender.id.toString()))
                            }
                        }
                        this.group.sendMessage("检测到黑名单成员，已移出群")
                        //this.group.members[this.sender.id]?.kick("检测到黑名单成员，已移出群")
                        this.group.sendMessage("正在检测邀请链并移除")
                        launch {
                            chainDetect(it.group, it.sender.id, it2, it1)
                            blackDetect(it2)
                        }
                        return@subscribeAlways
                    }else if (this.sender.id in it2.whiteList) {
                        return@subscribeAlways
                    }else if (this.sender.id !in it2.admin){
                        if (it2.msgControl) {
                            if (it2.keyword.any { it3 -> this.message.serializeToMiraiCode().contains(it3) }) {
                                this.message.let {
                                    if (this.sender.permission.level < this.group.botPermission.level){
                                        it.recall()
                                    }else{
                                        group.sendMessage(PlainText("权限不足，请撤回")+At(this.sender.id)+PlainText(" "+this.sender.id.toString()))
                                    }
                                }
                                this.group.sendMessage("检测到广告关键词，已移出群并加黑名单")
                                it2.blackList.add(this.sender.id)
                                //this.group.members[this.sender.id]?.kick("检测到广告关键词，已移出群")
                                this.group.sendMessage("正在检测邀请链并移除")
                                launch {
                                    chainDetect(it.group, it.sender.id, it2, it1)
                                    blackDetect(it2)
                                }
                                return@subscribeAlways
                            }
                        }
                        if (it2.joinControl) {
                            PluginData.validData[this.group.id.toString() + "-" + this.sender.id.toString()]?.let {
                                if (it.time == 3) {
                                    this.group.sendMessage("未通过加群验证")
                                    this.group.members[this.sender.id]?.kick("未通过加群验证")
                                    it1.inviteChain.remove(this.sender.id)
                                    PluginData.validData.remove(this.group.id.toString() + "-" + this.sender.id.toString())
                                    return@subscribeAlways
                                } else {
                                    if (m == it.code) {
                                        this.group.sendMessage("通过加群验证")
                                        PluginData.validData.remove(this.group.id.toString() + "-" + this.sender.id.toString())
                                        return@subscribeAlways
                                    } else {
                                        it.time += 1
                                        this.group.sendMessage(At(this.sender.id) + PlainText("验证失败，还有${3 - it.time}次机会"))
                                        return@subscribeAlways
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // help
            if (m == "/adhelp"){
                this.group.sendMessage(At(this.sender.id) +PlainText("/adcreate 规则名-创建规则\n/aduse 规则名-使用规则\n/addel 规则名-删除规则\n/adnotify-切换昵称通知\n/adadminadd QQ-添加管理员\n/adadmindel QQ-删除管理员\n/admsg-切换消息监控\n/admsgadd 关键词-添加检测关键词\n/admsgdel 关键词-删除检测关键词\n/adjoin-切换加群监控\n/adwhiteadd QQ-添加白名单\n/adwhitedel QQ-删除白名单\n/adblackadd QQ-添加黑名单\n/adblackdel QQ-删除黑名单\n/adblackdetect-检测黑名单"))
                return@subscribeAlways
            }

            // create
            if (m.startsWith("/adcreate")&&m.length>10){
                val c: Char = m[9]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    if (this.sender.id == PluginConf.manager){
                        PluginData.ruleData[m_split[1]]?:let {
                            PluginData.ruleData[m_split[1]] = RuleInfo(m_split[1])
                            this.group.sendMessage(At(this.sender.id) +PlainText("规则创建成功"))
                            return@subscribeAlways
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则已存在"))
                        return@subscribeAlways
                    }else{
                        this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                        return@subscribeAlways
                    }
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // use
            if (m.startsWith("/aduse")&&m.length>7){
                val c: Char = m[6]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    if (this.sender.id == PluginConf.manager){
                        PluginData.ruleData[m_split[1]]?.let {
                            PluginData.groupData[this.group.id]?.let {
                                it.rule = m_split[1]
                                this.group.sendMessage(At(this.sender.id) +PlainText("规则切换成功"))
                                return@subscribeAlways
                            }
                            PluginData.groupData[this.group.id] = GroupInfo(this.group.id, m_split[1])
                            this.group.sendMessage(At(this.sender.id) +PlainText("规则切换成功"))
                            return@subscribeAlways
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }else{
                        this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                        return@subscribeAlways
                    }
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // del
            if (m.startsWith("/addel")&&m.length>7){
                val c: Char = m[6]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    if (this.sender.id == PluginConf.manager){
                        PluginData.ruleData[m_split[1]]?.let {
                            PluginData.ruleData.remove(m_split[1])
                            PluginData.groupData.forEach {
                                if (it.value.rule == m_split[1]){
                                    PluginData.groupData.remove(it.key)
                                }
                            }
                            this.group.sendMessage(At(this.sender.id) +PlainText("规则删除成功"))
                            return@subscribeAlways
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }else{
                        this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                        return@subscribeAlways
                    }
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // adminadd
            if (m.startsWith("/adadminadd")&&m.length>12){
                val c: Char = m[11]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let {
                        PluginData.ruleData[it.rule]?.let {
                            if (this.sender.id in it.admin){
                                val qq: Long = m_split[1].toLong()
                                when (qq) {
                                    in it.whiteList -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("白名单成员"))
                                        return@subscribeAlways
                                    }
                                    in it.blackList -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("黑名单成员"))
                                        return@subscribeAlways
                                    }
                                    in it.admin -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("管理员已存在"))
                                        return@subscribeAlways
                                    }
                                    else -> {
                                        it.admin.add(qq)
                                        this.group.sendMessage(At(this.sender.id) + PlainText("管理员添加成功"))
                                        return@subscribeAlways
                                    }
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // admindel
            if (m.startsWith("/adadmindel")&&m.length>12){
                val c: Char = m[11]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let {
                        PluginData.ruleData[it.rule]?.let {
                            if (this.sender.id in it.admin){
                                val qq: Long = m_split[1].toLong()
                                if (qq in it.admin){
                                    it.admin.remove(qq)
                                    this.group.sendMessage(At(this.sender.id) + PlainText("管理员删除成功"))
                                    return@subscribeAlways
                                }else {
                                    this.group.sendMessage(At(this.sender.id) + PlainText("管理员不存在"))
                                    return@subscribeAlways
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // msg
            if (m=="/admsg"){
                PluginData.groupData[this.group.id]?.let {
                    PluginData.ruleData[it.rule]?.let {
                        if (this.sender.id in it.admin){
                            it.msgControl = !it.msgControl
                            this.group.sendMessage(At(this.sender.id) +PlainText("消息检测切换成功"))
                            return@subscribeAlways
                        }else{
                            this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                            return@subscribeAlways
                        }
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                    return@subscribeAlways
                }
                this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                return@subscribeAlways
            }

            // msgadd
            if (m.startsWith("/admsgadd")&&m.length>10){
                val c: Char = m[9]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let {
                        PluginData.ruleData[it.rule]?.let {
                            if (this.sender.id in it.admin){
                                if (m_split[1] in it.keyword){
                                    this.group.sendMessage(At(this.sender.id) +PlainText("关键词已存在"))
                                    return@subscribeAlways
                                }else {
                                    it.keyword.add(m_split[1])
                                    this.group.sendMessage(At(this.sender.id) + PlainText("关键词添加成功"))
                                    return@subscribeAlways
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // msgdel
            if (m.startsWith("/admsgdel")&&m.length>10){
                val c: Char = m[9]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let {
                        PluginData.ruleData[it.rule]?.let {
                            if (this.sender.id in it.admin){
                                if (m_split[1] in it.keyword){
                                    it.keyword.remove(m_split[1])
                                    this.group.sendMessage(At(this.sender.id) + PlainText("关键词删除成功"))
                                    return@subscribeAlways
                                }else {
                                    this.group.sendMessage(At(this.sender.id) + PlainText("关键词不存在"))
                                    return@subscribeAlways
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // join
            if (m=="/adjoin"){
                PluginData.groupData[this.group.id]?.let {
                    PluginData.ruleData[it.rule]?.let {
                        if (this.sender.id in it.admin){
                            it.joinControl = !it.joinControl
                            this.group.sendMessage(At(this.sender.id) +PlainText("入群检测切换成功"))
                            return@subscribeAlways
                        }else{
                            this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                            return@subscribeAlways
                        }
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                    return@subscribeAlways
                }
                this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                return@subscribeAlways
            }

            // whiteadd
            if (m.startsWith("/adwhiteadd")&&m.length>12){
                val c: Char = m[11]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let { it1 ->
                        PluginData.ruleData[it1.rule]?.let {
                            if (this.sender.id in it.admin){
                                val qq: Long = m_split[1].toLong()
                                when (qq) {
                                    in it.blackList -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("黑名单成员"))
                                        return@subscribeAlways
                                    }
                                    in it.admin -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("管理员"))
                                        return@subscribeAlways
                                    }
                                    in it.whiteList -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("白名单成员已存在"))
                                        return@subscribeAlways
                                    }
                                    else -> {
                                        it.whiteList.add(qq)
                                        this.group.sendMessage(At(this.sender.id) + PlainText("白名单成员添加成功"))
                                        return@subscribeAlways
                                    }
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // whitedel
            if (m.startsWith("/adwhitedel")&&m.length>12){
                val c: Char = m[11]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let { it1 ->
                        PluginData.ruleData[it1.rule]?.let {
                            if (this.sender.id in it.admin){
                                val qq: Long = m_split[1].toLong()
                                if (qq in it.whiteList){
                                    it.whiteList.remove(qq)
                                    this.group.sendMessage(At(this.sender.id) + PlainText("白名单成员删除成功"))
                                    return@subscribeAlways
                                }else {
                                    this.group.sendMessage(At(this.sender.id) + PlainText("白名单成员不存在"))
                                    return@subscribeAlways
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // blackdetect
            if (m == "/adblackdetect"){
                PluginData.groupData[this.group.id]?.let { it1 ->
                    PluginData.ruleData[it1.rule]?.let {
                        if (this.sender.id in it.admin){
                            this.group.sendMessage(At(this.sender.id) +PlainText("开始检测"))
                            launch {
                                blackDetect(it)
                            }
                            return@subscribeAlways
                        }else{
                            this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                            return@subscribeAlways
                        }
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                    return@subscribeAlways
                }
                return@subscribeAlways
            }

            // blackadd
            if (m.startsWith("/adblackadd")&&m.length>12){
                val c: Char = m[11]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let { it1 ->
                        PluginData.ruleData[it1.rule]?.let {
                            if (this.sender.id in it.admin){
                                val qq: Long = m_split[1].toLong()
                                when (qq) {
                                    in it.whiteList -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("白名单成员"))
                                        return@subscribeAlways
                                    }
                                    in it.admin -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("管理员"))
                                        return@subscribeAlways
                                    }
                                    in it.blackList -> {
                                        this.group.sendMessage(At(this.sender.id) +PlainText("黑名单成员已存在"))
                                        return@subscribeAlways
                                    }
                                    else -> {
                                        it.blackList.add(qq)
                                        this.group.sendMessage(At(this.sender.id) + PlainText("黑名单成员添加成功"))
                                        return@subscribeAlways
                                    }
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // blackdel
            if (m.startsWith("/adblackdel")&&m.length>12){
                val c: Char = m[11]
                val m_split: List<String> = m.split(c)
                if (m_split.size == 2){
                    PluginData.groupData[this.group.id]?.let { it1 ->
                        PluginData.ruleData[it1.rule]?.let {
                            if (this.sender.id in it.admin){
                                val qq: Long = m_split[1].toLong()
                                if (qq in it.blackList){
                                    it.blackList.remove(qq)
                                    this.group.sendMessage(At(this.sender.id) + PlainText("黑名单成员删除成功"))
                                    return@subscribeAlways
                                }else {
                                    this.group.sendMessage(At(this.sender.id) + PlainText("黑名单成员不存在"))
                                    return@subscribeAlways
                                }
                            }else{
                                this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                                return@subscribeAlways
                            }
                        }
                        this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                        return@subscribeAlways
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("未开启广告检测"))
                    return@subscribeAlways
                }else{
                    this.group.sendMessage(At(this.sender.id) +PlainText("格式错误"))
                    return@subscribeAlways
                }
            }

            // notify
            if (m == "/adnotify"){
                PluginData.groupData[this.group.id]?.let { it1 ->
                    PluginData.ruleData[it1.rule]?.let {
                        if (this.sender.id in it.admin){
                            it1.notify = !it1.notify
                            this.group.sendMessage(At(this.sender.id) +PlainText("切换成功"))
                            return@subscribeAlways
                        }else{
                            this.group.sendMessage(At(this.sender.id) +PlainText("权限不足"))
                            return@subscribeAlways
                        }
                    }
                    this.group.sendMessage(At(this.sender.id) +PlainText("规则不存在"))
                    return@subscribeAlways
                }
                return@subscribeAlways
            }
        }

        globalEventChannel().subscribeAlways<MemberJoinEvent> {
            if (it is MemberJoinEvent.Invite){
                PluginData.groupData[this.group.id]?.let { it1 ->
                    it1.inviteChain[this.member.id] = it.invitor.id
                }
            }
            PluginData.groupData[this.group.id]?.let { it1 ->
                PluginData.ruleData[it1.rule]?.let { it2 ->
                    if (this.member.id in it2.blackList){
                        launch {
                            chainDetect(it.group, it.member.id, it2, it1)
                            blackDetect(it2)
                        }
                        return@subscribeAlways
                    }else if (this.member.id !in it2.whiteList){
                        if (this.member.id !in it2.admin){
                            if (it2.joinControl){
                                val a: Int = Random.nextInt(0,5)
                                val b: Int = Random.nextInt(0,5)
                                val c: Int = a + b
                                val valid = ValidInfo(this.group.id, this.member.id, 0, c.toString())
                                this.group.sendMessage(At(this.member.id) + PlainText("请回答：$a + $b = ?"))
                                PluginData.validData[this.group.id.toString()+"-"+this.member.id.toString()] = valid
                            }
                        }
                    }
                }
            }
        }

        globalEventChannel().subscribeAlways<MemberJoinRequestEvent> {
            PluginData.groupData[this.group?.id]?.let { it1 ->
                PluginData.ruleData[it1.rule]?.let { it2 ->
                    when (this.fromId) {
                        in it2.whiteList -> {
                            this.accept()
                            return@subscribeAlways
                        }
                        in it2.admin -> {
                            this.accept()
                            return@subscribeAlways
                        }
                        in it2.blackList -> {
                            this.reject(false, "黑名单成员")
                            this.invitor?.let { mbr ->
                                it2.blackList.add(mbr.id)
                                launch {
                                    chainDetect(it.group!!, mbr.id, it2, it1)
                                    blackDetect(it2)
                                }
                            }
                        }
                        else -> {
                            if (it2.joinControl){
                                this.accept()
                                this.invitorId?.let {
                                    it1.inviteChain[this.fromId] = it
                                }
                                return@subscribeAlways
                            }
                        }
                    }
                }
            }
        }

        globalEventChannel().subscribeAlways<MemberLeaveEvent> {
            PluginData.groupData[this.group.id]?.inviteChain?.remove(this.member.id)
            PluginData.validData[this.group.id.toString()+"-"+this.member.id.toString()]?.let {
                PluginData.validData.remove(this.group.id.toString()+"-"+this.member.id.toString())
            }
        }

        globalEventChannel().subscribeAlways<MemberCardChangeEvent> {
            PluginData.groupData[this.group.id]?.let {
                if (it.notify){
                    this.group.sendMessage(this.member.id.toString()+"修改了群昵称： "+this.origin+" -> "+this.new)
                }
            }
        }
    }

    private suspend fun blackDetect(rule: RuleInfo){
        PluginData.groupData.forEach {  grp ->
            if (grp.value.rule == rule.ruleName){
                Bot.instances.forEach {  bot ->
                    bot.groups[grp.key]?.let { g ->
                        val bl: ArrayList<Long> = rule.blackList
                        bl.forEach {
                            chainDetect(g, it, rule, grp.value)
                        }
                    }
                }
            }
        }
    }
    private suspend fun chainDetect(group: Group, qq: Long, rule: RuleInfo, groupInfo: GroupInfo){
        val temp: ArrayList<Long> = arrayListOf()
        if (qq in rule.admin || qq in rule.whiteList){
            return
        }
        groupInfo.inviteChain[qq]?.let { it2 ->
            groupInfo.inviteChain.remove(qq)
            chainDetect(group, it2, rule, groupInfo)
            //group.members[it2]?.kick("邀请链检测")
            group.members[it2]?.let { it3 ->
                if (it3.permission.level < group.botPermission.level){
                    it3.kick("邀请链检测")
                }else{
                    group.sendMessage(PlainText("权限不足，请移除")+At(it3.id)+PlainText(" "+it3.id.toString()))
                }
            }
            if (it2 !in rule.blackList) {
                rule.blackList.add(it2)
            }
        }
        if (qq !in rule.blackList) {
            rule.blackList.add(qq)
        }
        //group.members[qq]?.kick("")
        group.members[qq]?.let { it3 ->
            if (it3.permission.level < group.botPermission.level){
                it3.kick("")
            }else{
                group.sendMessage(PlainText("权限不足，请移除")+At(it3.id)+PlainText(" "+it3.id.toString()))
            }
        }
        groupInfo.inviteChain.forEach {
            if (it.value == qq){
                temp.add(it.key)
            }
        }
        temp.forEach {
            //groupInfo.inviteChain.remove(it)
            chainDetect(group, it, rule, groupInfo)
            //group.members[it]?.kick("邀请链检测")
            //if (it !in rule.blackList) {
            //    rule.blackList.add(it)
            //}
        }
    }

    override fun onDisable() {
        PluginConf.save()
        PluginData.save()
        logger.info { "Plugin disabled" }
    }
}