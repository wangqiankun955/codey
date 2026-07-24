package com.vanilla.hook;

import java.util.Iterator;
import java.util.List;

public class HookDispatcher {

    private final static HookRegistery hookRegister = new HookRegistery();

    public static HookResult dispatch(HookEvent event, HookContext context) {
        List<Hook> hooks = hookRegister.hooks(event);
        Iterator<Hook> iterator = hooks.iterator();
        while (iterator.hasNext()) {
            HookResult result = iterator.next().execute(context);
            if (!result.continueRun()) {
                return result;
            }
        }
        return HookResult.allow();
    }
}
