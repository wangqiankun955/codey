package com.vanilla.hook;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class HookContext {

    String userPrompt;

    List<ToolExecutionRequest> toolUseRequest;

    List<ChatMessage> history;

    public HookContext(String userPrompt, List<ToolExecutionRequest> toolUseRequest, List<ChatMessage> history) {
        this.userPrompt = userPrompt;
        this.toolUseRequest = toolUseRequest;
        this.history = history;
    }

    public HookContext(String input) {
        this.userPrompt = input;
    }

    public static HookContext from(String input){
        return new HookContext(input);
    }
}
