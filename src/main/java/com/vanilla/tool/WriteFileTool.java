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
 * 将 UTF-8 文本写入文件。
 *
 * <p>目标文件不存在时会创建文件，父目录不存在时也会一并创建；目标文件已存在时会被完整覆盖。</p>
 */
public class WriteFileTool implements Tool {

    private static final String TOOL_NAME = "write_file";

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name(TOOL_NAME)
                .description("Write UTF-8 text to a file. Creates parent directories when needed and overwrites an existing file.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "The path of the file to write")
                        .addStringProperty("content", "The complete UTF-8 text to write")
                        .required("path", "content")
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

        String pathArgument = arguments.getStr("path");
        if (pathArgument == null || pathArgument.isBlank()) {
            pathArgument = arguments.getStr("file_path");
        }
        if (pathArgument == null || pathArgument.isBlank()) {
            return "Error: path cannot be empty.";
        }

        // 空字符串是合法文件内容，因此不能使用 isBlank() 判断 content 是否缺失。
        String content = arguments.getStr("content");
        if (content == null) {
            content = arguments.getStr("text");
        }
        if (content == null) {
            return "Error: content cannot be null.";
        }

        final Path path;
        try {
            path = resolvePath(pathArgument.strip());
        } catch (InvalidPathException e) {
            return "Error: invalid file path: " + safeMessage(e);
        }

        try {
            if (Files.exists(path) && !Files.isRegularFile(path)) {
                return "Error: path is not a regular file: " + path;
            }

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return "Successfully wrote " + content.length() + " characters to " + path + ".";
        } catch (IOException e) {
            return "Error: failed to write file '" + path + "': " + safeMessage(e);
        } catch (SecurityException e) {
            return "Error: permission denied when writing file '" + path + "'.";
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
