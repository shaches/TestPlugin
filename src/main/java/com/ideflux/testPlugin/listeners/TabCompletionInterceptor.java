package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;

import java.util.ArrayList;
import java.util.List;

public class TabCompletionInterceptor implements Listener {

    private final CoordinateStore crdStore;

    public TabCompletionInterceptor(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @EventHandler
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        String buffer = event.getBuffer();
        String[] args = buffer.split(" ", -1); // -1 preserves trailing empty strings
        String lastArg = args[args.length - 1];

        if (lastArg.startsWith("#")) {
            String partialName = lastArg.substring(1).toLowerCase();
            List<String> newCompletions = new ArrayList<>(event.getCompletions());

            for (String name : crdStore.getSavedNames()) {
                if (name.toLowerCase().startsWith(partialName)) {
                    newCompletions.add("#" + name);
                }
            }

            event.setCompletions(newCompletions);
        }
    }
}