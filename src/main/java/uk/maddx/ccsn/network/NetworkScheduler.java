package uk.maddx.ccsn.network;

import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs wired-network connection updates on the next server tick instead of
 * straight away. Block entities ask for a refresh while chunks are loading or
 * neighbours are changing, which is a bad moment to touch the network graph.
 * Server thread only.
 */
public final class NetworkScheduler {
    /** Something that (re)connects its wired node to its neighbours. */
    public interface Refreshable {
        void refreshConnections();

        boolean isRemoved();
    }

    private static final Set<Refreshable> PENDING = new LinkedHashSet<>();

    public static void schedule(Refreshable target) {
        PENDING.add(target);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Refreshable> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Refreshable target : batch) {
            if (!target.isRemoved()) target.refreshConnections();
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private NetworkScheduler() {
    }
}
