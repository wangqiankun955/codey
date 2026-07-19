package com.vanilla;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.internal.chat.Message;
import dev.langchain4j.model.output.FinishReason;

/**
 * Hello world!
 *
 */
public class Codey {

    static List<ChatMessage> history = new ArrayList<>();

    static Scanner sc = new Scanner(System.in);

    static OpenAiChatModel client = OpenAiChatModel.builder()
            .apiKey("sk-cp-RZhJK2wUGo-b2m18glB-pAyIG6X2-phMbLOSKFiONzBgW16K68UVoU3B7Ir7VOwo02KzJHyr5v6Uijst-jl4Lfx0XCjsVHtDbjFOP_k6FWRJxvDAnSzgbBc")
            .baseUrl("https://api.minimaxi.com/v1")
            .modelName("MiniMax-M3")
            .build();

    public static void main(String[] args) {

        System.out.println("输入问题，按q推出.");
        history.add(SystemMessage.from(Prompt.SYSTEM));
        while (true) {
            System.out.print(">>>");
            String input = sc.nextLine();
            history.add(UserMessage.from(input));
            toolAgent(history);
            AiMessage answer = ((AiMessage) history.get(history.size() - 1));
            System.out.println(answer.text());
        }

    }

    private static void toolAgent(List<ChatMessage> history) {

        while (true) {
            ChatResponse response = client.chat(ChatRequest.builder()
                .toolSpecifications(Tools.tools())
                .messages(history)
                .build());
                
            history.add(response.aiMessage());
            if (!FinishReason.TOOL_EXECUTION.equals(response.finishReason())) {
                break;
            }
            
            response.aiMessage().toolExecutionRequests().forEach(toolExeRequest -> {
                System.out.println(toolExeRequest.name());
                String arguments = toolExeRequest.arguments();
            });
        }
    }
}
