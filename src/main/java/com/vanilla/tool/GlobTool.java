package com.vanilla.tool;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 在工作区中查找匹配 glob 模式（如 {@code **\/*.java}）的文件。
 *
 * <p>相对路径以启动 Codey 时的工作目录为基准，绝对路径也可以直接使用。
 * 默认会跳过 {@code .git}、{@code node_modules}、{@code target} 等常见大型目录，
 * 避免给模型返回过多无关结果。</p>
 */
public class GlobTool implements Tool {

    private static final String TOOL_NAME = "glob";

    /** 默认需要跳过的目录名，逐级与目录名完全相等时跳过。 */
    private static final List<String> DEFAULT_IGNORED_DIRS = List.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode"
    );

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name(TOOL_NAME)
                .description("Find files matching a glob pattern (e.g. \"**/*.java\"). "
                        + "Search is performed relative to the workspace by default.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("pattern", "The glob pattern to match files against, e.g. \"**/*.java\".")
                        .addStringProperty("path",
                                "The base directory to search in. Defaults to the current working directory.")
                        .required("pattern")
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

        String pattern = arguments.getStr("pattern");
        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern cannot be empty.";
        }

        String basePathArgument = arguments.getStr("path");
        if (basePathArgument == null || basePathArgument.isBlank()) {
            basePathArgument = ".";
        }

        final Path basePath;
        try {
            basePath = resolvePath(basePathArgument.strip());
        } catch (InvalidPathException e) {
            return "Error: invalid base path: " + safeMessage(e);
        }

        if (!Files.exists(basePath)) {
            return "Error: base path does not exist: " + basePath;
        }
        if (!Files.isDirectory(basePath)) {
            return "Error: base path is not a directory: " + basePath;
        }

        final PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern.strip());
        } catch (IllegalArgumentException e) {
            return "Error: invalid glob pattern '" + pattern + "': " + safeMessage(e);
        }

        final List<String> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(basePath)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isUnderIgnoredDir(basePath, path))
                    .filter(path -> matcher.matches(toRelativeOrAbsolute(basePath, path)))
                    .sorted()
                    .forEach(path -> results.add(path.toString()));
        } catch (IOException e) {
            return "Error: failed to walk directory '" + basePath + "': " + safeMessage(e);
        } catch (SecurityException e) {
            return "Error: permission denied while searching '" + basePath + "'.";
        }

        if (results.isEmpty()) {
            return "No files matched pattern '" + pattern + "' under " + basePath + ".";
        }

        return String.join(System.lineSeparator(), results);
    }

    /**
     * 判断 {@code path} 是否落在任何一个被忽略的目录中。
     * 比较的是相对 basePath 的路径段，避免不同分支下同名目录被误判。
     */
    private static boolean isUnderIgnoredDir(Path basePath, Path path) {
        Path relative = basePath.relativize(path);
        for (Path segment : relative) {
            if (DEFAULT_IGNORED_DIRS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 优先返回相对 basePath 的路径，便于模型阅读；若相对化失败则回退为绝对路径。
     */
    private static Path toRelativeOrAbsolute(Path basePath, Path path) {
        if (path.startsWith(basePath)) {
            return basePath.relativize(path);
        }
        return path;
    }

    private static Path resolvePath(String pathArgument) {
        Path path = Paths.get(pathArgument);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}