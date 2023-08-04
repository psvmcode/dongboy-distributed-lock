package com.dongboy.util;

/**
 * @Author dongboy
 * @what time    2023/7/31 16:46
 */
public class LockUpdateThread extends Thread {

    public LockUpdateThread(Runnable runnable) {
        super(runnable);
        this.setDaemon(true);
    }

}
