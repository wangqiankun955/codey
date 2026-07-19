package com.vanilla;

import java.lang.String;

public interface Prompt {
    String SYSTEM = String.format(
            "You are a coding agent at %s. Use bash to solve tasks. Act, don't explain.",
            System.getenv("user.dir")
        );
}
