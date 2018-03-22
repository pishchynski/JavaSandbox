public class ConcurTest {

    public static void main(String[] args) {
        Runnable task = () -> System.out.println("Running task!");
        Thread thread = new Thread(task);
        thread.run();
    }
}
