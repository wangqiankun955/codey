package com.vanilla.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 读取工作区中的 UTF-8 文本文件。
 *
 * <p>相对路径以启动 Codey 时的工作目录为基准，绝对路径也可以直接使用。
 * 工具成功时只返回文件原文，避免给模型的上下文混入额外的包装文本。</p>
 */
public class ReadFileTool implements Tool {

    private static final String TOOL_NAME = "read_file";

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name(TOOL_NAME)
                .description("Read the UTF-8 text content of a file. Use a relative path from the workspace or an absolute path.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "The path of the file to read")
                        .required("path")
                        .build())
                .build();
    }

    @Override
    public String execute(ToolExecutionRequest request) {
        if (request == null || request.arguments() == null) {
            return "Error: tool execution request or arguments cannot be null.";
        }

        final JSONObject arguments;
        try {
            arguments = JSONUtil.parseObj(request.arguments());
        } catch (RuntimeException e) {
            return "Error: invalid tool arguments: " + safeMessage(e);
        }

        // file_path is accepted as a compatibility alias for callers using the
        // conventional name from other coding-agent tools.
        String pathArgument = arguments.getStr("path");
        if (pathArgument == null || pathArgument.isBlank()) {
            pathArgument = arguments.getStr("file_path");
        }
        if (pathArgument == null || pathArgument.isBlank()) {
            return "Error: path cannot be empty.";
        }

        final Path path;
        try {
            path = resolvePath(pathArgument.strip());
        } catch (InvalidPathException e) {
            return "Error: invalid file path: " + safeMessage(e);
        }

        if (!Files.exists(path)) {
            return "Error: file does not exist: " + path;
        }
        if (!Files.isRegularFile(path)) {
            return "Error: path is not a regular file: " + path;
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error: failed to read file '" + path + "': " + safeMessage(e);
        } catch (SecurityException e) {
            return "Error: permission denied when reading file '" + path + "'.";
        }
    }

    private static Path resolvePath(String pathArgument) {
        Path path = Path.of(pathArgument);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
