import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurTest {

    public static void main(String[] args) {
//        Runnable task = () -> {
//            String threadName = Thread.currentThread().getName();
//            System.out.println("Hello " + threadName);
//        }
//        Thread thread = new Thread(task);
//        thread.run();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        for (int i = 0; i < 100; ++i) {
            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println("Hello " + Math.sin(Math.sqrt(threadName.hashCode())));
            });
        }

        try {
            System.out.println("attempt to shutdown executor");
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MICROSECONDS);
        }
        catch (InterruptedException e) {
            System.err.println("tasks interrupted");
        }
        finally {
            if (!executor.isTerminated()) {
                System.err.println("cancel non-finished tasks");
            }
            executor.shutdownNow();
            System.out.println("shutdown finished");
        }

    }
}
