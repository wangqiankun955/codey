package com.vanilla.hook;

import java.util.ArrayList;
import java.util.List;

public class HookRegistery {

    private final List<Hook> hooks = new ArrayList<>();

    public HookRegistery() {
        hooks.add(new SecurityHook());
    }

    public void register(Hook hook) {
        hooks.add(hook);
    }

    public List<Hook> hooks(HookEvent event) {
        return hooks.stream().filter(hook -> event.equals(hook.support()))
                .sorted((o1, o2) -> o1.order() - o2.order())
                .toList();
    }
}
