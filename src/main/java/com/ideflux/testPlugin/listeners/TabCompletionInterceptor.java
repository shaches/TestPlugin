package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The TabCompletionInterceptor class listens for asynchronous tab completion events and
 * modifies or appends auto-completion suggestions based on user input and saved data
 * stored in a {@code CoordinateStore}.
 *
 * This class enables users to tab-complete strings starting with a designated trigger
 * character (e.g., "#") by matching against saved names available in the {@code CoordinateStore}.
 *
 * If the user provides a partial input that begins with the trigger character (e.g., "#partialName"),
 * the class dynamically suggests matching completions by prepending "#" to each matching saved
 * name in the {@code CoordinateStore}.
 *
 * Implements the {@code Listener} interface to handle Bukkit events.
 */
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