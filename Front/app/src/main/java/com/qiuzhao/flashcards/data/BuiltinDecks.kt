package com.qiuzhao.flashcards.data

data class BuiltinDeck(val chapter: Int, val name: String, val cards: List<CardDraft>)

/** Concise, original paraphrases of the ten chapters; no source prose is bundled. */
object BuiltinDecks {
    val all = listOf(
        BuiltinDeck(1, "第 1 章 · Agent 基础", listOf(
            CardDraft("一个可工作的 AI Agent 可以概括为哪三个部分？", "LLM 负责推理与生成；上下文提供任务状态和信息；工具让它能感知、执行与改变外部世界。三者缺一不可。"),
            CardDraft("为什么 Harness（运行外壳）工程很重要？", "模型能力相近时，任务拆解、上下文装配、工具调用、错误恢复与评估闭环，往往决定 Agent 是否稳定完成真实任务。")
        )),
        BuiltinDeck(2, "第 2 章 · 上下文工程", listOf(
            CardDraft("上下文工程与提示词工程的区别是什么？", "提示词工程关注单次指令；上下文工程管理整个任务期间进入模型的信息：历史、检索结果、工具输出、记忆与压缩策略。"),
            CardDraft("为什么需要压缩上下文？", "上下文窗口有限，冗余信息会增加成本、降低注意力质量。保留决策、约束和未完成事项，丢弃可重新获得的细节。")
        )),
        BuiltinDeck(3, "第 3 章 · 记忆与知识库", listOf(
            CardDraft("RAG 的核心流程是什么？", "将文档切分并建立索引；收到问题后检索相关片段；把片段连同问题交给模型生成有依据的回答。"),
            CardDraft("用户记忆和知识库分别解决什么问题？", "用户记忆保存偏好、历史和长期关系；知识库保存外部事实与文档。前者个性化，后者增强事实依据。")
        )),
        BuiltinDeck(4, "第 4 章 · 工具与 MCP", listOf(
            CardDraft("工具调用要包含哪些可靠性要素？", "明确的输入输出 schema、权限边界、超时与重试、可观察日志，以及能让模型理解失败原因的错误反馈。"),
            CardDraft("MCP 为什么有价值？", "它用统一协议连接模型与工具/资源，让客户端不必为每个数据源重写集成逻辑，并能按需发现能力。")
        )),
        BuiltinDeck(5, "第 5 章 · Coding Agent", listOf(
            CardDraft("Coding Agent 执行任务的典型闭环？", "理解仓库和约束，制定小步计划，修改代码，运行测试/构建，根据结果修复，并以可验证的改动交付。"),
            CardDraft("为什么代码生成需要验证环节？", "代码能通过语法检查不代表满足需求。测试、类型检查、构建和审查让 Agent 获得可纠错的外部反馈。", "./gradlew test\n./gradlew assembleDebug")
        )),
        BuiltinDeck(6, "第 6 章 · Agent 评估", listOf(
            CardDraft("评估 Agent 时为什么不能只看单次成功？", "Agent 输出存在随机性。需要多次运行、定义任务成功标准，并观察成本、时延、稳定性和显著性。"),
            CardDraft("离线评估与线上评估如何配合？", "离线评估用于可重复地比较版本；线上评估用真实行为与用户反馈发现分布外问题。两者共同形成迭代闭环。")
        )),
        BuiltinDeck(7, "第 7 章 · 模型后训练", listOf(
            CardDraft("SFT 与 RL 的适用差异？", "SFT 用高质量示范学习明确行为；RL 根据可验证奖励优化长期目标或探索策略。数据与奖励设计通常比算法名更关键。"),
            CardDraft("工具调用如何被“内化”？", "通过包含工具选择、参数、观察结果和纠错过程的训练轨迹，模型可学习在合适时机调用工具并利用返回结果。")
        )),
        BuiltinDeck(8, "第 8 章 · 持续进化", listOf(
            CardDraft("Agent 可以从运行轨迹中改进什么？", "可更新知识、提示指令、工作流程序或模型参数。应先判断失败来自信息、策略、工具还是模型能力。"),
            CardDraft("为什么线上自我改进需要安全边界？", "错误轨迹和噪声反馈可能放大问题。改动应经过评估、版本控制、回滚机制和权限隔离。")
        )),
        BuiltinDeck(9, "第 9 章 · 多模态与实时", listOf(
            CardDraft("实时语音 Agent 的关键工程挑战？", "低延迟的流式输入输出、打断处理、轮次管理、状态同步，以及在网络抖动下保持自然对话。"),
            CardDraft("Computer Use Agent 为什么风险更高？", "它能直接影响图形界面和外部系统；需要最小权限、危险操作确认、可审计轨迹与可靠的环境隔离。")
        )),
        BuiltinDeck(10, "第 10 章 · 多 Agent 协作", listOf(
            CardDraft("多 Agent 协作何时比单 Agent 更合适？", "任务可并行、需要不同专长、或独立检查能提高可靠性时。若沟通成本高或任务简单，单 Agent 往往更高效。"),
            CardDraft("协作系统应如何管理上下文？", "共享目标、契约和必要产物；隔离无关的私有推理与冗余历史；用结构化交接避免信息丢失和上下文膨胀。")
        ))
    )
}
