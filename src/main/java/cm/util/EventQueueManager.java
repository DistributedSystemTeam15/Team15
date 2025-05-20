package cm.util;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 클라이언트 이벤트가 UI에 의해 무시(ignore)되는 상황을 방지하기 위한 큐잉 유틸리티.
 * buffering 중에는 이벤트를 큐에 저장하고, 이후 일괄 실행할 수 있다.
 */
public class EventQueueManager {
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private boolean buffering = false;

    /**
     * 이벤트 큐잉 시작 (모든 dispatchOrQueue() 호출 시 큐에 저장)
     */
    public synchronized void bufferEvents() {
        buffering = true;
    }

    /**
     * 큐에 저장된 이벤트를 순차적으로 실행하고, 큐를 비움
     */
    public synchronized void releaseEvents() {
        buffering = false;
        while (!queue.isEmpty()) {
            Runnable r = queue.poll();
            try {
                r.run();
            } catch (Exception ex) {
                System.err.println("[EventQueueManager] Error while executing event: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /**
     * 현재 상태에 따라 이벤트를 즉시 실행하거나 큐에 저장
     *
     * @param r 실행할 이벤트
     */
    public synchronized void dispatchOrQueue(Runnable r) {
        if (buffering) {
            queue.offer(r);
        } else {
            r.run();
        }
    }

    /**
     * 강제로 큐 초기화 (flush 없이 버림)
     */
    public synchronized void clear() {
        queue.clear();
    }

    /**
     * 현재 큐 상태 조회용 (디버깅용)
     */
    public synchronized int getQueuedEventCount() {
        return queue.size();
    }

    public synchronized boolean isBuffering() {
        return buffering;
    }
}
