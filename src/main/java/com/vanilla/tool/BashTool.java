package com.vanilla.tool;

import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BashTool implements Tool {

    private static final long COMMAND_TIMEOUT_SECONDS = 120;


    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("bash")
                .description("Run a shell command.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("command")
                        .required("command")
                        .build())
                .build();
    }

    @Override
    public String execute(ToolExecutionRequest request) {

        if (request == null || request.arguments() == null) {
            return "Error: tool execution request or arguments cannot be null.";
        }

        final String command;
        try {
            command = JSONUtil.parseObj(request.arguments()).getStr("command");
        } catch (RuntimeException e) {
            return "Error: invalid tool arguments: " + e.getMessage();
        }

        if (command == null || command.isBlank()) {
            return "Error: command cannot be empty.";
        }

        Process process = null;
        ExecutorService outputReader = Executors.newSingleThreadExecutor();
        try {
            // Use bash -c so that pipes, redirects, &&, variables, etc. work as expected.
            process = new ProcessBuilder("/bin/bash", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            // Read output concurrently; otherwise a verbose command can fill the pipe
            // buffer and block before waitFor() gets a chance to return.
            Process runningProcess = process;
            Future<String> outputFuture = outputReader.submit(() -> readOutput(runningProcess));

            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                String output = getOutput(outputFuture);
                return output + (output.isEmpty() ? "" : System.lineSeparator())
                        + "Error: command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds.";
            }

            String output = getOutput(outputFuture);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return output;
            }

            String result = output.isEmpty() ? "" : output + System.lineSeparator();
            return result + "Command exited with code " + exitCode + ".";
        } catch (IOException e) {
            return "Error: failed to start bash: " + e.getMessage();
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return "Error: command execution was interrupted.";
        } finally {
            outputReader.shutdownNow();
        }
    }

    private String getOutput(Future<String> outputFuture)
            throws InterruptedException, IOException {
        try {
            return outputFuture.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("failed to read command output", cause);
        }
    }

    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        }
        return output.toString();
    }
}
