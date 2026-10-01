package com.macro.mall.portal.ai.client;

import org.springframework.ai.chat.model.ChatModel;

/**
 * LLM 对话客户端：把 prompt 发给模型拿回答文。
 * 仅在 ai.enabled=true 时装配（由 AiClientConfig 条件装配）。
 * Created by jiechu555 on 2026/10/01.
 */
public class AiChatClient {

    private final ChatModel chatModel;

    public AiChatClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * @return 模型生成的回答文本
     */
    public String chat(String prompt) {
        return chatModel.call(prompt);
    }
}
