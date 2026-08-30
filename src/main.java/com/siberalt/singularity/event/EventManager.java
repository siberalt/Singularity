package com.siberalt.singularity.event;

import com.siberalt.singularity.event.subscription.DefaultSubscription;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.event.subscription.SubscriptionManager;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;
import com.siberalt.singularity.event.subscription.exception.UnsupportedEventTypeException;
import com.siberalt.singularity.event.trigger.TriggerManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class EventManager implements EventDispatcher, SubscriptionManager, AutoCloseable {
    private final ConcurrentHashMap<SubscriptionSpec<?>, List<EventHandler<?>>> subscriptionsSpecs = new ConcurrentHashMap<>();
    private volatile EventMatcher eventMatcher = new NullEventMatcher();
    private final Executor executor;
    private volatile TriggerManager triggerManager = new NullTriggerManager();
    private final ConcurrentHashMap<EventHandler<?>, DefaultSubscription> handlerSubscriptions = new ConcurrentHashMap<>();
    private final Set<Class<? extends Event>> supportedEventTypes;

    public EventManager(Executor executor, Set<Class<? extends Event>> supportedEventTypes) {
        this.executor = executor;
        this.supportedEventTypes = supportedEventTypes;
    }

    public void setTriggerManager(TriggerManager triggerManager) {
        this.triggerManager = triggerManager;
    }

    public void setEventMatcher(EventMatcher eventMatcher) {
        this.eventMatcher = eventMatcher;
    }

    @Override
    public CompletableFuture<Void> dispatch(Event event) {
        List<CompletableFuture<Void>> futures = subscriptionsSpecs.entrySet().stream()
            .filter(entry -> matches(entry.getKey(), event))
            .flatMap(entry -> entry.getValue().stream().map(handler -> handleEvent(handler, event)))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public <T extends Event> Subscription subscribe(SubscriptionSpec<T> spec, EventHandler<T> handler) {
        // Check if the event type is supported
        if (!supportedEventTypes.contains(spec.getEventType())) {
            throw new UnsupportedEventTypeException(spec.getEventType());
        }

        // Check if the subscriptionSpec already exists
        List<EventHandler<?>> handlers = subscriptionsSpecs.computeIfAbsent(spec, k -> {
            triggerManager.enable(spec, this);
            // If it doesn't exist, create a new list for handlers
            return new CopyOnWriteArrayList<>();
        });
        // If not, create a new list and add the handler
        handlers.add(handler);

        // Return a Subscription object that allows for unsubscription
        DefaultSubscription subscription = newSubscription(spec, handler, handlers);
        handlerSubscriptions.put(handler, subscription);

        return subscription;
    }

    private <T extends Event> DefaultSubscription newSubscription(
        SubscriptionSpec<T> spec,
        EventHandler<T> handler,
        List<EventHandler<?>> handlers
    ) {
        Runnable onUnsubscribe = () -> {
            handlers.remove(handler);
            // Atomically drop the spec (and disable its trigger) only if no handler raced in
            // between the removal above and this check.
            subscriptionsSpecs.computeIfPresent(spec, (s, hs) -> {
                if (!hs.isEmpty()) {
                    return hs;
                }
                triggerManager.disable(s);
                return null;
            });
        };

        return new DefaultSubscription(true, onUnsubscribe);
    }

    private boolean matches(SubscriptionSpec<?> subscription, Event event) {
        // Check if the event type matches the subscription
        if (!subscription.getEventType().isAssignableFrom(event.getClass())) {
            return false;
        }
        // Check if the event matches the subscription criteria
        return eventMatcher.matches(subscription, event);
    }

    private CompletableFuture<Void> handleEvent(EventHandler<?> handler, Event event) {
        return CompletableFuture.runAsync(() -> {
            DefaultSubscription subscription = handlerSubscriptions.get(handler);

            if (subscription == null || !subscription.isActive()) {
                // If the subscription is inactive, skip handling
                return;
            }

            @SuppressWarnings("unchecked")
            EventHandler<Event> eventHandler = (EventHandler<Event>) handler;
            try {
                eventHandler.handle(event, subscription);
            } catch (Throwable throwable) {
                subscription.addError(throwable);
                throw new CompletionException(throwable);
            }
        }, executor);
    }

    @Override
    public void close() {
        subscriptionsSpecs.keySet().forEach(triggerManager::disable);
        subscriptionsSpecs.clear();
        handlerSubscriptions.clear();
    }
}
