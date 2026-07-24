package com.vanilla.hook;

public class HookResult {

    private boolean continueRun;

    private String msg;

    public HookResult(boolean continueRun, String msg) {
        this.continueRun = continueRun;
        this.msg = msg;
    }

    public HookResult(boolean continueRun) {
        this.continueRun = continueRun;
    }

    public boolean continueRun() {
        return continueRun;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }

    public static HookResult allow() {
        return new HookResult(true);
    }

    public static HookResult block(String msg) {
        return new HookResult(false, msg);
    }
}
