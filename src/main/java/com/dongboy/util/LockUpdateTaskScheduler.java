package com.dongboy.util;

import com.dongboy.annotation.DongDistributedLock;
import com.dongboy.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author dongboy
 * @what time    2023/7/31 16:41
 */
@Slf4j
public class LockUpdateTaskScheduler {

    private static final int NUMBER_OF_UPDATE_TASK_WORKER = 100;

    private static final int UPDATE_DELAY_MULLS = 5000;

    private final List<Set<DistributedLock>> taskList = new ArrayList<>(NUMBER_OF_UPDATE_TASK_WORKER);

    private final Map<DistributedLock, Integer> taskAssignment = new ConcurrentHashMap<>();

    private ScheduledExecutorService executorService;

    public LockUpdateTaskScheduler() {
        if (NUMBER_OF_UPDATE_TASK_WORKER < 1) {
            throw new IllegalArgumentException("update task worker number:" + NUMBER_OF_UPDATE_TASK_WORKER);
        }
        // 初始化线程
        for (int i = 0; i < NUMBER_OF_UPDATE_TASK_WORKER; i++) {
            taskList.add(ConcurrentHashMap.newKeySet());
        }
        executorService = Executors.newScheduledThreadPool(NUMBER_OF_UPDATE_TASK_WORKER, LockUpdateThread::new);
        // 初始化锁续期任务
        for (int i = 0; i < NUMBER_OF_UPDATE_TASK_WORKER; i++) {
            int finalI = i;
            executorService.scheduleAtFixedRate(() -> {
                Set<DistributedLock> tasks = taskList.get(finalI);
                for (DistributedLock lock : tasks) {
                    Thread heldByThread = lock.getHoldingThread();
                    if (heldByThread.isAlive()) {
                        log.debug("update lock!" + lock);
                        lock.update();
                    } else {
                        cancelTask(lock);
                    }
                }
            }, UPDATE_DELAY_MULLS / 2, UPDATE_DELAY_MULLS, TimeUnit.MILLISECONDS);
        }
        log.info("update thread pool initialized");
    }

    public void newTask(DistributedLock lock) {
        if (taskAssignment.containsKey(lock)) {
            return;
        }
        //创建续期任务时尽量使任务分片平均
        // 找到目前任务数目(非严格)最小的任务分片
        int min = taskList.get(0).size();
        int assigneeIndex = 0;
        for (int i = 1; i < taskList.size(); i++) {
            if (taskList.get(i).size() < min) {
                assigneeIndex = i;
                min = i;
            }
        }
        Set<DistributedLock> assigneeTaskList = taskList.get(assigneeIndex);
        assigneeTaskList.add(lock);
        taskAssignment.put(lock, assigneeIndex);
        log.debug("created update task for lock:" + lock + ".");
    }

    public void cancelTask(DistributedLock lock) {
        Integer assignIndex = taskAssignment.get(lock);
        taskList.get(assignIndex).remove(lock);
        taskAssignment.remove(lock);
        log.debug("canceled update task of lock:" + lock + ".");
    }

    public void clearTask() {
        taskList.forEach(Set::clear);
        taskAssignment.clear();
    }

}
