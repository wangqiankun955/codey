package com.vanilla.tool;


import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

public interface Tool {
    ToolSpecification getSpecification();

    String execute(ToolExecutionRequest request);
}
