package com.vanilla;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class Tools {

    private static Map<String, ToolSpecification> tools = new HashMap<>();

    static {
        ToolSpecification bash = ToolSpecification.builder()
                .name("bash")
                .description("脚本")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("command")
                        .required("command")
                        .build())
                .build();
        tools.put(bash.name(), bash);
    }

    public static ToolSpecification handler(String toolName) {
        return tools.get(toolName);
    }

    public static List<ToolSpecification> tools(){
        return tools.values().stream().toList();
    }
}
