package sys.scheduler;

public class Scheduling {

    private static VT_Scheduler instance;

    public static VT_Scheduler getScheduler() {
        if(instance == null) {
            instance = new RT_Scheduler();
            instance.start();
        }
        return instance;
    }

}
