package Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobin {

    private static final AtomicInteger IDX = new AtomicInteger(0);

    public static String next() {

        List<String> list = HealthMonitor.HEALTHY_BACKENDS;

        int n = list.size();

        if (n == 0) {
            return null;
        }

        int i = Math.floorMod(IDX.getAndIncrement(), n);

        return list.get(i);
    }
}