package com.vanilla.hook;

public interface Hook {

    String id();

    HookEvent support();

    default int order() {
        return 0;
    }

    HookResult execute(HookContext context);
}