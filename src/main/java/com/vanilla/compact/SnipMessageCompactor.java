package com.vanilla.compact;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;

public class SnipMessageCompactor {

    public static int MAX_MESSAGE_SIZE = 50;

    public static List<ChatMessage> snipCompact(List<ChatMessage> history) {
        int size = history.size();
        if (size < MAX_MESSAGE_SIZE) {
            return history;
        }
        int KEEP_HEAD_SIZE = 2, KEEP_TAIL_SIZE = MAX_MESSAGE_SIZE - KEEP_HEAD_SIZE;
        int startIdx = KEEP_HEAD_SIZE, endIdx = size - KEEP_TAIL_SIZE;
        while (ChatMessageType.TOOL_EXECUTION_RESULT.equals(history.get(endIdx).type())) {
            endIdx--;
        }
        List<ChatMessage> newHistory = new ArrayList<>();
        newHistory.addAll(history.subList(0, startIdx));
        newHistory.add(UserMessage.from(String.format("[snipped %d messages]", endIdx-startIdx)));
        newHistory.addAll(history.subList(endIdx, size));       

        System.out.println(String.format("[compact] 裁剪前数量：%d,裁剪后数量：%d",size,newHistory.size() - 1));
        history.clear();
        history.addAll(newHistory);
        return history;
    }
}
