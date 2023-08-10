package com.dongboy.aspect;

import com.dongboy.annotation.DongDistributedLock;
import com.dongboy.exception.DistributeLockException;
import com.dongboy.exception.DistributedLockResponseCode;
import com.dongboy.lock.DistributedLock;
import com.dongboy.lock.DistributedMultiLock;
import com.dongboy.lock.DistributedReentrantLock;
import com.dongboy.service.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author dongboy
 * @what time    2023/7/31 15:18
 */
@Slf4j
@Aspect
@Component
public class DongDistributedLockAspect {

    private final ExpressionParser parser = new SpelExpressionParser();

    private final LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();

    @Resource
    private DistributedLockService lockService;

    @Pointcut("@annotation(com.dongboy.annotation.DongDistributedLock)")
    public void dongDistributedLock() {

    }

    @Around("dongDistributedLock()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        DongDistributedLock annotation = method.getAnnotation(DongDistributedLock.class);
        String prefix = annotation.prefix();
        String[] lockKeys = annotation.value();
        long waitTimeout = annotation.waitTimeout();
        long lockTime = annotation.lockTime();
        lockKeys = parseSpELLockKeys(prefix, lockKeys, discoverer.getParameterNames(method), joinPoint.getArgs());
        if (lockKeys.length == 0) {
            throw new IllegalArgumentException("No lock key!");
        }
        DistributedLock lock;
        if (lockKeys.length == 1) {
            log.info("distributed lock with key:" + lockKeys[0]);
            lockService.checkLockKeyLegal(lockKeys[0]);
            lock = DistributedReentrantLock.get(lockKeys[0], lockService);
        } else {
            log.info("distributed batch lock with keys:" + Arrays.toString(lockKeys));
            lockService.checkLockKeyLegal(lockKeys);
            lock = DistributedMultiLock.get(Arrays.asList(lockKeys), lockService);
        }
        try {
            lock.lock(waitTimeout, lockTime);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.initSynchronization();
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        if (lock.isExpire()) {
                            // 抛出DistributedLockException异常，从而阻止事务提交
                            throw new DistributeLockException(DistributedLockResponseCode.LOCK_EXPIRED);
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        lock.unlock();
                    }
                });
            }
            return joinPoint.proceed();
        } finally {
            // 如果当前不在事务环境中或者没有注册事务同步事件
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                // 立即解锁
                lock.unlock();
            } // 否则锁会在事务完成后解锁
        }
    }

    private String[] parseSpELLockKeys(String prefix, String[] lockKeys, String[] params, Object[] args) {
        if (StringUtils.isEmpty(params)) {
            return lockKeys;
        }
        // 设置解析SpEL所需的上下文
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);
        }
        // 解析表达式并获取SpEL的值
        Set<String> parseLockKeys = new HashSet<>(lockKeys.length);
        Arrays.stream(lockKeys).forEach(e -> {
            if (e.startsWith("$")) {
                // 以为$开头是SpEL表达式，进行解析
                Object parsedValue = parser.parseExpression(e.substring(1)).getValue(context);
                if (Objects.isNull(parsedValue)) {
                    throw new NullPointerException("分布式锁的key解析为空:" + e.substring(1) + "，参数为" + Arrays.toString(args));
                }
                if (parsedValue instanceof List || parsedValue.getClass().isArray()) {
                    // 处理集合或者数组
                    List<Object> parseList = parsedValue instanceof List ? (List<Object>) parsedValue : wrapObjectList(parsedValue);
                    parseList.forEach(s -> {
                        if (Objects.isNull(s)) {
                            throw new NullPointerException("");
                        }
                        parseLockKeys.add(prefix + s);
                    });
                } else if (parsedValue instanceof Set) {
                    // 处理集合类型的参数
                    Set<Object> parseSet = (Set<Object>) parsedValue;
                    for (Object o : parseSet) {
                        if (Objects.isNull(o)) {
                            throw new NullPointerException("");
                        }
                        parseLockKeys.add(prefix + o);
                    }
                } else {
                    // 处理普通变量，使用toString()返回值作为key
                    parseLockKeys.add(prefix + parsedValue);
                }
            } else {
                // 不以￥开头，视为字符串常量
                parseLockKeys.add(prefix + e);
            }
        });
        return parseLockKeys.toArray(new String[0]);
    }

    /**
     * 数字转集合
     */
    private List<Object> wrapObjectList(Object object) {
        try {
            Object[] objectArray = (Object[]) object;
            return Arrays.asList(objectArray);
        } catch (ClassCastException e) {
            if (object instanceof int[]) {
                return Arrays.stream((int[]) object).boxed().collect(Collectors.toList());
            } else if (object instanceof long[]) {
                return Arrays.stream((long[]) object).boxed().collect(Collectors.toList());
            } else if (object instanceof double[]) {
                return Arrays.stream((double[]) object).boxed().collect(Collectors.toList());
            } else {
                throw new UnsupportedOperationException("数组类型为" + object.getClass().getName());
            }
        }
    }

}
