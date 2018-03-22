import java.util.concurrent.*;

public class ConcurTest {

    public static void main(String[] args) {
//        Runnable task = () -> {
//            String threadName = Thread.currentThread().getName();
//            System.out.println("Hello " + threadName);
//        }
//        Thread thread = new Thread(task);
//        thread.run();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 10; ++i) {
            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println("Hello " + threadName);
            });
        }

//        executor.shutdown();

        Callable<Integer> task = () -> {
            try {
                String threadName = Thread.currentThread().getName();
                System.out.println("Hello Callable " + threadName);
                TimeUnit.SECONDS.sleep(2);
                return 42;
            }
            catch (InterruptedException e) {
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
}
