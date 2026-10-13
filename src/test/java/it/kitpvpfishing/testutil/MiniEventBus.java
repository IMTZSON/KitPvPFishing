package it.kitpvpfishing.testutil;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Riproduce la logica di dispatch di Bukkit: ordine per priorita' (LOWEST -> MONITOR),
 * a parita' di priorita' ordine di registrazione, e {@code ignoreCancelled} rispettato
 * come in {@code RegisteredListener#callEvent}. Legge le vere annotazioni {@link EventHandler}.
 */
public final class MiniEventBus {

    public interface Handler<E extends Event> {
        void handle(E event);
    }

    private final List<Registration> registrations = new ArrayList<Registration>();

    public void register(final Listener listener) {
        for (final Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) {
                continue;
            }
            registrations.add(new Registration(annotation.priority(), annotation.ignoreCancelled(), new Handler<Event>() {
                @Override
                public void handle(Event event) {
                    if (!method.getParameterTypes()[0].isInstance(event)) {
                        return;
                    }
                    try {
                        method.invoke(listener, event);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    } catch (InvocationTargetException e) {
                        throw new IllegalStateException(e.getCause());
                    }
                }
            }));
        }
    }

    /** Simula il listener di un altro plugin. */
    @SuppressWarnings("unchecked")
    public <E extends Event> void register(EventPriority priority, boolean ignoreCancelled, Handler<E> handler) {
        registrations.add(new Registration(priority, ignoreCancelled, (Handler<Event>) handler));
    }

    public void call(Event event) {
        List<Registration> ordered = new ArrayList<Registration>(registrations);
        Collections.sort(ordered, new Comparator<Registration>() {
            @Override
            public int compare(Registration a, Registration b) {
                return a.priority.getSlot() - b.priority.getSlot();
            }
        });
        for (Registration registration : ordered) {
            if (registration.ignoreCancelled && event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
                continue;
            }
            registration.handler.handle(event);
        }
    }

    private static final class Registration {
        final EventPriority priority;
        final boolean ignoreCancelled;
        final Handler<Event> handler;

        Registration(EventPriority priority, boolean ignoreCancelled, Handler<Event> handler) {
            this.priority = priority;
            this.ignoreCancelled = ignoreCancelled;
            this.handler = handler;
        }
    }
}
