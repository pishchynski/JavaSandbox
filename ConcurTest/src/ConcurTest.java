import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.IntStream;

import static java.lang.Thread.activeCount;
import static java.lang.Thread.sleep;

public class ConcurTest {

    private static void stop(ExecutorService executor) {
        try {
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            System.err.println("termination interrupted");
        }
        finally {
            if (!executor.isTerminated()) {
                System.err.println("killing non-finished tasks");
            }
            executor.shutdownNow();
        }
    }

    private int counter = 0;

    private AtomicInteger atomicCounter = new AtomicInteger(0);

    private void increaseCount() {
        counter += 1;
    }

    private synchronized void synchronizedIncreaseCount() {
        counter += 1;
    }

    private void synchronizedIncreaseCount2() {
        synchronized (this) {
            synchronizedIncreaseCount();    // Reentrant lock
        }
    }

    private void lockedIncreaseCount() {
        ReentrantLock reentrantLock = new ReentrantLock();

        reentrantLock.lock();
        try {
            increaseCount();
        } finally {
            reentrantLock.unlock();
        }
    }

    private void runConcurrent() {
        Runnable runnableTask = () -> {
            String threadName = Thread.currentThread().getName();
            System.out.println("Hello " + threadName);
        };

        Thread thread = new Thread(runnableTask);
        thread.run();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 10; ++i) {
            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println("Hello " + threadName);
            });
        }

        Callable<Integer> task = () -> {
            try {
                String threadName = Thread.currentThread().getName();
                System.out.println("Hello Callable " + threadName);
                TimeUnit.SECONDS.sleep(2);
                return 42;
            } catch (InterruptedException e) {
                throw new IllegalStateException("task interrupted", e);
            }
        };

        try {
            System.out.println(task.call());
        } catch (Exception e) {
            e.printStackTrace();
        }

        Future<Integer> ft = executor.submit(task);
        try {
            int res = ft.get(1, TimeUnit.SECONDS);
            System.out.println(ft.isDone() ? "DONE" : "UNDONE");
            System.out.println(res);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }

        executor.shutdown();
    }

    private void testSynchronized() {
        ExecutorService executor = Executors.newFixedThreadPool(2);

//        IntStream.range(0, 10000)
//                .forEach(i -> executor.submit(this :: increaseCount));
//
//        System.out.println(counter);
//
////        executor.shutdown();
//
//        counter = 0;

//        IntStream.range(0, 10000).forEach(i -> executor.submit(this :: synchronizedIncreaseCount));
        IntStream.range(0, 10000).forEach(i -> executor.submit(this :: synchronizedIncreaseCount2));

        stop(executor);

        System.out.println(counter);
    }

    private void testLock() {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        IntStream.range(0, 10000).forEach(i -> executor.submit(this :: lockedIncreaseCount));

        stop(executor);

        System.out.println(counter);
    }

    private void testReadWriteLock() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<String, String> map = new HashMap<>();
        ReadWriteLock lock = new ReentrantReadWriteLock();

        Runnable readTask = () -> {
            lock.readLock().lock();
            try {
                System.out.println(map.get("foo"));
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.readLock().unlock();
            }
        };

        executor.submit(readTask);

        executor.submit(() -> {
            lock.writeLock().lock();
            try {
                TimeUnit.SECONDS.sleep(1);
                System.out.println("writing");
                map.put("foo", "bar");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.writeLock().unlock();
            }
        });

        executor.submit(readTask);
        executor.submit(readTask);

        stop(executor);
    }

    private void testStampedLock() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<String, String> map = new HashMap<>();
        StampedLock lock = new StampedLock();

        Runnable readTask = () -> {
            long stamp = lock.readLock();
            try {
                System.out.println(map.get("foo"));
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlockRead(stamp);
            }
        };

        executor.submit(readTask);

        executor.submit(() -> {
            long stamp = lock.writeLock();
            try {
                System.out.println("Locked =)");
                TimeUnit.SECONDS.sleep(1);
                map.put("foo", "bar");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlockWrite(stamp);
            }
        });

        executor.submit(readTask);

        stop(executor);
    }

    private void testOptimisticLock() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        StampedLock lock = new StampedLock();

        executor.submit(() -> {
            long stamp = lock.tryOptimisticRead();
            try {
                System.out.println("Optimistic Lock Valid: " + lock.validate(stamp));
                TimeUnit.SECONDS.sleep(1);
                System.out.println("Optimistic Lock Valid: " + lock.validate(stamp));
                TimeUnit.SECONDS.sleep(2);
                System.out.println("Optimistic Lock Valid: " + lock.validate(stamp));
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock(stamp);
            }
        });

        executor.submit(() -> {
            long stamp = lock.writeLock();
            try {
                System.out.println("Write Lock acquired");
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock(stamp);
                System.out.println("Write done");
            }
        });

        stop(executor);
    }

    private void testLockConversion() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        StampedLock lock = new StampedLock();

        executor.submit(() -> {
            long stamp = lock.readLock();
            try {
                if (counter == 0) {
                    stamp = lock.tryConvertToWriteLock(stamp);
                    if (stamp == 0L) {
                        System.out.println("Could not convert to write lock");
                        stamp = lock.writeLock();
                    }
                    counter = 23;
                }
                System.out.println(counter);
            } finally {
                lock.unlock(stamp);
            }
        });

        stop(executor);
    }

    private void testSemaphore() {
        ExecutorService executor = Executors.newFixedThreadPool(6);

        Semaphore semaphore = new Semaphore(5);

        Runnable longRunningTask = () -> {
            boolean permit = false;
            try {
                permit = semaphore.tryAcquire(1, TimeUnit.SECONDS);
                if (permit) {
                    System.out.println("Semaphore acquired");
                    TimeUnit.SECONDS.sleep(5);
                } else {
                    System.out.println("Could not acquire semaphore");
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            } finally {
                if (permit) {
                    semaphore.release();
                }
            }
        };

        IntStream.range(0, 12)
                .forEach(i -> executor.submit(longRunningTask));

        stop(executor);
    }

    private void testAtomic() {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        IntStream.range(0, 1000)
                .forEach(i -> executor.submit(atomicCounter::incrementAndGet));

        stop(executor);

        System.out.println(atomicCounter.get());
    }

    private void testConcurrentMap() {
        ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
        map.put("раз", "два");
        map.put("пять", "раз");
        map.put("r2", "d2");
        map.put("c3", "p0");

        map.forEach((key, value) -> System.out.println(key + " = " + value));

        map.replaceAll((key, value) -> "r2".equals(key) ? "d3" : value);
        System.out.println(map.get("r2"));

        map.compute("раз", (key, value) -> value + key);
        System.out.println(map.get("раз"));

        System.out.println(ForkJoinPool.getCommonPoolParallelism());

        ConcurrentHashMap<String, String> hashMap = (ConcurrentHashMap<String, String>) map;    // to achieve parallel operations
        hashMap.forEach(1, (key, value) -> System.out.println(key + " = " + value + " " + Thread.currentThread().getName()));

        System.out.println();

        String result = hashMap.search(1, (key, value) -> {
            System.out.println(Thread.currentThread().getName());
            if ("ра".equals(key)) {
                return value;
            }
            return null;
        });

        System.out.println("Result: " + (result == null ? "nothing" : result));

        String res = hashMap.reduce(1,
                (key, value) -> {
                    System.out.println("Transform: " + Thread.currentThread().getName());
                    return key + "=" + value;
                },
                (s1, s2) -> {
                    System.out.println("Reduce: " + Thread.currentThread().getName());
                    return s1 + ", " + s2;
                });

        System.out.println("Result: " + res);
    }

    public static void main(String[] args) {
        ConcurTest concurTest = new ConcurTest();
//        concurTest.runConcurrent();
//        concurTest.testSynchronized();
//        concurTest.testLock();
//        concurTest.testReadWriteLock();
//        concurTest.testStampedLock();
//        concurTest.testOptimisticLock();
//        concurTest.testLockConversion();
//        concurTest.testSemaphore();
//        concurTest.testAtomic();
        concurTest.testConcurrentMap();
    }
}
