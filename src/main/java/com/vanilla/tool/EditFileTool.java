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
 * 用精确的文本匹配修改 UTF-8 文件。
 *
 * <p>默认要求旧文本只出现一次，避免模型误修改多个位置；传入
 * {@code replace_all: true} 时才会替换所有匹配项。</p>
 */
public class EditFileTool implements Tool {

    private static final String TOOL_NAME = "edit_file";

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name(TOOL_NAME)
                .description("Replace an exact text snippet in a UTF-8 file. By default the old text must occur exactly once.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "The path of the file to edit")
                        .addStringProperty("old_string", "The exact text to find")
                        .addStringProperty("new_string", "The replacement text")
                        .addBooleanProperty("replace_all", "Whether to replace every occurrence; defaults to false")
                        .required("path", "old_string", "new_string")
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

        String oldString = arguments.getStr("old_string");
        if (oldString == null) {
            oldString = arguments.getStr("oldString");
        }
        if (oldString == null || oldString.isEmpty()) {
            return "Error: old_string cannot be empty.";
        }

        // 空字符串是合法的替换内容，因此只判断 null，不判断 isBlank()。
        String newString = arguments.getStr("new_string");
        if (newString == null) {
            newString = arguments.getStr("newString");
        }
        if (newString == null) {
            return "Error: new_string cannot be null.";
        }

        boolean replaceAll = Boolean.TRUE.equals(arguments.getBool("replace_all"));
        if (!replaceAll) {
            replaceAll = Boolean.TRUE.equals(arguments.getBool("replaceAll"));
        }

        final Path path;
        try {
            path = resolvePath(pathArgument.strip());
        } catch (InvalidPathException e) {
            return "Error: invalid file path: " + safeMessage(e);
        }

        try {
            if (!Files.exists(path)) {
                return "Error: file does not exist: " + path;
            }
            if (!Files.isRegularFile(path)) {
                return "Error: path is not a regular file: " + path;
            }

            String original = Files.readString(path, StandardCharsets.UTF_8);
            int occurrences = countOccurrences(original, oldString);
            if (occurrences == 0) {
                return "Error: old_string was not found in file: " + path;
            }
            if (!replaceAll && occurrences > 1) {
                return "Error: old_string occurs " + occurrences
                        + " times; set replace_all to true or provide a more specific old_string.";
            }

            String updated = replaceAll
                    ? original.replace(oldString, newString)
                    : replaceFirst(original, oldString, newString);
            Files.writeString(path, updated, StandardCharsets.UTF_8);
            return "Successfully replaced " + occurrences + " occurrence"
                    + (occurrences == 1 ? "" : "s") + " in " + path + ".";
        } catch (IOException e) {
            return "Error: failed to edit file '" + path + "': " + safeMessage(e);
        } catch (SecurityException e) {
            return "Error: permission denied when editing file '" + path + "'.";
        }
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = text.indexOf(target, fromIndex)) >= 0) {
            count++;
            fromIndex += target.length();
        }
        return count;
    }

    private static String replaceFirst(String text, String target, String replacement) {
        int index = text.indexOf(target);
        return text.substring(0, index)
                + replacement
                + text.substring(index + target.length());
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
