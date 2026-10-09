package gcy.system.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后执行回调；当前无事务时立即执行。
 * <p>
 * 用于发邮件、发 MQ、删缓存等「事务回滚了就不该发生」的副作用。
 * 回调异常统一吞掉并记日志：afterCommit 抛错会让调用方拿到失败响应，
 * 而事务其实已提交、数据已落库，容易被误判成业务失败。
 *
 * @author 郭名城
 * @date 2026-10-09
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * 推迟到当前事务提交后执行；无活动事务时立即执行。
     */
    public static void run(Runnable task) {
        if (task == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeSafely(task);
                }
            });
        } else {
            executeSafely(task);
        }
    }

    /**
     * 执行回调并吞掉异常，只记日志。
     */
    private static void executeSafely(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("事务提交后回调执行失败（事务已提交、数据已落库，仅记日志不影响主流程）", e);
        }
    }
}
